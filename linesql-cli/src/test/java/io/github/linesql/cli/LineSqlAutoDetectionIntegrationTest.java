package io.github.linesql.cli;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
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
    public void tracksPostgreSqlPreparedStatementsDuringAutomaticDetection() {
        List<LineageResult> results = LineSql.parseScript(
                "SELECT id FROM public.accounts; PREPARE lookup(integer) AS "
                        + "SELECT id FROM ods.events WHERE id = $1; EXECUTE lookup(2); "
                        + "DEALLOCATE ALL; EXECUTE lookup(3)");
        assertEquals(5, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        LineageResult execute = results.get(2);
        assertEquals("events", execute.getInputTables().get(0).getName());
        assertEquals("id", execute.getColumnUsages().get(0).getColumn().getName());
        assertTrue(results.get(4).getInputTables().isEmpty());
        assertDialect(SqlDialect.POSTGRESQL, "PREPARE \"CaseSensitive\" (numeric(10,2)) AS SELECT $1");
        assertDialect(SqlDialect.SPARK, "SELECT 'a; PREPARE test AS SELECT 1;' AS note");
    }

    @Test
    public void detectsPostgreSqlExplainOptions() {
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (GENERIC_PLAN) SELECT id FROM public.events WHERE id = $1");
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (ANALYZE, SERIALIZE BINARY) SELECT id FROM public.events");
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (MEMORY ON) SELECT id FROM public.events");
        assertDialect(SqlDialect.POSTGRESQL,
                "EXPLAIN (COSTS OFF) DECLARE event_cursor CURSOR FOR SELECT id FROM public.events");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXPLAIN (GENERIC_PLAN)' AS note");
    }

    @Test
    public void detectsPostgreSqlSearchPathCommands() {
        assertDialect(SqlDialect.POSTGRESQL, "SET SCHEMA 'reporting'");
        assertDialect(SqlDialect.POSTGRESQL, "SET LOCAL search_path TO app, public");
        assertDialect(SqlDialect.POSTGRESQL, "SET SESSION search_path = DEFAULT");
        assertDialect(SqlDialect.POSTGRESQL, "RESET search_path");
        assertDialect(SqlDialect.SPARK, "SELECT 'SET LOCAL search_path TO app' AS note");
    }

    @Test
    public void detectsCatalogSwitchesFromNativeCommands() {
        assertDialect(SqlDialect.SPARK, "SET CATALOG `lake`");
        assertDialect(SqlDialect.FLINK, "USE CATALOG lake");
        assertDialect(SqlDialect.SPARK, "SET catalog=lake");
        List<LineageResult> starRocks = LineSql.parseScript(
                "SET CATALOG lake; USE CATALOG lake; USE ads; SELECT o.id FROM orders o;"
                        + "SELECT id FROM orders TEMPORARY PARTITION (p1)");
        for (LineageResult result : starRocks) {
            assertEquals(SqlDialect.STARROCKS, result.getDialect());
        }
        TableRef input = starRocks.get(3).getInputTables().get(0);
        assertEquals("lake", input.getCatalog());
        assertEquals("ads", input.getSchema());
        assertEquals("orders", input.getName());
    }

    @Test
    public void carriesMySqlSessionStateToPreparedSqlDuringAutomaticDetection() {
        List<LineageResult> results = LineSql.parseScript(
                "USE sales; SET @statement_text = 'SELECT id FROM orders WHERE state = ?'; "
                        + "PREPARE read_orders FROM @statement_text");
        assertEquals(3, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.MYSQL, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        LineageResult prepared = results.get(2);
        TableRef source = prepared.getInputTables().get(0);
        assertEquals("sales", source.getSchema());
        assertEquals("orders", source.getName());
        assertEquals("id", prepared.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("state", prepared.getColumnUsages().get(0).getColumn().getName());
        assertDialect(SqlDialect.SPARK, "SELECT 'text; PREPARE stmt FROM @fake;' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 1 /* PREPARE stmt FROM @fake; */");
    }

    @Test
    public void detectsPreparedSqlForms() {
        assertDialect(SqlDialect.MYSQL, "PREPARE stmt FROM 'SELECT id FROM app.users WHERE status = ?'");
        assertDialect(SqlDialect.MYSQL, "PREPARE stmt FROM @runtime_sql");
        assertDialect(SqlDialect.STARROCKS, "PREPARE stmt FROM SELECT id FROM app.users WHERE status = ?");
    }

    @Test
    public void usesStarRocksPreparedQueryEvidenceForEarlierSessionCommands() {
        String script = "USE CATALOG lake; USE staging; PREPARE lookup FROM "
                + "SELECT id FROM events WHERE kind = ?; USE reporting; EXECUTE lookup USING @kind; "
                + "PREPARE another FROM 'SELECT id FROM snapshots'; EXECUTE another";
        List<LineageResult> results = LineSql.parseScript(script);
        assertEquals(7, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.STARROCKS, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(new TableRef("lake", "staging", "events"), results.get(4).getInputTables().get(0));
        assertEquals("kind", results.get(4).getColumnUsages().get(0).getColumn().getName());
        assertEquals(new TableRef("lake", "reporting", "snapshots"), results.get(6).getInputTables().get(0));
        assertDialect(SqlDialect.MYSQL, "PREPARE stmt FROM 'SELECT id FROM app.users'");
        List<LineageResult> literal = LineSql.parseScript(
                "SELECT 'x; PREPARE stmt FROM SELECT id FROM app.users;'; SELECT id FROM app.users");
        assertEquals(SqlDialect.SPARK, literal.get(1).getDialect());
    }

    @Test
    public void usesFlinkConnectorSyntaxWithoutClaimingStringData() {
        assertDialect(SqlDialect.POSTGRESQL, "INSERT INTO app.events(id, note) VALUES(1, 'connector')"
                + " ON CONFLICT DO NOTHING");
        assertDialect(SqlDialect.SPARK, "SELECT 'connector' AS note FROM app.events");
        assertDialect(SqlDialect.SPARK, "SELECT '/*+ OPTIONS (''connector''=''kafka'') */' AS note");
        assertDialect(SqlDialect.FLINK, "CREATE TABLE s (id INT)"
                + " WITH ('format'='json', 'connector.type'='kafka')");
        assertDialect(SqlDialect.FLINK, "SELECT id FROM s /*+ OPTIONS('scan.startup.mode'='latest-offset') */");
        assertDialect(SqlDialect.FLINK, "ALTER TABLE `warehouse`.`events` RESET ('format')");
        assertDialect(SqlDialect.FLINK, "ALTER TABLE IF EXISTS app.events SET ('connector'='kafka')");

        List<LineageResult> results = LineSql.parseScript("CREATE TABLE s (id INT)"
                + " WITH ('format'='json', 'connector'='kafka');"
                + " INSERT INTO t SELECT id FROM s ON CONFLICT DO NOTHING;");
        assertEquals(2, results.size());
        assertEquals(SqlDialect.FLINK, results.get(1).getDialect());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
    }

    @Test
    public void keepsFlinkCompilePlanSetWithNativeIfNotExistsPlacement() {
        List<LineageResult> script = LineSql.parseScript(
                "COMPILE PLAN 'file:///tmp/orders;daily.json' IF NOT EXISTS FOR STATEMENT SET BEGIN "
                + "INSERT INTO mart.orders (event_id) SELECT id FROM ods.orders;"
                + "INSERT INTO mart.audit (event_id) SELECT order_id FROM ods.audit; END; "
                + "SELECT id FROM ods.events;");
        assertEquals(2, script.size());
        LineageResult plan = script.get(0);
        assertEquals(SqlDialect.FLINK, plan.getDialect());
        assertEquals(StatementType.READ_METADATA, plan.getStatementType());
        assertTrue(plan.getDiagnostics().isEmpty());
        assertEquals(2, plan.getColumnLineage().size());
        assertEquals("order_id", plan.getColumnLineage().get(1).getSources().get(0).getName());
        assertEquals(StatementType.SELECT, script.get(1).getStatementType());
    }

    @Test
    public void recordsFlinkStatisticsColumnsAndPartitionKeys() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE lake.staging.orders (id BIGINT, amount DECIMAL(12,2), day_key STRING) "
                + "PARTITIONED BY (day_key) WITH ('connector'='filesystem', 'path'='file:///tmp/orders', 'format'='csv'); "
                + "ANALYZE TABLE lake.staging.orders PARTITION (day_key='2026-11-17') COMPUTE STATISTICS FOR ALL COLUMNS; "
                + "SHOW PARTITIONS lake.staging.orders PARTITION (day_key='2026-11-17');", SqlDialect.FLINK);
        assertEquals(3, results.size());
        LineageResult analyze = results.get(1);
        assertEquals(StatementType.READ_METADATA, analyze.getStatementType());
        assertTrue(analyze.getDiagnostics().isEmpty());
        assertEquals(3, analyze.getColumnUsages().size());
        assertEquals(new TableRef("lake", "staging", "orders"), analyze.getColumnUsages().get(0).getColumn().getTable());
        assertEquals("day_key", analyze.getColumnUsages().get(0).getColumn().getName());
        assertEquals("id", analyze.getColumnUsages().get(1).getColumn().getName());
        assertEquals("amount", analyze.getColumnUsages().get(2).getColumn().getName());
        LineageResult show = results.get(2);
        assertEquals(StatementType.READ_METADATA, show.getStatementType());
        assertEquals("day_key", show.getColumnUsages().get(0).getColumn().getName());
    }

    @Test
    public void detectsAndSplitsFlinkCompileAndExecutePlans() {
        String plan = "COMPILE AND EXECUTE PLAN 'file:///tmp/daily;orders.json' FOR ";
        LineageResult single = LineSql.parse(plan
                + "INSERT INTO mart.orders (event_id) SELECT id FROM ods.orders ON CONFLICT DO NOTHING");
        assertEquals(SqlDialect.FLINK, single.getDialect());
        assertEquals(StatementType.INSERT, single.getStatementType());
        assertTrue(single.getDiagnostics().isEmpty());
        assertEquals("event_id", single.getColumnLineage().get(0).getTarget().getName());

        List<LineageResult> script = LineSql.parseScript(plan + "STATEMENT SET BEGIN "
                + "INSERT INTO mart.orders SELECT id FROM ods.orders;"
                + "INSERT INTO mart.audit SELECT order_id FROM ods.audit; END; "
                + "EXECUTE PLAN 'file:///tmp/daily;orders.json';");
        assertEquals(2, script.size());
        LineageResult combined = script.get(0);
        assertEquals(SqlDialect.FLINK, combined.getDialect());
        assertEquals(StatementType.INSERT, combined.getStatementType());
        assertTrue(combined.getDiagnostics().isEmpty());
        assertEquals(2, combined.getOutputTables().size());
        assertEquals(2, combined.getColumnLineage().size());
        assertEquals(StatementType.CONTROL, script.get(1).getStatementType());
    }

    @Test
    public void detectsFlinkExecuteQueriesWithoutClaimingPreparedExecution() {
        LineageResult result = LineSql.parse("EXECUTE SELECT id AS event_id FROM ods.events WHERE active");
        assertEquals(SqlDialect.FLINK, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals("event_id", result.getColumnLineage().get(0).getTarget().getName());
        assertEquals("active", result.getColumnUsages().get(0).getColumn().getName());

        LineageResult explanation = LineSql.parse("EXPLAIN PLAN FOR EXECUTE SELECT id FROM ods.events");
        assertEquals(SqlDialect.FLINK, explanation.getDialect());
        assertEquals(StatementType.READ_METADATA, explanation.getStatementType());
        assertTrue(explanation.getDiagnostics().isEmpty());

        List<LineageResult> postgres = LineSql.parseScript(
                "PREPARE \"select\" AS SELECT id FROM ods.events; EXECUTE \"select\";");
        assertEquals(2, postgres.size());
        assertEquals(SqlDialect.POSTGRESQL, postgres.get(1).getDialect());
        assertEquals("id", postgres.get(1).getColumnLineage().get(0).getTarget().getName());
    }

    @Test
    public void detectsFlinkExecuteInsertWithSharedConflictSyntax() {
        String insert = "INSERT INTO mart.events (event_id) SELECT id FROM ods.events WHERE active"
                + " ON CONFLICT DO NOTHING";
        LineageResult result = LineSql.parse("EXECUTE " + insert);
        assertEquals(SqlDialect.FLINK, result.getDialect());
        assertEquals(StatementType.INSERT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals("event_id", result.getColumnLineage().get(0).getTarget().getName());
        assertEquals("id", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.POSTGRESQL, insert);
        assertDialect(SqlDialect.FLINK, "EXECUTE INSERT OVERWRITE mart.events SELECT id FROM ods.events");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXECUTE INSERT INTO mart.events' AS note");
        List<LineageResult> script = LineSql.parseScript("SELECT 1; EXECUTE " + insert + "; " + insert);
        assertEquals(3, script.size());
        assertEquals(SqlDialect.FLINK, script.get(1).getDialect());
        assertEquals(SqlDialect.FLINK, script.get(2).getDialect());
    }

    @Test
    public void usesFlinkSessionOptionsToResolveSharedConflictSyntax() {
        String insert = "INSERT INTO mart.events SELECT id FROM ods.events ON CONFLICT DO NOTHING";
        List<LineageResult> results = LineSql.parseScript("SELECT 1;"
                + " SET 'execution.runtime-mode' = 'streaming'; " + insert + "; "
                + "INSERT INTO mart.events VALUES (2) ON CONFLICT DO NOTHING RETURNING id");
        assertEquals(4, results.size());
        assertEquals(SqlDialect.FLINK, results.get(1).getDialect());
        LineageResult write = results.get(2);
        assertEquals(SqlDialect.FLINK, write.getDialect());
        assertEquals(StatementType.INSERT, write.getStatementType());
        assertTrue(write.getDiagnostics().isEmpty());
        assertEquals("events", write.getInputTables().get(0).getName());
        assertEquals("id", write.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals(SqlDialect.POSTGRESQL, results.get(3).getDialect());

        assertDialect(SqlDialect.FLINK, "SET 'sql-client.execution.result-mode' = 'tableau'");
        assertDialect(SqlDialect.FLINK, "RESET 'table.local-time-zone'");
        assertDialect(SqlDialect.POSTGRESQL, insert);
        List<LineageResult> literal = LineSql.parseScript(
                "SELECT 'note; SET ''execution.runtime-mode'' = ''streaming'';'; " + insert);
        assertEquals(SqlDialect.POSTGRESQL, literal.get(1).getDialect());
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
    public void ignoresSparkSignaturesInsidePlainStringLiterals() {
        LineageResult literal = LineSql.parse("SELECT 'using and explode(x)' AS note");
        assertEquals(SqlDialect.SPARK, literal.getDialect());
        assertEquals(0.50, literal.getDialectConfidence(), 0.001);
        assertTrue(literal.getDiagnostics().isEmpty());

        LineageResult expression = LineSql.parse("SELECT explode(items) FROM ods.orders");
        assertEquals(SqlDialect.SPARK, expression.getDialect());
        assertTrue(expression.getDialectConfidence() > 0.50);

        LineageResult substitution = LineSql.parse("SELECT '${bizdate}' AS ds");
        assertEquals(SqlDialect.SPARK, substitution.getDialect());
        assertTrue(substitution.getDialectConfidence() > 0.50);
    }

    @Test
    public void detectsStarRocksNativeIndexesAndBracketedJoinHints() {
        LineageResult join = LineSql.parse(
                "SELECT o.amount FROM ods.users u JOIN [broadcast] dwd.orders o ON u.id = o.user_id");
        assertEquals(SqlDialect.STARROCKS, join.getDialect());
        assertEquals(StatementType.SELECT, join.getStatementType());
        assertTrue(join.getDiagnostics().isEmpty());
        assertEquals("orders", join.getColumnLineage().get(0).getSources().get(0).getTable().getName());
        assertTrue(join.getColumnUsages().stream()
                .anyMatch(usage -> "user_id".equals(usage.getColumn().getName())));
        assertDialect(SqlDialect.SQLSERVER, "SELECT o.amount FROM dbo.users u JOIN [broadcast] o ON u.id = o.user_id");

        LineageResult index = LineSql.parse("ALTER TABLE mart.orders ADD INDEX idx_id (id) USING BITMAP");
        assertEquals(SqlDialect.STARROCKS, index.getDialect());
        assertEquals(StatementType.ALTER_TABLE, index.getStatementType());
        assertEquals("id", index.getColumnUsages().get(0).getColumn().getName());
        assertTrue(index.getDiagnostics().isEmpty());
        assertTrue(LineSql.parse("CREATE INDEX idx ON metrics.docs USING gin (body)").getDialect()
                != SqlDialect.STARROCKS);
        assertDialect(SqlDialect.SPARK, "CREATE TABLE raw.t (id BIGINT) USING gin");
        assertDialect(SqlDialect.SPARK, "SELECT 'JOIN [broadcast] mart.orders' AS note");
    }

    @Test
    public void distinguishesStarRocksExternalCatalogsAndFlinkCatalogProperties() {
        LineageResult external = LineSql.parse("CREATE EXTERNAL CATALOG iceberg_catalog "
                + "PROPERTIES (\"type\" = \"iceberg\", \"iceberg.catalog.type\" = \"hive\")");
        assertEquals(SqlDialect.STARROCKS, external.getDialect());
        assertEquals(StatementType.CONTROL, external.getStatementType());
        assertTrue(external.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.STARROCKS,
                "ALTER CATALOG `lake_catalog` RESET (\"hive.metastore.uris\")");
        assertDialect(SqlDialect.FLINK, "ALTER CATALOG `lake_catalog` RESET ('default-database')");
        assertDialect(SqlDialect.FLINK, "CREATE CATALOG mem WITH ('type' = 'generic_in_memory')");
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE EXTERNAL CATALOG iceberg_catalog' AS message");
    }

    @Test
    public void detectsStarRocksWarehouseKeyTableAndFormattedOutfile() {
        LineageResult unload = LineSql.parse("SELECT id FROM mart.orders WHERE active = 1 "
                + "INTO OUTFILE 's3://bucket/data' FORMAT AS parquet");
        assertEquals(SqlDialect.STARROCKS, unload.getDialect());
        assertEquals(StatementType.SELECT, unload.getStatementType());
        assertEquals("orders", unload.getInputTables().get(0).getName());
        assertTrue(unload.getColumnUsages().stream().anyMatch(usage -> "active".equals(usage.getColumn().getName())));
        assertTrue(unload.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.MYSQL, "SELECT id INTO OUTFILE '/tmp/data.csv' FROM mart.orders");

        LineageResult table = LineSql.parse("CREATE TABLE mart.generated_users (id INT, "
                + "data_json JSON, city STRING AS json_query(data_json, '$.city')) "
                + "PRIMARY KEY(id) DISTRIBUTED BY HASH(id)");
        assertEquals(SqlDialect.STARROCKS, table.getDialect());
        assertEquals(StatementType.CREATE_TABLE, table.getStatementType());
        assertEquals("data_json", table.getColumnLineage().get(0).getSources().get(0).getName());
        assertTrue(table.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.FLINK, "CREATE TABLE t (id INT, PRIMARY KEY (id) NOT ENFORCED) "
                + "DISTRIBUTED BY HASH(id) INTO 4 BUCKETS WITH ('connector' = 'blackhole')");
        assertDialect(SqlDialect.STARROCKS, "SUSPEND WAREHOUSE wh_etl");
        assertDialect(SqlDialect.STARROCKS, "SHOW WAREHOUSES");
        assertDialect(SqlDialect.SPARK, "SELECT 'SHOW WAREHOUSES' AS message");
    }

    @Test
    public void detectsNativeStarRocksJobAndHiveIndexMetadata() {
        LineageResult export = LineSql.parse("SHOW EXPORT FROM mart WHERE state = 'exporting' ORDER BY StartTime");
        assertEquals(SqlDialect.STARROCKS, export.getDialect());
        assertEquals(StatementType.READ_METADATA, export.getStatementType());
        assertTrue(export.getDiagnostics().isEmpty());
        assertTrue(export.getColumnUsages().stream()
                .anyMatch(usage -> "state".equalsIgnoreCase(usage.getColumn().getName())));
        assertDialect(SqlDialect.STARROCKS, "SHOW ALTER TABLE COLUMN FROM mart");
        assertDialect(SqlDialect.STARROCKS, "SHOW ANALYZE JOB");
        assertDialect(SqlDialect.STARROCKS, "SHOW ALL ROUTINE LOAD");

        LineageResult index = LineSql.parse("SHOW FORMATTED INDEXES ON mart.orders");
        assertEquals(SqlDialect.HIVE, index.getDialect());
        assertEquals(StatementType.READ_METADATA, index.getStatementType());
        assertEquals("orders", index.getInputTables().get(0).getName());
        assertTrue(index.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.SPARK, "SELECT 'SHOW INDEX ON mart.orders' AS note");
    }

    @Test
    public void detectsStarRocksDictionaryLifecycleAndColumnLineage() {
        LineageResult dictionary = LineSql.parse("CREATE DICTIONARY user_dictionary USING dim.v_users "
                + "(user_id KEY, user_name VALUE)");
        assertEquals(SqlDialect.STARROCKS, dictionary.getDialect());
        assertEquals("v_users", dictionary.getInputTables().get(0).getName());
        assertEquals("user_dictionary", dictionary.getOutputTables().get(0).getName());
        assertEquals("user_name", dictionary.getColumnLineage().get(1).getSources().get(0).getName());
        assertTrue(dictionary.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.STARROCKS, "CANCEL REFRESH DICTIONARY user_dictionary");
        assertDialect(SqlDialect.SPARK, "SELECT 'SHOW DICTIONARY' AS message");
    }

    @Test
    public void detectsStarRocksClusterAdministration() {
        LineageResult scale = LineSql.parse("ALTER SYSTEM ADD COMPUTE NODE 'cn.example.com:9050'");
        assertEquals(SqlDialect.STARROCKS, scale.getDialect());
        assertEquals(StatementType.CONTROL, scale.getStatementType());
        assertTrue(scale.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.STARROCKS, "ALTER SYSTEM DROP ALL BROKER hdfs_broker");
        assertDialect(SqlDialect.STARROCKS, "ADMIN CHECK TABLET (10000)");
        assertDialect(SqlDialect.STARROCKS, "SHOW USAGE RESOURCE GROUPS");
        assertDialect(SqlDialect.OCEANBASE, "ALTER SYSTEM ADD SERVER '127.0.0.1:2882' ZONE 'zone1'");
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER SYSTEM ADD COMPUTE NODE' AS note");
    }

    @Test
    public void detectsStarRocksPipeAndStorageVolumeStatements() {
        LineageResult pipe = LineSql.parse("CREATE OR REPLACE PIPE ods.user_pipe AS "
                + "INSERT INTO ods.users (id) SELECT id FROM FILES (\"path\" = \"s3://bucket/users/\", "
                + "\"format\" = \"parquet\")");
        assertEquals(SqlDialect.STARROCKS, pipe.getDialect());
        assertEquals(StatementType.LOAD_DATA, pipe.getStatementType());
        assertTrue(pipe.getDiagnostics().isEmpty());
        assertEquals("users", pipe.getOutputTables().get(0).getName());
        assertEquals("id", pipe.getColumnLineage().get(0).getTarget().getName());
        assertEquals("$pipe1.id", pipe.getColumnLineage().get(0).getSources().get(0).getName());

        LineageResult metadata = LineSql.parse("SHOW PIPES FROM ods WHERE name = 'user_pipe' ORDER BY name");
        assertEquals(SqlDialect.STARROCKS, metadata.getDialect());
        assertEquals(StatementType.READ_METADATA, metadata.getStatementType());
        assertEquals("name", metadata.getColumnUsages().get(0).getColumn().getName());

        assertDialect(SqlDialect.STARROCKS, "ALTER PIPE ods.user_pipe SUSPEND");
        assertDialect(SqlDialect.STARROCKS, "DROP PIPE IF EXISTS ods.user_pipe");
        assertDialect(SqlDialect.STARROCKS, "SHOW STORAGE VOLUMES");
        assertDialect(SqlDialect.STARROCKS, "ALTER STORAGE VOLUME s3_volume ENABLE");
        assertDialect(SqlDialect.SPARK, "SELECT 'SHOW STORAGE VOLUMES' AS note");
        assertDialect(SqlDialect.SPARK, "CREATE TABLE pipe_events (id BIGINT)");
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
    public void detectsHiveExplainDetailsAndSessionMetadata() {
        LineageResult dependency = LineSql.parse("EXPLAIN DEPENDENCY SELECT event_id FROM dwd.events");
        assertEquals(SqlDialect.HIVE, dependency.getDialect());
        assertEquals(StatementType.SELECT, dependency.getStatementType());
        assertEquals("events", dependency.getInputTables().get(0).getName());
        assertTrue(dependency.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.HIVE, "EXPLAIN AUTHORIZATION INSERT INTO TABLE mart.t SELECT id FROM ods.t");
        assertDialect(SqlDialect.HIVE, "SHOW CONF 'hive.exec.dynamic.partition'");
        assertDialect(SqlDialect.HIVE, "SHOW TRANSACTIONS");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXPLAIN DEPENDENCY SELECT id' AS note");
    }

    @Test
    public void detectsHiveTableAndPartitionMaintenance() {
        LineageResult archive = LineSql.parse("ALTER TABLE mart.orders ARCHIVE PARTITION (dt = '2026-09-10')");
        assertEquals(SqlDialect.HIVE, archive.getDialect());
        assertEquals(StatementType.ALTER_TABLE, archive.getStatementType());
        assertEquals("orders", archive.getOutputTables().get(0).getName());
        assertTrue(archive.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.HIVE, "ALTER TABLE mart.orders PARTITION (dt = '2026-09-10') TOUCH");
        assertDialect(SqlDialect.HIVE, "ALTER TABLE mart.orders CONCATENATE");
        assertDialect(SqlDialect.HIVE, "ALTER TABLE mart.orders PARTITION (dt = '2026-09-10') COMPACT 'major' AND WAIT");
        assertDialect(SqlDialect.SPARK, "ALTER TABLE mart.orders RENAME TO touch");
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER TABLE mart.orders CONCATENATE' AS note");
    }

    @Test
    public void detectsHiveTableLocksCompactionsAndFunctionReload() {
        LineageResult locks = LineSql.parse("SHOW LOCKS mart.orders EXTENDED");
        assertEquals(SqlDialect.HIVE, locks.getDialect());
        assertEquals(StatementType.READ_METADATA, locks.getStatementType());
        assertEquals("orders", locks.getInputTables().get(0).getName());
        assertTrue(locks.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.HIVE, "LOCK TABLE mart.orders SHARED");
        assertDialect(SqlDialect.HIVE, "UNLOCK TABLE mart.orders");
        assertDialect(SqlDialect.HIVE, "SHOW COMPACTIONS");
        assertDialect(SqlDialect.HIVE, "RELOAD FUNCTION");
        assertDialect(SqlDialect.MYSQL, "LOCK TABLES mart.orders READ");
        assertDialect(SqlDialect.SPARK, "SELECT 'LOCK TABLE mart.orders SHARED' AS note");
    }

    @Test
    public void recoversFlinkExplainAfterStrongSparkDetectionFails() {
        String sql = "EXPLAIN PLAN FOR SELECT `count`, COUNT(word) AS word_count "
                + "FROM (SELECT `count`, word FROM MyTable1 WHERE word LIKE 'F%' "
                + "UNION ALL SELECT `count`, word FROM MyTable2) tmp GROUP BY `count`";
        LineageResult hinted = LineSql.parse(sql, SqlDialect.SPARK);
        assertEquals("SPARK_PARSE_ERROR", hinted.getDiagnostics().get(0).getCode());

        LineageResult actual = LineSql.parse(sql);
        assertEquals(SqlDialect.FLINK, actual.getDialect());
        assertEquals(StatementType.READ_METADATA, actual.getStatementType());
        assertEquals("MyTable1", actual.getInputTables().get(0).getName());
        assertEquals("MyTable2", actual.getInputTables().get(1).getName());
        assertTrue(actual.getDiagnostics().isEmpty());
    }

    @Test
    public void recoversGenericSparkFailureOnlyWithAnUnambiguousParser() {
        String sql = "SELECT user_id, ARRAY_AGG(amount IGNORE NULLS) AS amounts "
                + "FROM dwd.orders GROUP BY user_id";
        LineageResult recovered = LineSql.parse(sql);
        assertEquals(SqlDialect.FLINK, recovered.getDialect());
        assertEquals(StatementType.SELECT, recovered.getStatementType());
        assertTrue(recovered.getDiagnostics().isEmpty());
        assertEquals("amount", recovered.getColumnLineage().get(1).getSources().get(0).getName());
        assertTrue(recovered.getDialectDetectionReason().contains("generic Spark failed"));

        LineageResult hinted = LineSql.parse(sql, SqlDialect.SPARK);
        assertEquals(SqlDialect.SPARK, hinted.getDialect());
        assertEquals("SPARK_PARSE_ERROR", hinted.getDiagnostics().get(0).getCode());

        LineageResult ambiguous = LineSql.parse("CREATE DATABASE IF NOT EXISTS mart COMMENT 'mart layer' "
                + "LOCATION '/warehouse/mart.db' TBLPROPERTIES ('owner' = 'data')");
        assertEquals(SqlDialect.SPARK, ambiguous.getDialect());
        assertEquals("SPARK_PARSE_ERROR", ambiguous.getDiagnostics().get(0).getCode());

        List<LineageResult> routineFragments =
                LineSql.parseScript("ALTER PROC dbo.refresh_orders AS BEGIN SELECT id FROM dbo.orders; END;");
        assertTrue(routineFragments.size() > 1);
        assertTrue(routineFragments.stream().allMatch(result -> result.getDialect() == SqlDialect.SPARK));

        LineageResult dynamic = LineSql.parse("EXECUTE IMMEDIATE sql_text");
        assertEquals(SqlDialect.SPARK, dynamic.getDialect());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", dynamic.getDiagnostics().get(0).getCode());
    }

    @Test
    public void detectsFlinkTemporaryViewLifecycleWithLaterDropEvidence() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TEMPORARY VIEW lake.ops.Rates AS SELECT amount FROM ods.LiveRates; "
                        + "CREATE TEMPORARY VIEW lake.ops.rates AS SELECT amount FROM ods.ArchivedRates; "
                        + "DROP TEMPORARY VIEW lake.ops.Rates; SELECT amount FROM lake.ops.rates;");
        assertEquals(4, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.FLINK, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(StatementType.DROP_VIEW, results.get(2).getStatementType());
        assertEquals("ArchivedRates", results.get(3).getColumnLineage().get(0).getSources().get(0).getTable().getName());
        assertDialect(SqlDialect.SPARK, "DROP VIEW IF EXISTS rates");
        assertDialect(SqlDialect.SPARK, "SELECT 'DROP TEMPORARY VIEW lake.ops.Rates' AS note");
    }

    @Test
    public void detectsFlinkDistributionWatermarkAndCurrentCatalog() {
        LineageResult distribution = LineSql.parse(
                "ALTER TABLE ods.orders ADD DISTRIBUTION BY HASH(order_id) INTO 4 BUCKETS");
        assertEquals(SqlDialect.FLINK, distribution.getDialect());
        assertEquals(StatementType.ALTER_TABLE, distribution.getStatementType());
        assertEquals("orders", distribution.getOutputTables().get(0).getName());
        assertEquals("order_id", distribution.getColumnUsages().get(0).getColumn().getName());
        assertTrue(distribution.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.FLINK, "ALTER TABLE ods.orders DROP WATERMARK");
        assertDialect(SqlDialect.FLINK, "SHOW CURRENT CATALOG");
        assertDialect(SqlDialect.SPARK, "SHOW CURRENT DATABASE");
        assertDialect(SqlDialect.SPARK, "SELECT current_catalog()");
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER TABLE ods.orders DROP WATERMARK' AS note");
    }

    @Test
    public void detectsFlinkFunctionClassesAndFilteredMetadata() {
        LineageResult function = LineSql.parse("CREATE FUNCTION udf.normalize_phone "
                + "AS com.example.udf.NormalizePhone LANGUAGE JAVA USING JAR 'file:///opt/flink/udf.jar'");
        assertEquals(SqlDialect.FLINK, function.getDialect());
        assertEquals(StatementType.CREATE_ROUTINE, function.getStatementType());
        assertTrue(function.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.FLINK, "ALTER FUNCTION udf.normalize_phone AS 'com.example.Phone' LANGUAGE JAVA");
        assertDialect(SqlDialect.SPARK,
                "CREATE TEMPORARY FUNCTION f AS 'com.example.F' USING JAR 'file:///opt/spark/f.jar'");

        LineageResult columns = LineSql.parse("SHOW COLUMNS FROM prod_catalog.dwd.orders NOT LIKE 'debug_%'");
        assertEquals(SqlDialect.FLINK, columns.getDialect());
        assertEquals(StatementType.READ_METADATA, columns.getStatementType());
        assertEquals("orders", columns.getInputTables().get(0).getName());
        assertTrue(columns.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.FLINK, "SHOW VIEWS FROM prod_catalog.dwd NOT LIKE 'tmp_%'");
        assertDialect(SqlDialect.FLINK, "SHOW PROCEDURES FROM prod_catalog.ops_db LIKE 'refresh_%'");
        assertDialect(SqlDialect.SPARK, "SHOW PROCEDURES IN system");
        assertDialect(SqlDialect.SPARK, "SELECT 'SHOW VIEWS NOT LIKE tmp' AS message");
    }

    @Test
    public void detectsFlinkFunctionArtifactsWithoutClaimingSparkJarFunctions() {
        LineageResult function = LineSql.parse("CREATE TEMPORARY FUNCTION udf.parse_json "
                + "AS 'com.example.udf.ParseJson' LANGUAGE JAVA "
                + "USING ARTIFACT 'file:///opt/flink/artifacts/json-udf.jar'");
        assertEquals(SqlDialect.FLINK, function.getDialect());
        assertEquals(StatementType.CREATE_ROUTINE, function.getStatementType());
        assertTrue(function.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.SPARK,
                "CREATE TEMPORARY FUNCTION f AS 'com.example.F' USING JAR 'file:///opt/spark/f.jar'");
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE FUNCTION f USING ARTIFACT' AS note");
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
    public void ignoresFlinkAndPostgresSyntaxInsideQuotedText() {
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE CATALOG analytics' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'USE CATALOG analytics' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'WATERMARK FOR event_time AS event_time' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'JSON_VALUE(payload)' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'ON CONFLICT DO NOTHING' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'RETURNING order_id' AS note");
        assertDialect(SqlDialect.SPARK, "SELECT 'name ILIKE pattern or value::text' AS note");
        assertDialect(SqlDialect.SQLSERVER, "SELECT id AS [WATERMARK FOR event_time] FROM ods.events");
        assertDialect(SqlDialect.FLINK, "CREATE CATALOG analytics WITH ('type' = 'in_memory')");
        assertDialect(SqlDialect.FLINK, "SELECT JSON_VALUE(payload, '$.code') FROM ods.events");
        assertDialect(SqlDialect.POSTGRESQL, "SELECT code::text FROM ods.events");
        assertDialect(SqlDialect.POSTGRESQL, "INSERT INTO ods.events VALUES (1) ON CONFLICT DO NOTHING");
    }

    @Test
    public void detectsFlinkCatalogAndDatabaseOptions() {
        assertDialect(SqlDialect.FLINK, "ALTER CATALOG `prod.catalog` SET ('default-database' = 'analytics')");
        assertDialect(SqlDialect.FLINK, "ALTER CATALOG production RESET ('default-database')");
        assertDialect(SqlDialect.FLINK, "ALTER CATALOG production COMMENT 'production catalog'");
        LineageResult database = LineSql.parse(
                "ALTER DATABASE production.analytics SET ('location' = 'file:///warehouse/analytics')");
        assertEquals(SqlDialect.FLINK, database.getDialect());
        assertTrue(database.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.SPARK, "SELECT 'ALTER CATALOG production COMMENT' AS notes");
    }

    @Test
    public void detectsFlinkExplainDetailsAndJarManagement() {
        LineageResult plan = LineSql.parse("EXPLAIN CHANGELOG_MODE, JSON_EXECUTION_PLAN "
                + "INSERT INTO mart.rollup SELECT id FROM ods.events WHERE active");
        assertEquals(SqlDialect.FLINK, plan.getDialect());
        assertEquals(StatementType.READ_METADATA, plan.getStatementType());
        assertEquals("events", plan.getInputTables().get(0).getName());
        assertEquals("rollup", plan.getOutputTables().get(0).getName());
        assertEquals("active", plan.getColumnUsages().get(0).getColumn().getName());
        assertTrue(plan.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.FLINK, "EXPLAIN PLAN_ADVICE SELECT id FROM ods.events");
        assertDialect(SqlDialect.FLINK, "SHOW JARS");
        assertDialect(SqlDialect.FLINK, "REMOVE JAR 'file:///opt/flink/extensions/example.jar'");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXPLAIN CHANGELOG_MODE SELECT id; SHOW JARS' AS notes");
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (GENERIC_PLAN TRUE, FORMAT JSON) SELECT id FROM ods.events");
    }

    @Test
    public void detectsFlinkChangelogConversion() {
        assertDialect(SqlDialect.FLINK, "SELECT id FROM FROM_CHANGELOG(input => TABLE ods.events)");
        assertDialect(SqlDialect.FLINK, "SELECT id FROM TO_CHANGELOG(input => TABLE ods.events)");
        assertDialect(SqlDialect.SPARK, "SELECT to_changelog(value) FROM ods.events");
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM FROM_CHANGELOG(input => TABLE ods.events)' AS note");
    }

    @Test
    public void detectsFlinkModelManagementAndInference() {
        assertDialect(SqlDialect.FLINK, "ALTER MODEL ml.churn RENAME TO ml.churn_v2");
        assertDialect(SqlDialect.FLINK, "SHOW CREATE MODEL ml.churn_v2");
        assertDialect(SqlDialect.FLINK, "DESCRIBE MODEL EXTENDED ml.churn_v2");
        assertDialect(SqlDialect.FLINK, "SHOW MODELS IN ml NOT LIKE 'temporary_%'");
        assertDialect(SqlDialect.FLINK, "DROP TEMPORARY MODEL IF EXISTS ml.churn");
        LineageResult inference = LineSql.parse("SELECT * FROM ML_PREDICT("
                + "TABLE dwd.user_features, MODEL ml.churn_v2, DESCRIPTOR(age))");
        assertEquals(SqlDialect.FLINK, inference.getDialect());
        assertEquals(StatementType.SELECT, inference.getStatementType());
        assertEquals("user_features", inference.getInputTables().get(0).getName());
        assertTrue(inference.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.SPARK, "SELECT ml_predict(age) FROM dwd.user_features");
        assertDialect(SqlDialect.SPARK, "SELECT 'FROM ML_PREDICT(TABLE t, MODEL m)' AS notes");
        assertDialect(SqlDialect.SQLSERVER, "DROP EXTERNAL MODEL churn");
    }

    @Test
    public void detectsFlinkModuleAndTemporarySystemFunctionCommands() {
        List<LineageResult> results = LineSql.parseScript("SHOW FULL MODULES; "
                + "LOAD MODULE hive WITH ('hive-version' = '3.1.3'); USE MODULES hive, core; "
                + "CREATE TEMPORARY SYSTEM FUNCTION parse_json AS 'com.example.ParseJson'; "
                + "DROP TEMPORARY SYSTEM FUNCTION parse_json; UNLOAD MODULE hive");
        assertEquals(6, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.FLINK, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(StatementType.READ_METADATA, results.get(0).getStatementType());
        assertEquals(StatementType.CONTROL, results.get(1).getStatementType());
        assertEquals(StatementType.CONTROL, results.get(2).getStatementType());
        assertEquals(StatementType.CREATE_ROUTINE, results.get(3).getStatementType());
        assertEquals(StatementType.DROP_ROUTINE, results.get(4).getStatementType());
        assertEquals(StatementType.CONTROL, results.get(5).getStatementType());
        assertDialect(SqlDialect.FLINK, "SHOW MODULES");
        assertDialect(SqlDialect.SPARK, "SELECT 'LOAD MODULE hive; SHOW FULL MODULES' AS notes");
        assertDialect(SqlDialect.SPARK, "/* CREATE TEMPORARY SYSTEM FUNCTION test */ SELECT id FROM ods.events");
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
    public void detectsPostgresForeignDataStatementsAcrossWhitespace() {
        LineageResult created = LineSql.parse("CREATE /* fdw */ FOREIGN\nTABLE ext.orders (id bigint) SERVER remote");
        assertEquals(SqlDialect.POSTGRESQL, created.getDialect());
        assertEquals(StatementType.CREATE_TABLE, created.getStatementType());
        assertEquals("orders", created.getOutputTables().get(0).getName());
        LineageResult imported = LineSql.parse("IMPORT FOREIGN SCHEMA sales LIMIT TO (orders, \"order.items\") "
                + "FROM SERVER remote INTO ext");
        assertEquals(SqlDialect.POSTGRESQL, imported.getDialect());
        assertEquals(StatementType.CONTROL, imported.getStatementType());
        assertEquals(2, imported.getOutputTables().size());
        assertEquals("ext", imported.getOutputTables().get(1).getSchema());
        assertEquals("order.items", imported.getOutputTables().get(1).getName());
        assertDialect(SqlDialect.POSTGRESQL, "CREATE USER MAPPING FOR CURRENT_USER SERVER remote");
        assertDialect(SqlDialect.SPARK, "SELECT 'IMPORT FOREIGN SCHEMA sales' AS message");
    }

    @Test
    public void detectsPostgresExtensionLifecycle() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA tools VERSION '1.6' CASCADE; "
                + "ALTER EXTENSION pg_trgm UPDATE TO '1.7'; "
                + "ALTER EXTENSION pg_trgm SET SCHEMA search_ext; "
                + "DROP EXTENSION IF EXISTS pg_trgm");
        assertEquals(4, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertEquals(StatementType.CONTROL, result.getStatementType());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE EXTENSION pg_trgm' AS message");
    }

    @Test
    public void detectsPostgresAliasForJoinUsingColumns() {
        LineageResult result = LineSql.parse("SELECT \"Join keys\".id FROM ods.orders "
                + "LEFT JOIN ods.invoices USING (ID) AS \"Join keys\"");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(2, result.getColumnUsages().size());
        assertEquals(new TableRef(null, "ods", "orders"),
                result.getColumnLineage().get(0).getSources().get(0).getTable());
        assertDialect(SqlDialect.SPARK, "SELECT id FROM ods.orders JOIN ods.invoices USING (id)");
        assertDialect(SqlDialect.SPARK, "SELECT 'JOIN a USING (id) AS keys' AS note");
    }

    @Test
    public void tracesSqlServerInsertExecLiteralSelect() {
        LineageResult result = LineSql.parse("INSERT INTO report.sink (marker, source_id, total) "
                + "EXEC sys.sp_executesql @stmt = N'SELECT 7, src.id, src.price + src.tax "
                + "FROM dbo.events src WHERE src.active = 1'");
        assertEquals(SqlDialect.SQLSERVER, result.getDialect());
        assertEquals(StatementType.INSERT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals("events", result.getInputTables().get(0).getName());
        assertEquals("sink", result.getOutputTables().get(0).getName());
        assertEquals(3, result.getColumnLineage().size());
        assertEquals("marker", result.getColumnLineage().get(0).getTarget().getName());
        assertTrue(result.getColumnLineage().get(0).getSources().isEmpty());
        assertEquals("source_id", result.getColumnLineage().get(1).getTarget().getName());
        assertEquals("id", result.getColumnLineage().get(1).getSources().get(0).getName());
        assertEquals(2, result.getColumnLineage().get(2).getSources().size());
        assertEquals("active", result.getColumnUsages().get(0).getColumn().getName());

        LineageResult wildcard = LineSql.parse("INSERT INTO report.sink (id) "
                + "EXEC sys.sp_executesql N'SELECT * FROM dbo.events WHERE active = 1'");
        assertEquals("events", wildcard.getInputTables().get(0).getName());
        assertTrue(wildcard.getColumnLineage().isEmpty());
        assertEquals(1, wildcard.getColumnUsages().size());

        LineageResult set = LineSql.parse("INSERT INTO report.sink (event_id) "
                + "EXEC sys.sp_executesql N'SELECT id FROM dbo.live UNION ALL SELECT id FROM dbo.archive'");
        assertEquals(2, set.getInputTables().size());
        assertEquals(1, set.getColumnLineage().size());
        assertEquals("event_id", set.getColumnLineage().get(0).getTarget().getName());
        assertEquals(2, set.getColumnLineage().get(0).getSources().size());

        LineageResult mismatch = LineSql.parse("INSERT INTO report.sink (id, amount) "
                + "EXEC sys.sp_executesql N'SELECT id FROM dbo.events'");
        assertEquals(1, mismatch.getInputTables().size());
        assertTrue(mismatch.getColumnLineage().isEmpty());

        LineageResult variable = LineSql.parse("INSERT INTO report.sink EXEC sys.sp_executesql @query");
        assertEquals(StatementType.INSERT, variable.getStatementType());
        assertTrue(variable.getInputTables().isEmpty());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", variable.getDiagnostics().get(0).getCode());

        LineageResult assignment = LineSql.parse("INSERT INTO report.sink (id) "
                + "EXEC sys.sp_executesql N'SELECT @id = e.id FROM dbo.events e', N'@id int', 0");
        assertTrue(assignment.getColumnLineage().isEmpty());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", assignment.getDiagnostics().get(0).getCode());

        LineageResult write = LineSql.parse("INSERT INTO report.sink EXEC sys.sp_executesql "
                + "N'SELECT id INTO dbo.other FROM dbo.events'");
        assertEquals(1, write.getOutputTables().size());
        assertTrue(write.getInputTables().isEmpty());
        assertEquals("DYNAMIC_SQL_NOT_EXPANDED", write.getDiagnostics().get(0).getCode());
    }

    @Test
    public void detectsSqlServerTemporaryTableScriptsAndMaintenanceSyntax() {
        List<LineageResult> results = LineSql.parseScript("CREATE TABLE #active (event_id int); "
                + "INSERT INTO #active (event_id) SELECT id FROM dbo.events WHERE enabled = 1; "
                + "SELECT event_id FROM #active; DROP TABLE IF EXISTS #active;");
        assertEquals(4, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.SQLSERVER, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(StatementType.CREATE_TABLE, results.get(0).getStatementType());
        assertEquals("#active", results.get(1).getColumnLineage().get(0).getTarget().getTable().getName());
        assertEquals("events", results.get(1).getColumnLineage().get(0).getSources().get(0).getTable().getName());
        assertEquals(StatementType.DROP_TABLE, results.get(3).getStatementType());
        assertDialect(SqlDialect.SQLSERVER, "DROP TABLE IF EXISTS ##shared_events");
        assertDialect(SqlDialect.SQLSERVER, "CREATE NONCLUSTERED COLUMNSTORE INDEX idx ON dbo.events (id)");
        assertDialect(SqlDialect.SQLSERVER, "DBCC CHECKTABLE (dbo.events)");
        assertDialect(SqlDialect.SPARK, "SELECT id FROM #events#");
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE TABLE #events (id int)' AS note");
    }

    @Test
    public void detectsSqlServerExecuteAndPreservesUnicodeMetadataNames() {
        LineageResult columns = LineSql.parse("EXEC @rc = sys.sp_columns @table_owner=N'ops.schema', "
                + "@table_qualifier=N'warehouse', @table_name=N'order.history', @column_name=N'order''id'");
        assertEquals(SqlDialect.SQLSERVER, columns.getDialect());
        assertEquals(StatementType.READ_METADATA, columns.getStatementType());
        assertTrue(columns.getDiagnostics().isEmpty());
        assertEquals(new TableRef("warehouse", "ops.schema", "order.history"), columns.getInputTables().get(0));
        assertEquals("order'id", columns.getColumnUsages().get(0).getColumn().getName());
        assertDialect(SqlDialect.SQLSERVER, "EXEC dbo.refresh_cache @force = 1");
        assertDialect(SqlDialect.SQLSERVER, "INSERT INTO audit.summary (id) EXEC dbo.fetch_ids");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXEC sys.sp_columns @table_name=sample' AS note");
    }

    @Test
    public void detectsPostgresTemporalRowOverlapExpressions() {
        LineageResult result = LineSql.parse("SELECT (a.start_time, a.end_time) OVERLAPS "
                + "(b.start_time, b.end_time) AS overlaps_window "
                + "FROM ops.windows a JOIN ops.requests b ON (a.id, a.kind) = (b.id, b.kind)");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(4, result.getColumnLineage().get(0).getSources().size());
        assertEquals(4, result.getColumnUsages().size());
        assertDialect(SqlDialect.SPARK, "SELECT '(a, b) OVERLAPS (c, d)' AS sample");
    }

    @Test
    public void detectsPostgresRowLockStrengthsAndTableInheritance() {
        LineageResult locks = LineSql.parse("SELECT q.id, p.owner FROM queue.pending q "
                + "JOIN queue.partitions p ON q.partition_id = p.id WHERE q.ready "
                + "FOR NO KEY UPDATE OF q SKIP LOCKED FOR KEY SHARE OF p NOWAIT");
        assertEquals(SqlDialect.POSTGRESQL, locks.getDialect());
        assertEquals(StatementType.SELECT, locks.getStatementType());
        assertTrue(locks.getDiagnostics().isEmpty());
        assertEquals(2, locks.getInputTables().size());
        assertEquals("ready", locks.getColumnUsages().get(2).getColumn().getName());

        LineageResult table = LineSql.parse("TABLE \"history.store\".\"current.orders\" *");
        assertEquals(SqlDialect.POSTGRESQL, table.getDialect());
        assertEquals(StatementType.SELECT, table.getStatementType());
        assertTrue(table.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "history.store", "current.orders"), table.getInputTables().get(0));
        assertDialect(SqlDialect.POSTGRESQL, "TABLE ONLY queue.pending");
        assertDialect(SqlDialect.SPARK, "SELECT 'FOR KEY SHARE OF q' AS note");
    }

    @Test
    public void detectsPostgresTableLikeTemplatesAndLocalColumns() {
        LineageResult create = LineSql.parse("CREATE TABLE mart.snapshot (LIKE \"template.set\".events INCLUDING ALL, "
                + "LIKE templates.audit EXCLUDING DEFAULTS, revision int GENERATED ALWAYS AS (version + 1) STORED)");
        assertEquals(SqlDialect.POSTGRESQL, create.getDialect());
        assertEquals(StatementType.CREATE_TABLE_LIKE, create.getStatementType());
        assertTrue(create.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "template.set", "events"), create.getInputTables().get(0));
        assertEquals(new TableRef(null, "templates", "audit"), create.getInputTables().get(1));
        assertEquals("revision", create.getColumnLineage().get(0).getTarget().getName());
        assertEquals("version", create.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.SPARK, "SELECT 'CREATE TABLE t (LIKE u INCLUDING ALL)' AS note");
    }

    @Test
    public void detectsPostgresTwoPhaseTransactionsWithDeferredConstraints() {
        List<LineageResult> results = LineSql.parseScript("BEGIN; SET CONSTRAINTS accounting.invoice_fk DEFERRED; "
                + "INSERT INTO accounting.audit (id) SELECT id FROM staging.events WHERE accepted; "
                + "PREPARE TRANSACTION 'batch;42'; COMMIT PREPARED 'batch;42';");
        assertEquals(5, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(StatementType.CONTROL, results.get(1).getStatementType());
        assertEquals(StatementType.INSERT, results.get(2).getStatementType());
        assertEquals("accepted", results.get(2).getColumnUsages().get(0).getColumn().getName());
        assertEquals(StatementType.CONTROL, results.get(3).getStatementType());
        assertEquals(StatementType.CONTROL, results.get(4).getStatementType());
        assertDialect(SqlDialect.POSTGRESQL, "ROLLBACK PREPARED 'batch;42'");
        assertDialect(SqlDialect.SPARK, "SELECT 'COMMIT PREPARED batch' AS note");
    }

    @Test
    public void usesPostgresDiscardToIdentifyTemporaryViewScripts() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TEMP VIEW recent_orders AS SELECT id FROM sales.orders WHERE is_recent; "
                + "SELECT id FROM recent_orders; DISCARD TEMP; SELECT id FROM recent_orders;");
        assertEquals(4, results.size());
        for (LineageResult result : results) {
            assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertEquals(new TableRef(null, "sales", "orders"), results.get(1).getInputTables().get(0));
        assertEquals("is_recent", results.get(1).getColumnUsages().get(0).getColumn().getName());
        assertEquals(StatementType.CONTROL, results.get(2).getStatementType());
        assertEquals(new TableRef(null, null, "recent_orders"), results.get(3).getInputTables().get(0));
        assertTrue(results.get(3).getColumnUsages().isEmpty());
        assertDialect(SqlDialect.POSTGRESQL, "DISCARD SEQUENCES");
        assertDialect(SqlDialect.SPARK, "SELECT 'x; DISCARD TEMP; y' AS note");
    }

    @Test
    public void detectsPostgresAliasedDmlAndDataModifyingCtes() {
        LineageResult delete = LineSql.parse("DELETE FROM ONLY sales.orders o USING staging.expired e "
                + "WHERE o.id = e.order_id");
        assertEquals(SqlDialect.POSTGRESQL, delete.getDialect());
        assertEquals(StatementType.DELETE, delete.getStatementType());
        assertTrue(delete.getDiagnostics().isEmpty());
        assertTrue(delete.getInputTables().contains(new TableRef(null, "staging", "expired")));
        assertEquals(new TableRef(null, "sales", "orders"), delete.getOutputTables().get(0));
        assertDialect(SqlDialect.POSTGRESQL, "DELETE FROM sales.orders AS \"target orders\" "
                + "USING staging.expired WHERE \"target orders\".id = expired.id");
        assertDialect(SqlDialect.POSTGRESQL, "UPDATE ONLY sales.orders o SET amount = o.amount + 1");
        assertDialect(SqlDialect.MYSQL, "DELETE FROM o USING sales.orders o JOIN staging.expired e ON o.id = e.id");
        assertDialect(SqlDialect.SPARK, "WITH note AS (SELECT 'DELETE FROM t' AS value) SELECT value FROM note");

        LineageResult moved = LineSql.parse("WITH moved AS (DELETE FROM sales.orders RETURNING id) "
                + "INSERT INTO archive.orders (id) SELECT id FROM moved");
        assertEquals(SqlDialect.POSTGRESQL, moved.getDialect());
        assertEquals(StatementType.INSERT, moved.getStatementType());
        assertTrue(moved.getDiagnostics().isEmpty());
        assertEquals(2, moved.getOutputTables().size());
        assertEquals(new TableRef(null, "sales", "orders"),
                moved.getColumnLineage().get(0).getSources().get(0).getTable());
    }

    @Test
    public void detectsPostgresMaintenanceWithNativeOptions() {
        LineageResult analyze = LineSql.parse("ANALYSE (SKIP_LOCKED, BUFFER_USAGE_LIMIT '128kB') "
                + "\"ops.archive\".\"daily.events\" (\"event.id\")");
        assertEquals(SqlDialect.POSTGRESQL, analyze.getDialect());
        assertEquals(StatementType.READ_METADATA, analyze.getStatementType());
        assertTrue(analyze.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "ops.archive", "daily.events"), analyze.getInputTables().get(0));
        assertEquals("event.id", analyze.getColumnUsages().get(0).getColumn().getName());

        LineageResult cluster = LineSql.parse("CLUSTER (VERBOSE true) mart.orders USING orders_idx");
        assertEquals(SqlDialect.POSTGRESQL, cluster.getDialect());
        assertEquals(StatementType.ALTER_TABLE, cluster.getStatementType());
        assertTrue(cluster.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "mart", "orders"), cluster.getOutputTables().get(0));
        assertDialect(SqlDialect.POSTGRESQL, "VACUUM (FULL, ANALYZE) mart.orders (id)");
        assertDialect(SqlDialect.POSTGRESQL, "REINDEX (VERBOSE) TABLE CONCURRENTLY mart.orders");
        assertDialect(SqlDialect.SPARK, "ANALYZE TABLE mart.orders COMPUTE STATISTICS");
        assertDialect(SqlDialect.SPARK, "SELECT 'CLUSTER (VERBOSE)' AS sample");
    }

    @Test
    public void detectsPostgresForeignKeyDeleteColumnsAndValidation() {
        LineageResult create = LineSql.parse("CREATE TABLE mart.links (tenant_id int, parent_id int, "
                + "FOREIGN KEY (tenant_id, parent_id) REFERENCES core.parent (tenant_id, id) "
                + "ON DELETE SET NULL (parent_id) DEFERRABLE INITIALLY DEFERRED)");
        assertEquals(SqlDialect.POSTGRESQL, create.getDialect());
        assertEquals(StatementType.CREATE_TABLE, create.getStatementType());
        assertTrue(create.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "core", "parent"), create.getInputTables().get(0));
        assertEquals("id", create.getColumnUsages().get(3).getColumn().getName());

        LineageResult alter = LineSql.parse("ALTER TABLE IF EXISTS ONLY mart.links ADD CONSTRAINT links_fk "
                + "FOREIGN KEY (parent_id) REFERENCES core.parent (id) NOT VALID");
        assertEquals(SqlDialect.POSTGRESQL, alter.getDialect());
        assertEquals(StatementType.ALTER_TABLE, alter.getStatementType());
        assertTrue(alter.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "core", "parent"), alter.getInputTables().get(0));
    }

    @Test
    public void detectsPostgresCopyTableAndQueryForms() {
        LineageResult output = LineSql.parse(
                "COPY \"sales.daily\".\"current.events\" (\"event.id\", payload) TO STDOUT WITH (FORMAT CSV)");
        assertEquals(SqlDialect.POSTGRESQL, output.getDialect());
        assertEquals(StatementType.SELECT, output.getStatementType());
        assertEquals("sales.daily", output.getInputTables().get(0).getSchema());
        assertEquals("current.events", output.getInputTables().get(0).getName());
        assertEquals("event.id", output.getColumnLineage().get(0).getTarget().getName());
        assertDialect(SqlDialect.POSTGRESQL, "COPY mart.events (id) FROM '/tmp/events.csv' WITH (FORMAT CSV)");
        assertDialect(SqlDialect.POSTGRESQL, "COPY (SELECT id FROM ods.events WHERE active) TO STDOUT");
        assertDialect(SqlDialect.POSTGRESQL, "COPY mart.events TO PROGRAM 'gzip'");
        assertDialect(SqlDialect.SPARK, "SELECT 'COPY mart.events TO STDOUT' AS message");
    }

    @Test
    public void detectsPostgresParenthesizedExplainOptions() {
        LineageResult explain = LineSql.parse("EXPLAIN (FORMAT JSON) SELECT id FROM ods.events WHERE active");
        assertEquals(SqlDialect.POSTGRESQL, explain.getDialect());
        assertEquals(StatementType.SELECT, explain.getStatementType());
        assertEquals("id", explain.getColumnLineage().get(0).getSources().get(0).getName());
        assertEquals("active", explain.getColumnUsages().get(0).getColumn().getName());
        assertTrue(explain.getDiagnostics().isEmpty());
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (ANALYZE FALSE, BUFFERS FALSE) SELECT id FROM ods.events");
        assertDialect(SqlDialect.POSTGRESQL, "EXPLAIN (SETTINGS, WAL) SELECT id FROM ods.events");
        assertDialect(SqlDialect.SPARK, "SELECT 'EXPLAIN (FORMAT JSON)' AS note");
    }

    @Test
    public void detectsPostgresDistinctOnAndCteMaterializationClauses() {
        assertDialect(SqlDialect.POSTGRESQL, "SELECT DISTINCT ON (event_id) event_id FROM ods.events "
                + "ORDER BY event_id, observed_at DESC");
        assertDialect(SqlDialect.POSTGRESQL, "WITH base AS (SELECT 1), selected AS NOT MATERIALIZED "
                + "(SELECT id FROM ods.events) SELECT id FROM selected");
        LineageResult result = LineSql.parse("WITH \"snapshot.data\" (event_key) AS MATERIALIZED "
                + "(SELECT e.id FROM ods.events AS e) SELECT s.event_key FROM \"snapshot.data\" s");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("id", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.SPARK, "WITH q AS (SELECT id FROM ods.events AS materialized(id)) "
                + "SELECT id FROM q");
        assertDialect(SqlDialect.SPARK, "SELECT 'SELECT DISTINCT ON (id)' AS note");
    }

    @Test
    public void detectsPostgresMaterializationDataOptions() {
        LineageResult table = LineSql.parse("CREATE TEMPORARY TABLE cache (id) AS "
                + "SELECT e.event_id FROM ods.events e WITH NO DATA");
        assertEquals(SqlDialect.POSTGRESQL, table.getDialect());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, table.getStatementType());
        assertEquals("event_id", table.getColumnLineage().get(0).getSources().get(0).getName());
        assertDialect(SqlDialect.POSTGRESQL,
                "CREATE MATERIALIZED VIEW mart.snapshot AS SELECT event_id FROM ods.events WITH DATA");
        assertDialect(SqlDialect.POSTGRESQL, "REFRESH MATERIALIZED VIEW CONCURRENTLY mart.snapshot");
        assertDialect(SqlDialect.POSTGRESQL, "REFRESH MATERIALIZED VIEW mart.snapshot WITH NO DATA");
        assertDialect(SqlDialect.POSTGRESQL, "CREATE UNLOGGED TABLE mart.snapshot AS SELECT event_id FROM ods.events");
        assertDialect(SqlDialect.SPARK, "CREATE TABLE snapshot AS SELECT 'WITH NO DATA' AS label");
        assertDialect(SqlDialect.SPARK, "CREATE TABLE snapshot AS SELECT event_id FROM ods.events /* WITH DATA */");
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
    public void detectsOracleJsonTableColumnTypesWithinNestedColumns() {
        LineageResult result = LineSql.parse(
                "SELECT j.amount, j.\"Display Name\" AS display_name FROM ods.events e, "
                        + "JSON_TABLE(e.payload, '$' COLUMNS (NESTED PATH '$.items[*]' COLUMNS "
                        + "(amount NUMBER PATH '$.amount', \"Display Name\" VARCHAR2(100) PATH '$.name'))) j");
        assertEquals(SqlDialect.ORACLE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(2, result.getColumnLineage().size());
        assertEquals("payload", result.getColumnLineage().get(1).getSources().get(0).getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));

        assertDialect(SqlDialect.MYSQL,
                "SELECT jt.label FROM ods.events e, JSON_TABLE(e.payload, '$' COLUMNS "
                        + "(label VARCHAR(100) PATH '$.\"label NUMBER\"')) jt");
    }

    @Test
    public void detectsOracleJsonScalarReturningTypes() {
        LineageResult result = LineSql.parse(
                "SELECT JSON_VALUE(e.payload, '$.sku' RETURNING VARCHAR2(100 CHAR)) AS sku, "
                        + "JSON_QUERY(e.payload, '$.cart' RETURNING CLOB WITH CONDITIONAL WRAPPER) AS cart "
                        + "FROM ods.events e");
        assertEquals(SqlDialect.ORACLE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(2, result.getColumnLineage().size());
        assertEquals("payload", result.getColumnLineage().get(0).getSources().get(0).getName());
        assertFalse(result.getDiagnostics().stream().anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
        assertDialect(SqlDialect.FLINK,
                "SELECT JSON_VALUE(e.payload, '$.\"label RETURNING NUMBER\"' RETURNING STRING) AS label "
                        + "FROM ods.events e");
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
