package com.studioquack.nodelibrary.core;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * RFC 5322 message for an operation's {@code mime} block, built from the input fields
 * {@code from, to, cc, bcc, reply_to, subject, text, html, in_reply_to, references, attachments}.
 * {@code to}/{@code cc}/{@code bcc} take a comma-separated string or a list; {@code attachments}
 * takes platform file blocks. Bcc stays in the headers because the sending API (Gmail) delivers
 * from them.
 */
final class OfficialApiMime {

    /** Encoded message, or {@code error} when the fields cannot form a message. */
    record Built(String encoded, String error) {
    }

    private static final int MAX_ATTACHMENTS = 25;

    private OfficialApiMime() {
        // Utility — never instantiated.
    }

    static Built build(Map<String, Object> in, String encoding, int timeoutSeconds) {
        Map<String, Object> f = in == null ? Map.of() : in;
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
            String from = str(f.get("from"));
            if (!from.isEmpty()) {
                msg.setFrom(new InternetAddress(from, true));
            }
            InternetAddress[] to = addresses(f.get("to"));
            if (to.length == 0) {
                return new Built(null, "to is required to build the message");
            }
            msg.setRecipients(Message.RecipientType.TO, to);
            setIfAny(msg, Message.RecipientType.CC, addresses(f.get("cc")));
            setIfAny(msg, Message.RecipientType.BCC, addresses(f.get("bcc")));
            InternetAddress[] replyTo = addresses(f.get("reply_to"));
            if (replyTo.length > 0) {
                msg.setReplyTo(replyTo);
            }
            msg.setSubject(str(f.get("subject")), StandardCharsets.UTF_8.name());
            if (!str(f.get("in_reply_to")).isEmpty()) {
                msg.setHeader("In-Reply-To", str(f.get("in_reply_to")));
            }
            String references = str(f.get("references")).isEmpty() ? str(f.get("in_reply_to")) : str(f.get("references"));
            if (!references.isEmpty()) {
                msg.setHeader("References", references);
            }
            String text = str(f.get("text"));
            String html = str(f.get("html"));
            List<OfficialApiBody.FilePart> files = attachments(f.get("attachments"));
            if (files.size() > MAX_ATTACHMENTS) {
                return new Built(null, "attachments exceeds " + MAX_ATTACHMENTS + " files");
            }
            if (files.isEmpty()) {
                if (!html.isEmpty() && !text.isEmpty()) {
                    msg.setContent(alternative(text, html));
                } else if (!html.isEmpty()) {
                    msg.setText(html, StandardCharsets.UTF_8.name(), "html");
                } else {
                    msg.setText(text, StandardCharsets.UTF_8.name());
                }
            } else {
                MimeMultipart mixed = new MimeMultipart("mixed");
                mixed.addBodyPart(contentPart(text, html));
                for (OfficialApiBody.FilePart file : files) {
                    OfficialApiBody.Prepared data = OfficialApiBody.resolveFileBytes(file, timeoutSeconds, "attachment");
                    if (data.failed()) {
                        return new Built(null, data.error());
                    }
                    MimeBodyPart part = new MimeBodyPart();
                    String type = file.contentType() == null || file.contentType().isBlank()
                            ? "application/octet-stream" : file.contentType();
                    part.setDataHandler(new DataHandler(new ByteArrayDataSource(data.bytes(), type)));
                    part.setFileName(file.fileName() == null || file.fileName().isBlank()
                            ? "attachment" : file.fileName());
                    part.setDisposition(MimeBodyPart.ATTACHMENT);
                    mixed.addBodyPart(part);
                }
                msg.setContent(mixed);
            }
            msg.saveChanges();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            msg.writeTo(out);
            byte[] bytes = out.toByteArray();
            return new Built(switch (encoding) {
                case "none" -> new String(bytes, StandardCharsets.UTF_8);
                case "base64" -> Base64.getEncoder().encodeToString(bytes);
                default -> Base64.getUrlEncoder().encodeToString(bytes);
            }, null);
        } catch (AddressException e) {
            return new Built(null, "invalid email address: " + e.getRef());
        } catch (MessagingException | IOException e) {
            return new Built(null, "message could not be built: " + e.getClass().getSimpleName());
        }
    }

    private static MimeMultipart alternative(String text, String html) throws MessagingException {
        MimeMultipart alt = new MimeMultipart("alternative");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText(text, StandardCharsets.UTF_8.name());
        MimeBodyPart rich = new MimeBodyPart();
        rich.setText(html, StandardCharsets.UTF_8.name(), "html");
        alt.addBodyPart(plain);
        alt.addBodyPart(rich);
        return alt;
    }

    private static MimeBodyPart contentPart(String text, String html) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        if (!html.isEmpty() && !text.isEmpty()) {
            part.setContent(alternative(text, html));
        } else if (!html.isEmpty()) {
            part.setText(html, StandardCharsets.UTF_8.name(), "html");
        } else {
            part.setText(text, StandardCharsets.UTF_8.name());
        }
        return part;
    }

    private static void setIfAny(MimeMessage msg, Message.RecipientType type, InternetAddress[] list)
            throws MessagingException {
        if (list.length > 0) {
            msg.setRecipients(type, list);
        }
    }

    private static InternetAddress[] addresses(Object v) throws AddressException {
        List<InternetAddress> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (!str(item).isEmpty()) {
                    out.addAll(List.of(InternetAddress.parse(str(item), true)));
                }
            }
        } else if (!str(v).isEmpty()) {
            out.addAll(List.of(InternetAddress.parse(str(v), true)));
        }
        return out.toArray(InternetAddress[]::new);
    }

    private static List<OfficialApiBody.FilePart> attachments(Object v) {
        List<OfficialApiBody.FilePart> out = new ArrayList<>();
        List<?> items = v instanceof List<?> list ? list : v == null ? List.of() : List.of(v);
        for (Object item : items) {
            OfficialApiBody.FilePart part = OfficialApiBody.filePart(item);
            if (part != null) {
                out.add(part);
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
