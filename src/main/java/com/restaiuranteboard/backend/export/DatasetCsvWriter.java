package com.restaiuranteboard.backend.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class DatasetCsvWriter {

    private DatasetCsvWriter() {
    }

    public static byte[] write(String[] headers, List<Map<String, Object>> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            w.write(String.join(",", headers));
            w.write('\n');
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < headers.length; i++) {
                    if (i > 0) {
                        w.write(',');
                    }
                    w.write(escape(cell(row, headers[i])));
                }
                w.write('\n');
            }
            w.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar CSV.", e);
        }
    }

    private static Object cell(Map<String, Object> row, String column) {
        if (row == null) {
            return "";
        }
        if (row.containsKey(column)) {
            return row.get(column);
        }
        String lower = column.toLowerCase();
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        return "";
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains("\"") || s.contains(",") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
