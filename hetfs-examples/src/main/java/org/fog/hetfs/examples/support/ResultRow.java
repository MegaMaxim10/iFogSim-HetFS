package org.fog.hetfs.examples.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One row of results, with named columns kept in insertion order, written as CSV.
 *
 * <p>Floating-point values are written with six decimals in the root locale; missing
 * values ({@code null}) are written as empty fields. Values must not contain commas.
 */
public final class ResultRow {

    private final Map<String, Object> columns = new LinkedHashMap<>();

    /** Adds a column; adding a column twice replaces its value but keeps its position. */
    public ResultRow put(String column, Object value) {
        columns.put(column, value);
        return this;
    }

    /** Value of a column, or {@code null} if the row has no such column. */
    public Object get(String column) {
        return columns.get(column);
    }

    /** Column names, comma-separated. */
    public String header() {
        return String.join(",", columns.keySet());
    }

    /** Formatted values, comma-separated, in column order. */
    public String values() {
        StringJoiner row = new StringJoiner(",");
        for (Object value : columns.values()) {
            row.add(format(value));
        }
        return row.toString();
    }

    /**
     * Appends the row to {@code file}, writing the header first if the file is new or
     * empty.
     *
     * @throws IllegalStateException if the file already has a different header
     */
    public void appendTo(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        if (!Files.exists(file) || Files.size(file) == 0) {
            Files.write(file, (header() + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (!lines.get(0).equals(header())) {
                throw new IllegalStateException("CSV header mismatch in " + file + ":\n  " + lines.get(0)
                        + "\n  " + header());
            }
        }
        Files.write(file, (values() + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    /** Appends the row to {@code file} if it is not {@code null}, prints it otherwise. */
    public void output(String file) throws IOException {
        if (file == null) {
            System.out.println(header());
            System.out.println(values());
        } else {
            appendTo(Path.of(file));
        }
    }

    /**
     * Formats a value: {@code null} as an empty field, floating-point numbers with six
     * decimals in the root locale, anything else with {@code toString()}.
     *
     * @throws IllegalArgumentException if the text contains a comma or a line break
     */
    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%.6f", ((Number) value).doubleValue());
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\n")) {
            throw new IllegalArgumentException("CSV value contains a separator: " + text);
        }
        return text;
    }
}
