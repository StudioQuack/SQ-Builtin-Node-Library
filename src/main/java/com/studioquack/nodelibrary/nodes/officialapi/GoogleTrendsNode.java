package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unofficial Google Trends node (pytrends HTTP port; no API key).
 *
 * <p>Closed by default: {@code accept_google_terms} must be true or the handler
 * raises {@code INVALID_INPUT} and never contacts Google. The scraper path is
 * unchanged for operators who opt in.
 *
 * <p>Thread-safe: no shared cookie jar. Each {@link #handle} builds a dedicated
 * {@link HttpClient} with its own {@link CookieManager} so NID cookies cannot
 * leak across tenants. Never logs cookies or widget tokens.
 */
public final class GoogleTrendsNode implements NodeHandler {

    public static final String DEFAULT_TEMPLATE_ID = "8c7d24b0-8434-4a06-8660-ff49c1d0788c";

    private static final String BASE = "https://trends.google.com/trends";
    private static final String HL = "en-US";
    private static final String TZ = "0";
    private static final int MAX_KEYWORDS = 5;

    private static final java.util.Set<String> ACTIONS = java.util.Set.of(
            "interest_over_time", "related_queries", "related_topics",
            "interest_by_region", "trending_searches", "suggestions");
    private static final int TRENDING_CAP = 20;
    private static final int REGION_CAP = 50;

    private final String templateId;

    public GoogleTrendsNode() {
        this.templateId = DataSupport.templateId("SQ_TEMPLATE_ID_GOOGLE_TRENDS", DEFAULT_TEMPLATE_ID);
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Dispatch one Trends action. Failures raise a BPMN error
     * ({@link DataSupport#invalidInput} for the ToS gate and input checks,
     * {@link DataSupport#fail} for provider/transport errors).
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unused) {
        try {
            Map<String, Object> cfg = DataSupport.readConfig(request);
            // Unofficial Trends endpoints stay intact for opt-in operators, but the
            // default is closed: no Google call without an explicit ToS acceptance.
            if (!DataSupport.inBool(cfg, "accept_google_terms", false)) {
                return DataSupport.invalidInput(
                        "Google Trends is an unofficial endpoint and is not called unless you accept "
                                + "Google's Terms of Service. Set configurationParameter accept_google_terms "
                                + "to true only if you accept https://policies.google.com/terms and will use "
                                + "the data as those terms allow. Prefer official Google products (Google Ads API, "
                                + "Search Console API, or licensed data partners). No request was sent.");
            }
            Map<String, Object> in = DataSupport.readInput(request, unused);
            int timeout = DataSupport.cfgInt(cfg, "timeout_seconds", 30);
            Object rawKw = in.get("keywords");
            if (!(rawKw instanceof List<?>)) {
                return DataSupport.invalidInput("keywords is required and must be a list of strings (max 5).");
            }
            List<String> keywords = DataSupport.inStringList(in, "keywords");
            if (keywords.isEmpty()) {
                return DataSupport.invalidInput("keywords is required and must be a list of strings (max 5).");
            }
            if (keywords.size() > MAX_KEYWORDS) {
                keywords = new ArrayList<>(keywords.subList(0, MAX_KEYWORDS));
            }
            String action = DataSupport.inStr(in, "action", "interest_over_time");
            Ctx ctx = new Ctx(timeout, keywords,
                    DataSupport.inStr(in, "timeframe", "now 7-d"),
                    DataSupport.inStr(in, "geo", ""),
                    DataSupport.inInt(in, "category", 0),
                    DataSupport.inStr(in, "country", "united_states"),
                    normalizeResolution(DataSupport.inStr(in, "resolution", "COUNTRY")));
            // Validate the action before any network use: an unknown action must
            // never contact Google (the ToS gate only covers known operations).
            if (!ACTIONS.contains(action)) {
                return DataSupport.invalidInput("Unknown action: " + action
                        + ". Use interest_over_time, related_queries, related_topics, "
                        + "interest_by_region, trending_searches, or suggestions.");
            }
            try (HttpClient client = newClient(timeout)) {
                warmup(client, timeout);
                return switch (action) {
                    case "interest_over_time" -> interestOverTime(client, ctx);
                    case "related_queries" -> related(client, ctx, false);
                    case "related_topics" -> related(client, ctx, true);
                    case "interest_by_region" -> interestByRegion(client, ctx);
                    case "trending_searches" -> trendingSearches(client, ctx);
                    default -> suggestions(client, ctx); // "suggestions" — validated above
                };
            }
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (err.contains("429") || err.toLowerCase(Locale.ROOT).contains("too many requests")) {
                return DataSupport.fail(
                        "Google Trends rate limited. Wait 60 seconds before retrying. Error: " + err);
            }
            return DataSupport.fail("Error: " + e.getClass().getSimpleName() + ": " + err);
        }
    }

    /** Isolated cookie store per invoke — never reuse {@link DataSupport#httpClient()}. */
    private static HttpClient newClient(int timeoutSeconds) {
        int t = Math.max(1, timeoutSeconds);
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(t))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * pytrends GetGoogleCookie: GET explore/?geo=US and keep NID. Status is ignored.
     * If explore is huge or 429, ping the smaller Trends UI batchexecute URL.
     */
    private static void warmup(HttpClient client, int timeout) {
        DataSupport.HttpResp explore = get(client, BASE + "/explore/?geo=US", timeout);
        boolean huge = explore.body() != null && explore.body().length() > 100_000;
        if (explore.status() == 429 || !DataSupport.httpOk(explore.status()) || huge) {
            get(client, "https://trends.google.com/_/TrendsUi/data/batchexecute", timeout);
        }
    }

    private static Map<String, Object> interestOverTime(HttpClient client, Ctx ctx) {
        Map<String, Object> ts = widgetById(exploreWidgets(client, ctx, "interest_over_time"), "TIMESERIES");
        if (ts == null || ts.get("token") == null) {
            throw handshake("interest_over_time", "TIMESERIES widget token missing");
        }
        Object json = requireJson(widgetGet(client, "/api/widgetdata/multiline", ts, ctx.timeout),
                "interest_over_time");
        List<Object> timeline = Json.asList(Json.asMap(Json.asMap(json).get("default")).get("timelineData"));
        if (timeline.isEmpty()) {
            return DataSupport.ports(okEmptyData());
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object row : timeline) {
            Map<String, Object> point = Json.asMap(row);
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("date", ymd(point.get("time")));
            List<Object> values = Json.asList(point.get("value"));
            for (int i = 0; i < ctx.keywords.size() && i < values.size(); i++) {
                rec.put(ctx.keywords.get(i), toInt(values.get(i)));
            }
            records.add(rec);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("data_points", records.size());
        out.put("data", records);
        return DataSupport.ports(out);
    }

    /** related_topics: empty lists on non-429 widget failure (scraper sessions), not a hard error. */
    private static Map<String, Object> related(HttpClient client, Ctx ctx, boolean topics) {
        String action = topics ? "related_topics" : "related_queries";
        Map<String, Object> perKw = emptyRelated(ctx.keywords);
        List<Map<String, Object>> found = widgetsContaining(exploreWidgets(client, ctx, action),
                topics ? "RELATED_TOPICS" : "RELATED_QUERIES");
        for (int i = 0; i < found.size() && i < ctx.keywords.size(); i++) {
            Map<String, Object> w = found.get(i);
            String kw = matchKeyword(ctx.keywords, keywordOf(w, ""), i);
            DataSupport.HttpResp r = widgetGet(client, "/api/widgetdata/relatedsearches", w, ctx.timeout);
            if (topics) {
                throwIfRateLimited(r);
                Object parsed = DataSupport.httpOk(r.status()) ? DataSupport.parseJson(r.body()) : null;
                if (parsed != null) {
                    perKw.put(kw, rankedBlock(parsed, true));
                }
            } else {
                perKw.put(kw, rankedBlock(requireJson(r, action), false));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put(topics ? "related_topics" : "related_queries", perKw);
        return DataSupport.ports(out);
    }

    private static Map<String, Object> interestByRegion(HttpClient client, Ctx ctx) {
        Map<String, Object> geo = widgetById(exploreWidgets(client, ctx, "interest_by_region"), "GEO_MAP");
        if (geo == null || geo.get("token") == null) {
            throw handshake("interest_by_region", "GEO_MAP widget token missing");
        }
        Map<String, Object> req = new LinkedHashMap<>(Json.asMap(geo.get("request")));
        req.put("resolution", ctx.resolution);
        req.put("includeLowSearchVolumeGeos", Boolean.TRUE);
        Map<String, Object> patched = new LinkedHashMap<>(geo);
        patched.put("request", req);
        Object json = requireJson(widgetGet(client, "/api/widgetdata/comparedgeo", patched, ctx.timeout),
                "interest_by_region");
        List<Object> rows = Json.asList(Json.asMap(Json.asMap(json).get("default")).get("geoMapData"));
        if (rows.isEmpty()) {
            return DataSupport.ports(okEmptyData());
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> g = Json.asMap(row);
            List<Object> values = Json.asList(g.get("value"));
            if (!anyPositive(values)) {
                continue;
            }
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("geoName", g.get("geoName") == null ? "" : g.get("geoName"));
            Object code = g.get("geoCode") != null ? g.get("geoCode") : g.get("coordinates");
            rec.put("geoCode", code == null ? "" : code);
            for (int i = 0; i < ctx.keywords.size() && i < values.size(); i++) {
                rec.put(ctx.keywords.get(i), toInt(values.get(i)));
            }
            records.add(rec);
        }
        records.sort((a, b) -> String.valueOf(a.get("geoName")).compareTo(String.valueOf(b.get("geoName"))));
        if (records.size() > REGION_CAP) {
            records = new ArrayList<>(records.subList(0, REGION_CAP));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("regions_returned", records.size());
        out.put("data", records);
        return DataSupport.ports(out);
    }

    private static Map<String, Object> trendingSearches(HttpClient client, Ctx ctx) {
        DataSupport.HttpResp r = get(client, BASE + "/hottrends/visualize/internal/data", ctx.timeout);
        Object json = requireJson(r, "trending_searches");
        Map<String, Object> keyed = Json.asMap(json);
        if (!keyed.containsKey(ctx.country)) {
            throw DataSupport.failError(
                    "Error: no trending searches for country '" + ctx.country
                            + "' (hottrends pn key not present).");
        }
        List<String> trending = new ArrayList<>();
        for (Object row : Json.asList(keyed.get(ctx.country))) {
            if (trending.size() >= TRENDING_CAP) {
                break;
            }
            if (row instanceof List<?> list && !list.isEmpty()) {
                trending.add(String.valueOf(list.get(0)));
            } else if (row != null) {
                trending.add(String.valueOf(row));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("trending", trending);
        return DataSupport.ports(out);
    }

    private static Map<String, Object> suggestions(HttpClient client, Ctx ctx) {
        String kw = ctx.keywords.get(0);
        String url = BASE + "/api/autocomplete/" + DataSupport.urlEncode(kw) + "?"
                + DataSupport.query(Map.of("hl", HL));
        Object json = requireJson(get(client, url, ctx.timeout), "suggestions");
        List<Object> topics = Json.asList(Json.asMap(Json.asMap(json).get("default")).get("topics"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("suggestions", topics);
        return DataSupport.ports(out);
    }

    /** POST /api/explore with comparisonItem JSON (pytrends token handshake). */
    private static List<Object> exploreWidgets(HttpClient client, Ctx ctx, String action) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String kw : ctx.keywords) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("keyword", kw);
            item.put("geo", ctx.geo);
            item.put("time", ctx.timeframe);
            items.add(item);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("comparisonItem", items);
        req.put("category", ctx.category);
        req.put("property", "");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("hl", HL);
        q.put("tz", TZ);
        q.put("req", Json.write(req));
        DataSupport.HttpResp r = DataSupport.exchange(client, "POST",
                BASE + "/api/explore?" + DataSupport.query(q), headers(true), "", ctx.timeout);
        Object json = requireJson(r, action);
        List<Object> widgets = Json.asList(Json.asMap(json).get("widgets"));
        if (widgets.isEmpty()) {
            throw handshake(action, "explore returned no widgets");
        }
        return widgets;
    }

    private static DataSupport.HttpResp widgetGet(HttpClient client, String path,
                                                  Map<String, Object> widget, int timeout) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("hl", HL);
        q.put("tz", TZ);
        q.put("req", Json.write(widget.get("request")));
        q.put("token", widget.get("token") == null ? "" : String.valueOf(widget.get("token")));
        return get(client, BASE + path + "?" + DataSupport.query(q), timeout);
    }

    private static DataSupport.HttpResp get(HttpClient client, String url, int timeout) {
        return DataSupport.exchange(client, "GET", url, headers(false), null, timeout);
    }

    private static Map<String, String> headers(boolean post) {
        return post
                ? DataSupport.headers("accept-language", HL, "Content-Type", "application/x-www-form-urlencoded")
                : DataSupport.headers("accept-language", HL);
    }

    private static Object requireJson(DataSupport.HttpResp r, String action) {
        throwIfRateLimited(r);
        if (r.error() != null) {
            throw DataSupport.failError("Error: " + r.error());
        }
        if (!DataSupport.httpOk(r.status())) {
            if ("trending_searches".equals(action) || "suggestions".equals(action)) {
                throw DataSupport.failError(
                        "Error: " + DataSupport.httpStatusMessage(r.status(), r.body())
                                + " for action '" + action + "'");
            }
            throw handshake(action, DataSupport.httpStatusMessage(r.status(), r.body()));
        }
        Object json = DataSupport.parseJson(r.body()); // strips XSSI )]}' by seeking first { or [
        if (json == null) {
            throw handshake(action, "response was not JSON");
        }
        return json;
    }

    private static void throwIfRateLimited(DataSupport.HttpResp r) {
        String body = r.body();
        if (r.status() == 429 || (body != null && body.contains("Too Many Requests"))) {
            String detail = r.error() != null && !r.error().isBlank() ? r.error() : ("HTTP " + r.status());
            throw DataSupport.failError(
                    "Google Trends rate limited. Wait 60 seconds before retrying. Error: " + detail);
        }
    }

    private static studioquack.StudioQuackError handshake(String action, String why) {
        return DataSupport.failError(
                "Google Trends unofficial handshake failed for action '" + action
                        + "' (cookie/token). " + why);
    }

    private static Map<String, Object> widgetById(List<Object> widgets, String id) {
        for (Object w : widgets) {
            Map<String, Object> m = Json.asMap(w);
            if (id.equals(String.valueOf(m.get("id")))) {
                return m;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> widgetsContaining(List<Object> widgets, String fragment) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object w : widgets) {
            Map<String, Object> m = Json.asMap(w);
            if (String.valueOf(m.get("id")).contains(fragment)) {
                out.add(m);
            }
        }
        return out;
    }

    private static String matchKeyword(List<String> keywords, String extracted, int index) {
        if (keywords.contains(extracted)) {
            return extracted;
        }
        return index < keywords.size() ? keywords.get(index) : extracted;
    }

    private static String keywordOf(Map<String, Object> widget, String fallback) {
        List<Object> kws = Json.asList(Json.asMap(Json.asMap(Json.asMap(widget.get("request"))
                .get("restriction")).get("complexKeywordsRestriction")).get("keyword"));
        if (!kws.isEmpty()) {
            String v = String.valueOf(Json.asMap(kws.get(0)).get("value"));
            if (!v.isBlank() && !"null".equals(v)) {
                return v;
            }
        }
        return fallback;
    }

    private static Map<String, Object> emptyRelated(List<String> keywords) {
        Map<String, Object> perKw = new LinkedHashMap<>();
        Map<String, Object> empty = Map.of("top", List.of(), "rising", List.of());
        for (String kw : keywords) {
            perKw.put(kw, new LinkedHashMap<>(empty));
        }
        return perKw;
    }

    private static Map<String, Object> rankedBlock(Object json, boolean topics) {
        List<Object> ranked = Json.asList(Json.asMap(Json.asMap(json).get("default")).get("rankedList"));
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("top", rankedRows(ranked, 0, topics));
        block.put("rising", rankedRows(ranked, 1, topics));
        return block;
    }

    private static List<Map<String, Object>> rankedRows(List<Object> ranked, int idx, boolean topics) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (idx >= ranked.size()) {
            return rows;
        }
        for (Object item : Json.asList(Json.asMap(ranked.get(idx)).get("rankedKeyword"))) {
            Map<String, Object> m = Json.asMap(item);
            if (topics) {
                rows.add(flatten(m, ""));
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("query", m.get("query"));
                row.put("value", m.get("value"));
                rows.add(row);
            }
        }
        return rows;
    }

    /** pandas json_normalize(sep='_'): nested maps become topic_mid, topic_title, … */
    private static Map<String, Object> flatten(Map<String, Object> src, String prefix) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "_" + e.getKey();
            if (e.getValue() instanceof Map<?, ?>) {
                out.putAll(flatten(Json.asMap(e.getValue()), key));
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    private static Map<String, Object> okEmptyData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("data", List.of());
        out.put("message", "No data returned.");
        return out;
    }

    private static String normalizeResolution(String raw) {
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "COUNTRY", "REGION", "CITY", "DMA" -> u;
            default -> raw.trim();
        };
    }

    private static String ymd(Object time) {
        try {
            long sec = Long.parseLong(String.valueOf(time).trim());
            return Instant.ofEpochSecond(sec).atZone(ZoneOffset.UTC).toLocalDate().toString();
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return String.valueOf(time);
        }
    }

    private static boolean anyPositive(List<Object> values) {
        for (Object v : values) {
            if (toInt(v) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(o).trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return 0;
        }
    }

    private record Ctx(int timeout, List<String> keywords, String timeframe, String geo,
                       int category, String country, String resolution) {
    }
}
