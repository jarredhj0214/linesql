package io.github.linesql.cli;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

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

    private static void assertDialect(SqlDialect expected, String sql) {
        LineageResult result = LineSql.parse(sql);

        assertEquals(expected, result.getDialect());
    }
}
