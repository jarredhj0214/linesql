package io.github.linesql.core.internal;

import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.StatementSplitter;
import io.github.linesql.core.util.MySqlExecutableComments;
import io.github.linesql.core.util.OracleQuotedLiterals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleStatementSplitter implements StatementSplitter {
    private static final Pattern GO_LINE = Pattern.compile(
            "[ \t]*GO(?:[ \t]+0*[1-9][0-9]*)?[ \t]*(?:--[^\r\n]*)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern GO_SCRIPT_LINE = Pattern.compile(
            "^" + GO_LINE.pattern() + "$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern MYSQL_DELIMITER_LINE = Pattern.compile(
            "[ \\t]*(DELIMITER|\\\\d)[ \\t]+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MYSQL_DELIMITER_SCRIPT_LINE = Pattern.compile(
            "^" + MYSQL_DELIMITER_LINE.pattern() + "$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final String MYSQL_ROUTINE_NAME = "(?:`(?:``|[^`])*`|[\\p{L}\\p{N}_$]+)";
    private static final Pattern MYSQL_PROCEDURE_PREFIX = Pattern.compile(
            "^\\s*CREATE\\s+(?:DEFINER\\s*=\\s*\\S+\\s+)?PROCEDURE\\s+"
                    + "(?:IF\\s+NOT\\s+EXISTS\\s+)?" + MYSQL_ROUTINE_NAME
                    + "(?:\\s*\\.\\s*" + MYSQL_ROUTINE_NAME + ")?\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MYSQL_PROCEDURE_CHARACTERISTICS = Pattern.compile(
            "(?:\\s+(?:COMMENT\\s+(?:'(?:''|\\\\.|[^'\\\\])*'|\"(?:\"\"|\\\\.|[^\"\\\\])*\")"
                    + "|LANGUAGE\\s+SQL|(?:NOT\\s+)?DETERMINISTIC|(?:CONTAINS|NO)\\s+SQL"
                    + "|(?:READS|MODIFIES)\\s+SQL\\s+DATA|SQL\\s+SECURITY\\s+(?:DEFINER|INVOKER)))*\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MYSQL_BEGIN = Pattern.compile("\\bBEGIN\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_SERVER_ROUTINE = Pattern.compile(
            "^\\s*(?:CREATE\\s+(?:OR\\s+ALTER\\s+)?(?:PROC(?:EDURE)?|FUNCTION|TRIGGER)"
                    + "|ALTER\\s+(?:PROC(?:EDURE)?|FUNCTION))\\b", Pattern.CASE_INSENSITIVE);

    private static final String SQL_TRIVIA = "(?:\\s++|--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/)";
    private static final Pattern POSTGRES_ROUTINE = Pattern.compile(
            "^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:FUNCTION|PROCEDURE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSTGRES_ATOMIC = Pattern.compile(
            "\\bBEGIN" + SQL_TRIVIA + "++ATOMIC\\b(?!" + SQL_TRIVIA + "++WITH" + SQL_TRIVIA + "*+\\()",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORACLE_BLOCK = Pattern.compile(
            "^\\s*(?:BEGIN|DECLARE|CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PROCEDURE|FUNCTION|PACKAGE|TRIGGER))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORACLE_BLOCK_END = Pattern.compile(
            "\\bEND(?:\\s+[\\p{L}_][\\p{L}\\p{N}_$#]*)?\\s*;?\\s*(?:/\\s*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSACTION_BEGIN = Pattern.compile(
            "^\\s*BEGIN\\s*(?:;|(?:WORK|(?:DISTRIBUTED\\s+)?TRAN(?:SACTION)?|ISOLATION\\s+LEVEL"
                    + "|READ\\s+(?:ONLY|WRITE)|(?:NOT\\s+)?DEFERRABLE)\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORACLE_SLASH = Pattern.compile("[ \\t]*/[ \\t]*");

    private static final Pattern FLINK_STATEMENT_SET = Pattern.compile(
            "(?:^|;)\\s*(?:(?:EXPLAIN(?:\\s+(?:PLAN\\s+FOR|\\w+(?:\\s*,\\s*\\w+)*))?"
                    + "|COMPILE\\s+PLAN(?:\\s+IF\\s+NOT\\s+EXISTS)?"
                    + "(?:\\s+(?:'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"))?\\s+FOR)\\s+)?"
                    + "(?:(?:EXECUTE\\s+)?STATEMENT\\s+SET\\s+BEGIN|BEGIN\\s+STATEMENT\\s+SET)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FLINK_STATEMENT_SET_CONTINUATION = Pattern.compile(
            "(?:\\s++|--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/)*+(?:INSERT|WITH|END)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public List<String> split(String script) {
        return split(script, SqlDialect.UNKNOWN);
    }

    List<String> split(String script, SqlDialect dialect) {
        return splitWithDelimiters(script, dialect).statements;
    }

    List<String> splitDetectedSqlServerBatches(String script) {
        if (containsSqlServerGo(script)) {
            ScriptStatements batches = splitWithDelimiters(script, SqlDialect.SQLSERVER);
            if (batches.sqlServerGo) {
                return batches.statements;
            }
        }
        return split(script);
    }

    private static ScriptStatements splitWithDelimiters(String script, SqlDialect dialect) {
        boolean sqlServer = dialect == SqlDialect.SQLSERVER;
        boolean oracle = dialect == SqlDialect.ORACLE;
        boolean oracleQuotedLiterals = oracle || dialect == SqlDialect.UNKNOWN || dialect == SqlDialect.OCEANBASE;
        boolean flinkStatementSets = dialect == SqlDialect.FLINK || dialect == SqlDialect.UNKNOWN;
        boolean postgresAtomic = dialect == SqlDialect.POSTGRESQL || dialect == SqlDialect.UNKNOWN;
        boolean nestedComments = sqlServer || dialect == SqlDialect.POSTGRESQL;
        boolean mysql = dialect == SqlDialect.MYSQL;
        boolean checkMySqlDelimiters = (mysql || dialect == SqlDialect.UNKNOWN || dialect == SqlDialect.OCEANBASE)
                && MYSQL_DELIMITER_SCRIPT_LINE.matcher(script).find();
        Matcher mysqlCommand = MYSQL_DELIMITER_LINE.matcher(script);
        String mysqlDelimiter = ";";
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean routineBatch = false;
        boolean sawGo = false;
        boolean sawMySqlDelimiter = false;
        int statementSetPartStart = -1;
        int compoundPartStart = -1;
        int mysqlCompoundDepth = 0;
        Matcher mysqlOpening = MYSQL_BEGIN.matcher(script).useTransparentBounds(true);
        Matcher atomicOpening = POSTGRES_ATOMIC.matcher(script).useTransparentBounds(true);
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
            if ((sqlServer || oracle || checkMySqlDelimiters) && quote == 0 && isLineStart(script, i)) {
                int lineEnd = i;
                while (lineEnd < script.length()
                        && script.charAt(lineEnd) != '\r' && script.charAt(lineEnd) != '\n') {
                    lineEnd++;
                }
                if (checkMySqlDelimiters && mysqlCommand.region(i, lineEnd).matches()
                        && (mysql || "DELIMITER".equalsIgnoreCase(mysqlCommand.group(1)))
                        && !hasSqlContent(current.toString(), SqlDialect.MYSQL, false)) {
                    String newDelimiter = readMySqlDelimiter(mysqlCommand.group(2).trim());
                    if (newDelimiter != null) {
                        mysql = true;
                        dialect = SqlDialect.MYSQL;
                        mysqlDelimiter = newDelimiter;
                        sawMySqlDelimiter = true;
                        current.setLength(0);
                        i = lineEnd - 1;
                        continue;
                    }
                }
                boolean delimiter = sqlServer && GO_LINE.matcher(script).region(i, lineEnd).matches();
                if (oracle && ORACLE_SLASH.matcher(script).region(i, lineEnd).matches()) {
                    String preceding = stripComments(current.toString(), false);
                    delimiter = routineBatch || preceding.trim().isEmpty() || ORACLE_BLOCK.matcher(preceding).find();
                }
                if (delimiter) {
                    sawGo |= sqlServer;
                    addIfNotBlank(statements, current, dialect, nestedComments);
                    current.setLength(0);
                    routineBatch = false;
                    i = lineEnd - 1;
                    continue;
                }
            }
            if (mysql && quote == 0 && !";".equals(mysqlDelimiter) && script.startsWith(mysqlDelimiter, i)) {
                addIfNotBlank(statements, current, dialect, nestedComments);
                current.setLength(0);
                i += mysqlDelimiter.length() - 1;
                continue;
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
            if (oracleQuotedLiterals && !mysql && quote == 0 && (c == 'q' || c == 'Q' || c == 'n' || c == 'N')) {
                int end = oracle ? OracleQuotedLiterals.endOfLiteral(script, i)
                        : OracleQuotedLiterals.endOfClosedLiteral(script, i);
                if (end > i) {
                    current.append(script, i, end);
                    i = end - 1;
                    continue;
                }
            }
            if (quote == 0 && startsLineComment(script, i, mysql)) {
                current.append(c);
                if (c == '-') {
                    current.append(next);
                    i++;
                }
                lineComment = true;
                continue;
            }
            if (quote == 0 && c == '/' && next == '*') {
                current.append(c).append(next);
                i++;
                blockCommentDepth = 1;
                continue;
            }
            if (!sqlServer && !mysql && quote == 0 && c == '$') {
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
            if (quote == 0 && (c == 'b' || c == 'B')) {
                if (postgresAtomic && !mysql && compoundPartStart < 0
                        && atomicOpening.region(i, script.length()).lookingAt()
                        && POSTGRES_ROUTINE.matcher(stripComments(current.toString(), nestedComments)).find()) {
                    compoundPartStart = current.length() + atomicOpening.end() - i;
                } else if (mysql && ";".equals(mysqlDelimiter) && mysqlOpening.region(i, script.length()).lookingAt()) {
                    if (compoundPartStart < 0 && isCompleteMySqlProcedureHeader(current.toString())) {
                        compoundPartStart = current.length() + mysqlOpening.end() - i;
                        mysqlCompoundDepth = 1;
                    } else if (mysqlCompoundDepth > 0 && compoundPartStart <= current.length()
                            && stripComments(current.substring(compoundPartStart), false, true).trim().isEmpty()) {
                        mysqlCompoundDepth++;
                    }
                }
            }
            if (quote == 0 && c == ';' && !routineBatch && ";".equals(mysqlDelimiter)) {
                if (compoundPartStart >= 0) {
                    String part = stripComments(current.substring(compoundPartStart), nestedComments, mysql).trim();
                    if (!part.equalsIgnoreCase("END") || (mysqlCompoundDepth > 0 && --mysqlCompoundDepth > 0)) {
                        current.append(c);
                        compoundPartStart = current.length();
                        continue;
                    }
                    compoundPartStart = -1;
                }
                if (flinkStatementSets && !mysql && FLINK_STATEMENT_SET_CONTINUATION.matcher(script)
                        .region(i + 1, script.length()).lookingAt()) {
                    String part = stripComments(current.substring(Math.max(0, statementSetPartStart)), false);
                    boolean inStatementSet = statementSetPartStart >= 0
                            ? !part.trim().equalsIgnoreCase("END")
                            : FLINK_STATEMENT_SET.matcher(part).lookingAt();
                    if (inStatementSet) {
                        current.append(c);
                        statementSetPartStart = current.length();
                        continue;
                    }
                }
                statementSetPartStart = -1;
                if (sqlServer || oracle) {
                    Pattern routine = sqlServer ? SQL_SERVER_ROUTINE : ORACLE_BLOCK;
                    routineBatch = routine.matcher(stripComments(current.toString(), nestedComments)).find();
                }
                if (!routineBatch) {
                    addIfNotBlank(statements, current, dialect, nestedComments);
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }

        addIfNotBlank(statements, current, dialect, nestedComments);
        return new ScriptStatements(statements, sawGo, sawMySqlDelimiter);
    }

    static boolean containsMySqlDelimiterDirective(String sql) {
        return MYSQL_DELIMITER_SCRIPT_LINE.matcher(sql).find()
                && splitWithDelimiters(sql, SqlDialect.UNKNOWN).mySqlDelimiterDirective;
    }

    private static boolean isCompleteMySqlProcedureHeader(String sql) {
        String header = stripComments(sql, false, true);
        Matcher prefix = MYSQL_PROCEDURE_PREFIX.matcher(header);
        if (!prefix.find()) {
            return false;
        }
        int depth = 1;
        char quote = 0;
        for (int i = prefix.end(); i < header.length(); i++) {
            char current = header.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                if (quote == 0) {
                    quote = current;
                } else if (current == quote && !isEscaped(header, i)) {
                    quote = 0;
                }
            } else if (quote == 0 && current == '(') {
                depth++;
            } else if (quote == 0 && current == ')' && --depth == 0) {
                return MYSQL_PROCEDURE_CHARACTERISTICS.matcher(header).region(i + 1, header.length()).matches();
            }
        }
        return false;
    }

    private static String readMySqlDelimiter(String argument) {
        if (argument.isEmpty()) {
            return null;
        }
        char first = argument.charAt(0);
        boolean quoted = first == '\'' || first == '"' || first == '`';
        StringBuilder delimiter = new StringBuilder();
        for (int i = quoted ? 1 : 0; i < argument.length(); i++) {
            char current = argument.charAt(i);
            if (quoted ? current == first : Character.isWhitespace(current)) {
                return delimiter.length() == 0 ? null : delimiter.toString();
            }
            if (current == '\\') {
                if (i + 1 >= argument.length() || argument.charAt(i + 1) == '\\') {
                    return null;
                }
                current = argument.charAt(++i);
            }
            delimiter.append(current);
        }
        return quoted || delimiter.length() == 0 ? null : delimiter.toString();
    }

    private static final class ScriptStatements {
        private final List<String> statements;
        private final boolean sqlServerGo;
        private final boolean mySqlDelimiterDirective;

        private ScriptStatements(List<String> statements, boolean sqlServerGo, boolean mySqlDelimiterDirective) {
            this.statements = statements;
            this.sqlServerGo = sqlServerGo;
            this.mySqlDelimiterDirective = mySqlDelimiterDirective;
        }
    }

    static boolean containsSqlServerGo(String sql) {
        return GO_SCRIPT_LINE.matcher(sql).find();
    }

    static boolean containsPostgresAtomicRoutine(String sql) {
        Matcher routine = POSTGRES_ROUTINE.matcher(sql);
        if (!routine.find()) {
            return false;
        }
        int semicolon = sql.indexOf(';', routine.end());
        int end = semicolon < 0 ? sql.length() : semicolon;
        return POSTGRES_ATOMIC.matcher(sql).region(routine.end(), end).find();
    }

    static boolean isOracleBlockScript(String script) {
        String withoutComments = stripComments(script, false);
        return ORACLE_BLOCK.matcher(withoutComments).find()
                && !TRANSACTION_BEGIN.matcher(withoutComments).find()
                && (ORACLE_BLOCK_END.matcher(script).find() || ORACLE_BLOCK_END.matcher(withoutComments).find());
    }

    static boolean containsFlinkStatementSet(String sql) {
        return FLINK_STATEMENT_SET.matcher(sql).find();
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

    private static void addIfNotBlank(List<String> statements, StringBuilder sql,
                                      SqlDialect dialect, boolean nestedComments) {
        String value = sql.toString().trim();
        if (hasSqlContent(value, dialect, nestedComments)) {
            statements.add(value);
        }
    }

    private static boolean hasSqlContent(String sql, SqlDialect dialect, boolean nestedComments) {
        if (sql.isEmpty()) {
            return false;
        }
        boolean mysql = dialect == SqlDialect.MYSQL;
        boolean executableComments = mysql || dialect == SqlDialect.OCEANBASE || dialect == SqlDialect.UNKNOWN;
        return !stripComments(sql, nestedComments, mysql).trim().isEmpty()
                || (executableComments && sql.contains("/*!")
                        && !stripComments(MySqlExecutableComments.unwrap(sql), false, mysql).trim().isEmpty());
    }

    private static boolean startsLineComment(String sql, int start, boolean mysql) {
        return mysql ? MySqlExecutableComments.startsLineComment(sql, start) : sql.startsWith("--", start);
    }

    private static String stripComments(String sql, boolean nestedComments) {
        return stripComments(sql, nestedComments, false);
    }

    private static String stripComments(String sql, boolean nestedComments, boolean mysql) {
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
            if (quote == 0 && startsLineComment(sql, i, mysql)) {
                if (c == '-') {
                    i++;
                }
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
