package com.studioquack.nodelibrary.nodes.protocol;

import com.jcraft.jsch.Session;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FTP / explicit FTPS / SFTP file transfer ({@code nodes/ftp/}).
 *
 * <p>SFTP is preferred (JSch, mandatory host-key pin). Plain FTP is refused
 * unless {@code allow_plain_ftp} is true. Explicit FTPS uses {@code AUTH TLS}
 * and {@code PROT P} with JDK certificate verification.
 */
public final class FtpNode implements NodeHandler {

    /** Matches {@code nodes/ftp/node_definition.json} {@code _id}. */
    public static final String DEFAULT_ID = "e523b30c-5da7-4b39-b55f-e25f52123701";

    private final String templateId;

    public FtpNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    public FtpNode() {
        this(DataSupport.templateId("SQ_TEMPLATE_ID_FTP", DEFAULT_ID));
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        List<String> secrets = new ArrayList<>();
        Session session = null;
        FtpControlClient ftp = null;
        try {
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, String> types = DataSupport.readConfigTypes(request);
            Map<String, Object> in = DataSupport.readInput(request, helper);

            String password = ProtocolSupport.passwordTyped(cfg, types, "password");
            String privateKey = ProtocolSupport.passwordTyped(cfg, types, "private_key");
            String passphrase = ProtocolSupport.passwordTyped(cfg, types, "passphrase");
            ProtocolSupport.addSecret(secrets, password);
            ProtocolSupport.addSecret(secrets, privateKey);
            ProtocolSupport.addSecret(secrets, passphrase);

            String op = ProtocolSupport.operation(in, cfg, "list");
            if (!"list".equals(op) && !"download".equals(op) && !"upload".equals(op)
                    && !"delete".equals(op) && !"rename".equals(op)) {
                DataSupport.invalidInput(
                        "unknown operation: " + op + " (accepted: list, download, upload, delete, rename)");
            }

            String protocol = DataSupport.cfgStr(cfg, "protocol", "sftp");
            protocol = protocol == null ? "sftp" : protocol.trim().toLowerCase(Locale.ROOT);
            if (!"sftp".equals(protocol) && !"ftp".equals(protocol) && !"ftps".equals(protocol)) {
                DataSupport.invalidInput("protocol must be sftp, ftp, or ftps");
            }

            String host = ProtocolSupport.requireHost(in, cfg);
            String user = ProtocolSupport.requireUser(cfg);
            int defaultPort = "sftp".equals(protocol) ? 22 : 21;
            int port = ProtocolSupport.portOf(cfg, defaultPort);
            int timeout = ProtocolSupport.clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds",
                    ProtocolSupport.DEFAULT_TIMEOUT_SECONDS));
            if ("list".equals(op)) {
                ProtocolSupport.optionalRemotePath(in, "remote_path", ".");
            } else {
                ProtocolSupport.requireRemotePath(in, "remote_path");
                if ("rename".equals(op)) {
                    ProtocolSupport.requireRemotePath(in, "new_path");
                }
                if ("upload".equals(op)) {
                    FilesSupport.readFileValue(in.get("file"), "file");
                }
            }

            if ("sftp".equals(protocol)) {
                String knownHosts = DataSupport.cfgStr(cfg, "known_hosts", null);
                String authType = privateKey != null && !privateKey.isBlank() ? "key" : "password";
                session = ProtocolSupport.openSession(host, port, user, authType,
                        password, privateKey, passphrase, knownHosts, timeout);
                return sftp(session, op, in, timeout * 1000);
            }

            if ("ftp".equals(protocol) && !ProtocolSupport.cfgFlag(cfg, "allow_plain_ftp", false)) {
                DataSupport.invalidInput(
                        "plain FTP is disabled; set allow_plain_ftp=true to permit cleartext, or use sftp / ftps");
            }
            if (!ProtocolSupport.cfgFlag(cfg, "passive", true)) {
                DataSupport.invalidInput("active FTP (PORT) is not supported; keep passive=true");
            }
            ftp = new FtpControlClient(host, port, user, password, "ftps".equals(protocol), timeout);
            return ftpOp(ftp, op, in);
        } catch (StudioQuackError e) {
            throw ProtocolSupport.redactError(e, secrets);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw ProtocolSupport.mappedFailure(e, secrets);
        } finally {
            ProtocolSupport.closeQuietly(session);
            if (ftp != null) {
                ftp.close();
            }
        }
    }

    private static Map<String, Object> sftp(Session session, String op, Map<String, Object> in, int timeoutMs)
            throws Exception {
        return switch (op) {
            case "list" -> ProtocolSupport.sftpList(session,
                    ProtocolSupport.optionalRemotePath(in, "remote_path", "."), timeoutMs);
            case "download" -> ProtocolSupport.sftpDownload(session,
                    ProtocolSupport.requireRemotePath(in, "remote_path"), timeoutMs);
            case "upload" -> {
                FilesSupport.FileBytes file = FilesSupport.readFileValue(in.get("file"), "file");
                yield ProtocolSupport.sftpUpload(session,
                        ProtocolSupport.requireRemotePath(in, "remote_path"), file.bytes(), timeoutMs);
            }
            case "delete" -> ProtocolSupport.sftpDelete(session,
                    ProtocolSupport.requireRemotePath(in, "remote_path"), timeoutMs);
            case "rename" -> ProtocolSupport.sftpRename(session,
                    ProtocolSupport.requireRemotePath(in, "remote_path"),
                    ProtocolSupport.requireRemotePath(in, "new_path"), timeoutMs);
            default -> DataSupport.invalidInput("unknown operation: " + op);
        };
    }

    private static Map<String, Object> ftpOp(FtpControlClient ftp, String op, Map<String, Object> in)
            throws Exception {
        return switch (op) {
            case "list" -> ftp.list(ProtocolSupport.optionalRemotePath(in, "remote_path", "."));
            case "download" -> ftp.download(ProtocolSupport.requireRemotePath(in, "remote_path"));
            case "upload" -> {
                FilesSupport.FileBytes file = FilesSupport.readFileValue(in.get("file"), "file");
                yield ftp.upload(ProtocolSupport.requireRemotePath(in, "remote_path"), file.bytes());
            }
            case "delete" -> ftp.delete(ProtocolSupport.requireRemotePath(in, "remote_path"));
            case "rename" -> ftp.rename(ProtocolSupport.requireRemotePath(in, "remote_path"),
                    ProtocolSupport.requireRemotePath(in, "new_path"));
            default -> DataSupport.invalidInput("unknown operation: " + op);
        };
    }
}
