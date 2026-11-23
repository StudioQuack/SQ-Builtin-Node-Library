package com.studioquack.nodelibrary.nodes.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/**
 * Best-effort, dependency-free PDF text extraction (the definition scopes PDF
 * to "text-only"). Handles uncompressed and FlateDecode content streams and
 * the common text-showing operators (Tj, TJ, ', "), with literal-string and
 * hex-string decoding.
 *
 * <p>Not a full PDF interpreter: no font CMap mapping, no object streams, no
 * xref walking. Encoded fonts that do not map 1:1 to Latin-1 yield partial
 * text — acceptable for a text-only contract, and never an exception.
 */
final class PdfText {

    /** Cap extracted text so a hostile PDF cannot bloat the run payload. */
    private static final int MAX_TEXT_CHARS = 1_000_000;
    private static final int MAX_STREAM_BYTES = 32 * 1024 * 1024;

    private PdfText() {
        // utility — never instantiated
    }

    static String extract(byte[] pdf) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < pdf.length) {
            int streamAt = indexOf(pdf, "stream".getBytes(StandardCharsets.ISO_8859_1), pos);
            if (streamAt < 0) {
                break;
            }
            int dataStart = streamAt + 6;
            if (dataStart < pdf.length && pdf[dataStart] == '\r') {
                dataStart++;
            }
            if (dataStart < pdf.length && pdf[dataStart] == '\n') {
                dataStart++;
            }
            int endAt = indexOf(pdf, "endstream".getBytes(StandardCharsets.ISO_8859_1), dataStart);
            if (endAt < 0) {
                break;
            }
            // The dictionary precedes the stream keyword; scan back for FlateDecode.
            int dictFrom = Math.max(0, streamAt - 4096);
            String dictRegion = new String(pdf, dictFrom, streamAt - dictFrom, StandardCharsets.ISO_8859_1);
            boolean flate = dictRegion.contains("FlateDecode");
            byte[] raw = new byte[endAt - dataStart];
            System.arraycopy(pdf, dataStart, raw, 0, raw.length);
            byte[] content = flate ? inflate(raw) : raw;
            if (content != null) {
                parseContent(new String(content, StandardCharsets.ISO_8859_1), out);
            }
            if (out.length() >= MAX_TEXT_CHARS) {
                break;
            }
            pos = endAt + 9;
        }
        String text = out.toString();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private static byte[] inflate(byte[] raw) {
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(raw))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buf)) > 0) {
                total += read;
                if (total > MAX_STREAM_BYTES) {
                    return null;
                }
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null; // not flate after all, or corrupt — skip the stream
        }
    }

    /** Walk one content stream: BT/ET text objects, Tj/TJ/'/" show operators. */
    private static void parseContent(String s, StringBuilder out) {
        int i = 0;
        int n = s.length();
        boolean inText = false;
        while (i < n && out.length() < MAX_TEXT_CHARS) {
            char c = s.charAt(i);
            if (c == '(') {
                int[] end = literalEnd(s, i);
                if (end == null) {
                    break;
                }
                if (inText) {
                    out.append(decodeLiteral(s.substring(i + 1, end[0])));
                }
                i = end[0] + 1;
                continue;
            }
            if (c == '<' && i + 1 < n && s.charAt(i + 1) != '<') {
                int gt = s.indexOf('>', i);
                if (gt < 0) {
                    break;
                }
                if (inText) {
                    out.append(decodeHex(s.substring(i + 1, gt)));
                }
                i = gt + 1;
                continue;
            }
            if (c == 'B' && match(s, i, "BT")) {
                inText = true;
                i += 2;
                continue;
            }
            if (c == 'E' && match(s, i, "ET")) {
                inText = false;
                out.append('\n');
                i += 2;
                continue;
            }
            if (inText && (c == 'T')) {
                if (match(s, i, "Td") || match(s, i, "TD")) {
                    out.append('\n');
                    i += 2;
                    continue;
                }
                if (c == 'T' && i + 1 < n && s.charAt(i + 1) == '*') {
                    out.append('\n');
                    i += 2;
                    continue;
                }
            }
            if (inText && c == '\'') {
                out.append('\n');
                i++;
                continue;
            }
            i++;
        }
    }

    /** End index of a literal string starting at {@code (}; null when unbalanced. */
    private static int[] literalEnd(String s, int from) {
        int depth = 1;
        int i = from + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return new int[]{i};
                }
            }
            i++;
        }
        return null;
    }

    private static String decodeLiteral(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            char e = raw.charAt(++i);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case '(', ')', '\\' -> sb.append(e);
                default -> {
                    if (e >= '0' && e <= '7') {
                        int oct = e - '0';
                        int used = 1;
                        while (used < 3 && i + 1 < raw.length()
                                && raw.charAt(i + 1) >= '0' && raw.charAt(i + 1) <= '7') {
                            oct = oct * 8 + (raw.charAt(++i) - '0');
                            used++;
                        }
                        sb.append((char) oct);
                    } else {
                        sb.append(e);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String decodeHex(String hex) {
        String h = hex.replaceAll("\\s+", "");
        StringBuilder sb = new StringBuilder(h.length() / 2);
        for (int i = 0; i + 1 < h.length(); i += 2) {
            try {
                sb.append((char) Integer.parseInt(h.substring(i, i + 2), 16));
            } catch (NumberFormatException e) {
                return sb.toString();
            }
        }
        return sb.toString();
    }

    private static boolean match(String s, int at, String token) {
        return s.regionMatches(at, token, 0, token.length());
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
