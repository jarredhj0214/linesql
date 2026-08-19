package io.github.linesql.dialect.spark;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SparkPartialLineageExtractor {
    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?is)\\b(?:insert\\s+(?:overwrite|into)(?:\\s+table)?|create\\s+(?:or\\s+replace\\s+)?(?:global\\s+)?(?:temporary\\s+)?(?:view|table)|alter\\s+table|drop\\s+table|truncate\\s+table)\\s+(`[^`]+`|[a-zA-Z_][\\w$]*(?:\\s*\\.\\s*(?:`[^`]+`|[a-zA-Z_][\\w$]*))*)");
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+(`[^`]+`|[a-zA-Z_][\\w$]*(?:\\s*\\.\\s*(?:`[^`]+`|[a-zA-Z_][\\w$]*))*)");
    private static final Set<String> NON_TABLE_RELATIONS = new LinkedHashSet<>(Arrays.asList(
            "select", "values", "table", "range", "explode", "posexplode", "unnest", "json_table"));

    private SparkPartialLineageExtractor() {
    }

    static void extract(String sql, LineageResult result) {
        String normalized = stripComments(sql);
        result.setStatementType(detectStatementType(normalized));
        result.setInputTables(new ArrayList<>(extractTables(normalized, SOURCE_PATTERN)));
        result.setOutputTables(new ArrayList<>(extractTables(normalized, TARGET_PATTERN)));
    }

    private static StatementType detectStatementType(String sql) {
        String lower = sql.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("insert")) {
            return StatementType.INSERT;
        }
        if (lower.startsWith("create")) {
            if (lower.matches("(?s)^create\\s+.*\\bview\\b.*")) {
                return StatementType.CREATE_VIEW;
            }
            return StatementType.CREATE_TABLE_AS_SELECT;
        }
        if (lower.startsWith("alter table")) {
            return StatementType.ALTER_TABLE;
        }
        if (lower.startsWith("drop table")) {
            return StatementType.DROP_TABLE;
        }
        if (lower.startsWith("truncate table")) {
            return StatementType.TRUNCATE_TABLE;
        }
        if (lower.startsWith("merge")) {
            return StatementType.MERGE;
        }
        if (lower.startsWith("update")) {
            return StatementType.UPDATE;
        }
        if (lower.startsWith("delete")) {
            return StatementType.DELETE;
        }
        if (lower.startsWith("select") || lower.startsWith("with")) {
            return StatementType.SELECT;
        }
        return StatementType.UNKNOWN;
    }

    private static Set<TableRef> extractTables(String sql, Pattern pattern) {
        Set<TableRef> tables = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            TableRef table = tableRef(raw);
            if (table != null && !NON_TABLE_RELATIONS.contains(table.getName().toLowerCase(Locale.ROOT))) {
                tables.add(table);
            }
        }
        return tables;
    }

    private static TableRef tableRef(String raw) {
        List<String> parts = splitIdentifier(raw);
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() >= 3) {
            return new TableRef(parts.get(parts.size() - 3), parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (parts.size() == 2) {
            return new TableRef(null, parts.get(0), parts.get(1));
        }
        return new TableRef(null, null, parts.get(0));
    }

    private static List<String> splitIdentifier(String raw) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '`') {
                quoted = !quoted;
            } else if (c == '.' && !quoted) {
                addPart(parts, current);
            } else if (!Character.isWhitespace(c)) {
                current.append(c);
            }
        }
        addPart(parts, current);
        return parts;
    }

    private static void addPart(List<String> parts, StringBuilder current) {
        String value = current.toString().trim();
        current.setLength(0);
        if (!value.isEmpty()) {
            parts.add(value);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder stripped = new StringBuilder(sql.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean backQuoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (!doubleQuoted && !backQuoted && current == '\'' && !isEscaped(sql, i)) {
                singleQuoted = !singleQuoted;
                stripped.append(current);
                continue;
            }
            if (!singleQuoted && !backQuoted && current == '"' && !isEscaped(sql, i)) {
                doubleQuoted = !doubleQuoted;
                stripped.append(current);
                continue;
            }
            if (!singleQuoted && !doubleQuoted && current == '`') {
                backQuoted = !backQuoted;
                stripped.append(current);
                continue;
            }
            if (!singleQuoted && !doubleQuoted && !backQuoted && current == '-' && next == '-') {
                stripped.append(' ');
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                if (i < sql.length()) {
                    stripped.append(sql.charAt(i));
                }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && !backQuoted && current == '/' && next == '*') {
                stripped.append(' ');
                i += 2;
                while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < sql.length()) {
                    i++;
                }
                stripped.append(' ');
                continue;
            }
            stripped.append(current);
        }
        return stripped.toString();
    }

    private static boolean isEscaped(String sql, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && sql.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }
}
