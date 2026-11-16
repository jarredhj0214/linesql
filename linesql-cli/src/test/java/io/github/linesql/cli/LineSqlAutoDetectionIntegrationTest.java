package io.github.linesql.cli;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LineSqlAutoDetectionIntegrationTest {
    @Test
    public void autoDetectsRegisteredDialectAnchorsThroughPublicApi() {
        assertDialect(SqlDialect.MYSQL,
                "update mart.t join app.s on t.id = s.id set t.c = s.c");
        assertDialect(SqlDialect.HIVE,
                "create table ods.users(id bigint) stored as parquet");
        assertDialect(SqlDialect.FLINK,
                "create table ods_users(id bigint) with ('connector' = 'kafka')");
        assertDialect(SqlDialect.STARROCKS,
                "create table dwd.orders(id bigint) duplicate key(id) distributed by hash(id)");
        assertDialect(SqlDialect.ORACLE,
                "select id from dual");
        assertDialect(SqlDialect.SQLSERVER,
                "select top (10) u.id as user_id from dbo.users u with (nolock)");
        assertDialect(SqlDialect.POSTGRESQL,
                "insert into mart.users(id) select id from staging.users_delta on conflict (id) do nothing");
        assertDialect(SqlDialect.OCEANBASE,
                "select id from oceanbase.__all_virtual_table");
    }

    @Test
    public void recognizesPostgresClientCopyAndDumpEnvelopes() {
        List<LineageResult> dump = LineSql.parseScript(scriptFixture("/sql/postgresql/pg_dump_envelope.sql"));
        assertEquals(2, dump.size());
        assertEquals(StatementType.CONTROL, dump.get(0).getStatementType());
        assertEquals(StatementType.CREATE_TABLE, dump.get(1).getStatementType());
        assertEquals(SqlDialect.POSTGRESQL, dump.get(1).getDialect());
        assertEquals("events", dump.get(1).getOutputTables().get(0).getName());

        List<LineageResult> copy = LineSql.parseScript(scriptFixture("/sql/postgresql/psql_copy_commands.sql"));
        assertEquals(3, copy.size());
        assertEquals(StatementType.LOAD_DATA, copy.get(0).getStatementType());
        assertEquals(StatementType.LOAD_DATA, copy.get(1).getStatementType());
        assertEquals(StatementType.SELECT, copy.get(2).getStatementType());
        assertEquals("notes", copy.get(0).getOutputTables().get(0).getName());
        assertEquals(0, copy.get(0).getInputTables().size());
        assertEquals("events", copy.get(1).getOutputTables().get(0).getName());
        assertEquals("events", copy.get(2).getInputTables().get(0).getName());
        for (LineageResult result : copy) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }

        assertDialect(SqlDialect.SPARK, "SELECT 'string\n\\restrict key\n' FROM ods.events");
    }

    @Test
    public void keepsPostgresCopyDataOutOfScriptLineage() {
        String script = scriptFixture("/sql/postgresql/copy_stdin_script.sql");
        assertPostgresCopyScript(LineSql.parseScript(script));
        assertPostgresCopyScript(LineSql.parseScript(script.replace("\n", "\r\n"), SqlDialect.POSTGRESQL));

        List<LineageResult> noInlineData = LineSql.parseScript(
                "COPY staging.events FROM STDIN; SELECT id FROM staging.events;", SqlDialect.POSTGRESQL);
        assertEquals(2, noInlineData.size());
        assertEquals(StatementType.SELECT, noInlineData.get(1).getStatementType());
    }

    @Test
    public void ignoresDialectSignalsInLargeCopyPayloads() {
        StringBuilder script = new StringBuilder("COPY staging.events (note) FROM STDIN;\n");
        for (int i = 0; i < 10000; i++) {
            script.append("SELECT * FROM fake; GO ' \" $\n");
        }
        script.append("DELIMITER //\n\\.\n");
        script.append("\\copy staging.notes (note) FROM STDIN\n");
        script.append("SELECT TOP 1 * FROM fake;\nGO\n\\.\n");
        script.append("SELECT id FROM staging.events;");
        List<LineageResult> results = LineSql.parseScript(script.toString());
        assertEquals(3, results.size());
        assertEquals(StatementType.LOAD_DATA, results.get(0).getStatementType());
        assertEquals(StatementType.LOAD_DATA, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals("notes", results.get(1).getOutputTables().get(0).getName());
        assertEquals("events", results.get(2).getInputTables().get(0).getName());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
    }

    @Test
    public void distinguishesPostgresDmlTargetsFromSharedDeleteUsing() {
        assertDialect(SqlDialect.POSTGRESQL, "DELETE FROM ONLY (mart.orders) USING staging.expired e"
                + " WHERE orders.id = e.order_id");
        assertDialect(SqlDialect.POSTGRESQL, "DELETE FROM mart.orders * USING staging.expired e"
                + " WHERE orders.id = e.order_id");
        assertDialect(SqlDialect.POSTGRESQL, "UPDATE ONLY mart.orders SET status = e.status"
                + " FROM staging.expired AS e WHERE orders.id = e.order_id");
        assertDialect(SqlDialect.MYSQL, "DELETE FROM o USING mart.orders AS o"
                + " JOIN staging.expired AS e ON o.id = e.order_id");
        assertDialect(SqlDialect.MYSQL, "DELETE FROM mart.orders USING staging.expired e"
                + " WHERE orders.id = e.order_id");
        assertDialect(SqlDialect.SPARK, "SELECT 'DELETE FROM ONLY orders USING expired' AS message");
    }

    @Test
    public void detectsPostgresRowsFromWithoutJsonbSignals() {
        String sql = "SELECT f.item, f.ordinality FROM ods.orders o"
                + " CROSS JOIN LATERAL ROWS FROM (unnest(o.quantities)) WITH ORDINALITY AS f(item)";
        LineageResult result = LineSql.parseScript(sql).get(0);
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertEquals("quantities", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals(0, result.getColumnLineage().get(1).getSources().size());
    }

    @Test
    public void retriesDeleteUsingReturningWhenMySqlRejectsIt() {
        String sql = "DELETE FROM mart.orders USING staging.expired WHERE orders.id = expired.order_id"
                + " RETURNING orders.id, expired.reason AS deleted_reason";
        LineageResult result = LineSql.parseScript(sql).get(0);
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.DELETE, result.getStatementType());
        assertEquals("orders", result.getOutputTables().get(0).getName());
        assertEquals("expired", result.getColumnLineage().get(1).getSources().get(0).getTable().getName());
        assertEquals(2, result.getColumnUsages().size());

        LineageResult cte = LineSql.parseScript("WITH q AS (SELECT order_id FROM staging.expired)"
                + " DELETE FROM mart.orders AS o USING q WHERE o.id = q.order_id RETURNING o.id").get(0);
        assertEquals(SqlDialect.POSTGRESQL, cte.getDialect());
        assertEquals(StatementType.DELETE, cte.getStatementType());
        assertEquals(2, cte.getInputTables().size());
        assertDialect(SqlDialect.MYSQL, "DELETE FROM o USING mart.orders o JOIN staging.expired e"
                + " ON o.id = e.order_id WHERE e.returning = 1");

        LineageResult hinted = LineSql.parseScript(sql, SqlDialect.MYSQL).get(0);
        assertEquals(SqlDialect.MYSQL, hinted.getDialect());
        assertTrue(hinted.getDiagnostics().stream().anyMatch(d -> "MYSQL_PARSE_ERROR".equals(d.getCode())));
    }

    @Test
    public void detectsHiveIcebergPartitionSpecifications() {
        String script = "CREATE EXTERNAL TABLE mart.events (event_time TIMESTAMP, user_id BIGINT)"
                + " PARTITIONED BY SPEC (DAY(event_time), BUCKET(16, user_id)) STORED BY ICEBERG;"
                + " ALTER TABLE mart.events SET PARTITION SPEC (MONTH(event_time), user_id);";
        List<LineageResult> results = LineSql.parseScript(script);
        assertEquals(2, results.size());
        assertEquals(StatementType.CREATE_TABLE, results.get(0).getStatementType());
        assertEquals(StatementType.ALTER_TABLE, results.get(1).getStatementType());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.HIVE, result.getDialect());
            assertEquals("events", result.getOutputTables().get(0).getName());
            assertEquals(2, result.getColumnUsages().size());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
    }

    @Test
    public void usesHiveIcebergContextForSharedTimeTravelSyntax() {
        String query = "SELECT e.id FROM mart.events FOR SYSTEM_VERSION AS OF 3369973735913135680 AS e";
        String script = "CREATE EXTERNAL TABLE mart.events (id BIGINT) STORED BY ICEBERG;" + query + ";";
        List<LineageResult> results = LineSql.parseScript(script);
        assertEquals(2, results.size());
        assertEquals(SqlDialect.HIVE, results.get(1).getDialect());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("events", results.get(1).getInputTables().get(0).getName());
        assertEquals("id", results.get(1).getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.HIVE, "ALTER TABLE mart.events EXECUTE EXPIRE_SNAPSHOTS RETAIN LAST 3");
        assertDialect(SqlDialect.HIVE, "ALTER TABLE mart.events EXECUTE CHERRY_PICK 3369973735913135680");
    }

    @Test
    public void detectsSqlServerTableAndQueryHints() {
        String script = "SELECT o.id FROM dbo.orders AS o WITH (UPDLOCK, READPAST, ROWLOCK) WHERE o.status = 1;"
                + " SELECT o.id FROM dbo.orders AS o WHERE o.status = 1 OPTION (RECOMPILE, MAXDOP 2);";
        List<LineageResult> results = LineSql.parseScript(script);
        assertEquals(2, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.SQLSERVER, result.getDialect());
            assertEquals(StatementType.SELECT, result.getStatementType());
            assertEquals("orders", result.getInputTables().get(0).getName());
            assertEquals("id", result.getColumnLineage().get(0).getSources().get(0).getName());
            assertEquals("status", result.getColumnUsages().get(0).getColumn().getName());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
        LineageResult merge = LineSql.parse("MERGE dbo.orders WITH (HOLDLOCK) AS t USING dbo.changes AS s"
                + " ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.status = s.status;");
        assertEquals(SqlDialect.SQLSERVER, merge.getDialect());
        assertEquals(StatementType.MERGE, merge.getStatementType());
        assertEquals("orders", merge.getOutputTables().get(0).getName());
        assertEquals("changes", merge.getColumnLineage().get(0).getSources().get(0).getTable().getName());
        LineageResult truncate = LineSql.parse("TRUNCATE TABLE dbo.events WITH (PARTITIONS (1, 3 TO 5))");
        assertEquals(SqlDialect.SQLSERVER, truncate.getDialect());
        assertEquals(StatementType.TRUNCATE_TABLE, truncate.getStatementType());
        assertEquals("events", truncate.getOutputTables().get(0).getName());
        assertFalse(truncate.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM t WITH (HOLDLOCK)' AS text");
        assertDialect(SqlDialect.SPARK, "SELECT id FROM ods.t WHERE option(recompile)");
    }

    @Test
    public void detectsMultilineMySqlDuplicateKeyUpdates() {
        String sql = "INSERT INTO mart.events TABLE ods.new_events ORDER BY event_id LIMIT 100"
                + " ON\nDUPLICATE /* duplicate path */ KEY\nUPDATE touches = touches + 1;";
        LineageResult result = LineSql.parse(sql);
        assertEquals(SqlDialect.MYSQL, result.getDialect());
        assertEquals(StatementType.INSERT, result.getStatementType());
        assertEquals("new_events", result.getInputTables().get(0).getName());
        assertEquals("events", result.getOutputTables().get(0).getName());
        assertEquals("touches", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("event_id", result.getColumnUsages().get(0).getColumn().getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.SPARK, "SELECT 'log: on duplicate key update ignored' AS msg");
    }

    @Test
    public void splitsPlainSqlBuffersInSqlPlusScripts() {
        String script = scriptFixture("/sql/oracle/sqlplus_slash_statements.sql");
        assertSqlPlusSlashStatements(LineSql.parseScript(script));
        assertSqlPlusSlashStatements(LineSql.parseScript(script.replace("\n", "\r\n"), SqlDialect.ORACLE));
        assertDialect(SqlDialect.SPARK, "SELECT 10\n/\n2 AS ratio FROM ods.values_table");
        assertDialect(SqlDialect.SPARK, "SELECT 'before\n/\nSELECT inside a literal' AS text");
    }

    private void assertSqlPlusSlashStatements(List<LineageResult> results) {
        assertEquals(3, results.size());
        assertEquals(StatementType.SELECT, results.get(0).getStatementType());
        assertEquals(2, results.get(0).getColumnLineage().get(0).getSources().size());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("audit", results.get(1).getOutputTables().get(0).getName());
        assertEquals("events", results.get(1).getInputTables().get(0).getName());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals("audit", results.get(2).getInputTables().get(0).getName());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.ORACLE, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
    }

    @Test
    public void expandsStaticSqlServerExecuteStatements() {
        LineageResult result = LineSql.parse("EXEC @status = [sys].[sp_executesql]"
                + " @stmt=N'SELECT id FROM dbo.events WHERE id = @id', @params=N'@id int', @id=42");
        assertEquals(SqlDialect.SQLSERVER, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("events", result.getInputTables().get(0).getName());
        assertEquals("id", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals(1, result.getColumnUsages().size());

        LineageResult opaque = LineSql.parse("EXEC sys.sp_executesql @stmt=@sql");
        assertEquals(SqlDialect.SQLSERVER, opaque.getDialect());
        assertEquals(StatementType.CONTROL, opaque.getStatementType());
        assertTrue(opaque.getDiagnostics().stream().anyMatch(d -> "DYNAMIC_SQL_NOT_EXPANDED".equals(d.getCode())));
        LineageResult nonUnicode = LineSql.parse("EXEC sys.sp_executesql 'SELECT id FROM dbo.events'");
        assertEquals(StatementType.CONTROL, nonUnicode.getStatementType());
        assertTrue(nonUnicode.getInputTables().isEmpty());
        LineageResult ordinary = LineSql.parse("EXEC dbo.sp_executesql N'SELECT id FROM dbo.events'", SqlDialect.SQLSERVER);
        assertEquals(StatementType.CONTROL, ordinary.getStatementType());
        assertTrue(ordinary.getInputTables().isEmpty());
        assertTrue(ordinary.getDiagnostics().isEmpty());
    }

    @Test
    public void usesOracleSignalsAlongsideSharedQualifySyntax() {
        String sql = "SELECT o.id, ROW_NUMBER() OVER (PARTITION BY o.customer_id ORDER BY o.created_at) AS rn"
                + " FROM ods.orders o WHERE o.status = q'[active]' QUALIFY rn = 1";
        LineageResult result = LineSql.parse(sql);
        assertEquals(SqlDialect.ORACLE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("orders", result.getInputTables().get(0).getName());
        assertEquals(2, result.getColumnLineage().get(1).getSources().size());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
    }

    @Test
    public void detectsOracleDirectJoinDmlWithReturningTargets() {
        String sql = "DELETE FROM mart.orders o USING staging.expired e"
                + " WHERE o.id = e.order_id RETURNING o.id INTO :deleted_id;";
        LineageResult delete = LineSql.parse(sql);
        assertEquals(SqlDialect.ORACLE, delete.getDialect());
        assertEquals(StatementType.DELETE, delete.getStatementType());
        assertEquals("orders", delete.getOutputTables().get(0).getName());
        assertEquals("expired", delete.getInputTables().get(1).getName());
        assertEquals("orders", delete.getColumnLineage().get(0).getSources().get(0).getTable().getName());
        assertFalse(delete.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.ORACLE, "DELETE FROM mart.orders o FROM staging.expired e"
                + " WHERE o.id = e.order_id RETURN o.id BULK COLLECT INTO deleted_ids");
    }

    @Test
    public void detectsSqlServerJsonAggregationWithDialectSpecificSyntax() {
        String top = "SELECT TOP (5) e.segment, JSON_ARRAYAGG(e.payload ORDER BY e.created_at RETURNING JSON)"
                + " AS items FROM dbo.events e GROUP BY e.segment";
        String object = "SELECT JSON_OBJECTAGG(j.[key]:j.value RETURNING JSON) AS items"
                + " FROM dbo.events e CROSS APPLY OPENJSON(e.payload) j";
        for (String sql : new String[] {top, object}) {
            LineageResult result = LineSql.parse(sql);
            assertEquals(SqlDialect.SQLSERVER, result.getDialect());
            assertEquals(StatementType.SELECT, result.getStatementType());
            assertEquals("events", result.getInputTables().get(0).getName());
            assertFalse(result.getColumnLineage().isEmpty());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
        assertDialect(SqlDialect.SQLSERVER, "SELECT id FROM dbo.events FOR JSON PATH");
        assertDialect(SqlDialect.SQLSERVER, "SELECT id FROM dbo.events FOR XML PATH('event')");
        assertDialect(SqlDialect.FLINK, "SELECT JSON_ARRAYAGG(payload) AS items FROM ods.events");
        assertDialect(SqlDialect.FLINK, "SELECT top(5) AS cap, JSON_ARRAYAGG(payload) AS items FROM ods.events");
    }

    @Test
    public void detectsPostgresJsonbReturningWithoutClaimingOtherJsonAggregates() {
        assertDialect(SqlDialect.POSTGRESQL, "SELECT json_arrayagg(e.payload ORDER BY e.seq"
                + " ABSENT ON NULL RETURNING jsonb) FROM events e");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT json_objectagg(e.key VALUE e.payload"
                + " WITH UNIQUE KEYS RETURNING pg_catalog.jsonb) FROM events e");
        assertDialect(SqlDialect.SQLSERVER, "SELECT TOP (5) JSON_ARRAYAGG(payload) FROM events");
    }

    @Test
    public void usesOracleVectorSearchOptionsWithSharedDistanceOperators() {
        LineageResult nearest = LineSql.parse("SELECT id FROM search.items ORDER BY embedding <-> "
                + "TO_VECTOR('[1,2,3]', 3, FLOAT32) FETCH APPROX FIRST 5 ROWS ONLY WITH TARGET ACCURACY 90");
        assertEquals(SqlDialect.ORACLE, nearest.getDialect());
        assertEquals(StatementType.SELECT, nearest.getStatementType());
        assertEquals("embedding", nearest.getColumnUsages().get(0).getColumn().getName());
        assertDialect(SqlDialect.POSTGRESQL,
                "SELECT id FROM search.items ORDER BY embedding <-> '[1,2,3]' LIMIT 5");
    }

    @Test
    public void detectsOracleJsonAggregateOptions() {
        assertDialect(SqlDialect.ORACLE, "SELECT JSON_ARRAYAGG(e.payload ORDER BY e.seq"
                + " RETURNING VARCHAR2(1000 CHAR) STRICT) FROM app.events e");
        assertDialect(SqlDialect.ORACLE, "SELECT JSON_OBJECTAGG(KEY e.code VALUE e.payload"
                + " RETURNING CLOB STRICT WITH UNIQUE KEYS) FROM app.events e");
    }

    @Test
    public void distinguishesOracleUsecaseDomainDropFromPostgresIdentifiers() {
        assertDialect(SqlDialect.ORACLE, "DROP USECASE DOMAIN IF EXISTS workflow.ticket_status");
        assertDialect(SqlDialect.ORACLE, "DROP DOMAIN \"workflow\".\"force\" FORCE PRESERVE");
        assertDialect(SqlDialect.ORACLE, "DROP DOMAIN USECASE IF EXISTS workflow.ticket_status FORCE PRESERVE");
        assertDialect(SqlDialect.POSTGRESQL, "DROP DOMAIN force");
        assertDialect(SqlDialect.POSTGRESQL, "DROP DOMAIN IF EXISTS workflow.force RESTRICT");
        assertDialect(SqlDialect.POSTGRESQL, "DROP DOMAIN usecase");
    }

    @Test
    public void inheritsNativeRecursiveCteSettingsAcrossScripts() {
        String recursive = "WITH RECURSIVE walk(n) AS"
                + " (SELECT 1 UNION ALL SELECT n + 1 FROM walk WHERE n < 3) SELECT n FROM walk";
        List<LineageResult> starrocks = LineSql.parseScript("SET enable_recursive_cte = TRUE; " + recursive);
        assertEquals(2, starrocks.size());
        assertEquals(SqlDialect.STARROCKS, starrocks.get(0).getDialect());
        assertEquals(SqlDialect.STARROCKS, starrocks.get(1).getDialect());
        assertEquals(StatementType.SELECT, starrocks.get(1).getStatementType());
        List<LineageResult> mysql = LineSql.parseScript("SET SESSION cte_max_recursion_depth = 8; " + recursive);
        assertEquals(2, mysql.size());
        assertEquals(SqlDialect.MYSQL, mysql.get(0).getDialect());
        assertEquals(SqlDialect.MYSQL, mysql.get(1).getDialect());
        assertEquals(StatementType.SELECT, mysql.get(1).getStatementType());
        assertDialect(SqlDialect.SPARK, "SELECT 'SET enable_recursive_cte = TRUE' AS note");
    }

    @Test
    public void detectsOracleRecursiveSubqueryFactoringOptions() {
        assertDialect(SqlDialect.ORACLE, "WITH walk(n) AS (SELECT 1 FROM dual"
                + " UNION ALL SELECT n + 1 FROM walk WHERE n < 4)"
                + " SEARCH DEPTH FIRST BY n SET seq CYCLE n SET seen TO 'Y' DEFAULT 'N'"
                + " SELECT n, seq, seen FROM walk");
        assertDialect(SqlDialect.ORACLE, "WITH walk(n) AS (SELECT id FROM app.nodes"
                + " UNION ALL SELECT n + 1 FROM walk WHERE n < 4)"
                + " CYCLE n SET seen TO 'Y' DEFAULT 'N' SELECT n, seen FROM walk");
    }

    @Test
    public void detectsPostgresRecursiveCteSearchAndCycleOptions() {
        assertDialect(SqlDialect.POSTGRESQL, "WITH RECURSIVE walk(n) AS"
                + " (SELECT 1 UNION ALL SELECT n + 1 FROM walk WHERE n < 4)"
                + " SEARCH BREADTH FIRST BY n SET seq CYCLE n SET seen USING path"
                + " SELECT n, seq, seen FROM walk");
    }

    @Test
    public void detectsSparkRecursiveCteLevelLimit() {
        assertDialect(SqlDialect.SPARK, "WITH RECURSIVE walk(n) MAX RECURSION LEVEL 8 AS"
                + " (SELECT 1 UNION ALL SELECT n + 1 FROM walk WHERE n < 4) SELECT n FROM walk");
    }

    @Test
    public void detectsSqlServerExternalModelsAndEmbeddingSyntax() {
        assertDialect(SqlDialect.SQLSERVER, "SELECT AI_GENERATE_EMBEDDINGS(body USE MODEL encode_text"
                + " PARAMETERS @settings) AS embedding FROM dbo.documents");
        assertDialect(SqlDialect.SQLSERVER, "SELECT chunk FROM AI_GENERATE_CHUNKS("
                + "SOURCE = @text, CHUNK_TYPE = FIXED, CHUNK_SIZE = 16)");
        assertDialect(SqlDialect.SQLSERVER, "DROP EXTERNAL MODEL encode_text");
        assertDialect(SqlDialect.SPARK, "SELECT 'AI_GENERATE_EMBEDDINGS(x USE MODEL m)' AS example");
    }

    @Test
    public void distinguishesSqlServerAndOracleVectorSyntax() {
        assertDialect(SqlDialect.SQLSERVER, "CREATE VECTOR INDEX vec_idx ON dbo.docs (vec)"
                + " WITH (METRIC = 'cosine', TYPE = 'DiskANN')");
        assertDialect(SqlDialect.SQLSERVER, "CREATE VECTOR INDEX vec_idx ON dbo.docs (vec)");
        assertDialect(SqlDialect.ORACLE, "CREATE VECTOR INDEX vec_idx ON app.docs (vec)"
                + " ORGANIZATION INMEMORY NEIGHBOR GRAPH");
        assertDialect(SqlDialect.SQLSERVER, "SELECT search.distance FROM VECTOR_SEARCH(TABLE = dbo.docs,"
                + " COLUMN = vec, SIMILAR_TO = @needle, METRIC = 'cosine', TOP_N = 3) search");
        assertDialect(SqlDialect.SPARK, "SELECT 'VECTOR_SEARCH(TABLE = docs)' AS example");
    }

    @Test
    public void distinguishesExactVectorSignaturesFromNullSafeEquality() {
        assertDialect(SqlDialect.ORACLE, "SELECT d.embedding <=> TO_VECTOR(q.text, 8, FLOAT32, DENSE)"
                + " FROM app.docs d CROSS JOIN app.queries q");
        assertDialect(SqlDialect.ORACLE, "SELECT FROM_VECTOR(embedding RETURNING CLOB FORMAT SPARSE)"
                + " FROM app.docs");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT embedding <=> '[1,2,3]'::halfvec FROM app.docs");
        assertDialect(SqlDialect.SQLSERVER, "SELECT VECTOR_DISTANCE('cosine', left_vector, right_vector)"
                + " AS distance FROM docs");
        assertDialect(SqlDialect.SQLSERVER, "CREATE TABLE dbo.docs (embedding VECTOR(32, FLOAT16))");
        assertDialect(SqlDialect.SPARK, "SELECT a <=> b AS matches FROM docs");
        assertDialect(SqlDialect.SPARK, "SELECT 'VECTOR_DISTANCE(''cosine'',a,b)' AS example");
        assertDialect(SqlDialect.SPARK, "SELECT \"VECTOR_DISTANCE('cosine',a,b)\" AS example");
    }

    @Test
    public void detectsOracleVectorSchemasWithoutClaimingOtherVectorFunctions() {
        assertDialect(SqlDialect.ORACLE, "CREATE VECTOR INDEX doc_ivf_idx ON docs (embedding)"
                + " INCLUDE (category_id) ORGANIZATION NEIGHBOR PARTITIONS"
                + " WITH TARGET ACCURACY 90 DISTANCE COSINE");
        assertDialect(SqlDialect.ORACLE, "CREATE TABLE docs (embedding VECTOR(*, FLOAT32, SPARSE))");
        assertDialect(SqlDialect.SPARK, "SELECT vector(384, float32) FROM docs");
    }

    @Test
    public void detectsPostgresVectorOperatorsWithoutStealingNullSafeEquality() {
        LineageResult nearest = LineSql.parse("SELECT id FROM search.items "
                + "ORDER BY embedding <-> '[1,2,3]' LIMIT 5");
        assertEquals(SqlDialect.POSTGRESQL, nearest.getDialect());
        assertEquals(StatementType.SELECT, nearest.getStatementType());
        assertEquals("embedding", nearest.getColumnUsages().get(0).getColumn().getName());

        assertDialect(SqlDialect.POSTGRESQL, "SELECT a OPERATOR(pg_catalog.=) b AS same_value FROM ods.pairs");
        assertDialect(SqlDialect.SPARK, "SELECT a <=> b AS same_value FROM ods.pairs");
        assertDialect(SqlDialect.SPARK, "SELECT 'embedding <-> vector' AS note");
        LineageResult index = LineSql.parse("CREATE INDEX ON search.items "
                + "USING hnsw (embedding vector_cosine_ops)");
        assertEquals(SqlDialect.POSTGRESQL, index.getDialect());
        assertEquals(StatementType.ALTER_TABLE, index.getStatementType());
        assertEquals("embedding", index.getColumnUsages().get(0).getColumn().getName());
    }

    @Test
    public void detectsStarRocksTemporaryPartitionStatements() {
        LineageResult read = LineSql.parse("SELECT id FROM mart.events TEMPORARY PARTITION(tp1)");
        assertEquals(SqlDialect.STARROCKS, read.getDialect());
        assertEquals(StatementType.SELECT, read.getStatementType());
        assertEquals("events", read.getInputTables().get(0).getName());
        assertDialect(SqlDialect.STARROCKS, "INSERT INTO mart.events TEMPORARY PARTITION(tp1) VALUES (5)");
        assertDialect(SqlDialect.STARROCKS, "SHOW TEMPORARY PARTITIONS FROM mart.events");
        assertDialect(SqlDialect.STARROCKS, "ALTER TABLE mart.events REPLACE PARTITION(p1) "
                + "WITH TEMPORARY PARTITION(tp1) PROPERTIES ('strict_range' = 'true')");
        assertDialect(SqlDialect.ORACLE, "SELECT id FROM mart.events PARTITION (p1)");
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM mart.events TEMPORARY PARTITION(tp1)' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM mart.events SAMPLE (5)' AS note");
    }

    @Test
    public void detectsOraclePartitionedJoinOutsideWindowExpressions() {
        LineageResult partitioned = LineSql.parse("SELECT c.day_id, s.amount FROM dims.calendar c "
                + "LEFT JOIN facts.sales s PARTITION BY (s.region) ON s.day_id = c.day_id");
        assertEquals(SqlDialect.ORACLE, partitioned.getDialect());
        assertEquals(StatementType.SELECT, partitioned.getStatementType());
        assertTrue(partitioned.getColumnUsages().stream()
                .anyMatch(usage -> "region".equals(usage.getColumn().getName())));
        assertDialect(SqlDialect.SPARK,
                "SELECT sum(amount) OVER (PARTITION BY (region)) AS total FROM facts.sales");
    }

    @Test
    public void detectsHiveLateralTableValues() {
        LineageResult values = LineSql.parse("SELECT v.item FROM raw.docs d "
                + "LATERAL TABLE(VALUES(d.sku)) v(item)");
        assertEquals(SqlDialect.HIVE, values.getDialect());
        assertEquals(StatementType.SELECT, values.getStatementType());
        assertEquals("sku", values.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM TABLE(VALUES(1))' AS text_value");
    }

    @Test
    public void detectsOracleDataUseCaseDomainSyntax() {
        LineageResult domain = LineSql.parse("CREATE DOMAIN workflow.state AS ENUM (queued = 1, done = 2)");
        assertEquals(SqlDialect.ORACLE, domain.getDialect());
        assertEquals(StatementType.CONTROL, domain.getStatementType());
        assertDialect(SqlDialect.ORACLE, "ALTER DOMAIN workflow.state MODIFY DISPLAY LOWER(state)");
        assertDialect(SqlDialect.ORACLE, "DROP DOMAIN workflow.state FORCE PRESERVE");
        assertDialect(SqlDialect.POSTGRESQL, "ALTER DOMAIN workflow.state VALIDATE CONSTRAINT state_check");
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER DOMAIN workflow.state DROP DISPLAY' AS message");
    }

    @Test
    public void detectsPostgresSchemaDdlWithoutGenericObjectNames() {
        assertEquals(StatementType.CONTROL, LineSql.parse("CREATE EVENT TRIGGER audit_ddl "
                + "ON ddl_command_end WHEN TAG IN ('CREATE VIEW') EXECUTE FUNCTION audit.log_ddl()")
                .getStatementType());
        assertDialect(SqlDialect.POSTGRESQL, "ALTER EVENT TRIGGER audit_ddl ENABLE REPLICA");
        assertDialect(SqlDialect.POSTGRESQL, "ALTER TYPE app.status ADD VALUE IF NOT EXISTS 'queued' AFTER 'new'");
        assertDialect(SqlDialect.POSTGRESQL, "CREATE TYPE app.time_range AS RANGE (subtype = timestamptz)");
        assertDialect(SqlDialect.POSTGRESQL, "ALTER DOMAIN app.amount VALIDATE CONSTRAINT nonnegative");
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE EVENT TRIGGER audit_ddl' AS note");
    }

    @Test
    public void detectsSqlServerNativeCursorControl() {
        LineageResult fetch = LineSql.parse("FETCH ABSOLUTE @offset FROM report_cursor INTO @amount");
        assertEquals(SqlDialect.SQLSERVER, fetch.getDialect());
        assertEquals(StatementType.CONTROL, fetch.getStatementType());
        assertDialect(SqlDialect.SQLSERVER, "FETCH LAST FROM GLOBAL report_cursor");
        assertDialect(SqlDialect.SPARK, "FETCH report_cursor INTO @amount");
        assertDialect(SqlDialect.SQLSERVER, "DECLARE report_cursor CURSOR LOCAL SCROLL STATIC TYPE_WARNING "
                + "FOR SELECT amount FROM warehouse.orders");
        assertDialect(SqlDialect.SPARK, "SELECT 'FETCH LAST FROM GLOBAL report_cursor' AS notes");
        assertDialect(SqlDialect.POSTGRESQL,
                "DECLARE report_cursor CURSOR FOR SELECT type_warning::text FROM warehouse.orders");
    }

    @Test
    public void detectsFlinkMaterializedTableLifecycleStatements() {
        LineageResult alter = LineSql.parse("ALTER MATERIALIZED TABLE mart.sales "
                + "AS SELECT product_id FROM staging.orders");
        assertEquals(SqlDialect.FLINK, alter.getDialect());
        assertEquals(StatementType.ALTER_TABLE, alter.getStatementType());
        assertEquals("orders", alter.getInputTables().get(0).getName());
        assertDialect(SqlDialect.FLINK, "SHOW CREATE OR ALTER MATERIALIZED TABLE mart.sales");
        assertDialect(SqlDialect.FLINK, "SHOW MATERIALIZED TABLES IN mart");
        assertDialect(SqlDialect.FLINK, "DROP MATERIALIZED TABLE IF EXISTS mart.sales");
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER MATERIALIZED TABLE mart.sales SUSPEND' AS message");
    }

    @Test
    public void detectsPostgresUnicodePredicateOutsideQuotedText() {
        LineageResult result = LineSql.parse("SELECT NORMALIZE(name, NFKC) AS clean_name "
                + "FROM ods.contacts WHERE name IS NOT NFKC NORMALIZED");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals("name", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.SPARK, "SELECT 'name IS NOT NFKC NORMALIZED' AS message");
    }

    @Test
    public void detectsPostgresXmlTableWithDialectSpecificSyntax() {
        LineageResult exists = LineSql.parse("SELECT XMLEXISTS('/row' PASSING d.payload BY REF) "
                + "AS has_row FROM raw.docs d");
        assertEquals(SqlDialect.POSTGRESQL, exists.getDialect());
        assertEquals(StatementType.SELECT, exists.getStatementType());
        assertEquals("payload", exists.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.POSTGRESQL, "SELECT xt.code FROM raw.docs d CROSS JOIN "
                + "XMLTABLE('/row' PASSING BY REF d.payload COLUMNS code text) xt");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT xt.code FROM raw.docs d CROSS JOIN "
                + "XMLTABLE('/row' PASSING d.payload::xml COLUMNS code text) xt");
        assertDialect(SqlDialect.ORACLE, "SELECT xt.code FROM raw.docs d CROSS JOIN "
                + "XMLTABLE('/row' PASSING BY VALUE d.payload COLUMNS code VARCHAR2(32) "
                + "PATH 'literal ::xml BY REF COLUMNS') xt");
        assertDialect(SqlDialect.ORACLE, "SELECT xt.code FROM raw.docs d CROSS JOIN "
                + "XMLTABLE('/row' PASSING d.payload RETURNING SEQUENCE BY REF "
                + "COLUMNS code VARCHAR2(32)) xt");
    }

    @Test
    public void detectsPostgresJsonbAndTextSearchFunctions() {
        String update = "UPDATE mart.events e SET payload = pg_catalog.jsonb_set_lax(e.payload,"
                + " ARRAY['value'], to_jsonb(s.new_value)) FROM staging.event_changes s WHERE e.id = s.event_id";
        LineageResult result = LineSql.parse(update);
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.UPDATE, result.getStatementType());
        assertEquals(2, result.getColumnLineage().get(0).getSources().size());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.POSTGRESQL, "SELECT j.sku FROM ods.orders o"
                + " CROSS JOIN LATERAL jsonb_to_recordset(o.items) AS j(sku text)");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT id FROM ods.events"
                + " WHERE to_tsvector('simple', body) @@ websearch_to_tsquery('simple', query_text)");
        assertDialect(SqlDialect.SQLSERVER, "SELECT @@ROWCOUNT FROM dbo.events WITH (NOLOCK)");
        assertDialect(SqlDialect.SPARK, "SELECT 'jsonb_set(x,y,z)' AS message");
    }

    @Test
    public void distinguishesStarRocksAndSparkAsofJoins() {
        String starRocks = "SELECT h.id, p.price FROM ods.holdings h ASOF LEFT JOIN ods.prices p"
                + " ON h.ticker = p.ticker AND h.observed_at >= p.quoted_at";
        LineageResult result = LineSql.parse(starRocks);
        assertEquals(SqlDialect.STARROCKS, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("prices", result.getColumnLineage().get(1).getSources().get(0).getTable().getName());
        assertEquals(4, result.getColumnUsages().size());
        assertDialect(SqlDialect.STARROCKS, "SELECT h.id FROM ods.holdings h ASOF JOIN ods.prices p"
                + " ON h.ticker = p.ticker AND h.observed_at >= p.quoted_at");
        assertDialect(SqlDialect.STARROCKS, "SELECT p.price FROM ods.holdings h RIGHT ANTI JOIN ods.prices p"
                + " ON h.ticker = p.ticker");
        assertDialect(SqlDialect.SPARK, "SELECT h.id FROM ods.holdings h LEFT ASOF JOIN ods.prices p"
                + " MATCH_CONDITION (h.observed_at >= p.quoted_at) ON h.ticker = p.ticker");
    }

    private void assertPostgresCopyScript(List<LineageResult> results) {
        assertEquals(4, results.size());
        assertEquals(StatementType.CONTROL, results.get(0).getStatementType());
        assertEquals(StatementType.LOAD_DATA, results.get(1).getStatementType());
        assertEquals(StatementType.LOAD_DATA, results.get(2).getStatementType());
        assertEquals(StatementType.SELECT, results.get(3).getStatementType());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
        assertEquals("notes", results.get(1).getOutputTables().get(0).getName());
        assertEquals("note", results.get(1).getColumnUsages().get(0).getColumn().getName());
        assertEquals(0, results.get(1).getInputTables().size());
        assertEquals("empty", results.get(2).getOutputTables().get(0).getName());
        assertEquals("events", results.get(3).getInputTables().get(0).getName());
    }

    @Test
    public void detectsPostgreSqlJsonbEvenWhenJsonTableIsSharedSyntax() {
        LineageResult result = LineSql.parse(
                "SELECT jt.customer FROM ods.orders o CROSS JOIN "
                        + "JSON_TABLE(o.payload::jsonb, '$' COLUMNS (customer text PATH '$.customer')) jt");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("payload", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));

        assertDialect(SqlDialect.POSTGRESQL, "SELECT payload #>> '{customer}' AS customer FROM ods.orders");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT payload::jsonb->>'$.literal_key' AS value FROM ods.orders");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT jsonb_path_exists(payload, '$.id') AS present FROM ods.orders");
        assertDialect(SqlDialect.MYSQL,
                "SELECT jt.customer FROM ods.orders o CROSS JOIN "
                        + "JSON_TABLE(o.payload, '$' COLUMNS (customer varchar(100) PATH '$.cast::jsonb')) jt");
        assertDialect(SqlDialect.SQLSERVER, "GRANT SELECT ON OBJECT::jsonb TO reader");
    }

    @Test
    public void detectsPostgreSqlGeneratedExpressionChanges() {
        LineageResult result = LineSql.parse(
                "ALTER TABLE mart.metrics ALTER COLUMN net SET EXPRESSION AS (amount - discount)");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.ALTER_TABLE, result.getStatementType());
        assertEquals("net", result.getColumnLineage().get(0).getTarget().getName());
        assertEquals(2, result.getColumnLineage().get(0).getSources().size());
        assertEquals(3, result.getColumnUsages().size());
        assertDialect(SqlDialect.POSTGRESQL, "ALTER TABLE mart.metrics ALTER net DROP EXPRESSION IF EXISTS");
    }

    @Test
    public void autoDetectsPostgreSqlPolicyAndReplicationStatements() {
        LineageResult policy = LineSql.parse("CREATE POLICY tenant_view ON mart.events USING (tenant_id = 42)");
        assertEquals(SqlDialect.POSTGRESQL, policy.getDialect());
        assertEquals(StatementType.ALTER_TABLE, policy.getStatementType());
        assertEquals("tenant_id", policy.getColumnUsages().get(0).getColumn().getName());

        LineageResult publication = LineSql.parse(
                "CREATE PUBLICATION events FOR TABLE mart.events (id) WHERE (active)");
        assertEquals(SqlDialect.POSTGRESQL, publication.getDialect());
        assertEquals(StatementType.READ_METADATA, publication.getStatementType());
        assertEquals("events", publication.getInputTables().get(0).getName());
        assertEquals("id", publication.getColumnUsages().get(0).getColumn().getName());
        assertEquals("active", publication.getColumnUsages().get(1).getColumn().getName());

        assertDialect(SqlDialect.POSTGRESQL, "ALTER TABLE mart.events FORCE ROW LEVEL SECURITY");
        assertDialect(SqlDialect.POSTGRESQL, "ALTER SUBSCRIPTION events DISABLE");
        assertDialect(SqlDialect.POSTGRESQL, "ALTER DEFAULT PRIVILEGES GRANT SELECT ON TABLES TO analyst");
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE PUBLICATION ignored' FROM mart.events");
    }

    @Test
    public void detectsSqlServerTemporalSyntaxWithoutChangingFlinkAsOf() {
        LineageResult changes = LineSql.parse(
                "ALTER TABLE dbo.events SET (SYSTEM_VERSIONING = ON (HISTORY_TABLE = history.events))");
        assertEquals(SqlDialect.SQLSERVER, changes.getDialect());
        assertEquals(StatementType.ALTER_TABLE, changes.getStatementType());
        assertEquals(2, changes.getOutputTables().size());
        assertEquals("history", changes.getOutputTables().get(1).getSchema());

        assertDialect(SqlDialect.SQLSERVER, "SELECT id FROM dbo.events FOR SYSTEM_TIME ALL");
        assertDialect(SqlDialect.SQLSERVER, "SELECT id FROM dbo.events FOR SYSTEM_TIME AS OF @version_time");
        assertDialect(SqlDialect.FLINK, "SELECT id FROM lake.events FOR SYSTEM_TIME AS OF TIMESTAMP '2026-11-16 00:00:00'");
        assertDialect(SqlDialect.SPARK, "SELECT 'FOR SYSTEM_TIME ALL' FROM ods.events");
    }

    @Test
    public void autoDetectsSqlServerUpdateWithCaptureAndReturnedOutput() {
        LineageResult result = LineSql.parse(
                "UPDATE dbo.orders SET amount = a.amount "
                        + "OUTPUT deleted.amount INTO audit.changes(old_amount) OUTPUT inserted.amount AS new_amount "
                        + "FROM staging.adjustments a WHERE dbo.orders.id = a.order_id");
        assertEquals(SqlDialect.SQLSERVER, result.getDialect());
        assertEquals(StatementType.UPDATE, result.getStatementType());
        assertEquals(2, result.getOutputTables().size());
        assertEquals("changes", result.getOutputTables().get(1).getName());
        assertEquals(3, result.getColumnLineage().size());
        assertEquals("old_amount", result.getColumnLineage().get(1).getTarget().getName());
        assertEquals("new_amount", result.getColumnLineage().get(2).getTarget().getName());
        assertEquals("adjustments", result.getColumnLineage().get(2).getSources().get(0).getTable().getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));

        assertDialect(SqlDialect.POSTGRESQL,
                "UPDATE ods.events SET output = inserted.value::int FROM dim.changes inserted");
    }

    @Test
    public void analyzesSparkLiteralSqlWithoutUsingItsBodyForDialectDetection() {
        LineageResult query = LineSql.parse("EXECUTE IMMEDIATE 'SELECT id FROM dual WHERE enabled = 1'");
        assertEquals(SqlDialect.SPARK, query.getDialect());
        assertEquals(StatementType.SELECT, query.getStatementType());
        assertEquals("dual", query.getInputTables().get(0).getName());
        assertEquals("id", query.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("enabled", query.getColumnUsages().get(0).getColumn().getName());

        LineageResult bound = LineSql.parse(
                "EXECUTE IMMEDIATE 'SELECT id FROM ods.events WHERE id = ?' USING 1",
                SqlDialect.SPARK);
        assertEquals(StatementType.UNKNOWN, bound.getStatementType());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", bound.getDiagnostics().get(0).getCode());

        LineageResult templated = LineSql.parse(
                "EXECUTE IMMEDIATE 'SELECT id FROM ${source_table}'", SqlDialect.SPARK);
        assertEquals(StatementType.UNKNOWN, templated.getStatementType());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", templated.getDiagnostics().get(0).getCode());
        assertEquals(0, templated.getInputTables().size());
    }

    @Test
    public void mapsSparkUnnamedWriteExpressionsWithoutPartialLineage() {
        LineageResult result = LineSql.parse(
                "INSERT INTO mart.audit (bucket, net_amount, order_id) "
                        + "SELECT 1, amount - discount, id FROM ods.orders", SqlDialect.SPARK);
        assertEquals(StatementType.INSERT, result.getStatementType());
        assertEquals(3, result.getColumnLineage().size());
        assertEquals("net_amount", result.getColumnLineage().get(1).getTarget().getName());
        assertEquals(2, result.getColumnLineage().get(1).getSources().size());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> "COLUMN_LINEAGE_PARTIAL".equals(d.getCode())));
    }

    @Test
    public void detectsOracleRelationsWithDatabaseLinks() {
        LineageResult result = LineSql.parse(
                "SELECT r.id FROM ods.remote_orders@warehouse.example.com r WHERE r.active = 1");
        assertEquals(SqlDialect.ORACLE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("remote_orders", result.getInputTables().get(0).getName());
        assertEquals("id", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("active", result.getColumnUsages().get(0).getColumn().getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.SPARK, "SELECT \"from ods.orders@warehouse\" AS label FROM ods.events");
    }

    @Test
    public void detectsSqlServerTableVariablesThroughTheScriptApi() {
        List<LineageResult> results = LineSql.parseScript(scriptFixture("/sql/sqlserver/table_variable_batch.sql"));
        assertEquals(3, results.size());
        assertEquals(StatementType.CREATE_TABLE, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.SQLSERVER, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
        assertEquals("@events", results.get(1).getOutputTables().get(0).getName());
        assertEquals("events", results.get(1).getColumnLineage().get(0).getSources().get(0).getTable().getName());
        assertEquals("@events", results.get(2).getInputTables().get(0).getName());
        assertDialect(SqlDialect.SPARK, "SELECT id INTO @saved_id FROM ods.events");
    }

    @Test
    public void autoDetectedSqlServerDmlUsesRegisteredParser() {
        LineageResult result = LineSql.parse(
                "update [ads].[user_summary] set [user_name] = u.[name] "
                        + "from [ods].[users] u where [user_summary].[user_id] = u.[id]");

        assertEquals(SqlDialect.SQLSERVER, result.getDialect());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("user_summary", result.getOutputTables().get(0).getName());
    }

    @Test
    public void parseScriptAutoDetectsEachStatementIndependently() {
        List<LineageResult> results = LineSql.parseScript(
                "create table ods.users(id bigint) stored as parquet;"
                        + "create table ods_users(id bigint) with ('connector' = 'kafka');"
                        + "select top 10 id from dbo.users;");

        assertEquals(3, results.size());
        assertEquals(SqlDialect.HIVE, results.get(0).getDialect());
        assertEquals(SqlDialect.FLINK, results.get(1).getDialect());
        assertEquals(SqlDialect.SQLSERVER, results.get(2).getDialect());
    }

    @Test
    public void parseScriptKeepsPartialResultsAfterBadStatement() {
        List<LineageResult> results = LineSql.parseScript(
                "select id from ods.users;"
                        + "select id from (;"
                        + "create table ods_users(id bigint) with ('connector' = 'kafka');");

        assertEquals(3, results.size());
        assertEquals(StatementType.SELECT, results.get(0).getStatementType());
        assertFalse(results.get(1).getDiagnostics().isEmpty());
        assertEquals(DiagnosticSeverity.ERROR, results.get(1).getDiagnostics().get(0).getSeverity());
        assertEquals(SqlDialect.FLINK, results.get(2).getDialect());
    }

    @Test
    public void parseScriptHandlesSqlServerGoBatchesThroughPublicApi() {
        List<LineageResult> results = LineSql.parseScript(
                "select [id] from [dbo].[customers] /* outer /* inner */\nGO\n*/\n"
                        + "GO 3 -- repeat\n"
                        + "insert into [dbo].[archive] ([id]) select [id] from [dbo].[orders]\n"
                        + "GO", SqlDialect.SQLSERVER);

        assertEquals(2, results.size());
        assertEquals("customers", results.get(0).getInputTables().get(0).getName());
        assertEquals("orders", results.get(1).getInputTables().get(0).getName());
        assertEquals("archive", results.get(1).getOutputTables().get(0).getName());
        assertFalse(results.get(1).getColumnLineage().isEmpty());
    }

    @Test
    public void detectsSqlServerGoInScriptsWithTransactSqlSyntax() {
        List<LineageResult> results = LineSql.parseScript(scriptFixture("/sql/sqlserver/go_routine_and_query.sql"));

        assertEquals(2, results.size());
        LineageResult routine = results.get(0);
        assertEquals(SqlDialect.SQLSERVER, routine.getDialect());
        assertEquals(StatementType.CREATE_ROUTINE, routine.getStatementType());
        assertEquals("events", routine.getInputTables().get(0).getName());
        assertEquals("audit", routine.getOutputTables().get(0).getName());
        assertFalse(routine.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));

        assertEquals(SqlDialect.SQLSERVER, results.get(1).getDialect());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("audit", results.get(1).getInputTables().get(0).getName());

        List<LineageResult> top = LineSql.parseScript("SELECT TOP (3) id FROM dbo.events\nGO\nSELECT id FROM dbo.audit");
        assertEquals(2, top.size());
        assertEquals(SqlDialect.SQLSERVER, top.get(0).getDialect());
        assertEquals(SqlDialect.SQLSERVER, top.get(1).getDialect());

        assertDialect(SqlDialect.SPARK, "SELECT\ngo\nFROM ods.events");
    }

    @Test
    public void autoDetectsOracleQuotesWithoutSplittingLiteralContent() {
        List<LineageResult> results = LineSql.parseScript(
                "SELECT o.id FROM ods.orders o WHERE o.note = "
                        + "q'[It's paid; /*!80000 DELETE FROM ghost.orders */ $tag$]'; "
                        + "SELECT id::int FROM staging.events;");

        assertEquals(2, results.size());
        LineageResult oracle = results.get(0);
        assertEquals(SqlDialect.ORACLE, oracle.getDialect());
        assertEquals(StatementType.SELECT, oracle.getStatementType());
        assertEquals(1, oracle.getInputTables().size());
        assertEquals("orders", oracle.getInputTables().get(0).getName());
        assertEquals("note", oracle.getColumnUsages().get(0).getColumn().getName());
        assertFalse(oracle.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertEquals(SqlDialect.POSTGRESQL, results.get(1).getDialect());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertDialect(SqlDialect.SPARK, "SELECT id FROM staging.events WHERE note = 'q''!not Oracle!'''");
        assertDialect(SqlDialect.SQLSERVER, "SELECT [q'!not Oracle!'] FROM dbo.orders");
    }

    @Test
    public void autoDetectsAnOracleBlockWithAlternativeQuotedText() {
        List<LineageResult> results = LineSql.parseScript(
                "BEGIN EXECUTE IMMEDIATE q'[SELECT 'literal' FROM ghost.rows;]'; "
                        + "INSERT INTO mart.audit (id) SELECT id FROM ods.orders; END;");

        assertEquals(1, results.size());
        LineageResult oracle = results.get(0);
        assertEquals(SqlDialect.ORACLE, oracle.getDialect());
        assertEquals(StatementType.CONTROL, oracle.getStatementType());
        assertEquals(1, oracle.getInputTables().size());
        assertEquals("orders", oracle.getInputTables().get(0).getName());
        assertEquals("audit", oracle.getOutputTables().get(0).getName());
        assertFalse(oracle.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
    }

    @Test
    public void oceanBaseInfersOracleForStandaloneBlocksAndKeepsTransactionsSeparate() {
        List<LineageResult> block = LineSql.parseScript(
                "BEGIN INSERT INTO mart.audit (id) SELECT id FROM ods.orders; END",
                SqlDialect.OCEANBASE);
        assertEquals(1, block.size());
        assertEquals(SqlDialect.OCEANBASE, block.get(0).getDialect());
        assertEquals(StatementType.CONTROL, block.get(0).getStatementType());
        assertEquals("orders", block.get(0).getInputTables().get(0).getName());
        assertEquals("audit", block.get(0).getOutputTables().get(0).getName());
        assertFalse(block.get(0).getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));

        List<LineageResult> transaction = LineSql.parseScript(
                "BEGIN; SELECT id FROM ods.orders; COMMIT;", SqlDialect.OCEANBASE);
        assertEquals(3, transaction.size());
        assertEquals(StatementType.CONTROL, transaction.get(0).getStatementType());
        assertEquals(StatementType.SELECT, transaction.get(1).getStatementType());
        assertEquals(StatementType.CONTROL, transaction.get(2).getStatementType());

        assertEquals(3, LineSql.parseScript("BEGIN; SELECT id FROM dual; END;").size());
    }

    @Test
    public void autoDetectsPlPgSqlRoutineWithBodyBeforeLanguage() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE FUNCTION mart.score(IN n int DEFAULT 0) RETURNS int AS $body$ "
                        + "BEGIN PERFORM 'CREATE PUBLICATION fake;'; RETURN n; END; "
                        + "$body$ LANGUAGE plpgsql IMMUTABLE; "
                        + "SELECT id FROM ods.events;");

        assertEquals(2, results.size());
        assertEquals(SqlDialect.POSTGRESQL, results.get(0).getDialect());
        assertEquals(StatementType.CREATE_ROUTINE, results.get(0).getStatementType());
        assertFalse(results.get(0).getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("events", results.get(1).getInputTables().get(0).getName());
    }

    @Test
    public void keepsPostgreSqlAtomicRoutinesTogetherInAutomaticMode() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE PROCEDURE mart.refresh() LANGUAGE SQL BEGIN /* body */ ATOMIC "
                        + "SELECT CASE WHEN 1 = 1 THEN 'END;' ELSE '' END; "
                        + "INSERT INTO mart.audit (id) SELECT id FROM ods.events; END; "
                        + "SELECT id FROM staging.events;");

        assertEquals(2, results.size());
        LineageResult routine = results.get(0);
        assertEquals(SqlDialect.POSTGRESQL, routine.getDialect());
        assertEquals(StatementType.CREATE_ROUTINE, routine.getStatementType());
        assertEquals("events", routine.getInputTables().get(0).getName());
        assertEquals("audit", routine.getOutputTables().get(0).getName());
        assertEquals("id", routine.getColumnLineage().get(0).getTarget().getName());
        assertFalse(routine.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
    }

    @Test
    public void parseScriptAutoDetectsPostgresDollarQuotesAmongOtherDialects() {
        List<LineageResult> results = LineSql.parseScript(
                "DO $Body$ BEGIN PERFORM 'from dual;'; END; $Body$;"
                        + "SELECT id FROM ods.a;"
                        + "CREATE TABLE event_sink(id bigint) WITH ('connector' = 'kafka');");

        assertEquals(3, results.size());
        assertEquals(SqlDialect.POSTGRESQL, results.get(0).getDialect());
        assertEquals(StatementType.CONTROL, results.get(0).getStatementType());
        assertEquals(SqlDialect.FLINK, results.get(1).getDialect());
        assertEquals(SqlDialect.FLINK, results.get(2).getDialect());
    }

    @Test
    public void detectsSqlServerTopWithoutTableHints() {
        LineageResult query = LineSql.parse("SELECT TOP (10) u.id AS user_id FROM dbo.users u ORDER BY u.id");
        assertEquals(SqlDialect.SQLSERVER, query.getDialect());
        assertEquals("users", query.getInputTables().get(0).getName());
        assertEquals("user_id", query.getColumnLineage().get(0).getTarget().getName());

        LineageResult write = LineSql.parse(
                "UPDATE TOP (@count) dbo.users SET retries = retries + 1 WHERE active = 1");
        assertEquals(SqlDialect.SQLSERVER, write.getDialect());
        assertEquals("users", write.getOutputTables().get(0).getName());
        assertEquals("retries", write.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("active", write.getColumnUsages().get(0).getColumn().getName());
    }

    @Test
    public void autoDetectsSqlServerScopedPermissionsAndObjectTransfer() {
        LineageResult grant = LineSql.parse("GRANT SELECT ON OBJECT::dbo.orders TO reporting_role");
        assertEquals(SqlDialect.SQLSERVER, grant.getDialect());
        assertEquals("orders", grant.getOutputTables().get(0).getName());

        LineageResult schemaGrant = LineSql.parse("GRANT SELECT ON SCHEMA::sales TO reporting_role");
        assertEquals(SqlDialect.SQLSERVER, schemaGrant.getDialect());
        assertEquals(StatementType.CONTROL, schemaGrant.getStatementType());
        assertEquals(0, schemaGrant.getOutputTables().size());

        LineageResult transfer = LineSql.parse("ALTER SCHEMA archive TRANSFER OBJECT::dbo.orders");
        assertEquals(SqlDialect.SQLSERVER, transfer.getDialect());
        assertEquals("dbo", transfer.getInputTables().get(0).getSchema());
        assertEquals("archive", transfer.getOutputTables().get(0).getSchema());
    }

    @Test
    public void keepsSqlServerRoutineBodyTogetherUntilGo() {
        List<LineageResult> results = LineSql.parseScript(
                "ALTER /* module */ PROC dbo.rollup AS BEGIN\n"
                        + "SET NOCOUNT ON;\n"
                        + "INSERT INTO mart.rollup SELECT id FROM ods.orders;\n"
                        + "SELECT id FROM ods.customers;\n"
                        + "END;\n"
                        + "GO\n"
                        + "SELECT id FROM mart.rollup;",
                SqlDialect.SQLSERVER);

        assertEquals(2, results.size());
        assertEquals(StatementType.ALTER_ROUTINE, results.get(0).getStatementType());
        assertEquals("orders", results.get(0).getInputTables().get(0).getName());
        assertEquals("customers", results.get(0).getInputTables().get(1).getName());
        assertEquals("rollup", results.get(0).getOutputTables().get(0).getName());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
    }

    @Test
    public void splitsSqlServerStringsWithWindowsPathsBeforeGo() {
        List<LineageResult> results = LineSql.parseScript(
                "SELECT id FROM dbo.files WHERE path = N'C:\\temp\\'\n"
                        + "GO\n"
                        + "SELECT \"order id\" FROM dbo.\"order history\";",
                SqlDialect.SQLSERVER);

        assertEquals(2, results.size());
        assertEquals("files", results.get(0).getInputTables().get(0).getName());
        assertEquals("order history", results.get(1).getInputTables().get(0).getName());
        assertEquals("order id", results.get(1).getColumnLineage().get(0).getTarget().getName());
    }

    @Test
    public void autoDetectsHiveWriteOrderingWithIcebergStorage() {
        LineageResult result = LineSql.parse("CREATE TABLE mart.events (id BIGINT, ts TIMESTAMP) "
                + "WRITE LOCALLY ORDERED BY ZORDER(id, ts) STORED BY ICEBERG");
        assertEquals(SqlDialect.HIVE, result.getDialect());
        assertEquals(StatementType.CREATE_TABLE, result.getStatementType());
        assertEquals("events", result.getOutputTables().get(0).getName());
        assertEquals("id", result.getColumnUsages().get(0).getColumn().getName());
        assertEquals("ts", result.getColumnUsages().get(1).getColumn().getName());
    }

    @Test
    public void keepsDefaultFlinkStatementSetAndTemporaryViewLineage() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TEMPORARY VIEW staged AS SELECT order_id AS id FROM ods.orders;"
                        + "EXECUTE STATEMENT SET BEGIN "
                        + "INSERT INTO mart.order_events (id) SELECT id FROM staged; "
                        + "INSERT OVERWRITE mart.order_snapshot (id) SELECT id FROM staged; END;"
                        + "SELECT id FROM ods.footer;");

        assertEquals(3, results.size());
        assertEquals(SqlDialect.FLINK, results.get(0).getDialect());
        LineageResult statementSet = results.get(1);
        assertEquals(SqlDialect.FLINK, statementSet.getDialect());
        assertEquals(StatementType.INSERT, statementSet.getStatementType());
        assertEquals(2, statementSet.getOutputTables().size());
        assertEquals("orders", statementSet.getInputTables().get(0).getName());
        assertEquals("order_id", statementSet.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("order_id", statementSet.getColumnLineage().get(1).getSources().get(0).getName());
        assertEquals("footer", results.get(2).getInputTables().get(0).getName());
    }

    @Test
    public void keepsMySqlExecutableCommentsAsStatementsThroughPublicApi() {
        List<LineageResult> results = LineSql.parseScript(
                "/* ordinary note */; /*!40101 SET NAMES utf8mb4 */; "
                        + "/*!80000 SELECT id FROM ods.users WHERE active = 1 */;");
        assertEquals(2, results.size());
        assertEquals(SqlDialect.MYSQL, results.get(0).getDialect());
        assertEquals(StatementType.CONTROL, results.get(0).getStatementType());
        assertEquals(SqlDialect.MYSQL, results.get(1).getDialect());
        assertEquals("users", results.get(1).getInputTables().get(0).getName());
        assertEquals("id", results.get(1).getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("active", results.get(1).getColumnUsages().get(0).getColumn().getName());

        LineageResult literal = LineSql.parse(
                "SELECT CONCAT(payload, '/*!80000 leave this literal */') AS label FROM ods.users",
                SqlDialect.MYSQL);
        assertFalse(literal.getColumnLineage().isEmpty());
        assertEquals("CONCAT(payload,'/*!80000 leave this literal */')",
                literal.getColumnLineage().get(0).getExpression());
    }

    @Test
    public void keepsOracleAndOceanBaseOracleBlocksTogether() {
        String script = "DECLARE v NUMBER;\n"
                + "BEGIN\n"
                + "  INSERT INTO mart.audit (id) SELECT id FROM ods.orders;\n"
                + "  BEGIN NULL; END;\n"
                + "END;\n/\n"
                + "SELECT id FROM mart.audit;";
        assertOracleBlockScript(LineSql.parseScript(script, SqlDialect.ORACLE), SqlDialect.ORACLE);

        ParseOptions oceanBase = ParseOptions.builder()
                .dialectHints(Collections.singletonList(SqlDialect.OCEANBASE))
                .dialectOption("oceanbase.compatibilityMode", "oracle")
                .build();
        assertOracleBlockScript(LineSql.parseScript(script, oceanBase), SqlDialect.OCEANBASE);
    }

    private static void assertOracleBlockScript(List<LineageResult> results, SqlDialect dialect) {
        assertEquals(2, results.size());
        assertEquals(dialect, results.get(0).getDialect());
        assertEquals(StatementType.CONTROL, results.get(0).getStatementType());
        assertEquals("orders", results.get(0).getInputTables().get(0).getName());
        assertEquals("audit", results.get(0).getOutputTables().get(0).getName());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("audit", results.get(1).getInputTables().get(0).getName());
    }

    @Test
    public void keepsOracleAndPostgreSqlStandardStringBoundaries() {
        String standard = "SELECT id FROM ods.files WHERE archive_path = 'C:\\exports\\'; "
                + "SELECT id FROM ods.next_file;";
        for (SqlDialect dialect : new SqlDialect[]{SqlDialect.ORACLE, SqlDialect.POSTGRESQL}) {
            List<LineageResult> results = LineSql.parseScript(standard, dialect);
            assertEquals(2, results.size());
            assertEquals("archive_path", results.get(0).getColumnUsages().get(0).getColumn().getName());
            assertEquals("next_file", results.get(1).getInputTables().get(0).getName());
        }

        List<LineageResult> escaped = LineSql.parseScript(
                "SELECT id FROM ods.files WHERE owner = E'O\\'Brien; saved'; SELECT id FROM ods.next_file;",
                SqlDialect.POSTGRESQL);
        assertEquals(2, escaped.size());
        assertEquals("owner", escaped.get(0).getColumnUsages().get(0).getColumn().getName());
        assertEquals("next_file", escaped.get(1).getInputTables().get(0).getName());
    }

    @Test
    public void keepsAlternativeOracleQuotesThroughScriptSplitting() {
        List<LineageResult> results = LineSql.parseScript(
                "SELECT id FROM ods.orders WHERE note = q'[O'Brien;\n/\ninside]';"
                        + "SELECT id FROM ods.refunds WHERE note = nq'!it's paid;!';",
                SqlDialect.ORACLE);
        assertEquals(2, results.size());
        assertEquals("orders", results.get(0).getInputTables().get(0).getName());
        assertEquals("refunds", results.get(1).getInputTables().get(0).getName());
        assertEquals("note", results.get(0).getColumnUsages().get(0).getColumn().getName());
        assertEquals("note", results.get(1).getColumnUsages().get(0).getColumn().getName());
    }

    @Test
    public void mysqlUnframedProcedureKeepsStaticStatementsAndStopsAtItsEnd() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE PROCEDURE mart.refresh(IN minimum DECIMAL(12, 2)) "
                        + "COMMENT 'reload' MODIFIES SQL DATA BEGIN "
                        + "INSERT INTO mart.audit (id) SELECT id FROM ods.events WHERE amount > minimum; "
                        + "END; SELECT CASE WHEN 1 = 1 THEN id END FROM ods.checkpoints;",
                SqlDialect.MYSQL);

        assertEquals(2, results.size());
        LineageResult routine = results.get(0);
        assertEquals(StatementType.CREATE_ROUTINE, routine.getStatementType());
        assertEquals("events", routine.getInputTables().get(0).getName());
        assertEquals("audit", routine.getOutputTables().get(0).getName());
        assertEquals("id", routine.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals(1, routine.getColumnUsages().size());
        assertEquals("amount", routine.getColumnUsages().get(0).getColumn().getName());
        assertEquals("checkpoints", results.get(1).getInputTables().get(0).getName());

        List<LineageResult> simple = LineSql.parseScript(
                "CREATE PROCEDURE mart.read_events() SELECT COUNT(*) begin FROM ods.events; "
                        + "SELECT id FROM ods.checkpoints;",
                SqlDialect.MYSQL);
        assertEquals(2, simple.size());
        assertEquals(StatementType.CREATE_ROUTINE, simple.get(0).getStatementType());
    }

    @Test
    public void mysqlClientDelimiterDirectivesPreserveRoutineAndSubsequentStatements() {
        String script = scriptFixture("/sql/mysql/client_delimiters.sql");
        assertMySqlDelimiterScript(LineSql.parseScript(script), SqlDialect.MYSQL);
        assertMySqlDelimiterScript(LineSql.parseScript(script, SqlDialect.MYSQL), SqlDialect.MYSQL);

        ParseOptions oceanBase = ParseOptions.builder()
                .dialectHints(Collections.singletonList(SqlDialect.OCEANBASE))
                .dialectOption("oceanbase.compatibilityMode", "mysql")
                .build();
        assertMySqlDelimiterScript(LineSql.parseScript(script, oceanBase), SqlDialect.OCEANBASE);

        assertDialect(SqlDialect.POSTGRESQL,
                "DO $body$ BEGIN RAISE NOTICE 'literal\nDELIMITER //\n'; END; $body$");
        assertDialect(SqlDialect.SPARK, "SELECT\ndelimiter alias\nFROM ods.events;");
    }

    private void assertMySqlDelimiterScript(List<LineageResult> results, SqlDialect dialect) {
        assertEquals(4, results.size());
        assertEquals(StatementType.DROP_ROUTINE, results.get(0).getStatementType());
        assertEquals(StatementType.CREATE_ROUTINE, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals(StatementType.SELECT, results.get(3).getStatementType());
        for (LineageResult result : results) {
            assertEquals(dialect, result.getDialect());
            assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        }
        assertEquals("orders", results.get(1).getInputTables().get(0).getName());
        assertEquals("audit", results.get(1).getOutputTables().get(0).getName());
        assertEquals("id", results.get(1).getColumnLineage().get(0).getTarget().getName());
        assertEquals("sales", results.get(2).getInputTables().get(0).getName());
        assertEquals("net", results.get(2).getColumnLineage().get(0).getTarget().getName());
        assertEquals(2, results.get(2).getColumnLineage().get(0).getSources().size());
        assertEquals("refunds", results.get(3).getInputTables().get(0).getName());
    }

    @Test
    public void splitsMySqlAndOceanBaseMySqlScriptsWithHashComments() {
        String script = "# ignored; SELECT id FROM ghost.t\n"
                + "SELECT amount--discount AS net FROM ods.sales; "
                + "# ignored too;\nSELECT id FROM ods.refunds;";
        assertMySqlCommentScript(LineSql.parseScript(script, SqlDialect.MYSQL));

        ParseOptions oceanBase = ParseOptions.builder()
                .dialectHints(Collections.singletonList(SqlDialect.OCEANBASE))
                .dialectOption("oceanbase.compatibilityMode", "mysql")
                .build();
        assertMySqlCommentScript(LineSql.parseScript(script, oceanBase));
    }

    private static void assertMySqlCommentScript(List<LineageResult> results) {
        assertEquals(2, results.size());
        assertEquals("sales", results.get(0).getInputTables().get(0).getName());
        assertEquals(2, results.get(0).getColumnLineage().get(0).getSources().size());
        assertEquals("refunds", results.get(1).getInputTables().get(0).getName());
    }

    private String scriptFixture(String path) {
        try (Scanner scanner = new Scanner(getClass().getResourceAsStream(path), "UTF-8")) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    private static void assertDialect(SqlDialect expected, String sql) {
        LineageResult result = LineSql.parse(sql);

        assertEquals(expected, result.getDialect());
    }
}
