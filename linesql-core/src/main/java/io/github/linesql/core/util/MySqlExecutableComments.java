package io.github.linesql.core.util;

public final class MySqlExecutableComments {
    private MySqlExecutableComments() {
    }

    public static String unwrap(String sql) {
        if (!sql.contains("/*!")) {
            return sql;
        }
        StringBuilder normalized = new StringBuilder(sql.length());
        int copyFrom = 0;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                i = quotedEnd(sql, i) - 1;
            } else if (current == '#' || isDashComment(sql, i)) {
                while (i + 1 < sql.length() && sql.charAt(i + 1) != '\n' && sql.charAt(i + 1) != '\r') {
                    i++;
                }
            } else if (sql.startsWith("/*", i)) {
                int closing = sql.indexOf("*/", i + 2);
                if (closing < 0) {
                    break;
                }
                if (sql.startsWith("/*!", i)) {
                    int body = i + 3;
                    while (body < closing && sql.charAt(body) >= '0' && sql.charAt(body) <= '9') {
                        body++;
                    }
                    normalized.append(sql, copyFrom, i).append(' ').append(sql, body, closing).append(' ');
                    copyFrom = closing + 2;
                }
                i = closing + 1;
            }
        }
        return copyFrom == 0 ? sql : normalized.append(sql, copyFrom, sql.length()).toString();
    }

    private static boolean isDashComment(String sql, int start) {
        if (!sql.startsWith("--", start)) {
            return false;
        }
        int after = start + 2;
        return after == sql.length() || sql.charAt(after) <= ' ' || sql.charAt(after) == 127;
    }

    private static int quotedEnd(String sql, int start) {
        char quote = sql.charAt(start);
        for (int i = start + 1; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (quote != '`' && current == '\\') {
                i++;
            } else if (current == quote) {
                if (i + 1 >= sql.length() || sql.charAt(i + 1) != quote) {
                    return i + 1;
                }
                i++;
            }
        }
        return sql.length();
    }
}
