package com.studioquack.nodelibrary.nodes.protocol;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared SSH-2 / SFTP plumbing for {@link SshNode} and {@link FtpNode}.
 *
 * <p>Host-key verification is mandatory (no TOFU, never {@code StrictHostKeyChecking=no}).
 * Credentials are never logged. Timeouts and transfer sizes are capped.
 */
final class ProtocolSupport {

    static final int DEFAULT_TIMEOUT_SECONDS = 25;
    static final int MAX_TIMEOUT_SECONDS = 60;
    static final int MAX_FILE_BYTES = FilesSupport.MAX_FILE_BYTES;
    static final int MAX_EXEC_OUTPUT_BYTES = 2_000_000;
    static final int MAX_LIST_ENTRIES = 1000;
    static final int MAX_COMMAND_CHARS = 8192;

    /** RFC 8332 / modern host keys — {@code ssh-rsa} (SHA-1) is not offered. */
    static final String SERVER_HOST_KEY =
            "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,"
                    + "rsa-sha2-512,rsa-sha2-256";

    private ProtocolSupport() {
        // utility
    }

    static int clampTimeout(int seconds) {
        if (seconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(seconds, MAX_TIMEOUT_SECONDS);
    }

    static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    static boolean cfgFlag(Map<String, Object> cfg, String key, boolean fallback) {
        if (cfg == null || cfg.get(key) == null) {
            return fallback;
        }
        Object v = cfg.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
            return false;
        }
        return fallback;
    }

    /**
     * Secret from instance config only, and only when the declared type is
     * {@code password}. Never reads INPUT.
     */
    static String passwordTyped(Map<String, Object> cfg, Map<String, String> types, String key) {
        if (cfg == null || key == null) {
            return null;
        }
        Object raw = cfg.get(key);
        if (raw == null) {
            return null;
        }
        String value = DataSupport.trimToNull(String.valueOf(raw));
        if (value == null) {
            return null;
        }
        String type = types == null ? null : types.get(key);
        if (type != null && !"password".equalsIgnoreCase(type.trim())) {
            DataSupport.invalidInput(key + " must be a password-typed configuration parameter");
        }
        return value;
    }

    static void addSecret(List<String> secrets, String value) {
        if (value != null && !value.isBlank()) {
            secrets.add(value);
        }
    }

    static String requireHost(Map<String, Object> in, Map<String, Object> cfg) {
        String host = firstNonBlank(DataSupport.inStr(in, "host", null), DataSupport.cfgStr(cfg, "host", null));
        if (host == null || host.isBlank()) {
            DataSupport.invalidInput("host is required");
        }
        if (host.indexOf('\n') >= 0 || host.indexOf('\r') >= 0 || host.indexOf('\0') >= 0) {
            DataSupport.invalidInput("host contains invalid characters");
        }
        return host.trim();
    }

    static String requireUser(Map<String, Object> cfg) {
        String user = DataSupport.cfgStr(cfg, "user", null);
        if (user == null || user.isBlank()) {
            DataSupport.invalidInput("user is required");
        }
        return user.trim();
    }

    static int portOf(Map<String, Object> cfg, int fallback) {
        int port = DataSupport.cfgInt(cfg, "port", fallback);
        if (port <= 0 || port > 65535) {
            DataSupport.invalidInput("port must be between 1 and 65535");
        }
        return port;
    }

    static String operation(Map<String, Object> in, Map<String, Object> cfg, String fallback) {
        String op = DataSupport.inStr(in, "operation", null);
        if (op == null) {
            op = DataSupport.cfgStr(cfg, "operation", fallback);
        }
        return op == null ? fallback : op.trim().toLowerCase(Locale.ROOT);
    }

    static String requireRemotePath(Map<String, Object> in, String field) {
        return requireRemotePath(DataSupport.inStr(in, field, null), field);
    }

    static String requireRemotePath(String path, String field) {
        if (path == null || path.isBlank()) {
            DataSupport.invalidInput(field + " is required");
        }
        return checkRemotePath(path.trim(), field);
    }

    static String optionalRemotePath(Map<String, Object> in, String field, String fallback) {
        String path = DataSupport.inStr(in, field, fallback);
        if (path == null || path.isBlank()) {
            return fallback;
        }
        return checkRemotePath(path.trim(), field);
    }

    static String checkRemotePath(String path, String field) {
        if (path.indexOf('\0') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
            DataSupport.invalidInput(field + " contains invalid characters");
        }
        String norm = path.replace('\\', '/');
        for (String part : norm.split("/")) {
            if ("..".equals(part)) {
                DataSupport.invalidInput(field + " rejects path traversal ('..')");
            }
        }
        return path;
    }

    static String requireRemoteCommand(Map<String, Object> in) {
        String command = DataSupport.inStr(in, "command", null);
        if (command == null || command.isBlank()) {
            DataSupport.invalidInput("command is required for exec");
        }
        if (command.length() > MAX_COMMAND_CHARS) {
            DataSupport.invalidInput("command exceeds " + MAX_COMMAND_CHARS + " characters");
        }
        if (command.indexOf('\0') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\n') >= 0) {
            DataSupport.invalidInput("command must be a single remote command string");
        }
        return command;
    }

    static String fileNameOf(String remotePath) {
        if (remotePath == null || remotePath.isBlank()) {
            return "download.bin";
        }
        String norm = remotePath.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        String name = slash >= 0 ? norm.substring(slash + 1) : norm;
        return name.isBlank() ? "download.bin" : name;
    }

    static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        if (data != null) {
            out.put("data", data);
        }
        return DataSupport.ports(out);
    }

    static Map<String, Object> okWithFile(Map<String, Object> data, String name, byte[] bytes) {
        Map<String, Object> block = FilesSupport.fileBlock(name, null, bytes);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        if (data != null) {
            out.put("data", data);
        }
        out.put("file", block);
        out.put("outputFiles", List.of(block));
        return DataSupport.ports(out);
    }

    static StudioQuackError redactError(StudioQuackError e, List<String> secrets) {
        String[] arr = secrets == null ? new String[0] : secrets.toArray(String[]::new);
        String msg = DataSupport.redact(e.getMessage(), arr);
        return new StudioQuackError(e.errorCode(), msg, e.data());
    }

    static StudioQuackError mappedFailure(Exception e, List<String> secrets) {
        String[] arr = secrets == null ? new String[0] : secrets.toArray(String[]::new);
        String raw = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        String msg = DataSupport.redact(raw, arr);
        if (isAuthFailure(e)) {
            return new StudioQuackError("AUTH_FAILED",
                    DataSupport.redact("HTTP 401: authentication failed", arr),
                    Map.of("http_status", 401));
        }
        if (isHostKeyFailure(e)) {
            return DataSupport.failError(DataSupport.redact(
                    "host key mismatch or unknown host; known_hosts must pin the server key", arr));
        }
        return DataSupport.failError(msg);
    }

    static boolean isAuthFailure(Throwable e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("auth fail") || m.contains("auth cancel")
                || m.contains("authentication failed") || m.contains("auth fail");
    }

    static boolean isHostKeyFailure(Throwable e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("reject hostkey") || m.contains("unknownhostkey")
                || m.contains("hostkey has been changed") || m.contains("strict host key");
    }

    static Session openSession(String host, int port, String user, String authType,
                               String password, String privateKey, String passphrase,
                               String knownHosts, int timeoutSeconds) throws JSchException {
        if (knownHosts == null || knownHosts.isBlank()) {
            DataSupport.invalidInput("known_hosts is required (OpenSSH known_hosts content or a host-key fingerprint)");
        }
        StrictHostKeys keys = StrictHostKeys.parse(knownHosts);
        JSch jsch = new JSch();
        jsch.setInstanceLogger(SilentJschLogger.INSTANCE);
        jsch.setHostKeyRepository(keys);
        String auth = authType == null || authType.isBlank() ? "key" : authType.trim().toLowerCase(Locale.ROOT);
        if ("key".equals(auth) || "private_key".equals(auth) || "publickey".equals(auth)) {
            if (privateKey == null || privateKey.isBlank()) {
                DataSupport.invalidInput("private_key is required when auth_type=key");
            }
            byte[] pem = privateKey.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8);
            byte[] pass = (passphrase == null || passphrase.isBlank())
                    ? null : passphrase.getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("sq", pem, null, pass);
        } else if ("password".equals(auth)) {
            if (password == null || password.isBlank()) {
                DataSupport.invalidInput("password is required when auth_type=password");
            }
        } else {
            DataSupport.invalidInput("auth_type must be password or key");
        }
        Session session = jsch.getSession(user, host, port);
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("server_host_key", SERVER_HOST_KEY);
        session.setConfig("PreferredAuthentications",
                "password".equals(auth) ? "password" : "publickey");
        if ("password".equals(auth)) {
            session.setPassword(password);
        }
        session.setUserInfo(RefusePrompts.INSTANCE);
        int ms = timeoutSeconds * 1000;
        session.setTimeout(ms);
        session.connect(ms);
        return session;
    }

    static Map<String, Object> sftpList(Session session, String remotePath, int timeoutMs) throws Exception {
        ChannelSftp sftp = openSftp(session, timeoutMs);
        try {
            @SuppressWarnings("unchecked")
            java.util.Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath);
            List<Map<String, Object>> items = new ArrayList<>();
            int n = 0;
            if (entries != null) {
                for (ChannelSftp.LsEntry e : entries) {
                    if (e == null || e.getFilename() == null) {
                        continue;
                    }
                    String name = e.getFilename();
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    if (n >= MAX_LIST_ENTRIES) {
                        break;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", name);
                    if (e.getAttrs() != null) {
                        row.put("dir", e.getAttrs().isDir());
                        row.put("size", e.getAttrs().getSize());
                    }
                    items.add(row);
                    n++;
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entries", items);
            data.put("count", items.size());
            return ok(data);
        } finally {
            sftp.disconnect();
        }
    }

    static Map<String, Object> sftpDownload(Session session, String remotePath, int timeoutMs) throws Exception {
        ChannelSftp sftp = openSftp(session, timeoutMs);
        try (InputStream in = sftp.get(remotePath)) {
            byte[] bytes = readCapped(in, MAX_FILE_BYTES);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remote_path", remotePath);
            data.put("bytes", bytes.length);
            return okWithFile(data, fileNameOf(remotePath), bytes);
        } finally {
            sftp.disconnect();
        }
    }

    static Map<String, Object> sftpUpload(Session session, String remotePath, byte[] bytes, int timeoutMs)
            throws Exception {
        if (bytes == null) {
            bytes = new byte[0];
        }
        if (bytes.length > MAX_FILE_BYTES) {
            DataSupport.fail("upload is " + bytes.length + " bytes, above the "
                    + MAX_FILE_BYTES + " byte node limit");
        }
        ChannelSftp sftp = openSftp(session, timeoutMs);
        try {
            sftp.put(new ByteArrayInputStream(bytes), remotePath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remote_path", remotePath);
            data.put("bytes", bytes.length);
            return ok(data);
        } finally {
            sftp.disconnect();
        }
    }

    static Map<String, Object> sftpDelete(Session session, String remotePath, int timeoutMs) throws Exception {
        ChannelSftp sftp = openSftp(session, timeoutMs);
        try {
            sftp.rm(remotePath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remote_path", remotePath);
            return ok(data);
        } finally {
            sftp.disconnect();
        }
    }

    static Map<String, Object> sftpRename(Session session, String remotePath, String newPath, int timeoutMs)
            throws Exception {
        ChannelSftp sftp = openSftp(session, timeoutMs);
        try {
            sftp.rename(remotePath, newPath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remote_path", remotePath);
            data.put("new_path", newPath);
            return ok(data);
        } finally {
            sftp.disconnect();
        }
    }

    static Map<String, Object> exec(Session session, String command, int timeoutMs) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setPty(false);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setErrStream(err);
        channel.connect(timeoutMs);
        try {
            byte[] stdout = readCapped(channel.getInputStream(), MAX_EXEC_OUTPUT_BYTES);
            waitClosed(channel, timeoutMs);
            if (err.size() > MAX_EXEC_OUTPUT_BYTES) {
                DataSupport.fail("exec stderr exceeded " + MAX_EXEC_OUTPUT_BYTES + " bytes");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exit_code", channel.getExitStatus());
            data.put("stdout", new String(stdout, StandardCharsets.UTF_8));
            data.put("stderr", err.toString(StandardCharsets.UTF_8));
            return ok(data);
        } finally {
            channel.disconnect();
        }
    }

    private static ChannelSftp openSftp(Session session, int timeoutMs) throws JSchException {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(timeoutMs);
        return sftp;
    }

    static byte[] readCapped(InputStream in, int max) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        byte[] buf = in.readNBytes(max + 1);
        if (buf.length > max) {
            DataSupport.fail("transfer exceeded " + max + " bytes");
        }
        return buf;
    }

    private static void waitClosed(Channel channel, int timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!channel.isClosed()) {
            if (System.nanoTime() >= deadline) {
                DataSupport.fail("exec timed out");
            }
            Thread.sleep(20);
        }
    }

    static void closeQuietly(Session session) {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    static String safeExceptionName(Exception e) {
        if (e instanceof JSchException) {
            return "JSchException";
        }
        if (e instanceof SftpException) {
            return "SftpException";
        }
        return e.getClass().getSimpleName();
    }

    private static final class RefusePrompts implements UserInfo {
        static final RefusePrompts INSTANCE = new RefusePrompts();

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
            // never echo
        }
    }

    private static final class SilentJschLogger implements com.jcraft.jsch.Logger {
        static final SilentJschLogger INSTANCE = new SilentJschLogger();

        @Override
        public boolean isEnabled(int level) {
            return false;
        }

        @Override
        public void log(int level, String message) {
            // never log — JSch messages can include host keys or auth detail
        }
    }
}
