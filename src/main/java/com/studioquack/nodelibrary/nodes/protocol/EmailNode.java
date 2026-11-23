package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.imap.protocol.IMAPResponse;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * SMTP send and IMAP mailbox: {@code send | listen_imap | search | get | move | mark}.
 *
 * <p>TLS is required by default (SMTPS on 465, STARTTLS otherwise; IMAPS).
 * Plain sockets are allowed only when config {@code tls} is explicitly
 * {@code false}. Passwords are never logged. {@code listen_imap} is ingress.
 */
public final class EmailNode implements NodeHandler {

    public static final String DEFAULT_ID = "2772d388-f3ca-473b-a45b-4fb16bccb522";

    static final int MAX_SEARCH_RESULTS = 100;
    static final int MAX_BODY_CHARS = 200_000;
    static final int MAX_ATTACHMENTS = 8;
    static final int MAX_RECIPIENTS = 50;
    static final int MAX_QUERY_CHARS = 1_024;
    static final int DEFAULT_TIMEOUT = 25;
    static final int MAX_TIMEOUT = 60;

    private static final String INGRESS_MESSAGE =
            "Inbound events are not a node operation. Use a BPMN message start / "
                    + "intermediate catch / boundary message event; the StudioQuack webhook "
                    + "ingress correlates onto those events.";

    private static final Set<String> OPS = Set.of(
            "send", "listen_imap", "search", "get", "move", "mark");
    private static final Set<String> IMAP_OPS = Set.of("search", "get", "move", "mark");
    private static final Set<String> FLAGS = Set.of("seen", "flagged", "deleted");

    private final String templateId;

    public EmailNode(String templateId) {
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
        String password = DataSupport.cfgStr(cfg, "password", null);
        try {
            String op = operation(in, cfg);
            if ("listen_imap".equals(op)) {
                return DataSupport.invalidInput(INGRESS_MESSAGE);
            }
            return switch (op) {
                case "send" -> send(cfg, in, password);
                case "search", "get", "move", "mark" -> imap(op, cfg, in, password);
                default -> DataSupport.invalidInput(
                        "Unknown operation: " + op + ". Accepted: send, listen_imap, search, get, move, mark");
            };
        } catch (StudioQuackError e) {
            throw redactError(e, password);
        } catch (AuthenticationFailedException e) {
            throw new StudioQuackError("AUTH_FAILED",
                    DataSupport.redact("mailbox authentication failed", password));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DataSupport.fail(DataSupport.redact(safeMsg(e), password));
        }
    }

    static String operation(Map<String, Object> in, Map<String, Object> cfg) {
        String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "send"),
                "operation", "op");
        if (op == null || op.isBlank()) {
            throw invalid("operation is required. Accepted: send, listen_imap, search, get, move, mark");
        }
        op = op.trim().toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            throw invalid("Unknown operation: " + op + ". Accepted: send, listen_imap, search, get, move, mark");
        }
        return op;
    }

    static boolean tlsRequired(Map<String, Object> cfg) {
        String v = DataSupport.cfgStr(cfg, "tls", "true");
        return v == null || !"false".equalsIgnoreCase(v.trim());
    }

    static int timeoutSeconds(Map<String, Object> cfg) {
        int n = DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT);
        if (n <= 0) {
            return DEFAULT_TIMEOUT;
        }
        return Math.min(n, MAX_TIMEOUT);
    }

    static String searchCriteria(String query) {
        String q = query == null || query.isBlank() ? "ALL" : query.trim();
        if (q.length() > MAX_QUERY_CHARS) {
            throw invalid("query exceeds " + MAX_QUERY_CHARS + " characters");
        }
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c < 32) {
                throw invalid("query contains control characters");
            }
        }
        return q;
    }

    static Flags.Flag imapFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            throw invalid("flag is required for mark. Accepted: seen, flagged, deleted");
        }
        String f = flag.trim().toLowerCase(Locale.ROOT);
        if (!FLAGS.contains(f)) {
            throw invalid("Unknown flag: " + f + ". Accepted: seen, flagged, deleted");
        }
        return switch (f) {
            case "seen" -> Flags.Flag.SEEN;
            case "flagged" -> Flags.Flag.FLAGGED;
            default -> Flags.Flag.DELETED;
        };
    }

    static Properties smtpProperties(String host, int port, boolean tls, boolean auth, int timeoutMs) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", Integer.toString(port));
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.connectiontimeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.timeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.writetimeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        if (tls) {
            if (port == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
                props.put("mail.smtp.starttls.enable", "false");
                props.put("mail.smtp.starttls.required", "false");
            } else {
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
        }
        return props;
    }

    static Properties imapProperties(String host, int port, boolean tls, int timeoutMs) {
        Properties props = new Properties();
        String proto = tls ? "imaps" : "imap";
        props.put("mail.store.protocol", proto);
        props.put("mail." + proto + ".host", host);
        props.put("mail." + proto + ".port", Integer.toString(port));
        props.put("mail." + proto + ".connectiontimeout", Integer.toString(timeoutMs));
        props.put("mail." + proto + ".timeout", Integer.toString(timeoutMs));
        props.put("mail." + proto + ".writetimeout", Integer.toString(timeoutMs));
        if (tls) {
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.ssl.checkserveridentity", "true");
            props.put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3");
        } else {
            props.put("mail.imap.ssl.enable", "false");
            props.put("mail.imap.starttls.enable", "false");
        }
        return props;
    }

    private static Map<String, Object> send(Map<String, Object> cfg, Map<String, Object> in,
                                            String password) throws MessagingException {
        String host = DataSupport.cfgStr(cfg, "smtp_host", null);
        if (host == null || host.isBlank()) {
            return DataSupport.invalidInput("smtp_host is required for send");
        }
        String from = firstNonBlank(DataSupport.inStr(in, "from", null), DataSupport.cfgStr(cfg, "from", null));
        if (from == null) {
            return DataSupport.invalidInput("from is required for send");
        }
        List<InternetAddress> to = addresses(in, "to", true);
        List<InternetAddress> cc = addresses(in, "cc", false);
        List<InternetAddress> bcc = addresses(in, "bcc", false);
        if (to.size() + cc.size() + bcc.size() > MAX_RECIPIENTS) {
            return DataSupport.invalidInput("recipient count exceeds " + MAX_RECIPIENTS);
        }
        String subject = DataSupport.inStr(in, "subject", "");
        String text = DataSupport.inStr(in, "text", null);
        String html = DataSupport.inStr(in, "html", null);
        if (text != null && text.length() > MAX_BODY_CHARS) {
            return DataSupport.invalidInput("text exceeds " + MAX_BODY_CHARS + " characters");
        }
        if (html != null && html.length() > MAX_BODY_CHARS) {
            return DataSupport.invalidInput("html exceeds " + MAX_BODY_CHARS + " characters");
        }
        List<FilesSupport.FileBytes> files = readAttachments(in.get("attachments"));
        int port = DataSupport.cfgInt(cfg, "smtp_port", 587);
        int timeoutMs = timeoutSeconds(cfg) * 1000;
        boolean tls = tlsRequired(cfg);
        String user = DataSupport.cfgStr(cfg, "user", null);
        boolean auth = password != null && !password.isBlank();
        if (auth && (user == null || user.isBlank())) {
            return DataSupport.invalidInput("user is required when password is set");
        }
        Properties props = smtpProperties(host.trim(), port, tls, auth, timeoutMs);
        Session session = Session.getInstance(props, auth ? authenticator(user, password) : null);
        session.setDebug(false);
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(address(from));
        msg.setRecipients(Message.RecipientType.TO, to.toArray(InternetAddress[]::new));
        if (!cc.isEmpty()) {
            msg.setRecipients(Message.RecipientType.CC, cc.toArray(InternetAddress[]::new));
        }
        if (!bcc.isEmpty()) {
            msg.setRecipients(Message.RecipientType.BCC, bcc.toArray(InternetAddress[]::new));
        }
        msg.setSubject(subject == null ? "" : subject, "UTF-8");
        applyBody(msg, text, html, files);
        msg.saveChanges();
        if (auth) {
            Transport.send(msg, user, password);
        } else {
            Transport.send(msg);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", msg.getMessageID());
        data.put("accepted", recipientStrings(to, cc, bcc));
        return ok(data);
    }

    private static Map<String, Object> imap(String op, Map<String, Object> cfg, Map<String, Object> in,
                                            String password) throws MessagingException {
        String host = DataSupport.cfgStr(cfg, "imap_host", null);
        if (host == null || host.isBlank()) {
            return DataSupport.invalidInput("imap_host is required for " + op);
        }
        String user = DataSupport.cfgStr(cfg, "user", null);
        if (user == null || user.isBlank()) {
            return DataSupport.invalidInput("user is required for " + op);
        }
        if (password == null || password.isBlank()) {
            return DataSupport.invalidInput("password is required for " + op);
        }
        if (IMAP_OPS.contains(op) && !"search".equals(op)) {
            String uid = DataSupport.inStr(in, "uid", null);
            if (uid == null) {
                return DataSupport.invalidInput("uid is required for " + op);
            }
        }
        if ("move".equals(op) && DataSupport.inStr(in, "target_folder", null) == null) {
            return DataSupport.invalidInput("target_folder is required for move");
        }
        if ("mark".equals(op)) {
            imapFlag(DataSupport.inStr(in, "flag", null));
        }
        int port = DataSupport.cfgInt(cfg, "imap_port", 993);
        int timeoutMs = timeoutSeconds(cfg) * 1000;
        boolean tls = tlsRequired(cfg);
        String folderName = firstNonBlank(DataSupport.inStr(in, "folder", null), "INBOX");
        Properties props = imapProperties(host.trim(), port, tls, timeoutMs);
        Session session = Session.getInstance(props);
        session.setDebug(false);
        try (Store store = session.getStore(tls ? "imaps" : "imap")) {
            store.connect(host.trim(), port, user, password);
            Folder folder = store.getFolder(folderName);
            if (folder == null || !folder.exists()) {
                return DataSupport.fail("IMAP folder not found");
            }
            folder.open(Folder.READ_WRITE);
            try {
                return switch (op) {
                    case "search" -> search(folder, in);
                    case "get" -> get(folder, in);
                    case "move" -> move(store, folder, in);
                    default -> mark(folder, in);
                };
            } finally {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            }
        }
    }

    private static Map<String, Object> search(Folder folder, Map<String, Object> in)
            throws MessagingException {
        String criteria = searchCriteria(DataSupport.inStr(in, "query", null));
        List<String> uids = uidSearch(folder, criteria);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uids", uids);
        data.put("count", uids.size());
        return ok(data);
    }

    private static Map<String, Object> get(Folder folder, Map<String, Object> in)
            throws MessagingException {
        Message msg = messageByUid(folder, DataSupport.inStr(in, "uid", null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", DataSupport.inStr(in, "uid", null));
        data.put("subject", cap(msg.getSubject()));
        data.put("from", addressList(msg.getFrom()));
        data.put("to", addressList(msg.getRecipients(Message.RecipientType.TO)));
        data.put("cc", addressList(msg.getRecipients(Message.RecipientType.CC)));
        data.put("date", msg.getSentDate() == null ? null : msg.getSentDate().toInstant().toString());
        data.put("flags", flagNames(msg.getFlags()));
        BodySink sink = new BodySink();
        extractContent(msg, sink, 0);
        if (sink.text != null) {
            data.put("text", sink.text);
        }
        if (sink.html != null) {
            data.put("html", sink.html);
        }
        if (!sink.files.isEmpty()) {
            data.put("attachments", sink.files);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("data", data);
        if (!sink.files.isEmpty()) {
            body.put("file", sink.files.get(0));
        }
        return DataSupport.ports(body);
    }

    private static Map<String, Object> move(Store store, Folder folder, Map<String, Object> in)
            throws MessagingException {
        Message msg = messageByUid(folder, DataSupport.inStr(in, "uid", null));
        String target = DataSupport.inStr(in, "target_folder", null);
        Folder dest = store.getFolder(target);
        if (!dest.exists() && !dest.create(Folder.HOLDS_MESSAGES)) {
            return DataSupport.fail("could not create target_folder");
        }
        if (folder instanceof IMAPFolder imap) {
            imap.moveMessages(new Message[]{msg}, dest);
        } else {
            folder.copyMessages(new Message[]{msg}, dest);
            msg.setFlag(Flags.Flag.DELETED, true);
            folder.expunge();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", DataSupport.inStr(in, "uid", null));
        data.put("folder", target);
        return ok(data);
    }

    private static Map<String, Object> mark(Folder folder, Map<String, Object> in)
            throws MessagingException {
        Message msg = messageByUid(folder, DataSupport.inStr(in, "uid", null));
        String flagName = DataSupport.inStr(in, "flag", null);
        Flags flags = new Flags(imapFlag(flagName));
        msg.setFlags(flags, true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", DataSupport.inStr(in, "uid", null));
        data.put("flag", flagName.trim().toLowerCase(Locale.ROOT));
        return ok(data);
    }

    static List<String> uidSearch(Folder folder, String criteria) throws MessagingException {
        if (folder instanceof IMAPFolder imap) {
            Object raw = imap.doCommand((IMAPProtocol p) -> {
                Response[] responses = p.command("UID SEARCH " + criteria, null);
                List<String> uids = new ArrayList<>();
                Response last = responses[responses.length - 1];
                if (last.isOK()) {
                    for (Response r : responses) {
                        if (!(r instanceof IMAPResponse ir) || !ir.keyEquals("SEARCH")) {
                            continue;
                        }
                        int n;
                        while ((n = ir.readNumber()) != -1 && uids.size() < MAX_SEARCH_RESULTS) {
                            uids.add(Integer.toString(n));
                        }
                    }
                }
                p.notifyResponseHandlers(responses);
                p.handleResult(last);
                return uids;
            });
            @SuppressWarnings("unchecked")
            List<String> uids = raw instanceof List<?> list ? (List<String>) list : List.of();
            return uids;
        }
        throw DataSupport.failError("IMAP UID SEARCH requires an IMAP folder");
    }

    private static Message messageByUid(Folder folder, String uidText) throws MessagingException {
        long uid;
        try {
            uid = Long.parseLong(uidText.trim());
        } catch (NumberFormatException e) {
            throw invalid("uid must be a positive integer");
        }
        if (uid <= 0) {
            throw invalid("uid must be a positive integer");
        }
        if (!(folder instanceof UIDFolder uidFolder)) {
            throw DataSupport.failError("IMAP UID FETCH requires a UID folder");
        }
        Message msg = uidFolder.getMessageByUID(uid);
        if (msg == null) {
            throw DataSupport.failError("IMAP message not found for uid");
        }
        return msg;
    }

    private static void applyBody(MimeMessage msg, String text, String html,
                                  List<FilesSupport.FileBytes> files) throws MessagingException {
        if (files.isEmpty()) {
            if (text != null && html != null) {
                msg.setContent(alternative(text, html));
            } else if (html != null) {
                msg.setContent(html, "text/html; charset=UTF-8");
            } else {
                msg.setText(text == null ? "" : text, "UTF-8");
            }
            return;
        }
        MimeBodyPart body = new MimeBodyPart();
        if (text != null && html != null) {
            body.setContent(alternative(text, html));
        } else if (html != null) {
            body.setContent(html, "text/html; charset=UTF-8");
        } else {
            body.setText(text == null ? "" : text, "UTF-8");
        }
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(body);
        for (FilesSupport.FileBytes file : files) {
            MimeBodyPart part = new MimeBodyPart();
            part.setFileName(file.name());
            part.setDisposition(Part.ATTACHMENT);
            jakarta.activation.DataHandler handler = new jakarta.activation.DataHandler(
                    new ByteDataSource(file.bytes(), file.mimeType(), file.name()));
            part.setDataHandler(handler);
            mixed.addBodyPart(part);
        }
        msg.setContent(mixed);
    }

    private static MimeMultipart alternative(String text, String html) throws MessagingException {
        MimeMultipart alt = new MimeMultipart("alternative");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText(text, "UTF-8");
        MimeBodyPart rich = new MimeBodyPart();
        rich.setContent(html, "text/html; charset=UTF-8");
        alt.addBodyPart(plain);
        alt.addBodyPart(rich);
        return alt;
    }

    static List<FilesSupport.FileBytes> readAttachments(Object raw) {
        List<FilesSupport.FileBytes> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        List<?> items = raw instanceof List<?> list ? list : List.of(raw);
        if (items.size() > MAX_ATTACHMENTS) {
            throw invalid("attachments exceed " + MAX_ATTACHMENTS);
        }
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            out.add(FilesSupport.readFileValue(item, "attachments"));
        }
        return out;
    }

    private static void extractContent(Part part, BodySink sink, int depth) throws MessagingException {
        if (part == null || depth > 8) {
            return;
        }
        try {
            String disp = part.getDisposition();
            boolean attachment = disp != null && Part.ATTACHMENT.equalsIgnoreCase(disp);
            if (part.isMimeType("text/plain") && !attachment && sink.text == null) {
                sink.text = cap(stringify(part.getContent()));
                return;
            }
            if (part.isMimeType("text/html") && !attachment && sink.html == null) {
                sink.html = cap(stringify(part.getContent()));
                return;
            }
            if (part.isMimeType("multipart/*")) {
                Object content = part.getContent();
                if (content instanceof Multipart mp) {
                    int n = Math.min(mp.getCount(), MAX_ATTACHMENTS + 4);
                    for (int i = 0; i < n; i++) {
                        extractContent(mp.getBodyPart(i), sink, depth + 1);
                    }
                }
                return;
            }
            if (attachment || part.getFileName() != null) {
                if (sink.files.size() >= MAX_ATTACHMENTS) {
                    return;
                }
                try (InputStream in = part.getInputStream()) {
                    byte[] bytes = in.readNBytes(FilesSupport.MAX_FILE_BYTES + 1);
                    if (bytes.length > FilesSupport.MAX_FILE_BYTES) {
                        return;
                    }
                    String name = part.getFileName() == null ? "attachment.bin" : part.getFileName();
                    String mime = part.getContentType();
                    if (mime != null && mime.contains(";")) {
                        mime = mime.substring(0, mime.indexOf(';')).trim();
                    }
                    sink.files.add(FilesSupport.fileBlock(name, mime, bytes));
                }
            }
        } catch (StudioQuackError e) {
            throw e;
        } catch (MessagingException e) {
            throw e;
        } catch (Exception ignored) {
            // skip a single unreadable part
        }
    }

    private static List<InternetAddress> addresses(Map<String, Object> in, String key, boolean required) {
        List<String> raw = DataSupport.inStringList(in, key);
        if (raw.isEmpty()) {
            if (required) {
                throw invalid(key + " is required for send");
            }
            return List.of();
        }
        List<InternetAddress> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            out.add(address(s));
        }
        return out;
    }

    private static InternetAddress address(String raw) {
        try {
            InternetAddress a = new InternetAddress(raw, false);
            a.validate();
            return a;
        } catch (AddressException e) {
            throw invalid("invalid email address");
        }
    }

    private static List<String> recipientStrings(List<InternetAddress> to, List<InternetAddress> cc,
                                                 List<InternetAddress> bcc) {
        List<String> out = new ArrayList<>();
        for (InternetAddress a : to) {
            out.add(a.getAddress());
        }
        for (InternetAddress a : cc) {
            out.add(a.getAddress());
        }
        for (InternetAddress a : bcc) {
            out.add(a.getAddress());
        }
        return out;
    }

    private static List<String> addressList(Address[] addrs) {
        List<String> out = new ArrayList<>();
        if (addrs == null) {
            return out;
        }
        for (Address a : addrs) {
            out.add(a.toString());
        }
        return out;
    }

    private static List<String> flagNames(Flags flags) {
        List<String> out = new ArrayList<>();
        if (flags == null) {
            return out;
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            out.add("seen");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            out.add("flagged");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            out.add("deleted");
        }
        if (flags.contains(Flags.Flag.ANSWERED)) {
            out.add("answered");
        }
        return out;
    }

    private static Authenticator authenticator(String user, String password) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        };
    }

    private static StudioQuackError invalid(String message) {
        return new StudioQuackError("INVALID_INPUT",
                message == null || message.isBlank() ? "invalid input" : message);
    }

    private static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("data", data);
        return DataSupport.ports(body);
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
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + m;
    }

    private static String cap(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_BODY_CHARS ? s : s.substring(0, MAX_BODY_CHARS);
    }

    private static String stringify(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static final class BodySink {
        String text;
        String html;
        final List<Map<String, Object>> files = new ArrayList<>();
    }

    private static final class ByteDataSource implements jakarta.activation.DataSource {
        private final byte[] bytes;
        private final String mime;
        private final String name;

        ByteDataSource(byte[] bytes, String mime, String name) {
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.mime = mime == null || mime.isBlank() ? "application/octet-stream" : mime;
            this.name = name == null ? "attachment" : name;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public String getContentType() {
            return mime;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
