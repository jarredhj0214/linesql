package io.github.linesql.core.internal;

import io.github.linesql.core.model.DialectCandidate;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectDetector;
import io.github.linesql.core.util.MySqlExecutableComments;
import io.github.linesql.core.util.OracleQuotedLiterals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDialectDetector implements DialectDetector {
    private static final String TOP_COUNT = "\\(\\s*(?:[0-9]+(?:\\.[0-9]+)?|@[\\p{L}_$][\\p{L}\\p{N}_$]*)\\s*\\)";
    private static final Pattern TSQL_TOP_SELECT = Pattern.compile(
            "\\bselect\\s+(?:(?:all|distinct)\\s+)?top(?:\\s+\\d+\\b|\\s*" + TOP_COUNT
                    + "\\s*(?:\\*\\s*(?=from\\b|[,);]|$)"
                    + "|(?!(?:as|from|where|group|order|having|limit|offset|fetch|union|except|intersect"
                    + "|over|filter|within|is|in|like|between|not|and|or)\\b)"
                    + "[\\p{L}_@#\\[\"]))");
    private static final Pattern TSQL_TOP_DML = Pattern.compile(
            "\\b(?:insert|update|delete|merge)\\s+top\\s*" + TOP_COUNT + "\\s*[\\p{L}_@#\\[\"]");

    private static final Pattern TSQL_OBJECT_SCOPE = Pattern.compile(
            "(?:^|;)\\s*(?:(?:grant|revoke)\\b[^;]*\\bon\\s+(?:object|schema)\\s*::"
                    + "|deny\\b[^;]*\\bon\\b"
                    + "|alter\\s+schema\\b[^;]*\\btransfer\\s+object\\s*::)");

    private static final String TSQL_LOCAL_VARIABLE = "@[\\p{L}_][\\p{L}\\p{N}_$#]*";
    private static final Pattern TSQL_TABLE_VARIABLE = Pattern.compile(
            "\\bdeclare\\s+" + TSQL_LOCAL_VARIABLE + "\\s+(?:as\\s+)?table\\s*\\("
                    + "|\\b(?:from|join|update|delete|insert\\s+into|merge(?:\\s+into)?)\\s+" + TSQL_LOCAL_VARIABLE);
    private static final String ORACLE_NAME = "[\\p{L}_][\\p{L}\\p{N}_$#]*";
    private static final Pattern ORACLE_REMOTE_RELATION = Pattern.compile(
            "\\b(?:from|join|update|insert\\s+into|merge\\s+into)\\s+"
                    + ORACLE_NAME + "(?:\\s*\\.\\s*" + ORACLE_NAME + ")*\\s*@\\s*" + ORACLE_NAME);

    private static final Pattern STARROCKS_JOIN = Pattern.compile(
            "\\basof\\s+(?:inner|left(?:\\s+outer)?)\\s+join\\b"
                    + "|\\bright\\s+(?:semi|anti)\\s+join\\b|\\bnull\\s+aware\\s+left\\s+anti\\s+join\\b");
    private static final Pattern STARROCKS_ASOF_ON = Pattern.compile(
            "\\basof\\s+join\\b[^;]*\\bon\\b");

    private static final Pattern MYSQL_DUPLICATE_KEY_UPDATE = Pattern.compile(
            "\\bon\\s+duplicate\\s+key\\s+update\\b");

    private static final Pattern ORACLE_START_WITH = Pattern.compile("\\bstart\\s+with\\b");
    private static final Pattern ORACLE_SAMPLE = Pattern.compile("\\bsample\\s+(?:block\\s+)?\\(");
    private static final Pattern ORACLE_PARTITION = Pattern.compile("\\b(?:sub)?partition\\s*\\(");

    private static final Pattern ORACLE_RETURNING_TARGET = Pattern.compile(
            "\\breturn(?:ing)?\\b[^;]*\\b(?:bulk\\s+collect\\s+into\\b|into\\s*:[\\p{L}_][\\p{L}\\p{N}_$#]*)");

    private static final String POSTGRES_NAME = "[\\p{L}_][\\p{L}\\p{N}_$]*";
    private static final String POSTGRES_RELATION =
            POSTGRES_NAME + "(?:\\s*\\.\\s*" + POSTGRES_NAME + ")*";
    private static final Pattern STARROCKS_TEMPORARY_PARTITION = Pattern.compile(
            "\\b(?:from|join|into|overwrite)\\s+(?:table\\s+)?[a-z_][\\w$]*"
                    + "(?:\\s*\\.\\s*[a-z_][\\w$]*)*\\s+temporary\\s+partition\\s*\\("
                    + "|^\\s*show\\s+temporary\\s+partitions\\s+from\\b"
                    + "|^\\s*alter\\s+table\\b[^;]*\\b(?:add|drop|modify|with)\\s+temporary\\s+partitions?\\b");
    private static final Pattern HIVE_TABLE_VALUES = Pattern.compile(
            "\\b(?:from|lateral)\\s+table\\s*\\(\\s*values\\s*\\(");
    private static final Pattern FLINK_MATERIALIZED_TABLE = Pattern.compile(
            "^\\s*(?:(?:show\\s+)?create(?:\\s+or\\s+alter)?|alter|drop)\\s+materialized\\s+table\\b"
                    + "|^\\s*show\\s+materialized\\s+tables\\b");
    private static final Pattern POSTGRES_SCHEMA_DDL = Pattern.compile(
            "^\\s*(?:create|alter|drop)\\s+event\\s+trigger\\b"
                    + "|^\\s*create\\s+type\\b[^;]*\\bas\\s+(?:enum|range)\\s*\\("
                    + "|^\\s*alter\\s+type\\b[^;]*\\b(?:add|rename)\\s+value\\b"
                    + "|^\\s*alter\\s+domain\\b[^;]*\\b(?:validate\\s+constraint"
                    + "|(?:set|drop)\\s+(?:default|not\\s+null)|not\\s+valid)\\b");
    private static final Pattern POSTGRES_DML_TARGET = Pattern.compile(
            "\\b(?:delete\\s+from\\s+|update\\s+(?!set\\b))(?:only\\s+(?:\\(\\s*" + POSTGRES_RELATION + "\\s*\\)"
                    + "|" + POSTGRES_RELATION + "(?=\\s+(?:as|using|where|set|returning)\\b|\\s*(?:;|$)))"
                    + "|" + POSTGRES_RELATION + "\\s*\\*(?=\\s|;|$))");
    private static final Pattern POSTGRES_ROWS_FROM = Pattern.compile(
            "(?:\\b(?:from|join)\\b|,)\\s*(?:lateral\\s+)?rows\\s+from\\s*\\(");

    private static final Pattern POSTGRES_VECTOR_INDEX = Pattern.compile(
            "^\\s*create\\s+index\\b[^;]*\\bon\\b[^;]*\\busing\\s+(?:hnsw|ivfflat)\\b");
    private static final Pattern SHARED_VECTOR_OPERATOR = Pattern.compile("<[-#]>");
    private static final Pattern ORACLE_VECTOR_SCHEMA = Pattern.compile(
            "^\\s*create\\s+vector\\s+index\\b"
                    + "|(?s)^\\s*(?:create|alter)\\s+table\\b.*?\\bvector\\s*\\(\\s*(?:\\*|[0-9]+)\\s*,"
                    + "\\s*(?:float32|float64|int8|binary|\\*\\s*,\\s*(?:dense|sparse))\\b");
    private static final Pattern ORACLE_VECTOR_QUERY = Pattern.compile(
            "\\bfetch\\s+(?:approx(?:imate)?|exact)\\s+(?:first|next)\\b"
                    + "|\\bwith\\s+target\\s+accuracy\\b");
    private static final Pattern ORACLE_VECTOR_CONSTRUCTOR = Pattern.compile("\\bto_vector\\s*\\(");
    private static final Pattern POSTGRES_OPERATOR_SYNTAX = Pattern.compile(
            "<[+~%]>|\\boperator\\s*\\(\\s*(?:[a-z_][\\w$]*\\s*\\.\\s*)?"
                    + "[<>=!~+*/%?#@&|^-]+\\s*\\)");
    private static final Pattern POSTGRES_JSON_SYNTAX = Pattern.compile(
            "::\\s*(?:pg_catalog\\s*\\.\\s*)?jsonb\\b|#>>?|@\\?"
                    + "|\\breturning\\s+(?:pg_catalog\\s*\\.\\s*)?jsonb\\b"
                    + "|\\b(?:to_jsonb|jsonb_(?:array_elements(?:_text)?|array_length|each(?:_text)?|object_keys"
                    + "|build_(?:array|object)|agg(?:_strict)?|object_agg(?:_strict|_unique(?:_strict)?)?"
                    + "|strip_nulls|populate_record(?:set|_valid)?|to_record(?:set)?|insert|set(?:_lax)?|pretty|typeof"
                    + "|path_(?:exists|match|query(?:_array|_first)?)(?:_tz)?))\\s*\\(");
    private static final Pattern XML_PASSING_EXPRESSION = Pattern.compile("\\b(?:xmltable|xmlexists)\\s*\\(");
    private static final Pattern POSTGRES_XML_PASSING_OPTION = Pattern.compile(
            "\\bpassing\\s+by\\s+ref\\b|\\bby\\s+(?:ref|value)(?:\\s+columns\\b|\\s*\\))"
                    + "|::\\s*(?:pg_catalog\\s*\\.\\s*)?xml\\b");
    private static final Pattern ORACLE_PARTITIONED_JOIN = Pattern.compile(
            "\\b(?:from|join)\\s+[a-z_][\\w$#]*(?:\\s*\\.\\s*[a-z_][\\w$#]*)*\\s+"
                    + "(?:(?:as\\s+)?[a-z_][\\w$#]*\\s+)?partition\\s+by\\s*\\(");
    private static final Pattern ORACLE_DOMAIN_DDL = Pattern.compile(
            "^\\s*(?:create|alter)\\s+usecase\\s+domain\\b"
                    + "|^\\s*create\\s+domain\\s+(?:if\\s+not\\s+exists\\b|[^;]*\\bas\\s+(?:enum\\s*)?\\()"
                    + "|^\\s*alter\\s+domain\\s+(?:if\\s+exists\\b|[^;]*\\b(?:(?:add|modify|drop)\\s+"
                    + "(?:display|order)\\b|annotations\\s*\\())"
                    + "|^\\s*drop\\s+domain\\s+(?:usecase\\b|[^;]*\\bforce\\b)");
    private static final Pattern ORACLE_XML_SEQUENCE = Pattern.compile("\\breturning\\s+sequence\\s+by\\s+ref\\b");
    private static final Pattern POSTGRES_UNICODE_PREDICATE = Pattern.compile(
            "\\bis\\s+(?:not\\s+)?(?:nfc\\s+|nfd\\s+|nfkc\\s+|nfkd\\s+)?normalized\\b");
    private static final Pattern POSTGRES_TEXT_SEARCH = Pattern.compile(
            "::\\s*(?:pg_catalog\\s*\\.\\s*)?(?:tsvector|tsquery|regconfig)\\b"
                    + "|\\b(?:to_tsvector|(?:plain|phrase|websearch_)?to_tsquery|ts_rank(?:_cd)?|ts_headline)\\s*\\(");

    private static final Pattern TSQL_CURSOR_CONTROL = Pattern.compile(
            "^\\s*fetch\\b(?:\\s+(?:absolute|relative)\\s+@"
                    + "|\\s+(?:prior|first|last|absolute|relative)\\s+[^;]*\\binto\\s+@"
                    + "|[^;]*\\bfrom\\s+global\\s+\\S)"
                    + "|^\\s*declare\\s+[^;]+?\\bcursor\\s+"
                    + "(?:(?:local|global|forward_only|scroll|static|keyset|dynamic|fast_forward|read_only)\\s+)*"
                    + "(?:scroll_locks|optimistic|type_warning)\\b");
    private static final Pattern TSQL_DML_OUTPUT = Pattern.compile(
            "(?s)^\\s*(?:with\\b[^;]*?)?(?:insert|update|delete|merge)\\b[^;]*\\boutput\\s+"
                    + "(?:\\(\\s*)*(?:(?:inserted|deleted)\\s*\\.|\\$action\\b)");

    private static final String TSQL_NAME = "[\\p{L}_#][\\p{L}\\p{N}_$#]*";
    private static final Pattern TSQL_TABLE_HINT = Pattern.compile(
            "\\b(?:from|join|update|merge(?:\\s+into)?|insert\\s+into)\\s+"
                    + TSQL_NAME + "(?:\\s*\\.\\s*" + TSQL_NAME + ")*"
                    + "(?:\\s+(?:as\\s+)?" + TSQL_NAME + ")?\\s+with\\s*\\(\\s*"
                    + "(?:(?:holdlock|updlock|tablockx?|rowlock|paglock|xlock|readpast|readuncommitted|readcommittedlock"
                    + "|readcommitted|repeatableread|serializable|nowait|noexpand|forceseek|forcescan)\\b"
                    + "|index\\s*(?:\\(|=))");
    private static final Pattern TSQL_QUERY_OPTION_START = Pattern.compile("\\boption\\s*\\(");
    private static final Pattern TSQL_QUERY_HINT = Pattern.compile(
            "\\b(?:from|insert|update|delete|merge)\\b[^;]*?"
                    + "(?:\\b(?!(?:from|join|on|where|having|and|or|not|by)\\b)" + TSQL_NAME + "|[^\\s\\w$#;,])"
                    + "\\s+option\\s*\\(\\s*"
                    + "(?:(?:recompile|force\\s+order|(?:loop|merge|hash)\\s+join)\\s*(?=[,)])"
                    + "|(?:maxdop|maxrecursion|fast|querytraceon)\\s+\\d+\\b"
                    + "|use\\s+hint\\s*\\()[^;]*\\)\\s*(?=;|$)");

    private static final Pattern TSQL_TRUNCATE_PARTITIONS = Pattern.compile(
            "\\btruncate\\s+table\\b[^;]*?\\bwith\\s*\\(\\s*partitions\\s*\\(");

    private static final Pattern TSQL_SP_EXECUTESQL = Pattern.compile(
            "(?m)(?:^|;)\\s*exec(?:ute)?\\s+(?:" + TSQL_LOCAL_VARIABLE + "\\s*=\\s*)?"
                    + "(?:(?:sys|\\[sys\\]|\"sys\")\\s*\\.\\s*)?"
                    + "(?:sp_executesql|\\[sp_executesql\\]|\"sp_executesql\")(?=\\s|;|$)");

    private static final Pattern JSON_AGGREGATION = Pattern.compile("\\bjson_(?:array|object)agg\\s*\\(");
    private static final Pattern TSQL_JSON_AGGREGATION_CONTEXT = Pattern.compile(
            "\\bselect\\s+(?:(?:all|distinct)\\s+)?top(?:\\s+\\d+\\b|\\s*" + TOP_COUNT
                    + "\\s*(?:" + TSQL_NAME + "\\s*[.(]|\\*\\s*(?=from\\b|[,);]|$)))"
                    + "|\\b(?:from|join|apply)\\s+openjson\\s*\\(");
    private static final Pattern TSQL_FOR_SERIALIZATION = Pattern.compile(
            "\\bfor\\s+(?:json\\s+(?:auto|path)|xml\\s+(?:raw|auto|explicit|path))\\b");

    private static final Pattern TSQL_BATCH_ROUTINE = Pattern.compile(
            "(?m)^\\s*(?:create\\s+or\\s+alter\\s+(?:proc(?:edure)?|function|trigger)"
                    + "|(?:create|alter)\\s+proc)\\b");

    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile(
            "`(?:``|[^`])*`|\"(?:\"\"|[^\"])*\"|\\[(?:\\]\\]|[^\\]])*\\]");

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
        sql = SimpleStatementSplitter.withoutPostgresCopyData(sql);
        LexicalRewrite literals = maskQuotedLiterals(sql);
        String unwrapped = MySqlExecutableComments.unwrap(literals.text);
        boolean mysqlExecutableComment = !unwrapped.equals(literals.text);
        String rawNormalized = unwrapped.toLowerCase(Locale.ROOT);
        String normalized = stripComments(unwrapped).toLowerCase(Locale.ROOT);
        List<DialectCandidate> candidates = new ArrayList<DialectCandidate>();
        boolean sparkJsonTableSignal = hasSparkJsonTableSignal(normalized);
        boolean strongSparkSignal = hasStrongSparkSignal(normalized);
        boolean sparkSignal = strongSparkSignal || hasGeneralSparkSignal(normalized);
        boolean weakMySqlExpressionSignal = normalized.matches("(?s).*\\bdiv\\b.*")
                || normalized.matches("(?s).*\\bmod\\b.*")
                || normalized.matches("(?s).*\\blimit\\s+\\d+\\s*,\\s*\\d+.*");

        String relationSyntax = normalized.replaceAll("'(?:''|\\\\.|[^'\\\\])*'", " ");

        String unquotedSyntax = QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll(" ");

        boolean sqlServerTemporalQuery = unquotedSyntax.matches("(?s).*\\bfor\\s+system_time\\s+"
                + "(?:(?:all|between|from|contained\\s+in)\\b|as\\s+of\\s+" + TSQL_LOCAL_VARIABLE + ").*");

        if (SimpleStatementSplitter.containsMySqlDelimiterDirective(sql)) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.99, "MySQL client DELIMITER directive"));
        } else if (mysqlExecutableComment
                || normalized.matches("(?s).*\\bupdate\\b.+\\bjoin\\b.+\\bset\\b.*")
                || unquotedSyntax.matches("(?s).*\\bdelete\\s+from\\b[^;]*\\busing\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\bfrom\\b.+\\bjoin\\b.*")
                || normalized.matches("(?s)^\\s*replace\\s+into\\b.*")
                || normalized.matches("(?s)^\\s*rename\\s+table\\b.*")
                || normalized.matches("(?s)^\\s*lock\\s+tables\\b.*")
                || normalized.matches("(?s)^\\s*unlock\\s+tables\\b.*")
                || normalized.matches("(?s)^\\s*load\\s+xml\\b.*")
                || normalized.matches("(?s)^\\s*explain\\s+partitions\\b.*")
                || normalized.matches("(?s)^\\s*update\\s+(low_priority|ignore)\\b.*")
                || normalized.matches("(?s)^\\s*delete\\s+.*\\b(low_priority|quick|ignore)\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+algorithm\\b.*\\bview\\b.*")
                || normalized.matches("(?s).*\\binto\\s+(out|dump)file\\b.*")
                || (!sparkJsonTableSignal && normalized.matches("(?s).*\\bjson_table\\s*\\(.*"))
                || MYSQL_DUPLICATE_KEY_UPDATE.matcher(unquotedSyntax).find()
                || hasMySqlJsonArrowSignal(normalized)
                || (!sparkSignal && weakMySqlExpressionSignal)) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.97, "MySQL-specific write, DML, LOAD, JSON, or LIMIT syntax"));
        }
        if (normalized.contains("oceanbase")
                || normalized.contains("ob_read_consistency")
                || normalized.matches("(?s)^\\s*alter\\s+proxyconfig\\s+set\\b.*")
                || normalized.matches("(?s)^\\s*(create|alter|drop)\\s+tenant\\b.*")
                || normalized.matches("(?s)^\\s*change\\s+tenant\\b.*")
                || normalized.matches("(?s)^\\s*(create|alter|drop)\\s+resource\\s+(unit|pool)\\b.*")
                || normalized.matches("(?s)^\\s*(create|alter|drop)\\s+tablegroup\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+(add|delete|start|stop|cancel\\s+delete)\\s+server\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+(add|delete|isolate|start|(force\\s+)?stop)\\s+zone\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+(major|minor)\\s+freeze\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+(set|reset)\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+(no)?archivelog\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+system\\s+restore\\b.*")
                || normalized.matches("(?s)^\\s*(start|stop)\\s+server\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+(servers?|zone)\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+restore\\s+preview\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+(create\\s+)?tenant\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+resource\\s+(unit|pool)\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+tablegroups?\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+recyclebin\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+parameters\\b.*")
                || normalized.matches("(?s)^\\s*purge\\s+(recyclebin|table|index|database|tenant)\\b.*")
                || normalized.matches("(?s)^\\s*flashback\\s+table\\b.+\\bto\\s+before\\s+drop\\b.*")
                || normalized.matches("(?s)^\\s*flashback\\s+tenant\\b.+\\bto\\s+before\\s+drop\\b.*")
                || normalized.matches("(?s)^\\s*rename\\s+tenant\\b.*")
                || normalized.matches("(?s).*\\bas\\s+of\\s+snapshot\\b.*")
                || rawNormalized.matches("(?s).*?/\\*\\+\\s*.*\\b(read_consistency|query_timeout)\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.OCEANBASE, 0.94, "OceanBase-specific hint, option, or identifier anchor"));
        }
        if (normalized.contains("_ob_")) {
            candidates.add(candidate(SqlDialect.OCEANBASE, 0.45, "Weak OceanBase-style identifier anchor"));
        }
        if (unquotedSyntax.matches("(?s)^\\s*(?:create|alter|drop)\\s+(?:policy|publication|subscription)\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*alter\\s+default\\s+privileges\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*alter\\s+table\\b[^;]*\\b(?:enable|disable|force|no\\s+force)"
                        + "\\s+row\\s+level\\s+security\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98,
                    "PostgreSQL row-level security, logical replication, or default privileges syntax"));
        } else if (unquotedSyntax.matches("(?s)^\\s*create\\s+(?:or\\s+replace\\s+)?(?:function|procedure)\\b"
                + "[^;]*\\blanguage\\s+plpgsql\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL PL/pgSQL routine language"));
        } else if (SimpleStatementSplitter.containsPostgresAtomicRoutine(unquotedSyntax)) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL SQL-standard routine body"));
        } else if (POSTGRES_SCHEMA_DDL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL event trigger, enum, range, or domain DDL"));
        } else if (unquotedSyntax.matches("(?s)^\\s*alter\\s+table\\b[^;]*\\balter\\b[^;]*"
                + "\\b(?:set|drop)\\s+expression\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL generated-column expression alteration"));
        } else if (SimpleStatementSplitter.containsPostgresClientCommand(sql)) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL psql client command"));
        } else if (unquotedSyntax.contains("copy") && SimpleStatementSplitter.containsPostgresCopyFromStdin(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier"))) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL COPY FROM STDIN"));
        } else if ((unquotedSyntax.contains("delete") || unquotedSyntax.contains("update"))
                && POSTGRES_DML_TARGET.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL inherited DML target"));
        } else if (POSTGRES_JSON_SYNTAX.matcher(unquotedSyntax).find()
                && !TSQL_OBJECT_SCOPE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL JSONB cast, function, or path operator"));
        } else if (XML_PASSING_EXPRESSION.matcher(unquotedSyntax).find()
                && POSTGRES_XML_PASSING_OPTION.matcher(unquotedSyntax).find()
                && !ORACLE_XML_SEQUENCE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL XML passing mode or cast"));
        } else if (POSTGRES_VECTOR_INDEX.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL vector index method"));
        } else if (POSTGRES_OPERATOR_SYNTAX.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL vector or qualified operator"));
        } else if (POSTGRES_TEXT_SEARCH.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL text-search function or type"));
        } else if (SHARED_VECTOR_OPERATOR.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.90, "PostgreSQL-compatible vector distance operator"));
        } else if (POSTGRES_UNICODE_PREDICATE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL Unicode normalization predicate"));
        } else if (POSTGRES_ROWS_FROM.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL ROWS FROM relation"));
        } else if (literals.dollarQuoted && normalized.matches("(?s)^\\s*do\\s+''.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.94, "PostgreSQL dollar-quoted DO block"));
        } else if (normalized.matches("(?s).*\\bon\\s+conflict\\b.*")
                || normalized.matches("(?s).*\\breturning\\b.*")
                || normalized.matches("(?s).*::\\s*[a-zA-Z_][a-zA-Z0-9_]*.*")
                || normalized.matches("(?s).*\\bilike\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.92, "PostgreSQL ON CONFLICT, RETURNING, cast, or ILIKE syntax"));
        }
        if (HIVE_TABLE_VALUES.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive TABLE VALUES relation"));
        }
        if (unquotedSyntax.matches("(?s).*\\bpartitioned\\s+by\\s+spec\\s*\\(.*")
                || unquotedSyntax.matches("(?s)^\\s*alter\\s+table\\b[^;]*\\bset\\s+partition\\s+spec\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive Iceberg partition transform specification"));
        } else if (unquotedSyntax.matches("(?s)^\\s*alter\\s+table\\b[^;]*\\bexecute\\s+"
                + "(?:rollback|expire_snapshots|set_current_snapshot|fast_forward|cherry_pick|delete\\s+orphan_files)\\b.*")
                || unquotedSyntax.matches("(?s).*\\bstored\\s+by\\s+iceberg\\b.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive Iceberg storage or snapshot maintenance syntax"));
        } else if (normalized.matches("(?s).*\\brow\\s+format\\b.*")
                || normalized.matches("(?s).*\\bstored\\s+as\\b.*")
                || relationSyntax.matches("(?s)^\\s*create\\s+(?:(?:temporary|external)\\s+)*table\\b.*\\bstored\\s+by\\b.*")
                || normalized.matches("(?s).*\\bserdeproperties\\b.*")
                || normalized.matches("(?s).*\\bclustered\\s+by\\b.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.90, "Hive storage or table layout syntax"));
        }
        if (SimpleStatementSplitter.containsFlinkStatementSet(unquotedSyntax)
                || rawNormalized.matches("(?s).*?/\\*\\+\\s*options\\s*\\(.*")
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
                || relationSyntax.matches("(?s)^\\s*insert\\b.*\\bon\\s+conflict\\s+do\\s+(deduplicate|error)\\b.*")
                || relationSyntax.matches("(?s).*[,\\s](tumble|hop|session|cumulate)\\s*\\(\\s*(?:data\\s*=>\\s*)?table\\s+.*")
                || (!sqlServerTemporalQuery && normalized.matches("(?s).*\\bfor\\s+system_time\\s+as\\s+of\\b.*"))
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
                || FLINK_MATERIALIZED_TABLE.matcher(unquotedSyntax).find()
                || normalized.matches("(?s).*\\bwith\\s+connector\\b.*")) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink connector, hint, catalog, JSON, watermark, plan, model, job, or procedure syntax"));
        }
        if (unquotedSyntax.contains("temporary") && unquotedSyntax.contains("partition")
                && STARROCKS_TEMPORARY_PARTITION.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks temporary partition syntax"));
        } else if (STARROCKS_JOIN.matcher(unquotedSyntax).find()
                || (!unquotedSyntax.contains("match_condition") && STARROCKS_ASOF_ON.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.98, "StarRocks ASOF or directional semi/anti join"));
        } else if (normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bduplicate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\baggregate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bdistributed\\s+by\\s+hash\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+routine\\s+load\\b.*")
                || normalized.matches("(?s).*\\bload\\s+label\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+proc\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+tablet\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+(routine\\s+load|load)\\b.*")
                || normalized.matches("(?s)^\\s*show\\s+create\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*admin\\s+(show|cancel|repair)\\b.*")
                || normalized.matches("(?s).*\\brefresh\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*cancel\\s+refresh\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*cancel\\s+(load|export|alter\\s+table|backup|restore|decommission)\\b.*")
                || normalized.matches("(?s)^\\s*recover\\s+(database|table|partition)\\b.*")
                || normalized.matches("(?s)^\\s*sync\\s*$")
                || normalized.matches("(?s)^\\s*submit\\s+task\\b.*")
                || normalized.contains(" properties (\"replication_num\"")) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.93, "StarRocks key, load, materialized view, metadata, task, or admin syntax"));
        }
        if (SimpleStatementSplitter.containsOracleScriptSeparator(sql)) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle SQL*Plus slash separator"));
        } else if (literals.oracleQuoted) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle alternative-quoted string literal"));
        } else if (unquotedSyntax.contains("partition") && unquotedSyntax.contains("join")
                && ORACLE_PARTITIONED_JOIN.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle partitioned join"));
        } else if (ORACLE_VECTOR_SCHEMA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle vector schema"));
        } else if (ORACLE_VECTOR_QUERY.matcher(unquotedSyntax).find()
                || (SHARED_VECTOR_OPERATOR.matcher(unquotedSyntax).find()
                    && ORACLE_VECTOR_CONSTRUCTOR.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle vector search options or constructor"));
        } else if (ORACLE_DOMAIN_DDL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle data use case domain DDL"));
        } else if (ORACLE_RETURNING_TARGET.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle RETURNING bind or bulk collection"));
        } else if (relationSyntax.indexOf('@') >= 0 && ORACLE_REMOTE_RELATION
                .matcher(QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle database link on a relation"));
        } else if (relationSyntax.matches("(?s).*[,\\s]xmltable\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.97, "XMLTABLE relation syntax"));
        } else if (normalized.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || normalized.matches("(?s).*\\bconnect\\s+by\\b.*")
                || (ORACLE_START_WITH.matcher(relationSyntax).find()
                    && relationSyntax.matches("(?s).*\\bselect\\b[^;]*\\bstart\\s+with\\b.*"))
                || normalized.matches("(?s).*\\bas\\s+of\\s+(timestamp|scn)\\b.*")
                || (ORACLE_SAMPLE.matcher(unquotedSyntax).find()
                    && unquotedSyntax.matches("(?s).*\\bfrom\\b.+\\bsample\\s+(block\\s+)?\\(.*"))
                || (ORACLE_PARTITION.matcher(unquotedSyntax).find()
                    && unquotedSyntax.matches("(?s)^(?!\\s*(explain\\s+partitions|show\\s+tablet|admin\\s+show)\\b).*\\bfrom\\b.+\\b(sub)?partition\\s*\\(.*"))
                || normalized.matches("(?s)^\\s*create\\s+(global|private)\\s+temporary\\s+table\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+table\\b.*\\b(nologging|logging|parallel|noparallel|compress|nocompress)\\b.*\\bas\\s+select\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+table\\b.*\\bcascade\\s+constraints\\b.*")
                || normalized.matches("(?s)^\\s*truncate\\s+(table\\s+)?\\S+\\s+(drop|reuse)\\s+storage\\b.*")
                || normalized.matches("(?s).*\\breturning\\b.+\\binto\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+or\\s+replace\\s+trigger\\b.*")
                || normalized.matches("(?s)^\\s*(create|alter|drop)\\s+(public\\s+)?(sequence|synonym)\\b.*")
                || normalized.matches("(?s)^\\s*(create|drop)\\s+(public\\s+)?database\\s+link\\b.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.95, "Oracle DUAL, hierarchical, flashback, sequence, synonym, or DBLink syntax"));
        }
        boolean sqlServerGo = SimpleStatementSplitter.containsSqlServerGo(unquotedSyntax);
        if (unquotedSyntax.matches("(?s)^\\s*(?:create|alter)\\s+table\\b[^;]*"
                + "(?:\\bperiod\\s+for\\s+system_time\\b|\\bsystem_versioning\\s*=).*")
                || sqlServerTemporalQuery) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server system-versioned temporal syntax"));
        } else if (sqlServerGo && TSQL_BATCH_ROUTINE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server routine and GO batch delimiter"));
        } else if (relationSyntax.matches("(?s)^\\s*with\\s+xmlnamespaces\\s*\\(.*")
                || relationSyntax.matches("(?s).*\\b(?:cross|outer)\\s+apply\\s+[^\\s();]+\\s*\\.\\s*nodes\\s*\\(.*")
                || relationSyntax.matches("(?s).*\\bfrom\\s+@[\\w$#]+\\s*\\.\\s*nodes\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.97, "SQL Server XML namespaces or nodes() method"));
        } else if (TSQL_TABLE_VARIABLE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server table variable"));
        } else if (TSQL_DML_OUTPUT.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server DML OUTPUT row images"));
        } else if (TSQL_OBJECT_SCOPE.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.97, "SQL Server scoped permission or object transfer"));
        } else if (TSQL_TOP_DML.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server TOP in a write statement"));
        } else if ((unquotedSyntax.contains("with") && TSQL_TABLE_HINT.matcher(
                    QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find())
                || (unquotedSyntax.contains("option") && TSQL_QUERY_OPTION_START.matcher(unquotedSyntax).find()
                    && TSQL_QUERY_HINT.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server table or query hint"));
        } else if (TSQL_CURSOR_CONTROL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server cursor options or variables"));
        } else if (TSQL_TRUNCATE_PARTITIONS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server partition truncation"));
        } else if (TSQL_SP_EXECUTESQL.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server sp_executesql"));
        } else if (JSON_AGGREGATION.matcher(unquotedSyntax).find()
                && TSQL_JSON_AGGREGATION_CONTEXT.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.99, "SQL Server TOP or OPENJSON with JSON aggregation"));
        } else if (TSQL_FOR_SERIALIZATION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server FOR JSON or FOR XML output"));
        } else if (TSQL_TOP_SELECT.matcher(relationSyntax).find()
                || containsSqlServerBracketIdentifier(normalized)
                || normalized.matches("(?s).*\\bwith\\s*\\(\\s*nolock\\s*\\).*")
                || normalized.matches("(?s).*\\b(openjson|openxml|openquery|openrowset)\\s*\\(.*")) {
            candidates.add(sqlServerGo
                    ? candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server syntax and GO batch delimiter")
                    : candidate(SqlDialect.SQLSERVER, 0.91,
                            "SQL Server TOP, bracketed identifier, table hint, OPENJSON/OPENXML, or external rowset syntax"));
        }
        if (normalized.matches("(?s)^\\s*execute\\s+immediate\\s+r?['\"].*")) {
            candidates.add(candidate(SqlDialect.SPARK, 0.99, "Spark EXECUTE IMMEDIATE with a SQL literal"));
        } else if (sparkSignal) {
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

    static boolean hasDeleteUsingReturning(String sql) {
        String normalized = stripComments(maskQuotedLiterals(sql).text).toLowerCase(Locale.ROOT);
        String syntax = normalized.replaceAll("'(?:''|\\\\.|[^'\\\\])*'", " ");
        syntax = QUOTED_IDENTIFIER.matcher(syntax).replaceAll(" ");
        return syntax.matches("(?s)^\\s*(?:with\\b[^;]*?)?delete\\s+from\\b[^;]*"
                + "\\busing\\b[^;]*\\breturning\\b.*");
    }

    static boolean hasSharedConflictDoNothing(String sql) {
        LexicalRewrite literals = maskQuotedLiterals(sql);
        if (literals.dollarQuoted || literals.oracleQuoted) {
            return false;
        }
        String normalized = stripComments(sql).toLowerCase(Locale.ROOT);
        return normalized.matches("(?s)^\\s*(?:with|insert)\\b.*\\bon\\s+conflict\\s+do\\s+nothing\\s*;?\\s*$");
    }

    private static boolean hasStrongSparkSignal(String sql) {
        return sql.contains("${")
                || sql.contains("#day#")
                || sql.contains("`")
                || sql.matches("(?s)^\\s*set\\s+spark\\.sql\\..*")
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
                || sql.matches("(?s).*\\busing\\b.*");
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
        return rewriteLexicalText(sql, false).text;
    }

    private static LexicalRewrite maskQuotedLiterals(String sql) {
        return sql.indexOf('$') < 0 && sql.indexOf('\'') < 0
                ? new LexicalRewrite(sql, false, false) : rewriteLexicalText(sql, true);
    }

    private static LexicalRewrite rewriteLexicalText(String sql, boolean maskLiterals) {
        StringBuilder rewritten = new StringBuilder(sql.length());
        char quote = 0;
        boolean dollarQuoted = false;
        boolean oracleQuoted = false;
        Matcher quotedIdentifier = QUOTED_IDENTIFIER.matcher(sql);
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (quote != 0) {
                if (quote == current && (quote == '`' || !isEscaped(sql, i))) {
                    quote = 0;
                }
            } else if (current == '`' || ((current == '\'' || current == '"') && !isEscaped(sql, i))) {
                quote = current;
            } else if (current == '-' && next == '-') {
                int end = i + 2;
                while (end < sql.length() && sql.charAt(end) != '\n' && sql.charAt(end) != '\r') {
                    end++;
                }
                if (maskLiterals) {
                    rewritten.append(sql, i, end);
                } else {
                    rewritten.append(' ');
                }
                i = end - 1;
                continue;
            } else if (current == '/' && next == '*') {
                int closing = sql.indexOf("*/", i + 2);
                int end = closing < 0 ? sql.length() : closing + 2;
                if (maskLiterals) {
                    rewritten.append(sql, i, end);
                } else {
                    rewritten.append("  ");
                }
                i = end - 1;
                continue;
            } else if (maskLiterals && current == '[' && isIdentifierStart(next)
                    && quotedIdentifier.region(i, sql.length()).lookingAt()) {
                int end = quotedIdentifier.end();
                rewritten.append(sql, i, end);
                i = end - 1;
                continue;
            } else if (maskLiterals && current == '$') {
                int end = SimpleStatementSplitter.dollarQuotedEnd(sql, i, false);
                if (end > i) {
                    rewritten.append("''");
                    i = end - 1;
                    dollarQuoted = true;
                    continue;
                }
            } else if (maskLiterals && (current == 'q' || current == 'Q' || current == 'n' || current == 'N')) {
                int end = OracleQuotedLiterals.endOfClosedLiteral(sql, i);
                if (end > i) {
                    rewritten.append("''");
                    i = end - 1;
                    oracleQuoted = true;
                    continue;
                }
            }
            rewritten.append(current);
        }
        String text = !maskLiterals || dollarQuoted || oracleQuoted ? rewritten.toString() : sql;
        return new LexicalRewrite(text, dollarQuoted, oracleQuoted);
    }

    private static final class LexicalRewrite {
        private final String text;
        private final boolean dollarQuoted;
        private final boolean oracleQuoted;

        private LexicalRewrite(String text, boolean dollarQuoted, boolean oracleQuoted) {
            this.text = text;
            this.dollarQuoted = dollarQuoted;
            this.oracleQuoted = oracleQuoted;
        }
    }

    private static boolean isEscaped(String sql, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && sql.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }
}
