package com.studioquack.nodelibrary.nodes.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** robots.txt parsing and matching — pure string logic, no fetching. */
class RobotsTxtTest {

    private static final String HOST = "https://example.com";

    // ---------- empty / default allow ----------

    @Test
    void nullOrEmptyBodyAllowsEverything() {
        assertTrue(RobotsTxt.parse(null).canFetch("bot", HOST + "/anything"));
        assertTrue(RobotsTxt.parse("").canFetch("bot", HOST + "/anything"));
    }

    @Test
    void commentsAndBlankLinesOnlyAllowEverything() {
        RobotsTxt r = RobotsTxt.parse("# just a comment\n\n   \n# another\n");
        assertTrue(r.canFetch("bot", HOST + "/x"));
    }

    // ---------- basic disallow ----------

    @Test
    void wildcardGroupDisallowsPrefix() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /private\n");
        assertFalse(r.canFetch("anybot", HOST + "/private"));
        assertFalse(r.canFetch("anybot", HOST + "/private/page"));
        assertTrue(r.canFetch("anybot", HOST + "/public"));
    }

    @Test
    void emptyDisallowAllowsAll() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow:\n");
        assertTrue(r.canFetch("bot", HOST + "/anything"));
    }

    @Test
    void noMatchingRuleInGroupAllows() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /a\n");
        assertTrue(r.canFetch("bot", HOST + "/b"));
    }

    // ---------- user-agent selection ----------

    @Test
    void specificGroupBeatsWildcard() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: *\nDisallow: /x\n\n"
                        + "User-agent: googlebot\nAllow: /x\n");
        assertTrue(r.canFetch("MyGooglebot/2.1", HOST + "/x"),
                "specific UA group applies (case-insensitive substring)");
        assertFalse(r.canFetch("otherbot", HOST + "/x"), "wildcard group still applies to others");
    }

    @Test
    void userAgentMatchIsCaseInsensitiveSubstring() {
        RobotsTxt r = RobotsTxt.parse("User-agent: GoogleBot\nDisallow: /g\n");
        assertFalse(r.canFetch("mygooglebot/1.0", HOST + "/g"));
        assertTrue(r.canFetch("bingbot", HOST + "/g"));
    }

    @Test
    void nullUserAgentFallsToWildcard() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: googlebot\nDisallow: /g\n\nUser-agent: *\nDisallow: /w\n");
        assertTrue(r.canFetch(null, HOST + "/g"), "null UA does not match the specific group");
        assertFalse(r.canFetch(null, HOST + "/w"));
    }

    @Test
    void groupListingStarIsDefaultEntry() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nUser-agent: googlebot\nDisallow: /x\n");
        assertFalse(r.canFetch("googlebot", HOST + "/x"),
                "group containing * is the wildcard entry even with a named crawler");
        assertFalse(r.canFetch("otherbot", HOST + "/x"));
    }

    @Test
    void userAgentLineAfterRuleStartsNewRecordWithoutBlankLine() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: a\nDisallow: /x\nUser-agent: b\nDisallow: /y\n");
        assertFalse(r.canFetch("a", HOST + "/x"));
        assertTrue(r.canFetch("a", HOST + "/y"), "second record belongs to b");
        assertFalse(r.canFetch("b", HOST + "/y"));
        assertTrue(r.canFetch("b", HOST + "/x"));
    }

    @Test
    void multipleWildcardGroupsAreMerged() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: *\nDisallow: /a\n\nUser-agent: *\nDisallow: /b\n");
        assertFalse(r.canFetch("bot", HOST + "/a"));
        assertFalse(r.canFetch("bot", HOST + "/b"));
        assertTrue(r.canFetch("bot", HOST + "/c"));
    }

    // ---------- rule precedence ----------

    @Test
    void longestMatchingPrefixWins() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: *\nDisallow: /ab\nAllow: /abcd\n");
        assertTrue(r.canFetch("bot", HOST + "/abcd"));
        assertFalse(r.canFetch("bot", HOST + "/ab"));
        assertFalse(r.canFetch("bot", HOST + "/abc"), "/abc matches only the /ab prefix");
    }

    @Test
    void equalLengthPrefersAllow() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /x\nAllow: /x\n");
        assertTrue(r.canFetch("bot", HOST + "/x"));
    }

    @Test
    void starPathMatchesEverythingButLosesToRealPrefix() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: *\nAllow: /public\n");
        assertFalse(r.canFetch("bot", HOST + "/anything"));
        assertTrue(r.canFetch("bot", HOST + "/public/page"));
    }

    // ---------- parsing tolerance ----------

    @Test
    void crlfAndInlineCommentsHandled() {
        RobotsTxt r = RobotsTxt.parse("User-agent: * # all bots\r\nDisallow: /c # closed\r\n");
        assertFalse(r.canFetch("bot", HOST + "/c"));
    }

    @Test
    void bomIsStripped() {
        RobotsTxt r = RobotsTxt.parse("\uFEFFUser-agent: *\nDisallow: /bom\n");
        assertFalse(r.canFetch("bot", HOST + "/bom"));
    }

    @Test
    void unknownFieldsIgnored() {
        RobotsTxt r = RobotsTxt.parse(
                "User-agent: *\nDisallow: /d\nSitemap: https://example.com/sitemap.xml\n"
                        + "Crawl-delay: 10\nRequest-rate: 1/5\nHost: example.com\n");
        assertFalse(r.canFetch("bot", HOST + "/d"));
        assertTrue(r.canFetch("bot", HOST + "/sitemap.xml"));
    }

    @Test
    void rulesBeforeAnyUserAgentAreIgnored() {
        RobotsTxt r = RobotsTxt.parse("Disallow: /orphan\n\nUser-agent: *\nDisallow: /real\n");
        assertTrue(r.canFetch("bot", HOST + "/orphan"));
        assertFalse(r.canFetch("bot", HOST + "/real"));
    }

    @Test
    void linesWithoutColonAreSkipped() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\ngarbage line\nDisallow: /z\n");
        assertFalse(r.canFetch("bot", HOST + "/z"));
    }

    @Test
    void groupWithoutRulesIsDropped() {
        RobotsTxt r = RobotsTxt.parse("User-agent: lonely\n\nUser-agent: *\nDisallow: /w\n");
        assertFalse(r.canFetch("lonely", HOST + "/w"),
                "rule-less specific group must not shadow the wildcard entry");
    }

    // ---------- URL / path handling ----------

    @Test
    void queryStringIsNotPartOfMatch() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /search\n");
        assertFalse(r.canFetch("bot", HOST + "/search?q=cats&page=2"));
    }

    @Test
    void percentEncodedRuleMatchesDecodedUrlPath() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /%7Euser\n");
        assertFalse(r.canFetch("bot", HOST + "/~user"), "rule %7E decodes to ~");
    }

    @Test
    void percentEncodedUrlPathMatchesPlainRule() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /~user\n");
        assertFalse(r.canFetch("bot", HOST + "/%7Euser"), "URI.getPath decodes %7E");
    }

    // ---------- package-private helpers ----------

    @Test
    void pathOfVariants() {
        assertEquals("/", RobotsTxt.pathOf(null));
        assertEquals("/", RobotsTxt.pathOf(""));
        assertEquals("/", RobotsTxt.pathOf("   "));
        assertEquals("/", RobotsTxt.pathOf(HOST));
        assertEquals("/p", RobotsTxt.pathOf(HOST + "/p?q=1#frag"));
        assertEquals("/", RobotsTxt.pathOf("://not a uri"), "invalid URI → root");
    }

    @Test
    void decodePathVariants() {
        assertEquals("", RobotsTxt.decodePath(null));
        assertEquals("", RobotsTxt.decodePath("  "));
        assertEquals("/a b", RobotsTxt.decodePath("/a%20b"));
        assertEquals("/a+b", RobotsTxt.decodePath("/a+b"), "plus stays plus in robots paths");
        assertEquals("/bad%", RobotsTxt.decodePath("/bad%"), "invalid escape returned as-is");
    }
}
