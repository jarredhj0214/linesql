package io.github.linesql.core.internal;

import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.StatementSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SimpleStatementSplitter implements StatementSplitter {
    private static final Pattern GO_LINE = Pattern.compile(
            "[ \t]*GO(?:[ \t]+0*[1-9][0-9]*)?[ \t]*(?:--[^\r\n]*)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_SERVER_ROUTINE = Pattern.compile(
            "^\\s*(?:CREATE\\s+(?:OR\\s+ALTER\\s+)?(?:PROC(?:EDURE)?|FUNCTION|TRIGGER)"
                    + "|ALTER\\s+(?:PROC(?:EDURE)?|FUNCTION))\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public List<String> split(String script) {
        return split(script, SqlDialect.UNKNOWN);
    }

    List<String> split(String script, SqlDialect dialect) {
        boolean sqlServer = dialect == SqlDialect.SQLSERVER;
        boolean nestedComments = sqlServer || dialect == SqlDialect.POSTGRESQL;
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean routineBatch = false;
        int blockCommentDepth = 0;

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
            if (blockCommentDepth > 0) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockCommentDepth--;
                } else if (nestedComments && c == '/' && next == '*') {
                    current.append(next);
                    i++;
                    blockCommentDepth++;
                }
                continue;
            }
            if (sqlServer && quote == 0 && isLineStart(script, i)) {
                int lineEnd = i;
                while (lineEnd < script.length()
                        && script.charAt(lineEnd) != '\r' && script.charAt(lineEnd) != '\n') {
                    lineEnd++;
                }
                if (lineEnd > i && GO_LINE.matcher(script).region(i, lineEnd).matches()) {
                    addIfNotBlank(statements, current, nestedComments);
                    current.setLength(0);
                    routineBatch = false;
                    i = lineEnd - 1;
                    continue;
                }
            }
            if (sqlServer && quote == 0 && c == '[') {
                quote = ']';
                current.append(c);
                continue;
            }
            if (quote == ']') {
                current.append(c);
                if (c == ']' && next == ']') {
                    current.append(next);
                    i++;
                } else if (c == ']') {
                    quote = 0;
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
                blockCommentDepth = 1;
                continue;
            }
            if (!sqlServer && quote == 0 && c == '$') {
                int end = dollarQuotedEnd(script, i, true);
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
                } else if (sqlServer && quote == c && next == c) {
                    current.append(c).append(next);
                    i++;
                    continue;
                } else if (quote == c && (sqlServer || !isEscaped(script, i))) {
                    quote = 0;
                }
                current.append(c);
                continue;
            }
            if (quote == 0 && c == ';' && !routineBatch) {
                routineBatch = sqlServer && SQL_SERVER_ROUTINE.matcher(stripComments(current.toString(), true)).find();
                if (!routineBatch) {
                    addIfNotBlank(statements, current, nestedComments);
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }

        addIfNotBlank(statements, current, nestedComments);
        return statements;
    }

    private static boolean isLineStart(String text, int index) {
        return index == 0 || text.charAt(index - 1) == '\n' || text.charAt(index - 1) == '\r';
    }

    static int dollarQuotedEnd(String text, int start, boolean allowUnterminated) {
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
        return closing < 0 ? (allowUnterminated ? text.length() : -1) : closing + delimiter.length();
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

    private static void addIfNotBlank(List<String> statements, StringBuilder sql, boolean nestedComments) {
        String value = sql.toString().trim();
        if (!value.isEmpty() && !stripComments(value, nestedComments).trim().isEmpty()) {
            statements.add(value);
        }
    }

    private static String stripComments(String sql, boolean nestedComments) {
        StringBuilder stripped = new StringBuilder(sql.length());
        char quote = 0;
        boolean lineComment = false;
        int blockCommentDepth = 0;

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
            if (blockCommentDepth > 0) {
                if (c == '*' && next == '/') {
                    i++;
                    blockCommentDepth--;
                } else if (nestedComments && c == '/' && next == '*') {
                    i++;
                    blockCommentDepth++;
                }
                continue;
            }
            if (quote == 0 && c == '-' && next == '-') {
                i++;
                lineComment = true;
                continue;
            }
            if (quote == 0 && c == '/' && next == '*') {
                stripped.append(' ');
                i++;
                blockCommentDepth = 1;
                continue;
            }
            if (quote == 0 && c == '$') {
                int end = dollarQuotedEnd(sql, i, true);
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
