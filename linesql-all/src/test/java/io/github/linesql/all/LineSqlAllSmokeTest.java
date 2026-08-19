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
