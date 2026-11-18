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
    static final double GENERIC_SPARK_CONFIDENCE = 0.50;

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

    private static final Pattern STARROCKS_WAREHOUSE = Pattern.compile(
            "^\\s*(?:(?:create|alter|drop|suspend|resume)\\s+warehouse|show\\s+warehouses)\\b");
    private static final Pattern STARROCKS_PRIMARY_KEY_TABLE = Pattern.compile(
            "^\\s*create\\s+(?:temporary\\s+)?table\\b[^;]*\\)\\s*primary\\s+key\\s*\\([^;)]*\\)"
                    + "[^;]*\\bdistributed\\s+by\\s+hash\\s*\\(");
    private static final Pattern STARROCKS_FORMATTED_OUTFILE = Pattern.compile(
            "^\\s*(?:select|with)\\b[^;]*\\binto\\s+outfile\\s+format\\s+as\\b");

    private static final Pattern STARROCKS_LOAD_COMMAND = Pattern.compile(
            "^\\s*(?:load\\s+label|(?:create|alter|pause|resume|stop)\\s+routine\\s+load)\\b");

    private static final Pattern STARROCKS_JOB_METADATA = Pattern.compile(
            "^\\s*show\\s+(?:alter\\s+(?:table\\s+(?:column|optimize|rollup)|materialized\\s+view)|export"
                    + "|analyze\\s+(?:job|status)|all\\s+routine\\s+load)\\b");

    private static final Pattern STARROCKS_CLUSTER_METADATA = Pattern.compile(
            "^\\s*show\\s+(?:(?:backend|compute\\s+node)\\s+blacklist"
                    + "|backends|frontends|compute\\s+nodes|broker|resource\\s+groups?|resources"
                    + "|backup|repositories|snapshot\\s+on|restore(?=\\s+(?:from|in)\\b|\\s*(?:;|$)))\\b");

    private static final Pattern STARROCKS_ADMIN_METADATA = Pattern.compile(
            "^\\s*show\\s+(?:(?:all\\s+)?authentication|(?:multiple\\s+columns\\s+)?stats\\s+meta"
                    + "|histogram\\s+meta|dynamic\\s+partition\\s+tables|running\\s+queries|sqlblacklist"
                    + "|transaction|property|data|delete|file)\\b");

    private static final Pattern STARROCKS_CLUSTER_ADMIN = Pattern.compile(
            "^\\s*(?:alter\\s+system\\s+(?:(?:add|drop)\\s+(?:backend|compute\\s+node|follower|observer|broker)"
                    + "|drop\\s+all\\s+broker|decommission\\s+backend|create\\s+image)"
                    + "|admin\\s+(?:check\\s+tablet|set\\s+(?:frontend\\s+config|replica\\s+status|partition\\s+version)"
                    + "|skip\\s+committed\\s+transaction)|show\\s+usage\\s+resource\\s+groups)\\b");

    private static final Pattern STARROCKS_EXTERNAL_CATALOG =
            Pattern.compile("^\\s*create\\s+external\\s+catalog\\b");

    private static final Pattern STARROCKS_DICTIONARY_DDL = Pattern.compile(
            "^\\s*(?:create|show|drop|(?:cancel\\s+)?refresh)\\s+dictionary\\b");

    private static final Pattern STARROCKS_PIPE_OR_STORAGE_VOLUME = Pattern.compile(
            "^\\s*(?:(?:create(?:\\s+or\\s+replace)?|alter|drop)\\s+pipe"
                    + "|show\\s+pipes|(?:create|alter|drop|desc(?:ribe)?)\\s+storage\\s+volume"
                    + "|show\\s+storage\\s+volumes|set\\s+default\\s+storage\\s+volume)\\b");

    private static final Pattern STARROCKS_NATIVE_INDEX = Pattern.compile(
            "^\\s*(?:create\\s+index\\b|(?:create|alter)\\s+table\\b[^;]*"
                    + "(?:[,(]\\s*|\\badd\\s+)index\\s+\\w+\\s*\\()[^;]*"
                    + "\\)\\s+using\\s+(?:bitmap|ngrambf|gin|vector)\\b");

    private static final Pattern STARROCKS_JOIN = Pattern.compile(
            "\\basof\\s+(?:inner|left(?:\\s+outer)?)\\s+join\\b"
                    + "|\\bright\\s+(?:semi|anti)\\s+join\\b|\\bnull\\s+aware\\s+left\\s+anti\\s+join\\b");
    private static final Pattern STARROCKS_ASOF_ON = Pattern.compile(
            "\\basof\\s+join\\b[^;]*\\bon\\b");

    private static final Pattern MYSQL_DUPLICATE_KEY_UPDATE = Pattern.compile(
            "\\bon\\s+duplicate\\s+key\\s+update\\b");

    private static final Pattern ORACLE_START_WITH = Pattern.compile("\\bstart\\s+with\\b");

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
    private static final Pattern HIVE_PARTITION_OR_VIEW_DDL = Pattern.compile(
            "^\\s*(?:alter\\s+table\\b[^;]*\\bexchange\\s+partition\\s*\\([^;)]*="
                    + "|alter\\s+materialized\\s+view\\b[^;]*\\b(?:enable|disable)\\s+rewrite\\b)");
    private static final Pattern HIVE_OWNER_OR_PRIVILEGE_METADATA = Pattern.compile(
            "^\\s*(?:alter\\s+(?:database|schema)\\b[^;]*\\bset\\s+owner\\s+(?:user|role)"
                    + "|show\\s+grant\\s+(?:user|role)\\b[^;]*\\bon\\s+(?:table|database|schema))\\b");
    private static final Pattern HIVE_ROLE_METADATA = Pattern.compile(
            "^\\s*show\\s+(?:current\\s+roles|principals|role\\s+grant\\s+(?:user|role))\\b");
    private static final Pattern HIVE_EXPLAIN_OR_TRANSACTIONS = Pattern.compile(
            "^\\s*(?:explain\\s+(?:(?:formatted|extended)\\s+)?(?:authorization|dependency)"
                    + "|show\\s+transactions)\\b");
    private static final Pattern HIVE_SHOW_INDEX_ON =
            Pattern.compile("^\\s*show\\s+(?:formatted\\s+)?index(?:es)?\\s+on\\b");
    private static final Pattern HIVE_LOCK_OR_COMPACTION = Pattern.compile(
            "^\\s*(?:lock\\s+table\\b[^;]+\\b(?:shared|exclusive)\\s*(?:;|$)"
                    + "|unlock\\s+table\\b|show\\s+(?:locks|compactions)\\b|reload\\s+function\\b)");
    private static final Pattern FLINK_DISTRIBUTION_OR_WATERMARK = Pattern.compile(
            "^\\s*alter\\s+table\\b[^;]+\\b(?:drop\\s+watermark\\s*(?:;|$)"
                    + "|(?:add|modify)\\s+distribution\\b[^;]*\\binto\\s+[0-9]+\\s+buckets\\b)");
    private static final Pattern FLINK_SHOW_CURRENT_CATALOG = Pattern.compile("^\\s*show\\s+current\\s+catalog\\b");
    private static final Pattern FLINK_DROP_TEMPORARY_VIEW = Pattern.compile("^\\s*drop\\s+temporary\\s+view\\b");
    private static final Pattern FLINK_PARTITION_OPTIONS = Pattern.compile(
            "^\\s*alter\\s+table\\b[^;]*\\badd\\s+(?:if\\s+not\\s+exists\\s+)?"
                    + "partition\\s*\\([^;)]*\\)\\s+with\\s*\\(\\s*=");
    private static final Pattern FLINK_DESCRIBE_CATALOG =
            Pattern.compile("^\\s*desc(?:ribe)?\\s+catalog\\s+extended\\b");
    private static final Pattern FLINK_FILTERED_METADATA = Pattern.compile(
            "^\\s*show\\s+(?:(?:columns|views)\\b[^;]*\\bnot\\s+like"
                    + "|procedures\\s+(?:from|in)\\b[^;]*\\b(?:i?like))\\b");
    private static final Pattern FLINK_FUNCTION_ARTIFACT = Pattern.compile(
            "^\\s*create\\s+(?:temporary\\s+(?:system\\s+)?)?function\\b[^;]*\\busing\\s+artifact\\b");
    private static final Pattern HIVE_TABLE_VALUES = Pattern.compile(
            "\\b(?:from|lateral)\\s+table\\s*\\(\\s*values\\s*\\(");
    private static final String FLINK_SESSION_KEY =
            "(?:execution\\.runtime-mode|pipeline\\.(?:name|max-parallelism)"
                    + "|sql-client\\.execution\\.result-mode|table\\.local-time-zone|table\\.exec\\.[a-z0-9.-]+)";
    private static final String FLINK_SESSION_KEY_TOKEN =
            "(?:'" + FLINK_SESSION_KEY + "'|\"" + FLINK_SESSION_KEY + "\"|" + FLINK_SESSION_KEY + ")";
    private static final Pattern FLINK_SESSION_OPTION = Pattern.compile(
            "^\\s*(?:set\\s+" + FLINK_SESSION_KEY_TOKEN + "\\s*="
                    + "|reset\\s+" + FLINK_SESSION_KEY_TOKEN + "(?=\\s*(?:;|$)))");
    private static final Pattern SESSION_OPTION_COMMAND = Pattern.compile("\\b(?:set|reset)\\b");
    private static final Pattern FLINK_EXECUTE_QUERY = Pattern.compile(
            "^\\s*(?:explain\\s+(?:plan\\s+for\\s+)?)?execute\\s+(?:select|with)\\b");
    private static final Pattern FLINK_EXECUTE_INSERT = Pattern.compile(
            "^\\s*execute\\s+insert\\s+(?:into|overwrite)\\b");
    private static final Pattern FLINK_MODULE_OR_SYSTEM_FUNCTION = Pattern.compile(
            "^\\s*(?:(?:load|unload)\\s+module|use\\s+modules|show\\s+(?:full\\s+)?modules"
                    + "|(?:create|alter|drop)\\s+temporary\\s+system\\s+function)\\b");
    private static final Pattern FLINK_MODEL_DDL = Pattern.compile(
            "^\\s*(?:(?:(?:create|drop)\\s+(?:temporary\\s+)?|alter\\s+|(?:describe|desc)\\s+"
                    + "|show\\s+create\\s+)model|show\\s+models)\\b");
    private static final Pattern FLINK_MODEL_INFERENCE = Pattern.compile(
            "(?:\\b(?:from|join)\\s+|,\\s*)(?:lateral\\s+)?(?:table\\s*\\(\\s*)?"
                    + "ml_predict\\s*\\(\\s*table\\s+[^,;()]+,\\s*model\\b");
    private static final Pattern FLINK_CHANGELOG_FUNCTION = Pattern.compile(
            "(?:\\b(?:from|join)\\s+|,\\s*)(?:lateral\\s+)?(?:table\\s*\\(\\s*)?"
                    + "(?:from|to)_changelog\\s*\\(\\s*(?:input\\s*=>\\s*)?table\\b");
    private static final Pattern FLINK_EXPLAIN_OR_JAR = Pattern.compile(
            "^\\s*(?:explain\\s+(?:estimated_cost|changelog_mode|plan_advice|json_execution_plan)"
                    + "|show\\s+jars|remove\\s+jar)\\b");
    private static final Pattern FLINK_MATERIALIZED_TABLE = Pattern.compile(
            "^\\s*(?:(?:show\\s+)?create(?:\\s+or\\s+alter)?|alter|drop)\\s+materialized\\s+table\\b"
                    + "|^\\s*show\\s+materialized\\s+tables\\b");
    private static final Pattern POSTGRES_TABLE_LIKE_OPTIONS = Pattern.compile(
            "^\\s*create\\s+(?:(?:global|local)\\s+)?(?:(?:temp|temporary|unlogged)\\s+)?table\\b"
                    + "[^;]*[(,]\\s*like\\s+" + POSTGRES_RELATION + "\\s+(?:including|excluding)\\b");
    private static final Pattern POSTGRES_KEY_CONSTRAINT = Pattern.compile(
            "^\\s*(?:create|alter)\\s+table\\b[^;]*"
                    + "(?:\\bwithout\\s+overlaps\\b|\\bunique\\s+nulls\\s+not\\s+distinct\\b"
                    + "|\\bforeign\\s+key\\s*\\([^)]*,\\s*period\\s+[^)]+\\)\\s+references\\b)");
    private static final Pattern POSTGRES_FOREIGN_KEY_OPTIONS = Pattern.compile(
            "^\\s*(?:create|alter)\\s+table\\b[^;]*\\breferences\\b[^;]*"
                    + "(?:\\bon\\s+delete\\s+set\\s+(?:null|default)\\s*\\(|\\bnot\\s+valid\\b)");
    private static final Pattern POSTGRES_SCHEMA_DDL = Pattern.compile(
            "^\\s*(?:create|alter|drop)\\s+event\\s+trigger\\b"
                    + "|^\\s*(?:create|alter|drop)\\s+(?:foreign\\s+(?:table|data\\s+wrapper)|user\\s+mapping)\\b"
                    + "|^\\s*import\\s+foreign\\s+schema\\b"
                    + "|^\\s*(?:create|alter|drop)\\s+extension\\b"
                    + "|^\\s*create\\s+type\\b[^;]*\\bas\\s+(?:enum|range)\\s*\\("
                    + "|^\\s*alter\\s+type\\b[^;]*\\b(?:add|rename)\\s+value\\b"
                    + "|^\\s*alter\\s+domain\\b[^;]*\\b(?:validate\\s+constraint"
                    + "|(?:set|drop)\\s+(?:default|not\\s+null)|not\\s+valid)\\b");
    private static final Pattern POSTGRES_DISTINCT_ON = Pattern.compile(
            "\\bselect\\s+distinct\\s+on\\s*\\(");
    private static final Pattern POSTGRES_CTE_MATERIALIZATION = Pattern.compile(
            "^\\s*with\\s+(?:recursive\\s+)?" + POSTGRES_NAME
                    + "(?:\\s+|\\s*\\([^()]*\\)\\s*)as\\s+materialized\\s*\\("
                    + "|\\bas\\s+not\\s+materialized\\s*\\(");
    private static final Pattern POSTGRES_DATA_MATERIALIZATION = Pattern.compile(
            "^\\s*create\\s+unlogged\\s+table\\b"
                    + "|^\\s*refresh\\s+materialized\\s+view\\s+concurrently\\b");
    private static final Pattern POSTGRES_DATA_OPTION_STATEMENT = Pattern.compile(
            "^\\s*create\\s+(?:(?:(?:global|local)\\s+)?(?:temporary|temp)\\s+)?"
                    + "(?:table|materialized\\s+view)\\b[^;]*\\bas\\b"
                    + "|^\\s*refresh\\s+materialized\\s+view\\b");
    private static final Pattern POSTGRES_DATA_OPTION_END = Pattern.compile(
            "\\bwith\\s+(?:no\\s+)?data\\s*;?\\s*$");
    private static final Pattern POSTGRES_DML_TARGET = Pattern.compile(
            "\\b(?:delete\\s+from\\s+|update\\s+(?!set\\b))(?:only\\s+(?:\\(\\s*" + POSTGRES_RELATION + "\\s*\\)"
                    + "|" + POSTGRES_RELATION + "(?:\\s+(?:as\\s+)?" + POSTGRES_NAME + ")?"
                    + "(?=\\s+(?:using|where|set|returning)\\b|\\s*(?:[;)]|$)))"
                    + "|" + POSTGRES_RELATION + "\\s*\\*(?=\\s|;|$))");
    private static final String TSQL_TEMP_NAME = "#{1,2}[\\p{L}_][\\p{L}\\p{N}_$]*(?![#\\p{L}\\p{N}_$])";
    private static final Pattern TSQL_TEMP_RELATION = Pattern.compile(
            "^\\s*(?:create\\s+table\\s+" + TSQL_TEMP_NAME + "\\s*\\("
                    + "|drop\\s+table\\s+(?:if\\s+exists\\s+)?" + TSQL_TEMP_NAME + "(?=\\s*(?:[,;]|$))"
                    + "|insert\\s+(?:into\\s+)?" + TSQL_TEMP_NAME + "(?=\\s*(?:\\(|select|values|default|exec)\\b|\\s*\\()"
                    + "|(?:select|with)\\b[^;]*\\binto\\s+" + TSQL_TEMP_NAME + "\\s+from\\b"
                    + "|(?:select|with|delete)\\b[^;]*\\b(?:from|join)\\s+" + TSQL_TEMP_NAME
                    + "(?=\\s*(?:[,);]|$)|\\s+(?:as|where|join|order|group|union)\\b))");
    private static final Pattern TSQL_INDEX_OR_DBCC = Pattern.compile(
            "^\\s*(?:create\\s+(?:unique\\s+)?(?:clustered|nonclustered)\\s+(?:columnstore\\s+)?index"
                    + "|dbcc\\s+(?:checkdb|checktable|checkident|show_statistics))\\b");
    private static final String TSQL_EXEC_IDENTIFIER = "[\\p{L}_#][\\p{L}\\p{N}_#$]*";
    private static final String TSQL_EXEC_PATH =
            TSQL_EXEC_IDENTIFIER + "(?:\\s*\\.\\s*" + TSQL_EXEC_IDENTIFIER + ")*";
    private static final String TSQL_EXEC_PREFIX = "^\\s*exec(?:ute)?\\s+(?:@[\\w$]+\\s*=\\s*)?";
    private static final Pattern TSQL_EXEC_ROUTINE = Pattern.compile(
            TSQL_EXEC_PREFIX + "(?:" + TSQL_EXEC_IDENTIFIER + "\\s*\\.\\s*)*"
                    + "sp_(?:columns|help(?:text|index|constraint|db|server)?|tables|spaceused|who"
                    + "|describe_first_result_set)\\b(?!\\s*\\()"
                    + "|" + TSQL_EXEC_PREFIX + TSQL_EXEC_PATH + "\\s+[^;]*@[\\w$]+\\s*="
                    + "|^\\s*insert\\s+(?:into\\s+)?" + TSQL_EXEC_PATH
                    + "(?:\\s*\\([^()]*\\))?\\s+exec(?:ute)?\\b");
    private static final Pattern POSTGRES_TABLE_INHERITANCE = Pattern.compile(
            "^\\s*table\\s+(?:only(?:\\s*\\(\\s*" + POSTGRES_RELATION + "\\s*\\)|\\s+" + POSTGRES_RELATION + ")"
                    + "|" + POSTGRES_RELATION + "\\s*\\*)(?=\\s|;|$)");
    private static final Pattern POSTGRES_ROW_OVERLAPS = Pattern.compile(
            "\\)\\s*overlaps\\s*(?:row\\s*)?\\(");
    private static final Pattern POSTGRES_ROW_LOCKING = Pattern.compile(
            "\\bfor\\s+(?:no\\s+key\\s+update|key\\s+share)\\b");
    private static final Pattern POSTGRES_ALIASED_DELETE_USING = Pattern.compile(
            "\\bdelete\\s+from\\s+" + POSTGRES_RELATION + "\\s+as\\s+" + POSTGRES_NAME + "\\s+using\\b");
    private static final Pattern POSTGRES_DATA_MODIFYING_CTE = Pattern.compile(
            "^\\s*with\\b[^;]*\\bas\\s*\\(\\s*(?:delete\\s+from|insert\\s+into|update|merge\\s+into)\\b");
    private static final Pattern POSTGRES_JOIN_USING_ALIAS = Pattern.compile(
            "\\bjoin\\b[^;]*\\busing\\s*\\(\\s*" + POSTGRES_NAME
                    + "(?:\\s*,\\s*" + POSTGRES_NAME + ")*\\s*\\)\\s+as\\s+" + POSTGRES_NAME);
    private static final Pattern POSTGRES_ROWS_FROM = Pattern.compile(
            "(?:\\b(?:from|join)\\b|,)\\s*(?:lateral\\s+)?rows\\s+from\\s*\\(");

    private static final Pattern POSTGRES_VECTOR_INDEX = Pattern.compile(
            "^\\s*create\\s+index\\b[^;]*\\bon\\b[^;]*\\busing\\s+(?:hnsw|ivfflat)\\b");
    private static final Pattern ORACLE_VECTOR_SERIALIZER = Pattern.compile(
            "\\b(?:vector_serialize|from_vector)\\s*\\([^;]*?\\b(?:returning\\s+(?:clob|varchar2)|format\\s+(?:sparse|dense))\\b");
    private static final Pattern SQL_SERVER_VECTOR_FLOAT16 = Pattern.compile(
            "(?s)^\\s*(?:create|alter)\\s+table\\b[^;]*?\\bvector\\s*\\(\\s*[0-9]+\\s*,\\s*float16\\s*\\)");
    private static final Pattern POSTGRES_VECTOR_CAST = Pattern.compile(
            "::\\s*(?:[a-z_][\\w$]*\\s*\\.\\s*)?(?:vector|halfvec|sparsevec)\\b");
    private static final Pattern SHARED_VECTOR_OPERATOR = Pattern.compile("<[-#]>");
    private static final Pattern STARROCKS_RECURSION_SETTINGS = Pattern.compile(
            "\\bset\\s+(?:(?:global|session)\\s+)?(?:enable_recursive_cte|recursive_cte_max_depth)\\s*=");
    private static final Pattern MYSQL_RECURSION_SETTING = Pattern.compile(
            "\\bset\\s+(?:(?:global|session)\\s+)?cte_max_recursion_depth\\s*=");
    private static final Pattern ORACLE_RECURSIVE_CTE_OPTIONS = Pattern.compile(
            "\\bwith\\s+(?!recursive\\b)[^;]*?\\)\\s*(?:search\\s+(?:breadth|depth)\\s+first\\s+by\\b"
                    + "|cycle\\s+[a-z_][\\w$#]*(?:\\s*,\\s*[a-z_][\\w$#]*)*\\s+set\\s+[a-z_][\\w$#]*\\s+to\\b)");
    private static final Pattern POSTGRES_RECURSIVE_CTE_OPTIONS = Pattern.compile(
            "\\bwith\\s+recursive\\b[^;]*?\\)\\s*(?:search\\s+(?:breadth|depth)\\s+first\\s+by\\b"
                    + "|cycle\\s+[a-z_][\\w$]*(?:\\s*,\\s*[a-z_][\\w$]*)*\\s+set\\b)");
    private static final Pattern SPARK_RECURSION_LEVEL = Pattern.compile(
            "(?s)\\bwith\\s+recursive\\b[^;]*?\\bmax\\s+recursion\\s+level\\s+[0-9]+\\s+as\\b");
    private static final Pattern SQL_SERVER_AI_MODEL = Pattern.compile(
            "^\\s*(?:create|alter|drop)\\s+external\\s+model\\b"
                    + "|\\bai_generate_chunks\\s*\\(\\s*source\\s*="
                    + "|(?s)\\bai_generate_embeddings\\s*\\([^;]*?\\buse\\s+model\\b");
    private static final Pattern SQL_SERVER_VECTOR_QUERY = Pattern.compile(
            "\\bvector_search\\s*\\(\\s*table\\s*=|\\btop\\s*(?:\\([^)]*\\)|[0-9]+)\\s+with\\s+approximate\\b");
    private static final Pattern CREATE_VECTOR_INDEX = Pattern.compile("^\\s*create\\s+vector\\s+index\\b");
    private static final Pattern ORACLE_VECTOR_SCHEMA = Pattern.compile(
            "(?s)^\\s*create\\s+vector\\s+index\\b.*?\\borganization\\s+(?:inmemory|neighbor|partitions)\\b"
                    + "|(?s)^\\s*(?:create|alter)\\s+table\\b.*?\\bvector\\s*\\(\\s*(?:\\*|[0-9]+)\\s*,"
                    + "\\s*(?:float32|float64|int8|binary|\\*\\s*,\\s*(?:dense|sparse))\\b");
    private static final Pattern JSON_TABLE_CALL = Pattern.compile("\\bjson_table\\s*\\(");
    private static final Pattern JSON_TABLE_STRUCTURE = Pattern.compile("\\bcolumns\\s*\\(|[();]");
    private static final Pattern ORACLE_JSON_TABLE_COLUMN_TYPE = Pattern.compile(
            "[,(]\\s*" + ORACLE_NAME + "\\s+(?:number|varchar2|nvarchar2|binary_float|binary_double)\\b");
    private static final Pattern JSON_SCALAR_CALL = Pattern.compile("\\bjson_(?:value|query)\\s*\\(");
    private static final Pattern ORACLE_JSON_RETURNING_STRUCTURE = Pattern.compile(
            "\\breturning\\s+(?:number|varchar2|nvarchar2|clob|nclob|blob|binary_float|binary_double)\\b|[();]");
    private static final Pattern ORACLE_JSON_AGGREGATE = Pattern.compile("\\bjson_(?:arrayagg|objectagg)\\s*\\(");
    private static final Pattern ORACLE_JSON_AGGREGATE_OPTION = Pattern.compile(
            "\\breturning\\s+(?:clob|blob|varchar2)\\b|\\bstrict\\s*(?:with\\s+unique\\s+keys\\s*)?\\)");
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
    private static final String ORACLE_DOMAIN_NAME = "[a-z_][\\w$#]*(?:\\s*\\.\\s*[a-z_][\\w$#]*)?";
    private static final Pattern POSTGRES_DROP_DOMAIN = Pattern.compile("^\\s*drop\\s+domain\\b");
    private static final Pattern ORACLE_DOMAIN_DDL = Pattern.compile(
            "^\\s*(?:create|alter|drop)\\s+usecase\\s+domain\\b"
                    + "|^\\s*create\\s+domain\\s+(?:if\\s+not\\s+exists\\b|[^;]*\\bas\\s+(?:enum\\s*)?\\()"
                    + "|^\\s*alter\\s+domain\\s+(?:if\\s+exists\\b|[^;]*\\b(?:(?:add|modify|drop)\\s+"
                    + "(?:display|order)\\b|annotations\\s*\\())"
                    + "|^\\s*drop\\s+domain\\s+usecase\\s+(?:if\\s+exists\\s+)?" + ORACLE_DOMAIN_NAME
                    + "\\s*(?=force\\b|;|$)"
                    + "|^\\s*drop\\s+domain\\s+(?:if\\s+exists\\s+)?" + ORACLE_DOMAIN_NAME
                    + "\\s+force(?:\\s+preserve)?\\s*(?=;|$)");
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

    // Possessive repetition scans long quoted tokens without recursive backtracking.
    private static final String BACKTICK_IDENTIFIER = "`(?:``|[^`])*+`";
    private static final String DOUBLE_QUOTED_IDENTIFIER = "\"(?:\"\"|[^\"])*+\"";
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile(
            BACKTICK_IDENTIFIER + "|" + DOUBLE_QUOTED_IDENTIFIER + "|\\[(?:\\]\\]|[^\\]])*+\\]");
    private static final String SINGLE_QUOTED_STRING = "'(?:''|\\\\.|[^'\\\\])*+'";
    private static final String POSTGRES_COPY_IDENTIFIER = "(?:" + POSTGRES_NAME + "|" + DOUBLE_QUOTED_IDENTIFIER + ")";
    private static final Pattern POSTGRES_COPY_COMMAND = Pattern.compile(
            "^\\s*copy\\b\\s*(?:\\(\\s*(?:select|with|table|values|insert|update|delete|merge)\\b"
                    + "|(?!into\\b)" + POSTGRES_COPY_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + POSTGRES_COPY_IDENTIFIER + ")*"
                    + "(?:\\s*\\(\\s*" + POSTGRES_COPY_IDENTIFIER
                    + "(?:\\s*,\\s*" + POSTGRES_COPY_IDENTIFIER + ")*\\s*\\))?"
                    + "\\s+(?:from|to)\\s+(?:(?:program|stdin|stdout)\\b|" + SINGLE_QUOTED_STRING + "))");
    private static final Pattern POSTGRES_DISCARD = Pattern.compile(
            "^\\s*discard\\s+(?:all|plans|sequences|temp(?:orary)?)\\s*;?\\s*$");
    private static final Pattern POSTGRES_SCHEMA_CONTROL = Pattern.compile(
            "^\\s*(?:set\\s+(?:(?:session|local)\\s+)?(?:search_path\\s*(?:to\\b|=)"
                    + "|schema\\s+" + SINGLE_QUOTED_STRING + ")|reset\\s+search_path\\b)");
    private static final Pattern POSTGRES_TWO_PHASE_TRANSACTION = Pattern.compile(
            "^\\s*(?:prepare\\s+transaction|(?:commit|rollback)\\s+prepared)\\s+e?"
                    + SINGLE_QUOTED_STRING + "\\s*;?\\s*$");
    private static final Pattern POSTGRES_PREPARED_STATEMENT = Pattern.compile(
            "^\\s*prepare\\s+(?:[a-z_][\\w$]*|" + DOUBLE_QUOTED_IDENTIFIER + ")"
                    + "(?:\\s*\\([^;]*\\)\\s*|\\s+)as\\s+(?:select|values|table|with|insert|update|delete|merge)\\b");
    private static final Pattern POSTGRES_MAINTENANCE = Pattern.compile(
            "^\\s*(?:vacuum\\s*\\(|analyze\\s*(?:\\(|\\s+verbose\\b)|analyse\\b"
                    + "|reindex\\s*(?:\\(|\\s+(?:index|table|schema|database|system)\\b)"
                    + "|cluster\\b(?!\\s+by\\b))");
    private static final Pattern POSTGRES_EXPLAIN_OPTIONS = Pattern.compile(
            "^\\s*explain\\s*\\(\\s*(?:(?:analyze|analyse|verbose|costs|settings|generic_plan|buffers"
                    + "|serialize|wal|timing|summary|memory|format)\\b"
                    + "|[^()]*,\\s*(?:generic_plan|serialize|memory)\\b)");
    private static final Pattern POSTGRES_EXPLAIN_CURSOR = Pattern.compile(
            "^\\s*explain\\s+(?:\\([^()]*\\)\\s*)?declare\\b");
    private static final Pattern POSTGRES_PREPARED_RESET = Pattern.compile(
            "^\\s*(?:deallocate\\s+(?:prepare\\s+)?all|discard\\s+(?:all|plans))\\b");
    private static final Pattern STARROCKS_DICTIONARY_LOOKUP_OR_QUOTE = Pattern.compile(
            "(\\b(?:dictionary_get|dict_mapping)\\s*\\(\\s*" + SINGLE_QUOTED_STRING + "\\s*,)"
                    + "|" + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern());
    private static final Pattern SQL_SERVER_VECTOR_METRIC_OR_QUOTE = Pattern.compile(
            "(\\bvector_distance\\s*\\(\\s*n?'(?:cosine|dot|euclidean)'\\s*,)|"
                    + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern());

    private static final String PREPARED_STATEMENT_NAME = "(?:[a-z_][\\w$]*|" + BACKTICK_IDENTIFIER + ")";
    private static final Pattern MYSQL_PREPARED_LITERAL = Pattern.compile(
            "^\\s*prepare\\s+" + PREPARED_STATEMENT_NAME + "\\s+from\\s+(?:(?:n|_[a-z0-9]+)?"
                    + SINGLE_QUOTED_STRING + "|\"|@[a-z_][\\w$]*)");
    private static final Pattern STARROCKS_PREPARED_QUERY = Pattern.compile(
            "^\\s*prepare\\s+" + PREPARED_STATEMENT_NAME + "\\s+from\\s+(?:select|with)\\b");
    private static final String FLINK_OPTION_LITERAL =
            "(?:" + SINGLE_QUOTED_STRING + "|\"(?:\"\"|[^\"\\\\]|\\\\.)*+\")";
    private static final Pattern SQL_WITH_OPTIONS_OR_QUOTE = Pattern.compile(
            "(\\bwith\\s*\\()|" + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern());
    private static final Pattern FLINK_OPTION_PAIR = Pattern.compile(
            "\\s*(" + FLINK_OPTION_LITERAL + ")\\s*=\\s*" + FLINK_OPTION_LITERAL + "\\s*,?");
    private static final String SQL_IDENTIFIER =
            "(?:[a-z_][\\w$]*|" + BACKTICK_IDENTIFIER + "|" + DOUBLE_QUOTED_IDENTIFIER + ")";
    private static final String ORACLE_RELATION_IDENTIFIER = "(?:" + ORACLE_NAME + "|" + DOUBLE_QUOTED_IDENTIFIER + ")";
    private static final Pattern ORACLE_RELATION_OPTION_OR_QUOTE = Pattern.compile(
            "(\\b(?:from|join)\\s+" + ORACLE_RELATION_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + ORACLE_RELATION_IDENTIFIER + ")*"
                    + "\\s+(?:(?:sub)?partition|sample(?:\\s+block)?)\\s*\\()"
                    + "|" + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern());
    private static final Pattern STARROCKS_DATABASE_OPTIONS = Pattern.compile(
            "^\\s*alter\\s+database\\s+" + SQL_IDENTIFIER
                    + "\\s+(?:set\\s+(?:data|replica)\\s+quota\\b|rename\\s+(?!to\\b)" + SQL_IDENTIFIER
                    + "(?=\\s*(?:;|$)))");
    private static final Pattern STARROCKS_NATIVE_METADATA = Pattern.compile(
            "^\\s*(?:desc(?:ribe)?\\s+files\\s*\\("
                    + "|show\\s+(?:(?:temporary\\s+)?partitions\\s+from\\b|full\\s+builtin\\s+functions\\b"
                    + "|catalogs(?:\\s+like\\s+(?:" + SINGLE_QUOTED_STRING + "|" + DOUBLE_QUOTED_IDENTIFIER + "))?"
                    + "\\s*(?:\\\\g(?=\\s*(?:;|$))|\\b(?:order\\s+by|where|limit)\\b)))");
    private static final Pattern STARROCKS_FILTERED_MATERIALIZED_VIEWS = Pattern.compile(
            "^\\s*show\\s+materialized\\s+views?"
                    + "(?:\\s+(?:from|in)\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*)?"
                    + "(?:\\s+like\\s+(?:" + SINGLE_QUOTED_STRING + "|" + DOUBLE_QUOTED_IDENTIFIER + "))?\\s+where\\b");
    private static final String HIVE_TABLE_IDENTIFIER = "(?:[\\p{L}_][\\p{L}\\p{N}_$]*|" + BACKTICK_IDENTIFIER + ")";
    private static final Pattern HIVE_DATA_CONNECTOR = Pattern.compile(
            "^\\s*(?:(?:create|alter|drop)\\s+connector|desc(?:ribe)?\\s+connector"
                    + "|show\\s+connectors|create\\s+remote\\s+(?:database|schema))\\b");
    private static final Pattern HIVE_BARE_COLUMN_FILTER = Pattern.compile(
            "^\\s*show\\s+columns\\s+(?:from|in)\\s+" + HIVE_TABLE_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + HIVE_TABLE_IDENTIFIER + ")*"
                    + "(?:\\s+(?:from|in)\\s+" + HIVE_TABLE_IDENTIFIER + ")?"
                    + "\\s+(?:" + SINGLE_QUOTED_STRING + "|" + DOUBLE_QUOTED_IDENTIFIER + ")(?=\\s*(?:;|$))");
    private static final Pattern HIVE_FILTERED_PARTITIONS = Pattern.compile(
            "^\\s*show\\s+partitions\\s+(?!from\\b)" + HIVE_TABLE_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + HIVE_TABLE_IDENTIFIER + ")*"
                    + "(?:\\s+partition\\s*\\((?:" + SINGLE_QUOTED_STRING + "|" + DOUBLE_QUOTED_IDENTIFIER + "|" + BACKTICK_IDENTIFIER
                    + "|[^()'\\\"`;]++)*+\\))?\\s+(?:where\\b|order\\s+by\\b|limit\\s+\\d+\\b)");
    private static final Pattern HIVE_SET_FILEFORMAT = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + HIVE_TABLE_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + HIVE_TABLE_IDENTIFIER + ")*\\s+"
                    + "(?:partition\\s*\\([^;)]*\\)\\s+)?set\\s+fileformat\\b");
    private static final Pattern HIVE_PARTITION_MAINTENANCE = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + HIVE_TABLE_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + HIVE_TABLE_IDENTIFIER + ")*\\s+"
                    + "(?:(?:un)?archive\\s+partition\\s*\\("
                    + "|(?:partition\\s*\\([^;)]*\\)\\s+)?"
                    + "(?:(?:touch|concatenate)|compact\\s+and\\s+wait)\\s*(?:;|$))");

    private static final Pattern STARROCKS_USE_CATALOG_LITERAL = Pattern.compile(
            "^\\s*use\\s+(?:'catalog\\s+[^'\\s]+\\s*'|\"catalog\\s+[^\"\\s]+\\s*\")\\s*;?\\s*$");
    private static final Pattern STARROCKS_SHARED_CATALOG_SWITCH = Pattern.compile(
            "^\\s*(use|set)\\s+catalog\\s+" + SQL_IDENTIFIER + "\\s*;?\\s*$");
    private static final Pattern STARROCKS_BACKUP_RESTORE = Pattern.compile(
            "^\\s*(?:(?:backup|restore)\\s+(?:database\\s+" + SQL_IDENTIFIER + "\\s+)?snapshot"
                    + "|backup\\s+(?:all\\s+)?external\\s+catalogs?"
                    + "|create\\s+(?:read\\s+only\\s+)?repository|drop\\s+(?:repository|snapshot))\\b");
    private static final Pattern STARROCKS_DOUBLE_QUOTED_CATALOG_OPTIONS = Pattern.compile(
            "^\\s*alter\\s+catalog\\s+" + SQL_IDENTIFIER
                    + "\\s+(?:comment\\s+|(?:set|reset)\\s*\\(\\s*)" + DOUBLE_QUOTED_IDENTIFIER);
    private static final Pattern STARROCKS_BRACKET_JOIN_HINT = Pattern.compile(
            "\\bjoin\\s+\\[\\s*(?:broadcast|shuffle|bucket|colocate)\\s*\\]\\s+"
                    + SQL_IDENTIFIER + "\\s*\\.\\s*" + SQL_IDENTIFIER);
    private static final Pattern STARROCKS_MATERIALIZED_VIEW_OPTIONS = Pattern.compile(
            "^\\s*alter\\s+materialized\\s+view\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*\\s+"
                    + "(?:(?:active|inactive)(?=\\s*(?:;|$))|set\\s*\\("
                    + "|refresh\\s+(?:(?:immediate|deferred)\\s+)?(?:async|schedule|manual)\\b)");
    private static final Pattern STARROCKS_PARTITION_MAINTENANCE = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*\\s+"
                    + "(?:drop\\s+partitions\\s+(?:if\\s+exists\\s+)?(?:where\\b|start\\s*\\(|\\()"
                    + "|drop\\s+partition\\s+(?:if\\s+exists\\s+)?" + SQL_IDENTIFIER + "\\s+force\\b"
                    + "|add\\s+partition\\s+" + SQL_IDENTIFIER + "\\s+values\\s+(?:in\\s*\\(|\\[)"
                    + "|modify\\s+partition\\s+(?:" + SQL_IDENTIFIER + "|\\([^;)]*\\))\\s+set\\s*\\("
                    + "|partitions\\s+" + SQL_IDENTIFIER + "\\s+distributed\\s+by\\s+(?:hash|random)\\b"
                    + "|recover\\s+partition\\s+" + SQL_IDENTIFIER + ")");
    private static final String STARROCKS_TABLE_PROPERTY =
            "(?:dynamic_partition\\.[a-z_][\\w]*|bloom_filter_columns|colocate_with|replication_num|storage_medium)";
    private static final Pattern STARROCKS_TABLE_PROPERTIES = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*"
                    + "\\s+set\\s*\\(\\s*(?:'" + STARROCKS_TABLE_PROPERTY + "'|\"" + STARROCKS_TABLE_PROPERTY
                    + "\")\\s*=");
    private static final Pattern STARROCKS_ROLLUP_COLUMN_CHANGE = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*"
                    + "\\s+(?:add\\s+column\\b[^;]+?\\s+to|modify\\s+column\\b[^;]+?\\s+from)"
                    + "\\s+" + SQL_IDENTIFIER + "\\s+properties\\s*\\(");
    private static final Pattern STARROCKS_SCHEMA_MAINTENANCE = Pattern.compile(
            "^\\s*alter\\s+table\\s+" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*\\s+"
                    + "(?:(?:base|cumulative)\\s+compact\\b|drop\\s+persistent\\s+index\\s+on\\s+tablets\\b"
                    + "|distributed\\s+by\\s+hash\\s*\\([^;)]*\\)\\s+default\\s+buckets\\b"
                    + "|modify\\s+column\\s+" + SQL_IDENTIFIER + "\\s+(?:add|drop)\\s+field\\b"
                    + "|(?:enable|disable)\\s+dictionary\\s*\\("
                    + "|rename\\s+partition\\s+" + SQL_IDENTIFIER + "\\s+(?!to\\b)" + SQL_IDENTIFIER
                    + "(?=\\s*(?:;|$))"
                    + "|drop\\s+column\\s+" + SQL_IDENTIFIER + "\\s+from\\s+" + SQL_IDENTIFIER
                    + "|order\\s+by\\s*\\(\\s*" + SQL_IDENTIFIER
                    + "(?:\\s*,\\s*" + SQL_IDENTIFIER + ")*\\s*\\))");
    private static final Pattern STARROCKS_TABLE_MAINTENANCE = Pattern.compile(
            "^\\s*alter\\s+(?:table|materialized\\s+view)\\s+" + SQL_IDENTIFIER
                    + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*\\s+"
                    + "(?:(?:add|drop|rename)\\s+rollup|swap\\s+with|(?:split|merge)\\s+tablets?)\\b");
    private static final String FLINK_QUALIFIED_IDENTIFIER =
            SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*";
    private static final Pattern FLINK_BARE_COLUMN_RENAME = Pattern.compile(
            "^\\s*alter\\s+table\\s+(?:if\\s+exists\\s+)?" + FLINK_QUALIFIED_IDENTIFIER
                    + "\\s+rename\\s+" + SQL_IDENTIFIER + "\\s+to\\s+" + SQL_IDENTIFIER);
    private static final Pattern FLINK_JVM_FUNCTION = Pattern.compile(
            "^\\s*(?:create|alter)\\s+(?:temporary\\s+(?:system\\s+)?)?function\\s+"
                    + "(?:if\\s+(?:not\\s+)?exists\\s+)?" + FLINK_QUALIFIED_IDENTIFIER
                    + "\\s+as\\s+(?:" + SINGLE_QUOTED_STRING + "|" + FLINK_QUALIFIED_IDENTIFIER + ")"
                    + "\\s+language\\s+(?:java|scala|python)\\b");
    private static final String FLINK_STRING_OPTION_START = "\\s*\\(\\s*" + SINGLE_QUOTED_STRING;
    private static final Pattern FLINK_CREATE_DATABASE_OPTIONS = Pattern.compile(
            "^\\s*create\\s+database\\s+(?:if\\s+not\\s+exists\\s+)?" + FLINK_QUALIFIED_IDENTIFIER
                    + "(?:\\s+comment\\s+" + SINGLE_QUOTED_STRING + ")?"
                    + "\\s+with" + FLINK_STRING_OPTION_START + "\\s*=\\s*" + SINGLE_QUOTED_STRING);
    private static final Pattern FLINK_ALTER_STRING_OPTIONS = Pattern.compile(
            "^\\s*alter\\s+table\\s+(?:if\\s+exists\\s+)?" + FLINK_QUALIFIED_IDENTIFIER
                    + "\\s+(?:set|reset)" + FLINK_STRING_OPTION_START
                    + "|^\\s*alter\\s+database\\s+" + FLINK_QUALIFIED_IDENTIFIER + "\\s+set"
                    + FLINK_STRING_OPTION_START
                    + "|^\\s*alter\\s+catalog\\s+" + SQL_IDENTIFIER
                    + "\\s+(?:(?:set|reset)" + FLINK_STRING_OPTION_START
                    + "|comment\\s+" + SINGLE_QUOTED_STRING + ")");
    private static final Pattern STARROCKS_VARIABLE_HINT_OR_IGNORED_TOKEN = Pattern.compile(
            "(/\\*\\+\\s*set_var\\s*\\()|" + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern()
                    + "|--[^\\r\\n]*|(?s:/\\*.*?(?:\\*/|$))");
    private static final Pattern STARROCKS_VARIABLE_HINT_OPTION = Pattern.compile(
            "\\s*+([a-z_][\\w$]*)\\s*+=\\s*+(?:" + SINGLE_QUOTED_STRING + "|" + DOUBLE_QUOTED_IDENTIFIER
                    + "|[^\\s'\\\",();]++)\\s*+([,)])");
    private static final Pattern FLINK_HINT_OR_IGNORED_TOKEN = Pattern.compile(
            "(/\\*\\+\\s*options\\s*\\()|" + SINGLE_QUOTED_STRING + "|" + QUOTED_IDENTIFIER.pattern()
                    + "|--[^\\r\\n]*|(?s:/\\*.*?(?:\\*/|$))");

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
        String relationSyntax = normalized.replaceAll(SINGLE_QUOTED_STRING, " ");
        boolean sparkJsonTableSignal = hasSparkJsonTableSignal(relationSyntax);
        boolean strongSparkSignal = normalized.contains("${") || normalized.contains("#day#")
                || hasStrongSparkSignal(relationSyntax);
        boolean sparkSignal = strongSparkSignal || hasGeneralSparkSignal(relationSyntax);
        boolean weakMySqlExpressionSignal = normalized.matches("(?s).*\\bdiv\\b.*")
                || normalized.matches("(?s).*\\bmod\\b.*")
                || normalized.matches("(?s).*\\blimit\\s+\\d+\\s*,\\s*\\d+.*");

        String unquotedSyntax = QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll(" ");
        boolean oracleJsonTableType = unquotedSyntax.contains("json_table") && hasOracleJsonType(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier"),
                JSON_TABLE_CALL, JSON_TABLE_STRUCTURE, ORACLE_JSON_TABLE_COLUMN_TYPE);
        boolean oracleJsonReturningType = hasOracleJsonType(
                unquotedSyntax, JSON_SCALAR_CALL, ORACLE_JSON_RETURNING_STRUCTURE, null);

        boolean sqlServerTemporalQuery = unquotedSyntax.matches("(?s).*\\bfor\\s+system_time\\s+"
                + "(?:(?:all|between|from|contained\\s+in)\\b|as\\s+of\\s+" + TSQL_LOCAL_VARIABLE + ").*");

        boolean containsStatementSeparator = unquotedSyntax.indexOf(';') >= 0;
        boolean mysqlPrepare = MYSQL_PREPARED_LITERAL.matcher(normalized).find()
                || (containsStatementSeparator && unquotedSyntax.contains("prepare")
                        && hasStatementInScript(unwrapped, SqlDialect.MYSQL, MYSQL_PREPARED_LITERAL));
        if (SimpleStatementSplitter.containsMySqlDelimiterDirective(sql)) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.99, "MySQL client DELIMITER directive"));
        } else if (mysqlPrepare) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.97, "MySQL-compatible PREPARE SQL source"));
        } else if (MYSQL_RECURSION_SETTING.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.98, "MySQL recursive CTE depth setting"));
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
                || (!sparkJsonTableSignal && !oracleJsonTableType && JSON_TABLE_CALL.matcher(unquotedSyntax).find())
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
        } else if (POSTGRES_DROP_DOMAIN.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.95, "PostgreSQL-compatible DROP DOMAIN"));
        } else if (unquotedSyntax.contains("like") && POSTGRES_TABLE_LIKE_OPTIONS.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL parenthesized table LIKE options"));
        } else if (POSTGRES_KEY_CONSTRAINT.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL temporal or null-distinct key constraint"));
        } else if (POSTGRES_FOREIGN_KEY_OPTIONS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL foreign-key validation or delete action"));
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
        } else if (POSTGRES_COPY_COMMAND.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL COPY table or query"));
        } else if (POSTGRES_DATA_MODIFYING_CTE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL data-modifying common table expression"));
        } else if (unquotedSyntax.contains("delete") && POSTGRES_ALIASED_DELETE_USING.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL DELETE USING with a target alias"));
        } else if ((unquotedSyntax.contains("delete") || unquotedSyntax.contains("update"))
                && POSTGRES_DML_TARGET.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL inherited DML target"));
        } else if (POSTGRES_ROW_OVERLAPS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL temporal row OVERLAPS expression"));
        } else if (POSTGRES_ROW_LOCKING.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL query row-lock strength"));
        } else if (unquotedSyntax.contains("table") && POSTGRES_TABLE_INHERITANCE.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL TABLE inheritance syntax"));
        } else if (unquotedSyntax.contains("join") && unquotedSyntax.contains("using")
                && POSTGRES_JOIN_USING_ALIAS.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL alias for JOIN USING columns"));
        } else if (POSTGRES_RECURSIVE_CTE_OPTIONS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL recursive CTE search or cycle options"));
        } else if (POSTGRES_JSON_SYNTAX.matcher(unquotedSyntax).find()
                && !TSQL_OBJECT_SCOPE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL JSONB cast, function, or path operator"));
        } else if (XML_PASSING_EXPRESSION.matcher(unquotedSyntax).find()
                && POSTGRES_XML_PASSING_OPTION.matcher(unquotedSyntax).find()
                && !ORACLE_XML_SEQUENCE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.99, "PostgreSQL XML passing mode or cast"));
        } else if (POSTGRES_VECTOR_INDEX.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL vector index method"));
        } else if (POSTGRES_VECTOR_CAST.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL vector cast"));
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
        } else if (POSTGRES_EXPLAIN_CURSOR.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL EXPLAIN cursor"));
        } else if (POSTGRES_MAINTENANCE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL relation maintenance command"));
        } else if (POSTGRES_EXPLAIN_OPTIONS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL EXPLAIN plan options"));
        } else if (POSTGRES_PREPARED_STATEMENT.matcher(normalized).find()
                || POSTGRES_PREPARED_RESET.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("prepare")
                        && hasStatementInScript(unwrapped, SqlDialect.POSTGRESQL, POSTGRES_PREPARED_STATEMENT))) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.97, "PostgreSQL prepared statement control"));
        } else if (POSTGRES_DISTINCT_ON.matcher(unquotedSyntax).find()
                || (unquotedSyntax.contains("materialized") && POSTGRES_CTE_MATERIALIZATION.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find())) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL DISTINCT ON or CTE materialization"));
        } else if (POSTGRES_DATA_MATERIALIZATION.matcher(unquotedSyntax).find()
                || (POSTGRES_DATA_OPTION_END.matcher(unquotedSyntax).find()
                        && POSTGRES_DATA_OPTION_STATEMENT.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL table or materialized view data options"));
        } else if (POSTGRES_TWO_PHASE_TRANSACTION.matcher(normalized).find()
                || (containsStatementSeparator && unquotedSyntax.contains("prepar")
                        && hasStatementInScript(unwrapped, SqlDialect.POSTGRESQL, POSTGRES_TWO_PHASE_TRANSACTION))) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL two-phase transaction command"));
        } else if (POSTGRES_DISCARD.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("discard")
                        && hasStatementInScript(unwrapped, SqlDialect.POSTGRESQL, POSTGRES_DISCARD))) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.98, "PostgreSQL DISCARD session command"));
        } else if (POSTGRES_SCHEMA_CONTROL.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.97, "PostgreSQL schema search path control"));
        } else if (literals.dollarQuoted && normalized.matches("(?s)^\\s*do\\s+''.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.94, "PostgreSQL dollar-quoted DO block"));
        } else if (unquotedSyntax.matches("(?s).*\\bon\\s+conflict\\b.*")
                || unquotedSyntax.matches("(?s).*\\breturning\\b.*")
                || unquotedSyntax.matches("(?s).*::\\s*[a-zA-Z_][a-zA-Z0-9_]*.*")
                || unquotedSyntax.matches("(?s).*\\bilike\\b.*")) {
            candidates.add(candidate(SqlDialect.POSTGRESQL, 0.92, "PostgreSQL ON CONFLICT, RETURNING, cast, or ILIKE syntax"));
        }
        if (HIVE_SET_FILEFORMAT.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.91, "Hive-compatible ALTER TABLE SET FILEFORMAT syntax"));
        }
        if (HIVE_PARTITION_OR_VIEW_DDL.matcher(unquotedSyntax).find()
                || HIVE_OWNER_OR_PRIVILEGE_METADATA.matcher(unquotedSyntax).find()
                || HIVE_ROLE_METADATA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive partition exchange, view rewrite, or role metadata syntax"));
        }
        if (HIVE_EXPLAIN_OR_TRANSACTIONS.matcher(unquotedSyntax).find()
                || normalized.matches("(?s)^\\s*show\\s+conf\\s+'hive\\..*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive explain details or session metadata syntax"));
        }
        if (HIVE_DATA_CONNECTOR.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && (unquotedSyntax.contains("connector") || unquotedSyntax.contains("remote"))
                        && hasStatementInScript(unwrapped, SqlDialect.HIVE, HIVE_DATA_CONNECTOR))) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive data connector or remote database syntax"));
        }
        if (HIVE_BARE_COLUMN_FILTER.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive SHOW COLUMNS with bare pattern syntax"));
        }
        if (HIVE_FILTERED_PARTITIONS.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive filtered partition metadata syntax"));
        }
        if (HIVE_SHOW_INDEX_ON.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive SHOW INDEX ON syntax"));
        }
        if (HIVE_PARTITION_MAINTENANCE.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive table or partition maintenance syntax"));
        } else if (HIVE_LOCK_OR_COMPACTION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.HIVE, 0.98, "Hive table lock, compaction, or function reload syntax"));
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
        } else if (unquotedSyntax.matches("(?s).*\\brow\\s+format\\b.*")
                || unquotedSyntax.matches("(?s).*\\bstored\\s+as\\b.*")
                || relationSyntax.matches("(?s)^\\s*create\\s+(?:(?:temporary|external)\\s+)*table\\b.*\\bstored\\s+by\\b.*")
                || unquotedSyntax.matches("(?s).*\\bserdeproperties\\b.*")
                || unquotedSyntax.matches("(?s).*\\bclustered\\s+by\\b.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.90, "Hive storage or table layout syntax"));
        }
        if (SPARK_RECURSION_LEVEL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SPARK, 0.99, "Spark recursive CTE level limit"));
        }
        if (FLINK_SESSION_OPTION.matcher(normalized).find()
                || (containsStatementSeparator && SESSION_OPTION_COMMAND.matcher(unquotedSyntax).find()
                        && hasStatementInScript(unwrapped, SqlDialect.FLINK, FLINK_SESSION_OPTION))) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink SQL session option"));
        } else if (FLINK_DROP_TEMPORARY_VIEW.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("temporary")
                        && hasStatementInScript(unwrapped, SqlDialect.FLINK, FLINK_DROP_TEMPORARY_VIEW))) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink DROP TEMPORARY VIEW syntax"));
        } else if (FLINK_EXECUTE_QUERY.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("execute")
                        && hasStatementInScript(unwrapped, SqlDialect.FLINK, FLINK_EXECUTE_QUERY))) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink EXECUTE query"));
        } else if (FLINK_EXECUTE_INSERT.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("execute")
                        && hasStatementInScript(unwrapped, SqlDialect.FLINK, FLINK_EXECUTE_INSERT))) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink EXECUTE INSERT statement"));
        } else if (FLINK_MODEL_DDL.matcher(unquotedSyntax).find()
                || FLINK_MODEL_INFERENCE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink model lifecycle or ML_PREDICT syntax"));
        } else if (FLINK_CHANGELOG_FUNCTION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink changelog table function syntax"));
        } else if (FLINK_DISTRIBUTION_OR_WATERMARK.matcher(unquotedSyntax).find()
                || FLINK_SHOW_CURRENT_CATALOG.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink distribution, watermark, or current catalog syntax"));
        } else if (FLINK_PARTITION_OPTIONS.matcher(unquotedSyntax).find()
                || FLINK_DESCRIBE_CATALOG.matcher(unquotedSyntax).find()
                || FLINK_BARE_COLUMN_RENAME.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink partition properties, catalog description, or column rename syntax"));
        } else if (FLINK_CREATE_DATABASE_OPTIONS.matcher(normalized).find()
                || (containsStatementSeparator && unquotedSyntax.contains("database")
                        && hasStatementInScript(unwrapped, SqlDialect.FLINK, FLINK_CREATE_DATABASE_OPTIONS))) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink CREATE DATABASE string properties"));
        } else if (FLINK_FILTERED_METADATA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink filtered metadata syntax"));
        } else if (FLINK_JVM_FUNCTION.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink function language and class syntax"));
        } else if (FLINK_FUNCTION_ARTIFACT.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink function artifact syntax"));
        } else if (FLINK_EXPLAIN_OR_JAR.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink explain details or JAR management"));
        } else if (FLINK_MODULE_OR_SYSTEM_FUNCTION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink module or temporary system function syntax"));
        } else if (SimpleStatementSplitter.containsFlinkStatementSet(unquotedSyntax)
                || hasFlinkOptionsHint(rawNormalized)
                || hasFlinkConnectorOptions(normalized)
                || FLINK_ALTER_STRING_OPTIONS.matcher(normalized).find()
                || normalized.matches("(?s)^\\s*set\\s+table\\..*")
                || normalized.matches("(?s)^\\s*set\\s+['\"]table\\..*")
                || normalized.matches("(?s)^\\s*reset\\s+table\\..*")
                || unquotedSyntax.matches("(?s).*\\bcreate\\s+catalog\\b.*")
                || unquotedSyntax.matches("(?s).*\\buse\\s+catalog\\b.*")
                || (!oracleJsonReturningType
                        && unquotedSyntax.matches("(?s).*\\bjson_(value|query|exists|object|array|objectagg|arrayagg)\\s*\\(.*"))
                || unquotedSyntax.matches("(?s).*\\bfloor\\s*\\([^;]+\\bto\\s+(minute|hour|day|month|year)\\b.*")
                || unquotedSyntax.matches("(?s).*\\bceil\\s*\\([^;]+\\bto\\s+(minute|hour|day|month|year)\\b.*")
                || unquotedSyntax.matches("(?s).*\\bextract\\s*\\([^;]+\\bfrom\\b.*")
                || unquotedSyntax.matches("(?s).*\\b(tumble|hop|session)_(start|end|rowtime|proctime)\\s*\\(.*")
                || unquotedSyntax.matches("(?s).*\\bfrom\\s+table\\s*\\(\\s*(tumble|hop|session|cumulate)\\s*\\(.*")
                || relationSyntax.matches("(?s)^\\s*insert\\b.*\\bon\\s+conflict\\s+do\\s+(deduplicate|error)\\b.*")
                || relationSyntax.matches("(?s).*[,\\s](tumble|hop|session|cumulate)\\s*\\(\\s*(?:data\\s*=>\\s*)?table\\s+.*")
                || (!sqlServerTemporalQuery && unquotedSyntax.matches("(?s).*\\bfor\\s+system_time\\s+as\\s+of\\b.*"))
                || unquotedSyntax.matches("(?s).*\\bproctime\\s*\\(\\s*\\).*")
                || unquotedSyntax.matches("(?s).*\\bsplit_index\\s*\\(.*")
                || unquotedSyntax.matches("(?s).*\\bcreate\\s+(temporary\\s+)?view\\s+if\\s+not\\s+exist\\b.*")
                || unquotedSyntax.matches("(?s).*\\bwatermark\\s+for\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*(?:compile(?:\\s+and\\s+execute)?|execute)\\s+plan\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*show\\s+jobs\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*(describe|desc)\\s+job\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*stop\\s+job\\b.*")
                || normalized.matches("(?s)^\\s*call\\s+`[^`]+`\\s*\\..*")
                || FLINK_MATERIALIZED_TABLE.matcher(unquotedSyntax).find()
                || unquotedSyntax.matches("(?s).*\\bwith\\s+connector\\b.*")) {
            candidates.add(candidate(SqlDialect.FLINK, 0.98, "Flink connector, hint, catalog, JSON, watermark, plan, model, job, or procedure syntax"));
        }
        if (STARROCKS_PREPARED_QUERY.matcher(normalized).find()
                || (containsStatementSeparator && unquotedSyntax.contains("prepare")
                        && hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_PREPARED_QUERY))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks direct PREPARE query"));
        } else if (hasStarRocksVariableHint(rawNormalized)) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks statement variable hint"));
        } else if (STARROCKS_RECURSION_SETTINGS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks recursive CTE settings"));
        } else if (unquotedSyntax.contains("temporary") && unquotedSyntax.contains("partition")
                && STARROCKS_TEMPORARY_PARTITION.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks temporary partition syntax"));
        } else if (STARROCKS_USE_CATALOG_LITERAL.matcher(normalized).find()
                || (containsStatementSeparator && normalized.contains("catalog")
                        && (hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_USE_CATALOG_LITERAL)
                                || combinesStarRocksCatalogSwitches(unwrapped)))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks catalog session syntax"));
        } else if (STARROCKS_EXTERNAL_CATALOG.matcher(unquotedSyntax).find()
                || STARROCKS_DOUBLE_QUOTED_CATALOG_OPTIONS.matcher(relationSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("external")
                        && hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_EXTERNAL_CATALOG))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks external catalog or catalog properties syntax"));
        } else if ((unquotedSyntax.contains("dictionary_get") || unquotedSyntax.contains("dict_mapping"))
                && containsSignatureOutsideQuotes(STARROCKS_DICTIONARY_LOOKUP_OR_QUOTE, normalized)) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks dictionary lookup syntax"));
        } else if (STARROCKS_DICTIONARY_DDL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks dictionary object syntax"));
        } else if (STARROCKS_MATERIALIZED_VIEW_OPTIONS.matcher(relationSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("materialized")
                        && hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_MATERIALIZED_VIEW_OPTIONS))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks materialized view options or refresh syntax"));
        } else if (STARROCKS_PARTITION_MAINTENANCE.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks partition maintenance syntax"));
        } else if (STARROCKS_SCHEMA_MAINTENANCE.matcher(relationSyntax).find()
                || STARROCKS_TABLE_PROPERTIES.matcher(normalized).find()
                || (unquotedSyntax.contains("properties") && STARROCKS_ROLLUP_COLUMN_CHANGE.matcher(
                        QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find())) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks schema or storage maintenance syntax"));
        } else if (STARROCKS_TABLE_MAINTENANCE.matcher(relationSyntax).find()
                || (containsStatementSeparator
                        && (unquotedSyntax.contains("rollup") || unquotedSyntax.contains("swap")
                                || unquotedSyntax.contains("tablet"))
                        && hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_TABLE_MAINTENANCE))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks rollup, swap, or tablet maintenance syntax"));
        } else if (STARROCKS_WAREHOUSE.matcher(unquotedSyntax).find()
                || STARROCKS_PRIMARY_KEY_TABLE.matcher(unquotedSyntax).find()
                || STARROCKS_FORMATTED_OUTFILE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks warehouse, primary key table, or formatted outfile syntax"));
        } else if (STARROCKS_LOAD_COMMAND.matcher(unquotedSyntax).find()
                || (containsStatementSeparator && unquotedSyntax.contains("load")
                        && hasStatementInScript(unwrapped, SqlDialect.STARROCKS, STARROCKS_LOAD_COMMAND))) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks labeled or routine load syntax"));
        } else if (STARROCKS_JOB_METADATA.matcher(unquotedSyntax).find()
                || STARROCKS_FILTERED_MATERIALIZED_VIEWS.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks job metadata syntax"));
        } else if (STARROCKS_BACKUP_RESTORE.matcher(relationSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks snapshot or repository syntax"));
        } else if (STARROCKS_DATABASE_OPTIONS.matcher(relationSyntax).find()
                || STARROCKS_NATIVE_METADATA.matcher(normalized).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks database options or metadata syntax"));
        } else if (STARROCKS_ADMIN_METADATA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks administrative metadata syntax"));
        } else if (STARROCKS_CLUSTER_METADATA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks cluster, resource, or backup metadata syntax"));
        } else if (STARROCKS_CLUSTER_ADMIN.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks cluster administration syntax"));
        } else if (STARROCKS_PIPE_OR_STORAGE_VOLUME.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks pipe or storage volume syntax"));
        } else if ((unquotedSyntax.contains("index") && STARROCKS_NATIVE_INDEX.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find())
                || (relationSyntax.indexOf('[') >= 0 && STARROCKS_BRACKET_JOIN_HINT.matcher(relationSyntax).find())) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.99, "StarRocks index or bracketed join hint syntax"));
        } else if (STARROCKS_JOIN.matcher(unquotedSyntax).find()
                || (!unquotedSyntax.contains("match_condition") && STARROCKS_ASOF_ON.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.98, "StarRocks ASOF or directional semi/anti join"));
        } else if (normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bduplicate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\baggregate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bdistributed\\s+by\\s+hash\\b.*")
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
        } else if (ORACLE_RECURSIVE_CTE_OPTIONS.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle recursive factoring search or cycle options"));
        } else if (oracleJsonTableType) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle JSON_TABLE column type"));
        } else if (oracleJsonReturningType) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle SQL/JSON RETURNING type"));
        } else if (ORACLE_JSON_AGGREGATE.matcher(unquotedSyntax).find()
                && ORACLE_JSON_AGGREGATE_OPTION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle SQL/JSON aggregate options"));
        } else if (ORACLE_VECTOR_SCHEMA.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle vector schema"));
        } else if (ORACLE_VECTOR_QUERY.matcher(unquotedSyntax).find()
                || ORACLE_VECTOR_SERIALIZER.matcher(unquotedSyntax).find()
                || ((SHARED_VECTOR_OPERATOR.matcher(unquotedSyntax).find() || unquotedSyntax.contains("<=>"))
                    && ORACLE_VECTOR_CONSTRUCTOR.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle vector search options or constructor"));
        } else if (unquotedSyntax.contains("domain") && ORACLE_DOMAIN_DDL.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle data use case domain DDL"));
        } else if (ORACLE_RETURNING_TARGET.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.99, "Oracle RETURNING bind or bulk collection"));
        } else if (relationSyntax.indexOf('@') >= 0 && ORACLE_REMOTE_RELATION
                .matcher(QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.98, "Oracle database link on a relation"));
        } else if (unquotedSyntax.matches("(?s).*[,\\s]xmltable\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.97, "XMLTABLE relation syntax"));
        } else if (unquotedSyntax.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || unquotedSyntax.matches("(?s).*\\bconnect\\s+by\\b.*")
                || (ORACLE_START_WITH.matcher(unquotedSyntax).find()
                    && unquotedSyntax.matches("(?s).*\\bselect\\b[^;]*\\bstart\\s+with\\b.*"))
                || unquotedSyntax.matches("(?s).*\\bas\\s+of\\s+(timestamp|scn)\\b.*")
                || ((unquotedSyntax.contains("partition") || unquotedSyntax.contains("sample"))
                    && !unquotedSyntax.matches("(?s)^\\s*(?:explain\\s+partitions|show\\s+tablet|admin\\s+show)\\b.*")
                    && containsSignatureOutsideQuotes(ORACLE_RELATION_OPTION_OR_QUOTE, normalized))
                || unquotedSyntax.matches("(?s)^\\s*create\\s+(global|private)\\s+temporary\\s+table\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*create\\s+table\\b.*\\b(nologging|logging|parallel|noparallel|compress|nocompress)\\b.*\\bas\\s+select\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*drop\\s+table\\b.*\\bcascade\\s+constraints\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*truncate\\s+(table\\s+)?\\S+\\s+(drop|reuse)\\s+storage\\b.*")
                || unquotedSyntax.matches("(?s).*\\breturning\\b.+\\binto\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*create\\s+or\\s+replace\\s+trigger\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*(create|alter|drop)\\s+(public\\s+)?(sequence|synonym)\\b.*")
                || unquotedSyntax.matches("(?s)^\\s*(create|drop)\\s+(public\\s+)?database\\s+link\\b.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.95, "Oracle DUAL, hierarchical, flashback, sequence, synonym, or DBLink syntax"));
        }
        boolean sqlServerGo = SimpleStatementSplitter.containsSqlServerGo(unquotedSyntax);
        if (unquotedSyntax.matches("(?s)^\\s*(?:create|alter)\\s+table\\b[^;]*"
                + "(?:\\bperiod\\s+for\\s+system_time\\b|\\bsystem_versioning\\s*=).*")
                || sqlServerTemporalQuery) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server system-versioned temporal syntax"));
        } else if (SQL_SERVER_AI_MODEL.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.99, "SQL Server external model or embedding syntax"));
        } else if (SQL_SERVER_VECTOR_QUERY.matcher(unquotedSyntax).find()
                || SQL_SERVER_VECTOR_FLOAT16.matcher(unquotedSyntax).find()
                || (unquotedSyntax.contains("vector_distance") && containsSignatureOutsideQuotes(SQL_SERVER_VECTOR_METRIC_OR_QUOTE, normalized))
                || (CREATE_VECTOR_INDEX.matcher(unquotedSyntax).find()
                    && !ORACLE_VECTOR_SCHEMA.matcher(unquotedSyntax).find())) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.99, "SQL Server vector search or index syntax"));
        } else if (sqlServerGo && TSQL_BATCH_ROUTINE.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server routine and GO batch delimiter"));
        } else if (relationSyntax.matches("(?s)^\\s*with\\s+xmlnamespaces\\s*\\(.*")
                || relationSyntax.matches("(?s).*\\b(?:cross|outer)\\s+apply\\s+[^\\s();]+\\s*\\.\\s*nodes\\s*\\(.*")
                || relationSyntax.matches("(?s).*\\bfrom\\s+@[\\w$#]+\\s*\\.\\s*nodes\\s*\\(.*")) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.97, "SQL Server XML namespaces or nodes() method"));
        } else if (!mysqlPrepare && TSQL_TABLE_VARIABLE.matcher(unquotedSyntax).find()) {
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
        } else if (unquotedSyntax.contains("exec") && TSQL_EXEC_ROUTINE.matcher(
                QUOTED_IDENTIFIER.matcher(relationSyntax).replaceAll("quoted_identifier")).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server procedure execution or INSERT EXEC"));
        } else if (TSQL_TEMP_RELATION.matcher(unquotedSyntax).find()
                || TSQL_INDEX_OR_DBCC.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server temporary table, index, or DBCC syntax"));
        } else if (TSQL_FOR_SERIALIZATION.matcher(unquotedSyntax).find()) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.98, "SQL Server FOR JSON or FOR XML output"));
        } else if (TSQL_TOP_SELECT.matcher(relationSyntax).find()
                || containsSqlServerBracketIdentifier(relationSyntax)
                || unquotedSyntax.matches("(?s).*\\bwith\\s*\\(\\s*nolock\\s*\\).*")
                || unquotedSyntax.matches("(?s).*\\b(openjson|openxml|openquery|openrowset)\\s*\\(.*")) {
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
            candidates.add(candidate(SqlDialect.SPARK, GENERIC_SPARK_CONFIDENCE, "Dialect-neutral SQL; Spark is the current generic fallback"));
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

    static boolean hasSharedCatalogUse(String sql) {
        return stripComments(sql).matches("(?is)^\\s*use\\s+catalog\\s+" + SQL_IDENTIFIER + "\\s*;?\\s*$");
    }

    private static boolean combinesStarRocksCatalogSwitches(String sql) {
        boolean hasUse = false;
        boolean hasSet = false;
        for (String statement : new SimpleStatementSplitter().split(sql, SqlDialect.STARROCKS)) {
            Matcher switchCatalog = STARROCKS_SHARED_CATALOG_SWITCH.matcher(
                    stripComments(statement).toLowerCase(Locale.ROOT));
            if (switchCatalog.matches()) {
                hasUse |= "use".equals(switchCatalog.group(1));
                hasSet |= "set".equals(switchCatalog.group(1));
                if (hasUse && hasSet) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasSharedHiveSetFileFormat(String sql) {
        String syntax = stripComments(maskQuotedLiterals(sql).text).toLowerCase(Locale.ROOT);
        return HIVE_SET_FILEFORMAT.matcher(syntax.replaceAll(SINGLE_QUOTED_STRING, " ")).find();
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
        return sql.contains("`")
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
        if (sql.indexOf('[') < 0) {
            return false;
        }
        Matcher identifiers = QUOTED_IDENTIFIER.matcher(sql);
        while (identifiers.find()) {
            int start = identifiers.start();
            if (sql.charAt(start) != '[' || identifiers.end() <= start + 2) {
                continue;
            }
            char previous = previousAdjacentNonWhitespace(sql, start);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == '.' || previous == ')' || previous == '`') {
                continue;
            }
            if (isIdentifierStart(sql.charAt(start + 1))) {
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

    private static boolean hasOracleJsonType(String sql, Pattern call, Pattern tokens, Pattern columnType) {
        Matcher calls = call.matcher(sql);
        while (calls.find()) {
            Matcher structure = tokens.matcher(sql).region(calls.end(), sql.length());
            int depth = 1;
            int clauseStart = -1;
            while (structure.find()) {
                char token = sql.charAt(structure.start());
                if (token == ';') {
                    break;
                }
                if (token == '(') {
                    depth++;
                } else if (token == ')') {
                    if (--depth == 0) {
                        if (clauseStart >= 0 && (columnType == null
                                || columnType.matcher(sql).region(clauseStart, structure.start()).find())) {
                            return true;
                        }
                        break;
                    }
                } else {
                    int last = structure.end() - 1;
                    boolean opensColumns = sql.charAt(last) == '(';
                    if (depth == 1) {
                        clauseStart = opensColumns ? last : structure.start();
                    }
                    if (opensColumns) {
                        depth++;
                    }
                }
            }
        }
        return false;
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

    private static boolean hasStatementInScript(String sql, SqlDialect dialect, Pattern statementSyntax) {
        for (String statement : new SimpleStatementSplitter().split(sql, dialect)) {
            String normalized = stripComments(statement).toLowerCase(Locale.ROOT);
            if (statementSyntax.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStarRocksVariableHint(String sql) {
        if (!sql.contains("/*+")) {
            return false;
        }
        Matcher hints = STARROCKS_VARIABLE_HINT_OR_IGNORED_TOKEN.matcher(sql);
        Matcher option = STARROCKS_VARIABLE_HINT_OPTION.matcher(sql);
        while (hints.find()) {
            if (hints.group(1) == null) {
                continue;
            }
            int end = sql.indexOf("*/", hints.end());
            if (end < 0) {
                return false;
            }
            boolean nativeOption = false;
            int start = hints.end();
            while (option.region(start, end).lookingAt()) {
                String key = option.group(1);
                nativeOption |= "query_timeout".equals(key) || "insert_timeout".equals(key)
                        || "query_mem_limit".equals(key) || "dynamic_overwrite".equals(key);
                if (")".equals(option.group(2))) {
                    if (nativeOption) {
                        return true;
                    }
                    break;
                }
                start = option.end();
            }
            hints.region(end + 2, sql.length());
        }
        return false;
    }

    private static boolean hasFlinkOptionsHint(String sql) {
        if (!sql.contains("/*+")) {
            return false;
        }
        Matcher matcher = FLINK_HINT_OR_IGNORED_TOKEN.matcher(sql);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlinkConnectorOptions(String sql) {
        if (!sql.contains("connector")) {
            return false;
        }
        Matcher optionLists = SQL_WITH_OPTIONS_OR_QUOTE.matcher(sql);
        Matcher option = FLINK_OPTION_PAIR.matcher(sql);
        while (optionLists.find()) {
            if (optionLists.group(1) == null) {
                continue;
            }
            option.region(optionLists.end(), sql.length());
            while (option.lookingAt()) {
                String key = option.group(1);
                key = key.substring(1, key.length() - 1);
                if ("connector".equals(key) || "connector.type".equals(key)) {
                    return true;
                }
                option.region(option.end(), sql.length());
            }
        }
        return false;
    }

    private static boolean containsSignatureOutsideQuotes(Pattern signatureOrQuote, String sql) {
        Matcher matcher = signatureOrQuote.matcher(sql);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                return true;
            }
        }
        return false;
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
