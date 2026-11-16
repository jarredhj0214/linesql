package io.github.linesql.core.internal;

import io.github.linesql.core.spi.StatementSplitter;

import java.util.ArrayList;
import java.util.List;

public class SimpleStatementSplitter implements StatementSplitter {
    @Override
    public List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;

            if (lineComment) {
                current.append(c);
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (quote == 0 && c == '-' && next == '-') {
                current.append(c).append(next);
                i++;
                lineComment = true;
                continue;
            }
            if (quote == 0 && c == '/' && next == '*') {
                current.append(c).append(next);
                i++;
                blockComment = true;
                continue;
            }
            if (quote == 0 && c == '$') {
                int end = dollarQuotedEnd(script, i);
                if (end > i) {
                    current.append(script, i, end);
                    i = end - 1;
                    continue;
                }
            }
            if (quote == 0 && c == '\\' && (next == 'n' || next == 'r' || next == 't')) {
                current.append(next == 't' ? ' ' : '\n');
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                if (quote == 0) {
                    quote = c;
                } else if (quote == c && !isEscaped(script, i)) {
                    quote = 0;
                }
                current.append(c);
                continue;
            }
            if (quote == 0 && c == ';') {
                addIfNotBlank(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        addIfNotBlank(statements, current);
        return statements;
    }

    private static int dollarQuotedEnd(String text, int start) {
        if (start > 0) {
            char previous = text.charAt(start - 1);
            if (isTagPart(previous) || previous == '$') {
                return -1;
            }
        }
        int end = start + 1;
        if (end < text.length() && isTagStart(text.charAt(end))) {
            do {
                end++;
            } while (end < text.length() && isTagPart(text.charAt(end)));
        }
        if (end >= text.length() || text.charAt(end) != '$') {
            return -1;
        }
        String delimiter = text.substring(start, end + 1);
        int closing = text.indexOf(delimiter, end + 1);
        return closing < 0 ? text.length() : closing + delimiter.length();
    }

    private static boolean isTagStart(char c) {
        return c == '_' || Character.isLetter(c) || c >= 128;
    }

    private static boolean isTagPart(char c) {
        return isTagStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isEscaped(String text, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder sql) {
        String value = sql.toString().trim();
        if (!value.isEmpty() && !stripComments(value).trim().isEmpty()) {
            statements.add(value);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder stripped = new StringBuilder(sql.length());
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;

            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    stripped.append(c);
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (quote == 0 && c == '-' && next == '-') {
                i++;
                lineComment = true;
                continue;
            }
            if (quote == 0 && c == '/' && next == '*') {
                i++;
                blockComment = true;
                continue;
            }
            if (quote == 0 && c == '$') {
                int end = dollarQuotedEnd(sql, i);
                if (end > i) {
                    stripped.append(sql, i, end);
                    i = end - 1;
                    continue;
                }
            }
            if (c == '\'' || c == '"' || c == '`') {
                if (quote == 0) {
                    quote = c;
                } else if (quote == c && !isEscaped(sql, i)) {
                    quote = 0;
                }
            }
            stripped.append(c);
        }
        return stripped.toString();
    }
}
