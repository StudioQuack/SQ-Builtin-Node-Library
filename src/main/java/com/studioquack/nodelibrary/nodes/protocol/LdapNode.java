package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import javax.naming.AuthenticationException;
import javax.naming.AuthenticationNotSupportedException;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LDAP / LDAPS directory: {@code search | compare | create | update | delete | rename}.
 *
 * <p>Uses the JDK JNDI LDAP provider ({@code javax.naming.ldap}). LDAPS or StartTLS
 * is required; plain {@code ldap://} is refused unless {@code allow_plain_ldap} is
 * set. The bind password is read only from a password-typed configuration
 * parameter and is redacted from every error. Filter values built from input are
 * escaped per RFC 4515; DN components built from input are escaped per RFC 4514.
 * Referrals are not followed. Search size and time limits are constants.
 */
public final class LdapNode implements NodeHandler {

    public static final String DEFAULT_ID = "aa6766ed-4cb7-488e-9061-ddf73937f49a";

    /** Hard cap on search entries so a wide subtree cannot fill the sandbox heap. */
    static final int MAX_SEARCH_SIZE = 200;

    /** Default connect + read budget when {@code timeout_seconds} is blank or non-positive. */
    static final int DEFAULT_TIMEOUT_SECONDS = 25;

    /** Upper bound on the operator-supplied timeout. */
    static final int MAX_TIMEOUT_SECONDS = 60;

    private static final Set<String> OPS = Set.of(
            "search", "compare", "create", "update", "delete", "rename");

    /** RFC 4512 attribute type / descr (optional ;option suffixes). */
    private static final Pattern ATTR_NAME = Pattern.compile(
            "[A-Za-z][A-Za-z0-9-]*(?:;[A-Za-z][A-Za-z0-9-]*)*");

    private final String templateId;

    public LdapNode() {
        this(DEFAULT_ID);
    }

    public LdapNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return DataSupport.invalidInput("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        Map<String, String> types = DataSupport.readConfigTypes(request);
        String password = DataSupport.cfgStr(cfg, "bind_password", null);
        try {
            if (password != null && !"password".equalsIgnoreCase(types.get("bind_password"))) {
                return DataSupport.invalidInput(
                        "bind_password must be a password-typed configuration parameter");
            }
            String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "search"),
                    "operation").trim().toLowerCase(Locale.ROOT);
            if (!OPS.contains(op)) {
                return DataSupport.invalidInput("unknown operation: "
                        + (op.isEmpty() ? "<missing>" : op)
                        + ". Accepted: search, compare, create, update, delete, rename");
            }
            String url = DataSupport.cfgStr(cfg, "url", null);
            if (url == null) {
                return DataSupport.invalidInput("url is required (ldap:// or ldaps://)");
            }
            String bindDn = DataSupport.cfgStr(cfg, "bind_dn", null);
            if (bindDn == null) {
                return DataSupport.invalidInput("bind_dn is required");
            }
            if (password == null) {
                return DataSupport.invalidInput("bind_password is required");
            }
            boolean startTls = truthy(DataSupport.cfgStr(cfg, "start_tls", "true"), true);
            boolean allowPlain = truthy(DataSupport.cfgStr(cfg, "allow_plain_ldap", "false"), false);
            requireSecureTransport(url, startTls, allowPlain);
            int timeout = clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT_SECONDS));
            String baseDn = DataSupport.cfgStr(cfg, "base_dn", null);
            LdapContext ctx = openContext(url, bindDn, password, timeout, startTls);
            try {
                Map<String, Object> data = switch (op) {
                    case "search" -> search(ctx, in, baseDn, timeout);
                    case "compare" -> compare(ctx, in, baseDn);
                    case "create" -> create(ctx, in, baseDn);
                    case "update" -> update(ctx, in, baseDn);
                    case "delete" -> delete(ctx, in, baseDn);
                    case "rename" -> rename(ctx, in, baseDn);
                    default -> {
                        DataSupport.invalidInput("unknown operation: " + op);
                        yield Map.of();
                    }
                };
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", "ok");
                body.put("data", data);
                return DataSupport.ports(body);
            } finally {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // closing after the operation
                }
            }
        } catch (StudioQuackError e) {
            throw redactError(e, password);
        } catch (AuthenticationException | AuthenticationNotSupportedException e) {
            throw new StudioQuackError("AUTH_FAILED",
                    DataSupport.redact("LDAP bind failed", password));
        } catch (InvalidNameException e) {
            return DataSupport.invalidInput(DataSupport.redact("invalid DN: " + safeMsg(e), password));
        } catch (NamingException e) {
            return DataSupport.fail(DataSupport.redact(
                    "LDAP " + e.getClass().getSimpleName() + ": " + safeMsg(e), password));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DataSupport.fail(DataSupport.redact(
                    e.getClass().getSimpleName() + ": " + safeMsg(e), password));
        }
    }

    /**
     * Refuse cleartext {@code ldap://} unless StartTLS will run or the operator
     * set {@code allow_plain_ldap}.
     */
    static void requireSecureTransport(String url, boolean startTls, boolean allowPlain) {
        String scheme = schemeOf(url);
        if ("ldaps".equals(scheme)) {
            return;
        }
        if ("ldap".equals(scheme)) {
            if (startTls || allowPlain) {
                return;
            }
            DataSupport.invalidInput(
                    "plain ldap:// requires start_tls=true or allow_plain_ldap=true; use ldaps:// or StartTLS");
            return;
        }
        DataSupport.invalidInput("url must be ldap:// or ldaps:// (RFC 4516)");
    }

    /**
     * JNDI environment. {@code includeBind=false} omits credentials so StartTLS
     * can upgrade the socket before the simple bind.
     */
    static Hashtable<String, Object> buildEnvironment(String url, String bindDn, String bindPassword,
                                                      int timeoutSeconds, boolean includeBind) {
        int timeout = clampTimeout(timeoutSeconds);
        String ms = Integer.toString(timeout * 1000);
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.REFERRAL, "ignore");
        env.put("com.sun.jndi.ldap.connect.timeout", ms);
        env.put("com.sun.jndi.ldap.read.timeout", ms);
        env.put("java.naming.ldap.version", "3");
        if (includeBind) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, bindDn);
            env.put(Context.SECURITY_CREDENTIALS, bindPassword);
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        }
        return env;
    }

    static SearchControls buildSearchControls(Map<String, Object> in, int timeoutSeconds) {
        SearchControls sc = new SearchControls();
        sc.setCountLimit(MAX_SEARCH_SIZE);
        sc.setTimeLimit(clampTimeout(timeoutSeconds));
        sc.setReturningObjFlag(false);
        sc.setDerefLinkFlag(false);
        String scope = DataSupport.inStr(in, "scope", "sub").trim().toLowerCase(Locale.ROOT);
        sc.setSearchScope(switch (scope) {
            case "base", "object" -> SearchControls.OBJECT_SCOPE;
            case "one", "onelevel" -> SearchControls.ONELEVEL_SCOPE;
            default -> SearchControls.SUBTREE_SCOPE;
        });
        List<String> attrs = DataSupport.inStringList(in, "return_attributes");
        if (!attrs.isEmpty()) {
            sc.setReturningAttributes(attrs.toArray(String[]::new));
        }
        return sc;
    }

    /**
     * RFC 4515 assertion-value escape: {@code * ( ) \} and NUL become {@code \HH}.
     */
    static String escapeFilterValue(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\\' -> sb.append("\\5c");
                case '\0' -> sb.append("\\00");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * RFC 4514 string representation escape for one attribute value in a DN.
     */
    static String escapeDnValue(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        int last = value.length() - 1;
        for (int i = 0; i <= last; i++) {
            char c = value.charAt(i);
            boolean edge = i == 0 || i == last;
            if (c == '\0') {
                sb.append("\\00");
                continue;
            }
            if (c == '\\' || c == ',' || c == '+' || c == '"' || c == '<' || c == '>' || c == ';'
                    || (c == ' ' && edge) || (c == '#' && i == 0)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Filter used for search. {@code filter_attribute}+{@code filter_value} builds
     * an equality filter with the value escaped; otherwise {@code filter} is used
     * as an operator-authored RFC 4515 string.
     */
    static String buildFilter(Map<String, Object> in) {
        String attr = DataSupport.inStrAny(in, null, "filter_attribute");
        String value = DataSupport.inStr(in, "filter_value", null);
        if (attr != null && value != null) {
            requireAttrName(attr);
            return "(" + attr + "=" + escapeFilterValue(value) + ")";
        }
        String filter = DataSupport.inStr(in, "filter", null);
        if (filter != null) {
            return filter;
        }
        DataSupport.invalidInput(
                "search requires filter, or filter_attribute and filter_value");
        return "";
    }

    /**
     * Target DN. {@code rdn_type}+{@code rdn_value} (optional {@code parent_dn} /
     * config {@code base_dn}) is assembled with the RDN value escaped; otherwise
     * {@code dn} or the fallback is used as an operator-authored DN.
     */
    static String buildDn(Map<String, Object> in, String fallback, String... keys) {
        String rdnType = DataSupport.inStr(in, "rdn_type", null);
        String rdnValue = DataSupport.inStr(in, "rdn_value", null);
        if (rdnType != null && rdnValue != null) {
            requireAttrName(rdnType);
            String rdn = rdnType + "=" + escapeDnValue(rdnValue);
            String parent = DataSupport.inStrAny(in, fallback, "parent_dn");
            if (parent != null && !parent.isBlank()) {
                return rdn + "," + parent;
            }
            return rdn;
        }
        String[] aliases = (keys == null || keys.length == 0) ? new String[]{"dn"} : keys;
        String dn = DataSupport.inStrAny(in, fallback, aliases);
        if (dn == null || dn.isBlank()) {
            DataSupport.invalidInput("dn is required");
        }
        return dn;
    }

    static void requireAttrName(String name) {
        if (name == null || !ATTR_NAME.matcher(name).matches()) {
            DataSupport.invalidInput("invalid LDAP attribute type: "
                    + (name == null || name.isBlank() ? "<missing>" : name));
        }
    }

    private Map<String, Object> search(LdapContext ctx, Map<String, Object> in, String baseDn,
                                       int timeout) throws NamingException {
        String base = DataSupport.inStrAny(in, baseDn, "dn", "base_dn");
        if (base == null || base.isBlank()) {
            String built = tryBuiltDn(in, baseDn);
            if (built == null) {
                return DataSupport.invalidInput("search requires dn or base_dn");
            }
            base = built;
        }
        String filter = buildFilter(in);
        SearchControls sc = buildSearchControls(in, timeout);
        List<Map<String, Object>> entries = new ArrayList<>();
        boolean truncated = false;
        try {
            NamingEnumeration<SearchResult> results = ctx.search(base, filter, sc);
            try {
                while (results.hasMore()) {
                    if (entries.size() >= MAX_SEARCH_SIZE) {
                        truncated = true;
                        break;
                    }
                    entries.add(entryOf(results.next()));
                }
            } finally {
                results.close();
            }
        } catch (SizeLimitExceededException e) {
            truncated = true;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", entries);
        data.put("count", entries.size());
        data.put("truncated", truncated);
        return data;
    }

    private Map<String, Object> compare(LdapContext ctx, Map<String, Object> in, String baseDn)
            throws NamingException {
        String dn = buildDn(in, baseDn);
        String attr = DataSupport.inStrAny(in, null, "attribute");
        String value = DataSupport.inStr(in, "value", null);
        if (attr == null || value == null) {
            return DataSupport.invalidInput("compare requires attribute and value");
        }
        requireAttrName(attr);
        String filter = "(" + attr + "=" + escapeFilterValue(value) + ")";
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.OBJECT_SCOPE);
        sc.setCountLimit(1);
        sc.setTimeLimit(1);
        sc.setReturningAttributes(new String[0]);
        sc.setReturningObjFlag(false);
        boolean matched = false;
        NamingEnumeration<SearchResult> results = ctx.search(dn, filter, sc);
        try {
            matched = results.hasMore();
        } finally {
            results.close();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matched", matched);
        data.put("dn", dn);
        return data;
    }

    private Map<String, Object> create(LdapContext ctx, Map<String, Object> in, String baseDn)
            throws NamingException {
        String dn = buildDn(in, baseDn);
        Attributes attrs = toAttributes(in.get("attributes"));
        if (attrs.size() == 0) {
            return DataSupport.invalidInput("create requires attributes");
        }
        ctx.createSubcontext(dn, attrs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dn", dn);
        return data;
    }

    private Map<String, Object> update(LdapContext ctx, Map<String, Object> in, String baseDn)
            throws NamingException {
        String dn = buildDn(in, baseDn);
        Attributes attrs = toAttributes(in.get("attributes"));
        if (attrs.size() == 0) {
            return DataSupport.invalidInput("update requires attributes");
        }
        ctx.modifyAttributes(dn, DirContext.REPLACE_ATTRIBUTE, attrs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dn", dn);
        return data;
    }

    private Map<String, Object> delete(LdapContext ctx, Map<String, Object> in, String baseDn)
            throws NamingException {
        String dn = buildDn(in, baseDn);
        ctx.destroySubcontext(dn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dn", dn);
        return data;
    }

    private Map<String, Object> rename(LdapContext ctx, Map<String, Object> in, String baseDn)
            throws NamingException {
        String dn = buildDn(in, baseDn);
        String newDn = DataSupport.inStr(in, "new_dn", null);
        if (newDn == null) {
            String newRdnType = DataSupport.inStr(in, "new_rdn_type", null);
            String newRdnValue = DataSupport.inStr(in, "new_rdn_value", null);
            if (newRdnType != null && newRdnValue != null) {
                requireAttrName(newRdnType);
                newDn = newRdnType + "=" + escapeDnValue(newRdnValue);
                int comma = dn.indexOf(',');
                if (comma > 0) {
                    newDn = newDn + dn.substring(comma);
                }
            }
        }
        if (newDn == null || newDn.isBlank()) {
            return DataSupport.invalidInput("rename requires new_dn");
        }
        ctx.rename(dn, newDn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dn", dn);
        data.put("new_dn", newDn);
        return data;
    }

    private LdapContext openContext(String url, String bindDn, String password, int timeout,
                                    boolean startTls) throws NamingException {
        boolean ldaps = "ldaps".equals(schemeOf(url));
        boolean upgrade = !ldaps && startTls;
        Hashtable<String, Object> env = buildEnvironment(url, bindDn, password, timeout, !upgrade);
        InitialLdapContext ctx = new InitialLdapContext(env, null);
        if (upgrade) {
            StartTlsResponse tls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
            try {
                tls.negotiate();
            } catch (Exception e) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // closing after a failed upgrade
                }
                throw DataSupport.failError(DataSupport.redact(
                        "StartTLS failed: " + e.getClass().getSimpleName() + ": " + safeMsg(e),
                        password));
            }
            ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, bindDn);
            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
            ctx.reconnect(null);
        }
        return ctx;
    }

    private static Map<String, Object> entryOf(SearchResult result) throws NamingException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("dn", result.getNameInNamespace());
        Map<String, Object> attrs = new LinkedHashMap<>();
        Attributes raw = result.getAttributes();
        if (raw != null) {
            NamingEnumeration<? extends Attribute> all = raw.getAll();
            try {
                while (all.hasMore()) {
                    Attribute a = all.next();
                    List<Object> values = new ArrayList<>();
                    NamingEnumeration<?> vals = a.getAll();
                    try {
                        while (vals.hasMore()) {
                            Object v = vals.next();
                            if (v instanceof byte[]) {
                                values.add("<binary>");
                            } else {
                                values.add(String.valueOf(v));
                            }
                        }
                    } finally {
                        vals.close();
                    }
                    attrs.put(a.getID(), values.size() == 1 ? values.get(0) : values);
                }
            } finally {
                all.close();
            }
        }
        entry.put("attributes", attrs);
        return entry;
    }

    static Attributes toAttributes(Object raw) {
        Object value = raw;
        if (value instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{")) {
                value = DataSupport.parseJson(t);
            }
        }
        Map<String, Object> map = EmulatorUtil.asMap(value);
        BasicAttributes attrs = new BasicAttributes();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String name = e.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }
            requireAttrName(name);
            BasicAttribute attr = new BasicAttribute(name);
            Object v = e.getValue();
            if (v instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        attr.add(String.valueOf(item));
                    }
                }
            } else if (v != null) {
                attr.add(String.valueOf(v));
            }
            if (attr.size() > 0) {
                attrs.put(attr);
            }
        }
        return attrs;
    }

    private static String tryBuiltDn(Map<String, Object> in, String fallback) {
        String rdnType = DataSupport.inStr(in, "rdn_type", null);
        String rdnValue = DataSupport.inStr(in, "rdn_value", null);
        if (rdnType == null || rdnValue == null) {
            return null;
        }
        return buildDn(in, fallback);
    }

    static String schemeOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String scheme = URI.create(url.trim()).getScheme();
            return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            int colon = url.indexOf(':');
            return colon <= 0 ? "" : url.substring(0, colon).toLowerCase(Locale.ROOT);
        }
    }

    static int clampTimeout(int seconds) {
        if (seconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(seconds, MAX_TIMEOUT_SECONDS);
    }

    static boolean truthy(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return EmulatorUtil.isTruthy(raw);
    }

    private static StudioQuackError redactError(StudioQuackError e, String password) {
        String msg = DataSupport.redact(e.getMessage(), password);
        if (msg.equals(e.getMessage())) {
            return e;
        }
        return new StudioQuackError(e.errorCode(), msg, e.data());
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m;
    }
}
