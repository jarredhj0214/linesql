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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    private static void assertDialect(SqlDialect expected, String sql) {
        LineageResult result = LineSql.parse(sql);

        assertEquals(expected, result.getDialect());
    }
}
