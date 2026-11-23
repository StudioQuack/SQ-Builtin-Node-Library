package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Google Sheets API v4 node ({@code 8a614338-622c-4b2e-b00b-87cf4fa53fa7}).
 *
 * <p>Auth is a per-request service-account JWT bearer grant (RS256, JDK crypto only).
 * Access tokens and JWT assertions are never stored on the instance or in static
 * fields. OUTPUT {@code output} is the result map ({@code status} on success,
 * {@code error} on failure) so schema fields are readable. A JSON string on the
 * port is not walked into those fields.
 *
 * <p>Thread-safe. Never logs {@code helperToken}, {@code private_key}, the service-account
 * JSON, the JWT assertion, or {@code access_token}. Error strings pass through
 * {@link DataSupport#redact}.
 */
public final class GoogleSheetsNode implements NodeHandler {

    public static final String DEFAULT_TEMPLATE_ID = "8a614338-622c-4b2e-b00b-87cf4fa53fa7";

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SHEETS_BASE = "https://sheets.googleapis.com/v4/spreadsheets";
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final int DEFAULT_TIMEOUT = 25;
    private static final int API_ERR_BODY_CHARS = 500;
    private static final Pattern SPREADSHEET_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final String templateId;

    public GoogleSheetsNode() {
        this(DataSupport.templateId("SQ_TEMPLATE_ID_GOOGLE_SHEETS", DEFAULT_TEMPLATE_ID));
    }

    public GoogleSheetsNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Success returns OUTPUT {@code output}. Failures raise {@link studioquack.StudioQuackError}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        List<String> secrets = new ArrayList<>();
        try {
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.overlayConfig(
                    DataSupport.readInput(request, helper), cfg,
                    "action", "spreadsheet_id", "range", "ranges", "values",
                    "value_input_option", "insert_data_option");
            parseJsonContainers(in, "ranges", "values");
            int timeout = DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT);

            Object saRaw = cfg.get("service_account_json");
            if (saRaw instanceof String s) {
                secrets.add(s);
            }
            if (saRaw == null || (saRaw instanceof String str && str.isBlank())) {
                return invalid("service_account_json parameter is required. Provide the Google"
                        + " Service Account JSON key.", secrets);
            }
            Map<String, Object> sa = parseServiceAccount(saRaw);

            String action = DataSupport.inStr(in, "action", "read");
            String sheetId = DataSupport.inStr(in, "spreadsheet_id",
                    DataSupport.cfgStr(cfg, "spreadsheet_id", null));
            String range = DataSupport.inStr(in, "range", null);
            String valueInput = DataSupport.inStr(in, "value_input_option", "USER_ENTERED");
            String insertOpt = DataSupport.inStr(in, "insert_data_option", "INSERT_ROWS");

            if (sheetId == null) {
                return invalid("spreadsheet_id is required (parameter or input). Get it from the"
                        + " spreadsheet URL: docs.google.com/spreadsheets/d/{SPREADSHEET_ID}/edit",
                        secrets);
            }
            sheetId = sheetId.strip();
            if (!validSpreadsheetId(sheetId)) {
                return invalid("Invalid spreadsheet_id: '" + sheetId + "'. "
                        + "This looks like a placeholder, not a real Google Sheets ID. "
                        + "A valid ID is a ~44-character alphanumeric string from the spreadsheet URL: "
                        + "docs.google.com/spreadsheets/d/{SPREADSHEET_ID}/edit. "
                        + "Set the correct ID in the google_sheets node's spreadsheet_id config parameter.",
                        secrets);
            }

            String token = fetchAccessToken(sa, timeout, secrets);
            Map<String, String> headers = DataSupport.headers(
                    "Authorization", "Bearer " + token,
                    "Content-Type", "application/json");

            return switch (action) {
                case "read" -> doRead(sheetId, range, headers, timeout, secrets);
                case "batch_read" -> doBatchRead(sheetId, in.get("ranges"), headers, timeout, secrets);
                case "write" -> doWrite(sheetId, range, in.get("values"), valueInput, headers,
                        timeout, secrets);
                case "append" -> doAppend(sheetId, range, in.get("values"), valueInput, insertOpt,
                        headers, timeout, secrets);
                case "clear" -> doClear(sheetId, range, headers, timeout, secrets);
                case "get_metadata" -> doMetadata(sheetId, headers, timeout, secrets);
                default -> invalid("Unknown action: " + action
                        + ". Use read, batch_read, write, append, clear, or get_metadata.", secrets);
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return fail("Error: " + e.getClass().getSimpleName() + ": " + detail, secrets);
        }
    }

    private static Map<String, Object> doRead(String sheetId, String range, Map<String, String> headers,
                                              int timeout, List<String> secrets) {
        if (range == null) {
            return invalid("range is required for read action (e.g., 'Sheet1!A1:D10').", secrets);
        }
        String q = DataSupport.query(Map.of(
                "valueRenderOption", "FORMATTED_VALUE",
                "dateTimeRenderOption", "FORMATTED_STRING"));
        Map<String, Object> data = requireOk(DataSupport.get(
                valuesUrl(sheetId, range) + "?" + q, headers, timeout), secrets);
        List<Object> cells = listOrEmpty(data.get("values"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("read_range", data.get("range") == null ? range : data.get("range"));
        out.put("major_dimension", data.get("majorDimension") == null ? "ROWS" : data.get("majorDimension"));
        out.put("cell_data", cells);
        out.put("rows_returned", cells.size());
        return dump(out);
    }

    private static Map<String, Object> doBatchRead(String sheetId, Object rangesRaw,
                                                   Map<String, String> headers, int timeout,
                                                   List<String> secrets) {
        if (!(rangesRaw instanceof List<?> rangesList) || rangesList.isEmpty()) {
            return invalid("ranges (list of A1 notation strings) is required for batch_read.", secrets);
        }
        StringBuilder q = new StringBuilder();
        for (Object item : rangesList) {
            if (!q.isEmpty()) {
                q.append('&');
            }
            q.append("ranges=").append(DataSupport.urlEncode(EmulatorUtil.str(item)));
        }
        q.append("&valueRenderOption=FORMATTED_VALUE&dateTimeRenderOption=FORMATTED_STRING");
        Map<String, Object> data = requireOk(DataSupport.get(
                SHEETS_BASE + "/" + sheetId + "/values:batchGet?" + q, headers, timeout), secrets);
        List<Object> valueRanges = new ArrayList<>();
        for (Object vrObj : Json.asList(data.get("valueRanges"))) {
            Map<String, Object> vr = Json.asMap(vrObj);
            List<Object> values = listOrEmpty(vr.get("values"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("range", vr.get("range") == null ? "" : vr.get("range"));
            row.put("values", values);
            row.put("rows_returned", values.size());
            valueRanges.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("ranges_returned", valueRanges.size());
        out.put("value_ranges", valueRanges);
        return dump(out);
    }

    private static Map<String, Object> doWrite(String sheetId, String range, Object valuesRaw,
                                               String valueInput, Map<String, String> headers,
                                               int timeout, List<String> secrets) {
        if (range == null || !present(valuesRaw)) {
            return invalid("range and values (2D array) are required for write action.", secrets);
        }
        String q = DataSupport.query(Map.of(
                "valueInputOption", valueInput, "includeValuesInResponse", "false"));
        Map<String, Object> data = requireOk(DataSupport.put(
                valuesUrl(sheetId, range) + "?" + q, headers, valueRangeBody(range, valuesRaw),
                timeout), secrets);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("updated_range", orEmpty(data.get("updatedRange")));
        out.put("updated_rows", orZero(data.get("updatedRows")));
        out.put("updated_columns", orZero(data.get("updatedColumns")));
        out.put("updated_cells", orZero(data.get("updatedCells")));
        return dump(out);
    }

    private static Map<String, Object> doAppend(String sheetId, String range, Object valuesRaw,
                                                String valueInput, String insertOpt,
                                                Map<String, String> headers, int timeout,
                                                List<String> secrets) {
        if (range == null || !present(valuesRaw)) {
            return invalid("range and values (2D array) are required for append action.", secrets);
        }
        String q = DataSupport.query(Map.of(
                "valueInputOption", valueInput,
                "insertDataOption", insertOpt,
                "includeValuesInResponse", "false"));
        Map<String, Object> data = requireOk(DataSupport.post(
                valuesUrl(sheetId, range) + ":append?" + q, headers, valueRangeBody(range, valuesRaw),
                timeout), secrets);
        Map<String, Object> updates = Json.asMap(data.get("updates"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("updated_range", orEmpty(updates.get("updatedRange")));
        out.put("updated_rows", orZero(updates.get("updatedRows")));
        out.put("updated_cells", orZero(updates.get("updatedCells")));
        return dump(out);
    }

    private static Map<String, Object> doClear(String sheetId, String range, Map<String, String> headers,
                                               int timeout, List<String> secrets) {
        if (range == null) {
            return invalid("range is required for clear action.", secrets);
        }
        Map<String, Object> data = requireOk(DataSupport.post(
                valuesUrl(sheetId, range) + ":clear", headers, "{}", timeout), secrets);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("cleared_range", data.get("clearedRange") == null ? range : data.get("clearedRange"));
        return dump(out);
    }

    private static Map<String, Object> doMetadata(String sheetId, Map<String, String> headers,
                                                  int timeout, List<String> secrets) {
        String q = DataSupport.query(Map.of("fields", "spreadsheetId,properties.title,sheets.properties"));
        Map<String, Object> data = requireOk(DataSupport.get(
                SHEETS_BASE + "/" + sheetId + "?" + q, headers, timeout), secrets);
        List<Object> sheetsInfo = new ArrayList<>();
        for (Object sheetObj : Json.asList(data.get("sheets"))) {
            Map<String, Object> props = Json.asMap(Json.asMap(sheetObj).get("properties"));
            Map<String, Object> grid = Json.asMap(props.get("gridProperties"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sheet_id", props.get("sheetId"));
            row.put("title", props.get("title") == null ? "" : props.get("title"));
            row.put("index", props.get("index"));
            row.put("row_count", grid.get("rowCount"));
            row.put("column_count", grid.get("columnCount"));
            sheetsInfo.add(row);
        }
        Object title = Json.asMap(data.get("properties")).get("title");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("title", title == null ? "" : title);
        out.put("sheets_count", sheetsInfo.size());
        out.put("sheets", sheetsInfo);
        return dump(out);
    }

    /**
     * Mint a short-lived access token: RS256 JWT (iss=client_email, scope=spreadsheets,
     * aud=token endpoint) exchanged at {@code TOKEN_URL}. Token lives only on this stack.
     */
    private static String fetchAccessToken(Map<String, Object> sa, int timeout, List<String> secrets) {
        String email = DataSupport.trimToNull(EmulatorUtil.str(sa.get("client_email")));
        String pem = DataSupport.trimToNull(EmulatorUtil.str(sa.get("private_key")));
        if (email == null || pem == null) {
            throw DataSupport.failError("Error: KeyError: '"
                    + (email == null ? "client_email" : "private_key") + "'");
        }
        secrets.add(pem);
        String jwt = signJwtRs256(email, pem);
        secrets.add(jwt);
        String form = "grant_type=" + DataSupport.urlEncode(GRANT_TYPE)
                + "&assertion=" + DataSupport.urlEncode(jwt);
        DataSupport.HttpResp resp = DataSupport.post(TOKEN_URL,
                DataSupport.headers("Content-Type", "application/x-www-form-urlencoded"),
                form, timeout);
        Map<String, Object> json = requireOk(resp, secrets);
        String token = DataSupport.trimToNull(EmulatorUtil.str(json.get("access_token")));
        if (token == null) {
            throw DataSupport.failError("Google Sheets API Error: token response missing access_token");
        }
        secrets.add(token);
        return token;
    }

    /** Compact JWS: base64url(header).base64url(payload).base64url(SHA256withRSA). */
    private static String signJwtRs256(String clientEmail, String pem) {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", clientEmail);
        payload.put("scope", SCOPE);
        payload.put("aud", TOKEN_URL);
        payload.put("iat", now);
        payload.put("exp", now + 3600L);
        String header64 = b64Url(Json.write(header).getBytes(StandardCharsets.UTF_8));
        String payload64 = b64Url(Json.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = header64 + "." + payload64;
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(loadPkcs8(pem));
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64Url(sig.sign());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw DataSupport.failError("Error: " + e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "JWT RS256 sign failed" : e.getMessage()));
        }
    }

    private static PrivateKey loadPkcs8(String pem) {
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw DataSupport.failError("Error: " + e.getClass().getSimpleName()
                    + ": invalid PKCS8 private_key");
        }
    }

    private static Map<String, Object> parseServiceAccount(Object raw) {
        if (raw instanceof Map<?, ?>) {
            return new LinkedHashMap<>(Json.asMap(raw));
        }
        String s = DataSupport.trimToNull(EmulatorUtil.str(raw));
        if (s == null) {
            throw new studioquack.StudioQuackError("INVALID_INPUT",
                    "service_account_json parameter is required. Provide the Google"
                            + " Service Account JSON key.");
        }
        Object parsed;
        try {
            parsed = Json.parse(s);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw DataSupport.failError("Error: " + e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "invalid service_account_json" : e.getMessage()));
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new studioquack.StudioQuackError("INVALID_INPUT",
                    "Error: ValueError: service_account_json must be a JSON object");
        }
        return new LinkedHashMap<>(Json.asMap(parsed));
    }

    private static boolean validSpreadsheetId(String sid) {
        if (sid == null) {
            return false;
        }
        String s = sid.strip();
        if (s.length() < 20) {
            return false;
        }
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.contains("PLACEHOLDER") || upper.contains("YOUR_")
                || upper.contains("EXAMPLE") || upper.contains("TODO")) {
            return false;
        }
        return SPREADSHEET_ID.matcher(s).matches();
    }

    private static String valuesUrl(String sheetId, String range) {
        return SHEETS_BASE + "/" + sheetId + "/values/" + DataSupport.urlEncode(range);
    }

    private static String valueRangeBody(String range, Object values) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("range", range);
        body.put("majorDimension", "ROWS");
        body.put("values", values);
        return Json.write(body);
    }

    private static Map<String, Object> requireOk(DataSupport.HttpResp resp, List<String> secrets) {
        if (resp.error() != null) {
            throw DataSupport.failError(DataSupport.redact("Google Sheets API Error: " + resp.error(),
                    secrets.toArray(String[]::new)));
        }
        if (!resp.ok()) {
            String body = resp.body() == null ? "" : resp.body();
            if (body.length() > API_ERR_BODY_CHARS) {
                body = body.substring(0, API_ERR_BODY_CHARS);
            }
            throw DataSupport.failError(DataSupport.redact(
                    "Google Sheets API Error: HTTP " + resp.status() + ": " + body,
                    secrets.toArray(String[]::new)));
        }
        Map<String, Object> json = resp.jsonObject();
        if (json == null) {
            throw DataSupport.failError("Google Sheets API Error: HTTP " + resp.status() + ": non-JSON body");
        }
        return json;
    }

    private static boolean present(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof String s) {
            return !s.isBlank();
        }
        if (o instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (o instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
    }

    private static List<Object> listOrEmpty(Object raw) {
        return raw instanceof List<?> ? new ArrayList<>(Json.asList(raw)) : new ArrayList<>();
    }

    private static Object orEmpty(Object v) {
        return v == null ? "" : v;
    }

    private static Object orZero(Object v) {
        return v == null ? 0 : v;
    }

    private static String b64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** OUTPUT port map. Schema fields are this object, not a JSON string. */
    private static Map<String, Object> dump(Map<String, Object> body) {
        return DataSupport.ports(body);
    }

    private static Map<String, Object> fail(String message, List<String> secrets) {
        return DataSupport.fail(DataSupport.redact(
                message == null || message.isBlank() ? "request failed" : message,
                secrets.toArray(String[]::new)));
    }

    private static Map<String, Object> invalid(String message, List<String> secrets) {
        return DataSupport.invalidInput(DataSupport.redact(
                message == null || message.isBlank() ? "invalid input" : message,
                secrets.toArray(String[]::new)));
    }

    /** Config stores lists as JSON text. A wired list is left as-is. */
    private static void parseJsonContainers(Map<String, Object> in, String... keys) {
        if (in == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = in.get(key);
            if (!(value instanceof String text)) {
                continue;
            }
            String trimmed = text.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                continue;
            }
            try {
                in.put(key, Json.parse(trimmed));
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (RuntimeException ignored) {
                // leave the text; the action reports a missing list
            }
        }
    }
}
