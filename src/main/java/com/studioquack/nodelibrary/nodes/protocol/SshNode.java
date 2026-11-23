package com.studioquack.nodelibrary.nodes.protocol;

import com.jcraft.jsch.Session;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SSH-2 exec / SFTP download / SFTP upload ({@code nodes/ssh/}).
 *
 * <p>Exec runs a single command string on the remote host via SSH-2 {@code exec}
 * — never {@code Runtime.exec} / local process. Host-key verification is
 * mandatory. Credentials come only from password-typed configuration.
 */
public final class SshNode implements NodeHandler {

    /** Matches {@code nodes/ssh/node_definition.json} {@code _id}. */
    public static final String DEFAULT_ID = "058af46e-003f-4a87-9707-1103a8341ae6";

    private final String templateId;

    public SshNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    public SshNode() {
        this(DataSupport.templateId("SQ_TEMPLATE_ID_SSH", DEFAULT_ID));
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        List<String> secrets = new ArrayList<>();
        Session session = null;
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

            String op = ProtocolSupport.operation(in, cfg, "exec");
            if (!"exec".equals(op) && !"download".equals(op) && !"upload".equals(op)) {
                DataSupport.invalidInput("unknown operation: " + op + " (accepted: exec, download, upload)");
            }

            String host = ProtocolSupport.requireHost(in, cfg);
            String user = ProtocolSupport.requireUser(cfg);
            int port = ProtocolSupport.portOf(cfg, 22);
            int timeout = ProtocolSupport.clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds",
                    ProtocolSupport.DEFAULT_TIMEOUT_SECONDS));
            String knownHosts = DataSupport.cfgStr(cfg, "known_hosts", null);
            String authType = DataSupport.cfgStr(cfg, "auth_type", "key");
            String command = "exec".equals(op) ? ProtocolSupport.requireRemoteCommand(in) : null;
            String remotePath = "exec".equals(op) ? null : ProtocolSupport.requireRemotePath(in, "remote_path");
            FilesSupport.FileBytes upload = null;
            if ("upload".equals(op)) {
                upload = FilesSupport.readFileValue(in.get("file"), "file");
            }

            session = ProtocolSupport.openSession(host, port, user, authType,
                    password, privateKey, passphrase, knownHosts, timeout);
            int timeoutMs = timeout * 1000;
            return switch (op) {
                case "exec" -> ProtocolSupport.exec(session, command, timeoutMs);
                case "download" -> ProtocolSupport.sftpDownload(session, remotePath, timeoutMs);
                case "upload" -> ProtocolSupport.sftpUpload(session, remotePath, upload.bytes(), timeoutMs);
                default -> DataSupport.invalidInput("unknown operation: " + op);
            };
        } catch (StudioQuackError e) {
            throw ProtocolSupport.redactError(e, secrets);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw ProtocolSupport.mappedFailure(e, secrets);
        } finally {
            ProtocolSupport.closeQuietly(session);
        }
    }
}
