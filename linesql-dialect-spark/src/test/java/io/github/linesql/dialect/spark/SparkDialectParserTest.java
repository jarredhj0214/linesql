package io.github.linesql.dialect.spark;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkDialectParserTest {
    @Test
    public void parsesSelectInputTable() {
        LineageResult result = LineSql.parse("select id, name from ods.users");

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(1, result.getInputTables().size());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertTrue(result.getOutputTables().isEmpty());
    }

    @Test
    public void parsesInsertInputAndOutputTables() {
        LineageResult result = LineSql.parse("insert overwrite table ads.user_summary select id from ods.users");

        assertEquals(StatementType.INSERT, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("user_summary", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void parsesScriptStatements() {
        List<LineageResult> results = LineSql.parseScript(
                "select ';' as semi from db.a; create table db.b as select id from db.a");

        assertEquals(2, results.size());
        assertEquals(StatementType.SELECT, results.get(0).getStatementType());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, results.get(1).getStatementType());
        assertEquals("b", results.get(1).getOutputTables().get(0).getName());
    }

    @Test
    public void parsesJoinInputTables() {
        LineageResult result = LineSql.parse(
                "select u.id, o.id from ods.users u left join ods.orders o on u.id = o.user_id");

        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(2, result.getInputTables().size());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertEquals("orders", result.getInputTables().get(1).getName());
    }

    @Test
    public void parsesCreateViewOutputTable() {
        LineageResult result = LineSql.parse("create or replace temporary view mart.v_users as select id from ods.users");

        assertEquals(StatementType.CREATE_VIEW, result.getStatementType());
        assertEquals("mart", result.getOutputTables().get(0).getSchema());
        assertEquals("v_users", result.getOutputTables().get(0).getName());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void reportsParseErrorsAsDiagnostics() {
        LineageResult result = LineSql.parse("select @");

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals("SPARK_PARSE_ERROR", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void parsesMergeIntoTable() {
        LineageResult result = LineSql.parse(
                "merge into ads.users t using ods.users s on t.id = s.id "
                        + "when matched then update set * "
                        + "when not matched then insert *");

        assertEquals(StatementType.MERGE, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("users", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
    }

    @Test
    public void parsesCacheTableAsSelect() {
        LineageResult result = LineSql.parse(
                "cache lazy table cached_users as select id from ods.users where id > 0");

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertTrue(result.getDiagnostics().stream().noneMatch(d -> "SPARK_PARSE_ERROR".equals(d.getCode())));
    }
}
