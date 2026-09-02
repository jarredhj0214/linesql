package io.github.linesql.core.internal;

import io.github.linesql.core.model.DialectCandidate;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDialectDetector implements DialectDetector {
    private static final Pattern MYSQL_EXECUTABLE_COMMENT =
            Pattern.compile("/\\*!\\d*\\s*(.*?)\\*/", Pattern.DOTALL);

    @Override
    public List<SqlDialect> detect(String sql) {
        List<SqlDialect> dialects = new ArrayList<SqlDialect>();
        for (DialectCandidate candidate : detectCandidates(sql)) {
            dialects.add(candidate.getDialect());
        }
        return dialects;
    }

    @Override
    public List<DialectCandidate> detectCandidates(String sql) {
        boolean mysqlExecutableComment = containsMySqlExecutableComment(sql);
        String rawNormalized = unwrapMySqlExecutableComments(sql).toLowerCase(Locale.ROOT);
        String normalized = stripComments(unwrapMySqlExecutableComments(sql)).toLowerCase(Locale.ROOT);
        List<DialectCandidate> candidates = new ArrayList<DialectCandidate>();
        boolean sparkJsonTableSignal = hasSparkJsonTableSignal(normalized);
        boolean strongSparkSignal = hasStrongSparkSignal(normalized);
        boolean sparkSignal = strongSparkSignal || hasGeneralSparkSignal(normalized);
        boolean weakMySqlExpressionSignal = normalized.matches("(?s).*\\bdiv\\b.*")
                || normalized.matches("(?s).*\\bmod\\b.*")
                || normalized.matches("(?s).*\\blimit\\s+\\d+\\s*,\\s*\\d+.*");

        if (mysqlExecutableComment
                || normalized.matches("(?s).*\\bupdate\\b.+\\bjoin\\b.+\\bset\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\busing\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\bfrom\\b.+\\bjoin\\b.*")
                || normalized.matches("(?s)^\\s*replace\\s+into\\b.*")
                || normalized.matches("(?s)^\\s*rename\\s+table\\b.*")
                || normalized.matches("(?s)^\\s*lock\\s+tables\\b.*")
                || normalized.matches("(?s)^\\s*unlock\\s+tables\\b.*")
                || normalized.matches("(?s)^\\s*load\\s+xml\\b.*")
                || normalized.matches("(?s)^\\s*update\\s+(low_priority|ignore)\\b.*")
                || normalized.matches("(?s)^\\s*delete\\s+.*\\b(low_priority|quick|ignore)\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+algorithm\\b.*\\bview\\b.*")
                || normalized.matches("(?s).*\\binto\\s+(out|dump)file\\b.*")
                || (!sparkJsonTableSignal && normalized.matches("(?s).*\\bjson_table\\s*\\(.*"))
                || normalized.contains(" on duplicate key ")
                || hasMySqlJsonArrowSignal(normalized)
                || (!sparkSignal && weakMySqlExpressionSignal)) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.97, "MySQL-specific write, DML, LOAD, JSON, or LIMIT syntax"));
        }
        if (normalized.contains("oceanbase")
                || normalized.contains("ob_read_consistency")) {
            candidates.add(candidate(SqlDialect.OCEANBASE, 0.94, "OceanBase-specific hint, option, or identifier anchor"));
        }
        if (normalized.contains("_ob_")) {
            candidates.add(candidate(SqlDialect.OCEANBASE, 0.45, "Weak OceanBase-style identifier anchor"));
        }
        if (normalized.contains(" on conflict ")
                || normalized.matches("(?s).*\\breturning\\b.*")
                || normalized.matches("(?s).*::\\s*[a-zA-Z_][a-zA-Z0-9_]*.*")
                || normalized.matches("(?s).*\\bilike\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.92, "PostgreSQL ON CONFLICT, RETURNING, cast, or ILIKE syntax"));
        }
        if (normalized.matches("(?s).*\\brow\\s+format\\b.*")
                || normalized.matches("(?s).*\\bstored\\s+as\\b.*")
                || normalized.matches("(?s).*\\bserdeproperties\\b.*")
                || normalized.matches("(?s).*\\bclustered\\s+by\\b.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.90, "Hive storage or table layout syntax"));
        }
        if (rawNormalized.matches("(?s).*?/\\*\\+\\s*options\\s*\\(.*")
                || normalized.contains("'connector'")
                || normalized.contains("'connector.type'")
                || normalized.contains("\"connector\"")
                || normalized.contains("\"connector.type\"")
                || normalized.matches("(?s)^\\s*set\\s+table\\..*")
                || normalized.matches("(?s)^\\s*set\\s+['\"]table\\..*")
                || normalized.matches("(?s)^\\s*reset\\s+table\\..*")
                || normalized.matches("(?s).*\\bcreate\\s+catalog\\b.*")
                || normalized.matches("(?s).*\\buse\\s+catalog\\b.*")
                || normalized.matches("(?s).*\\bjson_(value|query|exists|object|array|objectagg|arrayagg)\\s*\\(.*")
                || normalized.matches("(?s).*\\bfloor\\s*\\([^;]+\\bto\\s+(minute|hour|day|month|year)\\b.*")
                || normalized.matches("(?s).*\\bceil\\s*\\([^;]+\\bto\\s+(minute|hour|day|month|year)\\b.*")
                || normalized.matches("(?s).*\\bextract\\s*\\([^;]+\\bfrom\\b.*")
                || normalized.matches("(?s).*\\b(tumble|hop|session)_(start|end|rowtime|proctime)\\s*\\(.*")
                || normalized.matches("(?s).*\\bfrom\\s+table\\s*\\(\\s*(tumble|hop|session|cumulate)\\s*\\(.*")
                || normalized.matches("(?s).*\\bfor\\s+system_time\\s+as\\s+of\\b.*")
                || normalized.matches("(?s).*\\bproctime\\s*\\(\\s*\\).*")
                || normalized.matches("(?s).*\\bsplit_index\\s*\\(.*")
                || normalized.matches("(?s).*\\bcreate\\s+(temporary\\s+)?view\\s+if\\s+not\\s+exist\\b.*")
                || normalized.matches("(?s).*\\bwatermark\\s+for\\b.*")
                || normalized.matches("(?s)^\\s*(compile|execute)\\s+plan\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+jobs\\b.*")
                || normalized.matches("(?s)^\\s*(describe|desc)\\s+job\\b.*")
                || normalized.matches("(?s)^\\s*stop\\s+job\\b.*")
                || normalized.matches("(?s)^\\s*call\\s+`[^`]+`\\s*\\..*")
                || normalized.matches("(?s)^\\s*create\\s+(temporary\\s+)?model\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(or\\s+alter\\s+)?materialized\\s+table\\b.*")
                || normalized.matches("(?s).*\\bwith\\s+connector\\b.*")) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink connector, hint, catalog, JSON, watermark, plan, model, job, or procedure syntax"));
        }
        if (normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bduplicate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\baggregate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bdistributed\\s+by\\s+hash\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+routine\\s+load\\b.*")
                || normalized.matches("(?s).*\\bload\\s+label\\b.*")
                || normalized.matches("(?s).*\\brefresh\\s+materialized\\s+view\\b.*")
                || normalized.contains(" properties (\"replication_num\"")) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.93, "StarRocks key, distribution, or replication syntax"));
        }
        if (normalized.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || normalized.matches("(?s).*\\bconnect\\s+by\\b.*")
                || normalized.matches("(?s).*\\bstart\\s+with\\b.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.90, "Oracle DUAL or hierarchical query syntax"));
        }
        if (normalized.matches("(?s).*\\bselect\\s+top\\s+\\d+\\b.*")
                || containsSqlServerBracketIdentifier(normalized)
                || normalized.matches("(?s).*\\bwith\\s*\\(\\s*nolock\\s*\\).*")) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.91, "SQL Server TOP, bracketed identifier, or table hint syntax"));
        }
        if (sparkSignal) {
            candidates.add(candidate(
                    SqlDialect.SPARK,
                    strongSparkSignal ? 0.96 : 0.93,
                    "Spark production SQL, function, variable, temporary view, QUALIFY, or USING syntax"));
        }
        if (!contains(candidates, SqlDialect.SPARK)) {
            candidates.add(candidate(SqlDialect.SPARK, 0.50, "Dialect-neutral SQL; Spark is the current generic fallback"));
        }
        candidates.sort(Comparator.comparingDouble(DialectCandidate::getConfidence).reversed());
        return candidates;
    }

    private static boolean hasStrongSparkSignal(String sql) {
        return sql.contains("${")
                || sql.contains("#day#")
                || sql.contains("`")
                || sql.matches("(?s).*\\bcast\\s*\\([^)]*\\bas\\s+string\\s*\\).*")
                || sql.matches("(?s).*\\b(get_json_object|regexp_replace|regexp_extract|date_format|date_sub|from_unixtime|unix_timestamp|collect_list|collect_set|named_struct|posexplode|explode|if|ifnull|nvl)\\s*\\(.*")
                || sql.matches("(?s).*\\brlike\\b.*")
                || hasSparkJsonTableSignal(sql)
                || hasSparkLambdaSignal(sql)
                || containsSparkSubscript(sql);
    }

    private static boolean hasGeneralSparkSignal(String sql) {
        return sql.contains("insert overwrite")
                || sql.contains("lateral view")
                || sql.contains("create temporary view")
                || sql.matches("(?s).*\\bqualify\\b.*")
                || sql.matches("(?s).*\\b(distribute|cluster|sort)\\s+by\\b.*")
                || sql.contains(" using ");
    }

    private static DialectCandidate candidate(SqlDialect dialect, double confidence, String reason) {
        return new DialectCandidate(dialect, confidence, reason);
    }

    private static boolean contains(List<DialectCandidate> candidates, SqlDialect dialect) {
        for (DialectCandidate candidate : candidates) {
            if (candidate.getDialect() == dialect) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSqlServerBracketIdentifier(String sql) {
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) != '[') {
                continue;
            }
            char previous = previousAdjacentNonWhitespace(sql, i);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == '.' || previous == ')' || previous == '`') {
                continue;
            }
            int end = sql.indexOf(']', i + 1);
            if (end < 0) {
                return false;
            }
            String content = sql.substring(i + 1, end);
            if (!content.isEmpty() && isIdentifierStart(content.charAt(0))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSparkSubscript(String sql) {
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) != '[') {
                continue;
            }
            char previous = previousAdjacentNonWhitespace(sql, i);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == ')' || previous == '`') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSparkLambdaSignal(String sql) {
        return sql.matches("(?s).*\\b(transform|filter|aggregate|exists|forall|zip_with|map_filter|transform_keys|transform_values|array_sort)\\s*\\([^;]*->.*");
    }

    private static boolean hasMySqlJsonArrowSignal(String sql) {
        return sql.matches("(?s).*->>?\\s*['\"]\\$\\..*");
    }

    private static boolean hasSparkJsonTableSignal(String sql) {
        return sql.matches("(?s).*\\bjson_table\\s*\\([^;]*\\bcolumns\\s*\\([^;]*\\bstring\\s+path\\b.*");
    }

    private static boolean containsMySqlExecutableComment(String sql) {
        return sql != null && MYSQL_EXECUTABLE_COMMENT.matcher(sql).find();
    }

    private static String unwrapMySqlExecutableComments(String sql) {
        Matcher matcher = MYSQL_EXECUTABLE_COMMENT.matcher(sql);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static char previousAdjacentNonWhitespace(String sql, int index) {
        if (index <= 0) {
            return '\0';
        }
        char previous = sql.charAt(index - 1);
        return Character.isWhitespace(previous) ? '\0' : previous;
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '@' || value == '#';
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
