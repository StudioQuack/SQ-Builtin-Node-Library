package com.studioquack.nodelibrary.nodes.protocol;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation and host-key refusal for {@link SshNode}. No in-process SSH server
 * is available offline in {@code ~/.m2}, so JSch is not exercised against a live
 * handshake — request building, {@link StrictHostKeys}, and pre-connect denies.
 */
class SshNodeTest {

    private final SshNode node = new SshNode("ssh-test-id");

    @Test
    void constructorKeepsTemplateId() {
        assertEquals("ssh-test-id", node.templateId());
        assertEquals(SshNode.DEFAULT_ID, new SshNode("").templateId());
    }

    @Test
    void missingHostIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "command", "uname"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("host"), e.getMessage());
    }

    @Test
    void missingKnownHostsRefusesBeforeConnect() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "command", "uname", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("known_hosts"), e.getMessage());
    }

    @Test
    void emptyKnownHostsRefuses() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "command", "uname", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("known_hosts", "string", "  ")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("known_hosts"), e.getMessage());
    }

    @Test
    void unknownOperationIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "shell", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"), e.getMessage());
    }

    @Test
    void execRequiresCommand() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("command"), e.getMessage());
    }

    @Test
    void multilineCommandRejected() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "host", "127.0.0.1",
                                "command", "uname\nrm -rf /"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("single remote command"), e.getMessage());
    }

    @Test
    void remotePathTraversalRejected() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "download", "host", "127.0.0.1",
                                "remote_path", "/var/tmp/../etc/passwd"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("path traversal"), e.getMessage());
    }

    @Test
    void passwordOnInputIsIgnored() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "host", "127.0.0.1",
                                "command", "uname", "password", "s3cret-from-input"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("password"), e.getMessage());
        assertTrue(!e.getMessage().contains("s3cret-from-input"), e.getMessage());
    }

    @Test
    void nonPasswordTypedSecretRejected() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "exec", "host", "127.0.0.1", "command", "uname"),
                        cfg("user", "string", "alice"),
                        cfg("auth_type", "select", "password"),
                        cfg("password", "string", "s3cret-pass"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("password-typed"), e.getMessage());
    }

    @Test
    void authErrorRedactsPassword() {
        StudioQuackError e = ProtocolSupport.mappedFailure(
                new RuntimeException("Auth fail for s3cret-pass"),
                List.of("s3cret-pass"));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertTrue(!e.getMessage().contains("s3cret-pass"), e.getMessage());
    }

    @Test
    void emptyKnownHostsParseRefuses() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> StrictHostKeys.parse(""));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void fingerprintMatchAndMismatch() throws Exception {
        byte[] key = ed25519Blob(bytes(32, (byte) 7));
        byte[] other = ed25519Blob(bytes(32, (byte) 9));
        HostKey hk = new HostKey("127.0.0.1", key);
        String fp = hk.getFingerPrint(new JSch());
        StrictHostKeys repo = StrictHostKeys.parse(fp);
        assertEquals(HostKeyRepository.OK, repo.check("127.0.0.1", key));
        assertEquals(HostKeyRepository.NOT_INCLUDED, repo.check("127.0.0.1", other));
    }

    @Test
    void knownHostsLineMatchesOnlyPinnedKey() throws Exception {
        byte[] key = ed25519Blob(bytes(32, (byte) 3));
        byte[] other = ed25519Blob(bytes(32, (byte) 4));
        HostKey hk = new HostKey("example.test", key);
        String line = "example.test " + hk.getType() + " " + hk.getKey();
        StrictHostKeys repo = StrictHostKeys.parse(line);
        assertEquals(HostKeyRepository.OK, repo.check("example.test", key));
        assertEquals(HostKeyRepository.NOT_INCLUDED, repo.check("example.test", other));
        // add() must not learn the unknown key (no TOFU)
        repo.add(new HostKey("example.test", other), null);
        assertEquals(HostKeyRepository.NOT_INCLUDED, repo.check("example.test", other));
    }

    @Test
    void looksLikeKnownHostsDetectsKeyTypeLine() {
        assertTrue(StrictHostKeys.looksLikeKnownHosts("host.example ssh-ed25519 AAAAC3NzaC1lZDI1NTE5"));
        assertTrue(!StrictHostKeys.looksLikeKnownHosts("SHA256:abcdef"));
    }

    static Map<String, Object> cfg(String code, String type, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shortCode", code);
        m.put("type", type);
        m.put("value", value);
        return m;
    }

    @SafeVarargs
    static Map<String, Object> request(Map<String, Object> input, Map<String, Object>... params) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configurationParameters", List.of(params));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("data", data);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("currentNode", node);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("inputVariables", Map.of("input", input));
        req.put("executionContext", ctx);
        return req;
    }

    static byte[] ed25519Blob(byte[] pub32) {
        byte[] name = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU32(out, name.length);
        out.writeBytes(name);
        writeU32(out, pub32.length);
        out.writeBytes(pub32);
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, int n) {
        out.write((n >>> 24) & 0xff);
        out.write((n >>> 16) & 0xff);
        out.write((n >>> 8) & 0xff);
        out.write(n & 0xff);
    }

    private static byte[] bytes(int n, byte fill) {
        byte[] b = new byte[n];
        java.util.Arrays.fill(b, fill);
        return b;
    }
}
