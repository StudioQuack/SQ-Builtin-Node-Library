package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AwsSigV4} against AWS's published Signature Version 4 fixtures.
 *
 * <p>Credentials, date, region and service are the test-suite constants from
 * <a href="https://docs.aws.amazon.com/general/latest/gr/signature-v4-test-suite.html">signature-v4-test-suite</a>
 * ({@code AKIDEXAMPLE} / {@code wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY},
 * {@code 20150830T123600Z}, {@code us-east-1}).
 */
class AwsSigV4Test {

    private static final String ACCESS = "AKIDEXAMPLE";
    private static final String SECRET = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    private static final Instant NOW = Instant.parse("2015-08-30T12:36:00Z");

    @Test
    void emptyPayloadHashIsPublishedSha256OfEmptyString() {
        assertEquals(AwsSigV4.EMPTY_PAYLOAD_HASH, AwsSigV4.hexSha256(new byte[0]));
        assertEquals(AwsSigV4.EMPTY_PAYLOAD_HASH, AwsSigV4.hexSha256(null));
    }

    @Test
    void iamSigningKeyMatchesAwsWorkedExample() {
        // Independently derived (HMAC-SHA256 chain); matches Python hmac + the
        // get-vanilla / ListUsers signatures below. A widely copied hex in older
        // blog posts ({@code ...d87041...}) is off by one nibble.
        byte[] key = AwsSigV4.signingKey(SECRET, "20150830", "us-east-1", "iam");
        assertEquals("c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9",
                AwsSigV4.hex(key));
    }

    @Test
    void getVanillaCanonicalRequestMatchesTestSuite() {
        // aws-sig-v4-test-suite/get-vanilla
        URI uri = URI.create("https://example.amazonaws.com/");
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", "example.amazonaws.com");
        headers.put("x-amz-date", "20150830T123600Z");
        String creq = AwsSigV4.canonicalRequest("GET", uri, headers, AwsSigV4.EMPTY_PAYLOAD_HASH);
        String expected = "GET\n/\n\nhost:example.amazonaws.com\nx-amz-date:20150830T123600Z\n\nhost;x-amz-date\n"
                + AwsSigV4.EMPTY_PAYLOAD_HASH;
        assertEquals(expected, creq);
        assertEquals("bb579772317eb040ac9ed261061d46c1f17a8133879d6129b6e1c25292927e63",
                AwsSigV4.hexSha256(creq.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void getVanillaAuthorizationMatchesCrtTestSuite() {
        // aws-c-auth / aws-crt-nodejs: tests/aws-sig-v4-test-suite/get-vanilla
        URI uri = URI.create("https://example.amazonaws.com/");
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", "example.amazonaws.com");
        headers.put("x-amz-date", "20150830T123600Z");
        String auth = AwsSigV4.authorization("GET", uri, headers, AwsSigV4.EMPTY_PAYLOAD_HASH,
                ACCESS, SECRET, "us-east-1", "service", NOW);
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31",
                auth);
    }

    @Test
    void iamListUsersWorkedExampleFromAwsDocs() {
        // Canonical request + signature published in the IAM "create a signed request" example.
        URI uri = URI.create("https://iam.amazonaws.com/?Action=ListUsers&Version=2010-05-08");
        Map<String, String> headers = new TreeMap<>();
        headers.put("content-type", "application/x-www-form-urlencoded; charset=utf-8");
        headers.put("host", "iam.amazonaws.com");
        headers.put("x-amz-date", "20150830T123600Z");
        String creq = AwsSigV4.canonicalRequest("GET", uri, headers, AwsSigV4.EMPTY_PAYLOAD_HASH);
        assertTrue(creq.startsWith("GET\n/\nAction=ListUsers&Version=2010-05-08\n"));
        String auth = AwsSigV4.authorization("GET", uri, headers, AwsSigV4.EMPTY_PAYLOAD_HASH,
                ACCESS, SECRET, "us-east-1", "iam", NOW);
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/iam/aws4_request, "
                        + "SignedHeaders=content-type;host;x-amz-date, "
                        + "Signature=5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7",
                auth);
    }

    @Test
    void productionSignStampsRequiredHeadersAndOptionalSessionToken() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        URI uri = URI.create("https://lambda.us-east-1.amazonaws.com/2015-03-31/functions/fn/invocations");
        byte[] body = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        String err = AwsSigV4.sign(headers, "POST", uri, body, ACCESS, SECRET,
                "us-east-1", "lambda", "session-token-example", NOW);
        assertEquals(null, err);
        assertEquals("20150830T123600Z", headers.get("x-amz-date"));
        assertEquals(AwsSigV4.hexSha256(body), headers.get("x-amz-content-sha256"));
        assertEquals("session-token-example", headers.get("x-amz-security-token"));
        assertTrue(headers.get("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=" + ACCESS));
        assertTrue(headers.get("Authorization").contains("SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token"));
        assertTrue(headers.get("Authorization").contains("Signature="));
    }

    @Test
    void canonicalQuerySortsByKeyThenValue() {
        URI uri = URI.create("https://example.amazonaws.com/?Param2=value2&Param1=value1");
        assertEquals("Param1=value1&Param2=value2", AwsSigV4.canonicalQuery(uri));
    }
}
