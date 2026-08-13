package io.github.linesql.dialect.spark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkDialectParserTest {
    @Test
    public void parsesSelectInputTable() {
        LineageResult result = LineSql.parse(sqlCase("select_basic"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(1, result.getInputTables().size());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertTrue(result.getOutputTables().isEmpty());
    }

    @Test
    public void parsesInsertInputAndOutputTables() {
        LineageResult result = LineSql.parse(sqlCase("insert_overwrite"));

        assertEquals(StatementType.INSERT, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("user_summary", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void parsesScriptStatements() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_semicolon"));

        assertEquals(2, results.size());
        assertEquals(StatementType.SELECT, results.get(0).getStatementType());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, results.get(1).getStatementType());
        assertEquals("b", results.get(1).getOutputTables().get(0).getName());
    }

    @Test
    public void parsesJoinInputTables() {
        LineageResult result = LineSql.parse(sqlCase("join_basic"));

        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(2, result.getInputTables().size());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertEquals("orders", result.getInputTables().get(1).getName());
    }

    @Test
    public void parsesCreateViewOutputTable() {
        LineageResult result = LineSql.parse(sqlCase("create_view"));

        assertEquals(StatementType.CREATE_VIEW, result.getStatementType());
        assertEquals("mart", result.getOutputTables().get(0).getSchema());
        assertEquals("v_users", result.getOutputTables().get(0).getName());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void reportsParseErrorsAsDiagnostics() {
        LineageResult result = LineSql.parse(sqlCase("parse_error"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals("SPARK_PARSE_ERROR", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void parsesMergeIntoTable() {
        LineageResult result = LineSql.parse(sqlCase("merge_into"));

        assertEquals(StatementType.MERGE, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("users", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
    }

    @Test
    public void parsesCacheTableAsSelect() {
        LineageResult result = LineSql.parse(sqlCase("cache_table_as_select"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertTrue(result.getDiagnostics().stream().noneMatch(d -> "SPARK_PARSE_ERROR".equals(d.getCode())));
    }

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/spark/manifest.json"));

        assertEquals("SPARK", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/spark/" + file));
        }
    }

    private static String sqlCase(String caseId) {
        String path = "/sql/spark/cases/" + caseId + ".sql";
        try {
            return resource(path);
        } catch (IOException e) {
            throw new AssertionError("Failed to read SQL case resource: " + path, e);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = SparkDialectParserTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            byte[] bytes = input.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream input = SparkDialectParserTest.class.getResourceAsStream(path)) {
            return input != null;
        } catch (IOException e) {
            return false;
        }
    }
}
