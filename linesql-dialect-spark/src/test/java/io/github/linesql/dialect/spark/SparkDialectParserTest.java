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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        assertEquals(StatementType.CACHE_TABLE, result.getStatementType());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertEquals("cached_users", result.getOutputTables().get(0).getName());
        assertTrue(result.getDiagnostics().stream().noneMatch(d -> "SPARK_PARSE_ERROR".equals(d.getCode())));
    }

    @Test
    public void propagatesTemporaryViewAcrossScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_temp_view_lineage"));

        assertEquals(2, results.size());
        assertEquals(StatementType.CREATE_VIEW, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
        assertEquals("ads.user_summary", tableNames(results.get(1).getOutputTables()).get(0));
        assertEquals(2, results.get(1).getColumnLineage().size());
        assertEquals("ads.user_summary.user_id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(results.get(1).getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ads.user_summary.user_name", columnName(results.get(1).getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.name", columnName(results.get(1).getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void continuesAfterBadSqlInScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_bad_sql_recovery"));

        assertEquals(2, results.size());
        assertDiagnostics("script_bad_sql_recovery", singletonTextArray("SPARK_PARSE_ERROR"), results.get(0));
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
    }

    @Test
    public void dropsTemporaryViewFromScriptContext() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_drop_temp_view"));

        assertEquals(3, results.size());
        assertEquals(StatementType.CREATE_VIEW, results.get(0).getStatementType());
        assertEquals(StatementType.DROP_VIEW, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals("tmp_users", tableNames(results.get(2).getInputTables()).get(0));
    }

    @Test
    public void propagatesCacheTableAcrossScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_cache_table_lineage"));

        assertEquals(2, results.size());
        assertEquals(StatementType.CACHE_TABLE, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
        assertEquals("ads.cached_user_summary", tableNames(results.get(1).getOutputTables()).get(0));
        assertEquals("ads.cached_user_summary.user_id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(results.get(1).getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void parsesNonLineageStatementsWithoutDiagnostics() {
        assertNonLineageStatement("use_database", StatementType.UNKNOWN);
        assertNonLineageStatement("set_catalog", StatementType.UNKNOWN);
        assertNonLineageStatement("reset_configuration", StatementType.UNKNOWN);
        assertNonLineageStatement("create_namespace", StatementType.UNKNOWN);
        assertNonLineageStatement("drop_namespace", StatementType.UNKNOWN);
        assertNonLineageStatement("show_namespaces", StatementType.READ_METADATA);
        assertNonLineageStatement("show_catalogs", StatementType.READ_METADATA);
        assertNonLineageStatement("analyze_tables", StatementType.READ_METADATA);
        assertNonLineageStatement("create_function", StatementType.UNKNOWN);
        assertNonLineageStatement("create_udf_return_query", StatementType.UNKNOWN);
        assertNonLineageStatement("drop_function", StatementType.UNKNOWN);
        assertNonLineageStatement("call_procedure", StatementType.UNKNOWN);
        assertNonLineageStatement("show_functions", StatementType.READ_METADATA);
        assertNonLineageStatement("describe_function", StatementType.READ_METADATA);
        assertNonLineageStatement("create_variable", StatementType.UNKNOWN);
        assertNonLineageStatement("declare_cursor", StatementType.UNKNOWN);
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

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/spark/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/spark/" + sqlCase.get("file").asText());
            String statementType = sqlCase.get("statementType").asText();

            if ("MULTI".equals(statementType)) {
                List<LineageResult> results = LineSql.parseScript(sql);
                assertTables(caseId, sqlCase.get("inputTables"), collectInputTables(results));
                assertTables(caseId, sqlCase.get("outputTables"), collectOutputTables(results));
                continue;
            }

            LineageResult result = LineSql.parse(sql);
            if ("ERROR".equals(statementType)) {
                assertDiagnostics(caseId, sqlCase.get("expectedDiagnostics"), result);
                continue;
            }

            assertEquals(caseId, StatementType.valueOf(statementType), result.getStatementType());
            assertTables(caseId, sqlCase.get("inputTables"), tableNames(result.getInputTables()));
            assertTables(caseId, sqlCase.get("outputTables"), tableNames(result.getOutputTables()));
            if (sqlCase.has("columnLineage")) {
                assertColumnLineage(caseId, sqlCase.get("columnLineage"), result);
            }
            if (sqlCase.has("expectedDiagnostics")) {
                assertDiagnostics(caseId, sqlCase.get("expectedDiagnostics"), result);
            }
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

    private static void assertTables(String caseId, JsonNode expectedNode, List<String> actual) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.asText()));
        assertEquals(caseId, expected, actual);
    }

    private static void assertDiagnostics(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.asText()));
        List<String> actual = result.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getCode())
                .collect(Collectors.toList());
        assertTrue(caseId + " diagnostics " + actual + " did not include " + expected, actual.containsAll(expected));
    }

    private static void assertNonLineageStatement(String caseId, StatementType statementType) {
        LineageResult result = LineSql.parse(sqlCase(caseId));
        assertEquals(caseId, statementType, result.getStatementType());
        assertTrue(caseId, result.getInputTables().isEmpty());
        assertTrue(caseId, result.getOutputTables().isEmpty());
        assertTrue(caseId, result.getColumnLineage().isEmpty());
        assertTrue(caseId, result.getDiagnostics().isEmpty());
    }

    private static JsonNode singletonTextArray(String value) {
        return new ObjectMapper().createArrayNode().add(value);
    }

    private static List<String> collectInputTables(List<LineageResult> results) {
        Set<String> tables = new LinkedHashSet<>();
        results.forEach(result -> tables.addAll(tableNames(result.getInputTables())));
        return new ArrayList<>(tables);
    }

    private static List<String> collectOutputTables(List<LineageResult> results) {
        Set<String> tables = new LinkedHashSet<>();
        results.forEach(result -> tables.addAll(tableNames(result.getOutputTables())));
        return new ArrayList<>(tables);
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(SparkDialectParserTest::tableName)
                .collect(Collectors.toList());
    }

    private static String tableName(io.github.linesql.core.model.TableRef table) {
        List<String> parts = new ArrayList<>();
        if (table.getCatalog() != null) {
            parts.add(table.getCatalog());
        }
        if (table.getSchema() != null) {
            parts.add(table.getSchema());
        }
        parts.add(table.getName());
        return String.join(".", parts);
    }

    private static void assertColumnLineage(String caseId, JsonNode expectedNode, LineageResult result) {
        assertEquals(caseId, expectedNode.size(), result.getColumnLineage().size());
        for (int i = 0; i < expectedNode.size(); i++) {
            JsonNode expected = expectedNode.get(i);
            io.github.linesql.core.model.ColumnLineage actual = result.getColumnLineage().get(i);
            assertEquals(caseId, expected.get("target").asText(), columnName(actual.getTarget()));
            List<String> expectedSources = new ArrayList<>();
            expected.get("sources").forEach(node -> expectedSources.add(node.asText()));
            List<String> actualSources = actual.getSources().stream()
                    .map(SparkDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static String columnName(io.github.linesql.core.model.ColumnRef column) {
        if (column.getTable() == null) {
            return column.getName();
        }
        return tableName(column.getTable()) + "." + column.getName();
    }
}
