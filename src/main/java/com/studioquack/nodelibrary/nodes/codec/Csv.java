package com.studioquack.nodelibrary.nodes.codec;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV reader: quoted fields, {@code ""} escapes, CRLF/LF, and
 * embedded newlines inside quotes. No writer — the library never emits CSV.
 */
final class Csv {

    private Csv() {
        // utility — never instantiated
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean haveData = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"' && field.length() == 0) {
                inQuotes = true;
                haveData = true;
                i++;
                continue;
            }
            if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                haveData = true;
                i++;
                continue;
            }
            if (c == '\r') {
                i++;
                continue;
            }
            if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                haveData = false;
                i++;
                continue;
            }
            field.append(c);
            haveData = true;
            i++;
        }
        if (haveData || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
