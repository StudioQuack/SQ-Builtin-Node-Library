package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.officialapi.DataNodeSupport.DataHttpException;
import com.studioquack.nodelibrary.nodes.officialapi.DataNodeSupport.HttpResult;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.StudioQuackHelper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * arXiv Atom API node — search preprints or fetch papers by id.
 *
 * <p>Unauthenticated Atom feed client. Thread-safe with no mutable instance state.
 */
public final class ArxivNode implements NodeHandler {

    static final String DEFAULT_ID = "f809e1c3-1ca4-4888-89a0-53155478ab70";

    /** Official arXiv Atom export endpoint. */
    private static final String API_BASE = "http://export.arxiv.org/api/query";
    private static final String ARXIV_ABS_BASE = "https://arxiv.org/abs/";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";
    private static final String OPENSEARCH_NS = "http://a9.com/-/spec/opensearch/1.1/";
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int MAX_RESULTS_CAP = 100;

    private static final Pattern ENTRY_BLOCK = Pattern.compile(
            "<entry\\b[^>]*>(.*?)</entry>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOTAL_RESULTS = Pattern.compile(
            "<(?:[\\w.-]+:)?totalResults\\b[^>]*>([^<]*)</(?:[\\w.-]+:)?totalResults>",
            Pattern.CASE_INSENSITIVE);

    private final String templateId;

    public ArxivNode() {
        this(DEFAULT_ID);
    }

    public ArxivNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Dispatch one invoke. Failures return {@code output.error}; never thrown.
     * Never logs {@code helperToken}, query text, or abstracts.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unused) {
        HelperClient logs = DataNodeSupport.logs(request);
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataNodeSupport.configParams(request);
            Map<String, Object> in = DataNodeSupport.inputPort(request);
            int timeout = DataNodeSupport.positiveInt(cfg.get("timeout_seconds"), DEFAULT_TIMEOUT);

            String action = DataNodeSupport.firstNonBlank(in, "action");
            if (action.isEmpty()) {
                action = "search";
            }
            logs.log(Map.of("message", "arxiv start", "action", action));

            Map<String, Object> output = switch (action) {
                case "search" -> search(in, timeout);
                case "get_paper" -> getPaper(in, timeout);
                default -> errorMap("Unknown action: " + action + ". Use 'search' or 'get_paper'.");
            };
            Object returned = output.get("papers_returned");
            logs.log(Map.of("message", "arxiv done", "action", action,
                    "papers_returned", returned == null ? 0 : returned));
            return DataNodeSupport.outputPorts(output);
        } catch (ArxivHttpStatusException e) {
            logs.log("arxiv http status");
            return DataSupport.fail(e.getMessage());
        } catch (ArxivXmlException e) {
            logs.log("arxiv xml: parse failure");
            return DataSupport.fail("arXiv XML Parse Error: " + e.getMessage());
        } catch (DataHttpException e) {
            logs.log("arxiv transport: " + e.getClass().getSimpleName());
            return DataSupport.fail(transportError(e));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            logs.log("arxiv internal: " + e.getClass().getSimpleName());
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Full-text / fielded search. {@code output.query} is the original, not the cat: wrapper. */
    private static Map<String, Object> search(Map<String, Object> in, int timeout) {
        String query = DataNodeSupport.firstNonBlank(in, "query", "q");
        if (query.isEmpty()) {
            return errorMap("query is required for search action.");
        }
        String category = in.get("category") == null ? null : DataNodeSupport.str(in.get("category")).trim();
        String searchQuery = query;
        if (category != null && !category.isEmpty()) {
            searchQuery = "cat:" + category + " AND (" + query + ")";
        }
        int start = DataNodeSupport.toInt(in.get("start"), 0);
        int maxResults = Math.min(DataNodeSupport.toInt(in.get("max_results"), DEFAULT_MAX_RESULTS),
                MAX_RESULTS_CAP);
        String sortBy = DataNodeSupport.firstNonBlank(in, "sort_by");
        if (sortBy.isEmpty()) {
            sortBy = "relevance";
        }
        String sortOrder = DataNodeSupport.firstNonBlank(in, "sort_order");
        if (sortOrder.isEmpty()) {
            sortOrder = "descending";
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("search_query", searchQuery);
        params.put("start", start);
        params.put("max_results", maxResults);
        params.put("sortBy", sortBy);
        params.put("sortOrder", sortOrder);

        Feed feed = fetchFeed(params, timeout);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "search");
        out.put("query", query);
        out.put("category", category);
        out.put("total_results", feed.total);
        out.put("papers_returned", feed.papers.size());
        out.put("start", start);
        out.put("papers", feed.papers);
        return out;
    }

    /** Lookup by {@code arxiv_id} (wins) or {@code arxiv_ids} (list or CSV). */
    private static Map<String, Object> getPaper(Map<String, Object> in, int timeout) {
        List<String> ids = new ArrayList<>(DataNodeSupport.stringList(in.get("arxiv_ids")));
        String single = DataNodeSupport.firstNonBlank(in, "arxiv_id");
        if (!single.isEmpty()) {
            ids = List.of(single);
        }
        if (ids.isEmpty()) {
            return errorMap("arxiv_id or arxiv_ids is required for get_paper action.");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id_list", String.join(",", ids));
        params.put("max_results", ids.size());
        Feed feed = fetchFeed(params, timeout);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "get_paper");
        out.put("papers_returned", feed.papers.size());
        out.put("papers", feed.papers);
        return out;
    }

    /** GET Atom XML. arXiv 503s unidentified clients — always send the product UA. */
    private static Feed fetchFeed(Map<String, Object> params, int timeout) {
        HttpResult r = DataNodeSupport.get(API_BASE,
                Map.of("User-Agent", DataNodeSupport.USER_AGENT), params, timeout);
        if (!r.ok()) {
            throw new ArxivHttpStatusException(httpError(r.status, r.url));
        }
        return parseFeed(r.body);
    }

    private static Feed parseFeed(String body) {
        try {
            Document doc = parseXml(body);
            Element root = doc.getDocumentElement();
            if (root == null) {
                throw new ArxivXmlException("missing document element");
            }
            return new Feed(readTotal(root), readEntries(root));
        } catch (ArxivXmlException e) {
            throw e;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            // Regex is last-resort when the hardened parser rejects the document.
            Feed fallback = parseFeedRegex(body);
            if (!fallback.papers.isEmpty() || (body != null
                    && (body.contains("<entry") || body.contains("<feed")))) {
                return fallback;
            }
            String msg = e.getMessage();
            throw new ArxivXmlException(msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg);
        }
    }

    /**
     * XXE-hardened namespace-aware parser. Features are best-effort: a JDK that
     * rejects one flag still applies the rest.
     */
    private static Document parseXml(String body) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setExpandEntityReferences(false);
        dbf.setXIncludeAware(false);
        dbf.setValidating(false);
        feature(dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        feature(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
        feature(dbf, "http://xml.org/sax/features/external-general-entities", false);
        feature(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
        feature(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // parser does not support the attribute
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        InputSource src = new InputSource(new StringReader(body == null ? "" : body));
        src.setEncoding("UTF-8");
        return db.parse(src);
    }

    private static void feature(DocumentBuilderFactory dbf, String name, boolean value) {
        try {
            dbf.setFeature(name, value);
        } catch (ParserConfigurationException ignored) {
            // optional hardening; parse still proceeds
        }
    }

    private static int readTotal(Element root) {
        for (Element el : kidsNs(root, OPENSEARCH_NS, "totalResults")) {
            String t = text(el);
            if (!t.isEmpty()) {
                return Integer.parseInt(t);
            }
        }
        return 0;
    }

    private static List<Map<String, Object>> readEntries(Element root) {
        List<Map<String, Object>> papers = new ArrayList<>();
        for (Element entry : kidsNs(root, ATOM_NS, "entry")) {
            Map<String, Object> row = parseEntry(entry);
            if (!DataNodeSupport.str(row.get("title")).isEmpty()) {
                papers.add(row);
            }
        }
        return papers;
    }

    /** Match Python {@code _parse_entry} field names ({@code abstract}, not summary). */
    private static Map<String, Object> parseEntry(Element entry) {
        String idRaw = firstText(entry, ATOM_NS, "id");
        String arxivId = idRaw.contains("/abs/") ? idRaw.substring(idRaw.indexOf("/abs/") + 5) : idRaw;
        List<String> authors = new ArrayList<>();
        for (Element author : kidsNs(entry, ATOM_NS, "author")) {
            String name = firstText(author, ATOM_NS, "name");
            if (!name.isEmpty()) {
                authors.add(name);
            }
        }
        List<String> categories = new ArrayList<>();
        for (Element cat : kidsNs(entry, ATOM_NS, "category")) {
            String term = attr(cat, "term");
            if (!term.isEmpty()) {
                categories.add(term);
            }
        }
        String primary = "";
        List<Element> primaryEl = kidsNs(entry, ARXIV_NS, "primary_category");
        if (!primaryEl.isEmpty()) {
            primary = attr(primaryEl.get(0), "term");
        }
        String pdfUrl = "";
        for (Element link : kidsNs(entry, ATOM_NS, "link")) {
            if ("pdf".equals(attr(link, "title"))) {
                pdfUrl = attr(link, "href");
                break;
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("arxiv_id", arxivId);
        row.put("title", DataNodeSupport.collapseWs(firstText(entry, ATOM_NS, "title")));
        row.put("authors", authors);
        row.put("abstract", DataNodeSupport.collapseWs(firstText(entry, ATOM_NS, "summary")));
        row.put("published", firstText(entry, ATOM_NS, "published"));
        row.put("updated", firstText(entry, ATOM_NS, "updated"));
        row.put("doi", firstText(entry, ARXIV_NS, "doi"));
        row.put("journal_ref", firstText(entry, ARXIV_NS, "journal_ref"));
        row.put("primary_category", primary);
        row.put("categories", categories);
        row.put("comment", firstText(entry, ARXIV_NS, "comment"));
        row.put("url", ARXIV_ABS_BASE + arxivId);
        row.put("pdf_url", pdfUrl.isEmpty() ? "https://arxiv.org/pdf/" + arxivId : pdfUrl);
        return row;
    }

    private static Feed parseFeedRegex(String xml) {
        String src = xml == null ? "" : xml;
        int total = 0;
        Matcher tot = TOTAL_RESULTS.matcher(src);
        if (tot.find()) {
            try {
                total = Integer.parseInt(tot.group(1).trim());
            } catch (NumberFormatException ignored) {
                total = 0;
            }
        }
        List<Map<String, Object>> papers = new ArrayList<>();
        Matcher em = ENTRY_BLOCK.matcher(src);
        while (em.find()) {
            Map<String, Object> row = parseEntryRegex(em.group(1));
            if (!DataNodeSupport.str(row.get("title")).isEmpty()) {
                papers.add(row);
            }
        }
        return new Feed(total, papers);
    }

    private static Map<String, Object> parseEntryRegex(String block) {
        String idRaw = tagInner(block, "id");
        String arxivId = idRaw.contains("/abs/") ? idRaw.substring(idRaw.indexOf("/abs/") + 5) : idRaw;
        List<String> authors = new ArrayList<>();
        Matcher am = tagBlocks(block, "author");
        while (am.find()) {
            String name = tagInner(am.group(1), "name");
            if (!name.isEmpty()) {
                authors.add(name);
            }
        }
        List<String> categories = new ArrayList<>();
        Matcher cm = Pattern.compile(
                "<(?:[\\w.-]+:)?category\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE).matcher(block);
        while (cm.find()) {
            String term = namedAttr(cm.group(1), "term");
            if (!term.isEmpty()) {
                categories.add(term);
            }
        }
        String primary = "";
        Matcher pm = Pattern.compile(
                "<(?:[\\w.-]+:)?primary_category\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE).matcher(block);
        if (pm.find()) {
            primary = namedAttr(pm.group(1), "term");
        }
        String pdfUrl = "";
        Matcher lm = Pattern.compile(
                "<(?:[\\w.-]+:)?link\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE).matcher(block);
        while (lm.find()) {
            String attrs = lm.group(1);
            if ("pdf".equals(namedAttr(attrs, "title"))) {
                pdfUrl = namedAttr(attrs, "href");
                break;
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("arxiv_id", arxivId);
        row.put("title", DataNodeSupport.collapseWs(tagInner(block, "title")));
        row.put("authors", authors);
        row.put("abstract", DataNodeSupport.collapseWs(tagInner(block, "summary")));
        row.put("published", tagInner(block, "published"));
        row.put("updated", tagInner(block, "updated"));
        row.put("doi", tagInner(block, "doi"));
        row.put("journal_ref", tagInner(block, "journal_ref"));
        row.put("primary_category", primary);
        row.put("categories", categories);
        row.put("comment", tagInner(block, "comment"));
        row.put("url", ARXIV_ABS_BASE + arxivId);
        row.put("pdf_url", pdfUrl.isEmpty() ? "https://arxiv.org/pdf/" + arxivId : pdfUrl);
        return row;
    }

    private static Matcher tagBlocks(String block, String tag) {
        return Pattern.compile(
                "<(?:[\\w.-]+:)?" + tag + "\\b[^>]*>(.*?)</(?:[\\w.-]+:)?" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(block);
    }

    private static String tagInner(String block, String tag) {
        Matcher m = tagBlocks(block, tag);
        if (!m.find()) {
            return "";
        }
        return m.group(1).replaceAll("<[^>]+>", "").trim();
    }

    private static String namedAttr(String attrs, String name) {
        Matcher m = Pattern.compile(
                Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(attrs);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Direct children matching local name; prefer namespace URI, else unprefixed. */
    private static List<Element> kidsNs(Element parent, String ns, String local) {
        List<Element> matched = new ArrayList<>();
        List<Element> localOnly = new ArrayList<>();
        if (parent == null) {
            return matched;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !local.equals(localName(e))) {
                continue;
            }
            String uri = e.getNamespaceURI();
            if (ns.equals(uri)) {
                matched.add(e);
            } else {
                localOnly.add(e);
            }
        }
        return matched.isEmpty() ? localOnly : matched;
    }

    private static String localName(Element e) {
        String ln = e.getLocalName();
        if (ln != null && !ln.isEmpty()) {
            return ln;
        }
        String tag = e.getTagName();
        int c = tag.indexOf(':');
        return c >= 0 ? tag.substring(c + 1) : tag;
    }

    private static String firstText(Element parent, String ns, String local) {
        List<Element> found = kidsNs(parent, ns, local);
        return found.isEmpty() ? "" : text(found.get(0));
    }

    private static String text(Element e) {
        if (e == null) {
            return "";
        }
        String t = e.getTextContent();
        return t == null ? "" : t.trim();
    }

    private static String attr(Element e, String name) {
        if (e == null) {
            return "";
        }
        String v = e.getAttribute(name);
        return v == null ? "" : v;
    }

    private static String httpError(int status, String url) {
        String msg = "arXiv API Error: HTTP " + status + " for url: " + url;
        return withRateLimitHint(msg, status);
    }

    private static String transportError(DataHttpException e) {
        if (e.timeout) {
            String raw = e.getMessage();
            return "arXiv API Error: " + (raw == null || raw.isBlank() ? "timed out" : raw);
        }
        String raw = e.getMessage();
        String msg = "Error: DataHttpException: " + (raw == null ? "" : raw);
        return withRateLimitHint(msg, e.status);
    }

    private static String withRateLimitHint(String message, Integer status) {
        if ((status != null && status == 503) || (message != null && message.contains("503"))) {
            return message + " | arXiv is rate-limiting. Wait 3+ seconds between requests.";
        }
        return message;
    }

    private static Map<String, Object> errorMap(String message) {
        return DataSupport.fail(message);
    }

    private record Feed(int total, List<Map<String, Object>> papers) {}

    /** XML parse failure → {@code arXiv XML Parse Error}. */
    private static final class ArxivXmlException extends RuntimeException {
        ArxivXmlException(String message) { super(message); }
    }

    /** HTTP 4xx/5xx mapped onto {@code output.error}. */
    private static final class ArxivHttpStatusException extends RuntimeException {
        ArxivHttpStatusException(String message) { super(message); }
    }
}
