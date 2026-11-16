package io.github.linesql.all;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LineSqlAllSmokeTest {

    @Test
    public void aggregateDependencyLoadsBundledDialectParsers() {
        assertParses(SqlDialect.SPARK, "select id from app.users");
        assertParses(SqlDialect.HIVE, "select id from app.users");
        assertParses(SqlDialect.FLINK, "select id from app.users");
        assertParses(SqlDialect.STARROCKS, "select id from app.users");
        assertParses(SqlDialect.MYSQL, "select id from app.users");
        assertParses(SqlDialect.ORACLE, "select id from app.users");
        assertParses(SqlDialect.SQLSERVER, "select id from dbo.users");
        assertParses(SqlDialect.POSTGRESQL, "select id from public.users");
        assertParsesOceanBase("select id from app.users");
    }

    @Test
    public void aggregateDependencySupportsAutomaticDialectDetection() {
        LineageResult result = LineSql.parse("select top 10 id from dbo.users");

        assertEquals(SqlDialect.SQLSERVER, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().isEmpty());
    }

    @Test
    public void detectsXmlTableAndWindowTableFunctionsInQueries() {
        assertAutomaticSelect(SqlDialect.ORACLE,
                "select x.amount from ods.orders o, "
                        + "xmltable(xmlnamespaces('urn:orders' as ns), '/ns:order[ns:id = $wanted]' "
                        + "passing by value o.payload, o.order_id as wanted columns amount number path 'ns:amount') x");
        assertAutomaticSelect(SqlDialect.FLINK,
                "select s.window_start from session(data => table ods.orders partition by user_id, "
                        + "timecol => descriptor(event_ts), gap => interval '10' minute) s");
        assertAutomaticSelect(SqlDialect.SPARK, "select ' xmltable(' as label from ods.events");
        assertAutomaticSelect(SqlDialect.SPARK, "select ' session(table t' as label from ods.events");
    }

    @Test
    public void distinguishesFlinkConflictActionsFromPostgreSql() {
        for (String action : new String[]{"DEDUPLICATE", "ERROR"}) {
            LineageResult result = LineSql.parse("insert into dwd.orders select id from ods.orders "
                    + "on conflict do " + action);
            assertEquals(SqlDialect.FLINK, result.getDialect());
            assertEquals(StatementType.INSERT, result.getStatementType());
            assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
        }
        LineageResult result = LineSql.parse("insert into dwd.orders (id) values (1) on conflict do nothing");
        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
    }

    private static void assertAutomaticSelect(SqlDialect dialect, String sql) {
        LineageResult result = LineSql.parse(sql);
        assertEquals(dialect, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
        assertEquals(1, result.getColumnLineage().size());
    }

    private static void assertParses(SqlDialect dialect, String sql) {
        LineageResult result = LineSql.parse(sql, dialect);

        assertEquals(dialect, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
    }

    private static void assertParsesOceanBase(String sql) {
        LineageResult result = LineSql.parse(sql, SqlDialect.OCEANBASE);

        assertEquals(SqlDialect.OCEANBASE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertTrue(result.getDiagnostics().toString(), !result.getDiagnostics().isEmpty());
    }
}
