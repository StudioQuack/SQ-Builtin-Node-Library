package com.studioquack.nodelibrary.nodes.protocol;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import javax.naming.Context;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LDAP handler: RFC 4515/4514 escaping, TLS refusal, JNDI request building.
 * No live directory.
 */
class LdapNodeTest {

    private final LdapNode node = new LdapNode();

    @Test
    void escapeFilterValueRfc4515() {
        assertEquals("ada", LdapNode.escapeFilterValue("ada"));
        assertEquals("a\\2aB", LdapNode.escapeFilterValue("a*B"));
        assertEquals("\\28uid\\29", LdapNode.escapeFilterValue("(uid)"));
        assertEquals("a\\5cb", LdapNode.escapeFilterValue("a\\b"));
        assertEquals("x\\00y", LdapNode.escapeFilterValue("x\0y"));
        assertEquals("\\2a\\28\\29\\5c\\00", LdapNode.escapeFilterValue("*()\\\0"));
    }

    @Test
    void escapeDnValueRfc4514() {
        assertEquals("ada", LdapNode.escapeDnValue("ada"));
        assertEquals("a\\,b", LdapNode.escapeDnValue("a,b"));
        assertEquals("a\\+b", LdapNode.escapeDnValue("a+b"));
        assertEquals("a\\\"b", LdapNode.escapeDnValue("a\"b"));
        assertEquals("a\\\\b", LdapNode.escapeDnValue("a\\b"));
        assertEquals("a\\<b\\>", LdapNode.escapeDnValue("a<b>"));
        assertEquals("a\\;b", LdapNode.escapeDnValue("a;b"));
        assertEquals("\\#hash", LdapNode.escapeDnValue("#hash"));
        assertEquals("\\ lead", LdapNode.escapeDnValue(" lead"));
        assertEquals("trail\\ ", LdapNode.escapeDnValue("trail "));
        assertEquals("x\\00y", LdapNode.escapeDnValue("x\0y"));
    }

    @Test
    void builtFilterEscapesAssertionValue() {
        String filter = LdapNode.buildFilter(Map.of(
                "filter_attribute", "uid",
                "filter_value", "ada)(|(uid=*"));
        assertEquals("(uid=ada\\29\\28|\\28uid=\\2a)", filter);
    }

    @Test
    void builtDnEscapesRdnValue() {
        String dn = LdapNode.buildDn(
                Map.of("rdn_type", "cn", "rdn_value", "Ada, Lovelace", "parent_dn", "dc=example,dc=org"),
                null);
        assertEquals("cn=Ada\\, Lovelace,dc=example,dc=org", dn);
    }

    @Test
    void operatorAuthoredFilterIsUnchanged() {
        assertEquals("(uid=ada)", LdapNode.buildFilter(Map.of("filter", "(uid=ada)")));
    }

    @Test
    void plainLdapWithoutFlagIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> LdapNode.requireSecureTransport("ldap://dir.example:389", false, false));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("allow_plain_ldap") || e.getMessage().contains("StartTLS"),
                e.getMessage());
    }

    @Test
    void handleRefusesPlainLdapWithoutFlag() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        params("operation", "search", "url", "ldap://dir.example:389",
                                "bind_dn", "cn=admin,dc=ex", "bind_password", "s3cret",
                                "start_tls", "false", "allow_plain_ldap", "false"),
                        Map.of("filter", "(uid=ada)", "dn", "dc=ex")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertFalse(e.getMessage().contains("s3cret"), e.getMessage());
    }

    @Test
    void ldapsIsAcceptedByTransportGuard() {
        LdapNode.requireSecureTransport("ldaps://dir.example:636", false, false);
    }

    @Test
    void startTlsAllowsLdapUrl() {
        LdapNode.requireSecureTransport("ldap://dir.example:389", true, false);
    }

    @Test
    void allowPlainLdapFlagAllowsCleartext() {
        LdapNode.requireSecureTransport("ldap://dir.example:389", false, true);
    }

    @Test
    void unknownSchemeIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> LdapNode.requireSecureTransport("https://dir.example", false, true));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void bindPasswordMustBePasswordTyped() {
        List<Object> params = List.of(
                param("operation", "search", "select"),
                param("url", "ldaps://dir.example:636", "string"),
                param("bind_dn", "cn=admin,dc=ex", "string"),
                param("bind_password", "s3cret", "string"));
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(params, Map.of("filter", "(objectClass=*)", "dn", "dc=ex")),
                        null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("password-typed"), e.getMessage());
        assertFalse(e.getMessage().contains("s3cret"), e.getMessage());
    }

    @Test
    void unknownOperationIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        params("operation", "bind", "url", "ldaps://dir.example:636",
                                "bind_dn", "cn=admin,dc=ex", "bind_password", "s3cret"),
                        Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"), e.getMessage());
    }

    @Test
    void missingUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        params("operation", "search", "bind_dn", "cn=admin,dc=ex",
                                "bind_password", "s3cret"),
                        Map.of("filter", "(uid=ada)")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("url"), e.getMessage());
    }

    @Test
    void buildEnvironmentIgnoresReferralsAndSetsTimeouts() {
        Hashtable<String, Object> env = LdapNode.buildEnvironment(
                "ldaps://dir.example:636", "cn=admin,dc=ex", "s3cret", 25, true);
        assertEquals("com.sun.jndi.ldap.LdapCtxFactory", env.get(Context.INITIAL_CONTEXT_FACTORY));
        assertEquals("ldaps://dir.example:636", env.get(Context.PROVIDER_URL));
        assertEquals("ignore", env.get(Context.REFERRAL));
        assertEquals("25000", env.get("com.sun.jndi.ldap.connect.timeout"));
        assertEquals("25000", env.get("com.sun.jndi.ldap.read.timeout"));
        assertEquals("3", env.get("java.naming.ldap.version"));
        assertEquals("simple", env.get(Context.SECURITY_AUTHENTICATION));
        assertEquals("cn=admin,dc=ex", env.get(Context.SECURITY_PRINCIPAL));
        assertTrue(env.containsKey(Context.SECURITY_CREDENTIALS));
    }

    @Test
    void startTlsEnvironmentOmitsBindUntilUpgrade() {
        Hashtable<String, Object> env = LdapNode.buildEnvironment(
                "ldap://dir.example:389", "cn=admin,dc=ex", "s3cret", 10, false);
        assertEquals("none", env.get(Context.SECURITY_AUTHENTICATION));
        assertFalse(env.containsKey(Context.SECURITY_CREDENTIALS));
        assertEquals("ignore", env.get(Context.REFERRAL));
    }

    @Test
    void searchControlsCapSizeAndTime() {
        SearchControls sc = LdapNode.buildSearchControls(Map.of("scope", "base"), 25);
        assertEquals(LdapNode.MAX_SEARCH_SIZE, sc.getCountLimit());
        assertEquals(25, sc.getTimeLimit());
        assertEquals(SearchControls.OBJECT_SCOPE, sc.getSearchScope());
        assertFalse(sc.getDerefLinkFlag());
        sc = LdapNode.buildSearchControls(Map.of("scope", "one"), 0);
        assertEquals(SearchControls.ONELEVEL_SCOPE, sc.getSearchScope());
        assertEquals(LdapNode.DEFAULT_TIMEOUT_SECONDS, sc.getTimeLimit());
        sc = LdapNode.buildSearchControls(Map.of(), 90);
        assertEquals(SearchControls.SUBTREE_SCOPE, sc.getSearchScope());
        assertEquals(LdapNode.MAX_TIMEOUT_SECONDS, sc.getTimeLimit());
    }

    @Test
    void toAttributesRejectsUnsafeNames() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> LdapNode.toAttributes(Map.of("uid)(|(cn=*", "x")));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void toAttributesAcceptsStringAndListValues() {
        Attributes attrs = LdapNode.toAttributes(Map.of(
                "cn", "Ada",
                "objectClass", List.of("inetOrgPerson", "person")));
        assertEquals(2, attrs.size());
    }

    @Test
    void templateIdUsesConstructorArgument() {
        assertEquals("custom-id", new LdapNode("custom-id").templateId());
        assertEquals(LdapNode.DEFAULT_ID, new LdapNode().templateId());
        assertEquals(LdapNode.DEFAULT_ID, new LdapNode("").templateId());
    }

    @Test
    void invalidInputDoesNotEchoBindPassword() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        params("operation", "search", "url", "ldap://dir.example",
                                "bind_dn", "cn=admin,dc=ex", "bind_password", "s3cret",
                                "start_tls", "false"),
                        Map.of("filter", "(uid=s3cret)")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertFalse(e.getMessage().contains("s3cret"), e.getMessage());
    }

    private static Map<String, Object> request(List<Object> params, Map<String, Object> input) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        req.put("inputVariables", Map.of("input", input));
        return req;
    }

    private static List<Object> params(String... kv) {
        List<Object> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = kv[i];
            String type = "bind_password".equals(key) ? "password"
                    : ("operation".equals(key) || key.endsWith("_tls") || key.startsWith("allow_"))
                    ? "select" : "string";
            out.add(param(key, kv[i + 1], type));
        }
        return out;
    }

    private static Map<String, Object> param(String code, Object value, String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shortCode", code);
        row.put("value", value);
        row.put("type", type);
        return row;
    }
}
