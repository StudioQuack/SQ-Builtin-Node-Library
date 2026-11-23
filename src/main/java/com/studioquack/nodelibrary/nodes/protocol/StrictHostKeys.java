package com.studioquack.nodelibrary.nodes.protocol;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;
import com.studioquack.nodelibrary.core.DataSupport;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Host-key pin: OpenSSH {@code known_hosts} content or a single fingerprint.
 *
 * <p>{@link #add} is a no-op — this repository never learns keys (no trust-on-first-use).
 * A missing or empty pin is {@code INVALID_INPUT} before any socket is opened.
 */
final class StrictHostKeys implements HostKeyRepository {

    private final HostKeyRepository knownHosts;
    private final String fingerprint;
    private final JSch fingerprintJsch;

    private StrictHostKeys(HostKeyRepository knownHosts, String fingerprint) {
        this.knownHosts = knownHosts;
        this.fingerprint = fingerprint;
        this.fingerprintJsch = fingerprint == null ? null : new JSch();
    }

    static StrictHostKeys parse(String knownHostsOrFingerprint) {
        String raw = knownHostsOrFingerprint == null ? "" : knownHostsOrFingerprint.trim();
        if (raw.isEmpty()) {
            DataSupport.invalidInput(
                    "known_hosts is required (OpenSSH known_hosts content or a host-key fingerprint)");
        }
        if (looksLikeKnownHosts(raw)) {
            try {
                JSch jsch = new JSch();
                jsch.setKnownHosts(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
                HostKeyRepository inner = jsch.getHostKeyRepository();
                HostKey[] loaded = inner.getHostKey();
                if (loaded == null || loaded.length == 0) {
                    DataSupport.invalidInput("known_hosts contains no host keys");
                }
                return new StrictHostKeys(inner, null);
            } catch (JSchException e) {
                DataSupport.invalidInput("known_hosts is not valid OpenSSH known_hosts content");
            }
        }
        return new StrictHostKeys(null, normalizeFingerprint(raw));
    }

    static boolean looksLikeKnownHosts(String raw) {
        String t = raw.trim();
        if (t.startsWith("@cert-authority") || t.startsWith("@revoked") || t.startsWith("|1|")) {
            return true;
        }
        for (String line : t.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains(" ssh-") || lower.contains(" ecdsa-") || lower.contains(" sk-")) {
                return true;
            }
        }
        return false;
    }

    static String normalizeFingerprint(String raw) {
        String n = raw.trim();
        if (n.regionMatches(true, 0, "SHA256:", 0, 7)) {
            return "SHA256:" + n.substring(7).trim();
        }
        if (n.regionMatches(true, 0, "MD5:", 0, 4)) {
            return "MD5:" + n.substring(4).trim().toLowerCase(Locale.ROOT);
        }
        if (n.matches("(?i)([0-9a-f]{2}:){15}[0-9a-f]{2}")) {
            return "MD5:" + n.toLowerCase(Locale.ROOT);
        }
        return "SHA256:" + n;
    }

    static boolean fingerprintsMatch(String presented, String expected) {
        if (presented == null || expected == null) {
            return false;
        }
        return normalizeFingerprint(presented).equalsIgnoreCase(normalizeFingerprint(expected));
    }

    @Override
    public int check(String host, byte[] key) {
        if (key == null || key.length == 0) {
            return NOT_INCLUDED;
        }
        if (knownHosts != null) {
            int r = knownHosts.check(host, key);
            return r == OK ? OK : NOT_INCLUDED;
        }
        try {
            HostKey presented = new HostKey(host == null ? "" : host, key);
            String fp = presented.getFingerPrint(fingerprintJsch);
            return fingerprintsMatch(fp, fingerprint) ? OK : NOT_INCLUDED;
        } catch (JSchException e) {
            return NOT_INCLUDED;
        }
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        // no TOFU
    }

    @Override
    public void remove(String host, String type) {
        // pin is immutable for the invoke
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        // pin is immutable for the invoke
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return "studioquack-strict-host-keys";
    }

    @Override
    public HostKey[] getHostKey() {
        return knownHosts == null ? new HostKey[0] : knownHosts.getHostKey();
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        return knownHosts == null ? new HostKey[0] : knownHosts.getHostKey(host, type);
    }

    String pinnedFingerprint() {
        return fingerprint;
    }
}
