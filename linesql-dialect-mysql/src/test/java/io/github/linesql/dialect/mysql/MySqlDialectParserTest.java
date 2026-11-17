package io.github.linesql.dialect.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.ColumnUsage;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.dialect.mysql.antlr.MySqlLineageLexer;
import io.github.linesql.dialect.mysql.antlr.MySqlParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySqlDialectParserTest {
    private final MySqlDialectParser parser = new MySqlDialectParser();

    @Test
    public void intersectBindsMoreTightlyThanUnionAndExcept() {
        String sql = "SELECT a FROM t1 UNION SELECT b FROM t2 INTERSECT ALL SELECT c FROM t3 EXCEPT SELECT d FROM t4";
        MySqlLineageLexer lexer = new MySqlLineageLexer(CharStreams.fromString(sql));
        MySqlParser grammar = new MySqlParser(new CommonTokenStream(lexer));
        MySqlParser.SetOperationContext except = (MySqlParser.SetOperationContext) grammar.query().queryTerm();
        MySqlParser.SetOperationContext union = (MySqlParser.SetOperationContext) except.left;
        MySqlParser.SetOperationContext intersect = (MySqlParser.SetOperationContext) union.right;

        assertEquals(0, grammar.getNumberOfSyntaxErrors());
        assertTrue(except.EXCEPT() != null);
        assertTrue(union.UNION() != null);
        assertTrue(intersect.INTERSECT() != null);
        assertEquals("ALL", intersect.setQuantifier().getText());
    }

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/mysql/manifest.json"));

        assertEquals("MYSQL", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/mysql/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/mysql/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/mysql/" + sqlCase.get("file").asText());
            String statementType = sqlCase.get("statementType").asText();

            if ("MULTI".equals(statementType)) {
                List<LineageResult> results = LineSql.parseScript(sql, SqlDialect.MYSQL);
                assertTables(caseId, sqlCase.get("inputTables"), collectInputTables(results));
                assertTables(caseId, sqlCase.get("outputTables"), collectOutputTables(results));
                if (sqlCase.has("statements")) {
                    JsonNode statements = sqlCase.get("statements");
                    assertEquals(caseId, statements.size(), results.size());
                    for (int index = 0; index < results.size(); index++) {
                        assertCaseLineage(caseId + "#" + (index + 1), statements.get(index), results.get(index));
                    }
                }
                continue;
            }

            LineageResult result = parser.parse(sql, ParseOptions.defaults(), new ParseContext());

            assertCaseLineage(caseId, sqlCase, result);
        }
    }

    private static void assertCaseLineage(String caseId, JsonNode expected, LineageResult result) {
        assertEquals(caseId, SqlDialect.MYSQL, result.getDialect());
        assertEquals(caseId, StatementType.valueOf(expected.get("statementType").asText()), result.getStatementType());
        assertTables(caseId, expected.get("inputTables"), tableNames(result.getInputTables()));
        assertTables(caseId, expected.get("outputTables"), tableNames(result.getOutputTables()));
        if (expected.has("columnLineage")) {
            assertColumnLineage(caseId, expected.get("columnLineage"), result);
        }
        if (expected.has("columnUsages")) {
            assertColumnUsages(caseId, expected.get("columnUsages"), result);
        }
    }

    @Test
    public void autoDetectsMysqlSpecificStatements() {
        LineageResult update = LineSql.parse(sqlCase("update_join"));
        assertEquals(SqlDialect.MYSQL, update.getDialect());
        assertEquals(StatementType.UPDATE, update.getStatementType());

        LineageResult delete = LineSql.parse(sqlCase("delete_using"));
        assertEquals(SqlDialect.MYSQL, delete.getDialect());
        assertEquals(StatementType.DELETE, delete.getStatementType());

        LineageResult deleteJoin = LineSql.parse(sqlCase("delete_join"));
        assertEquals(SqlDialect.MYSQL, deleteJoin.getDialect());
        assertEquals(StatementType.DELETE, deleteJoin.getStatementType());
    }

    @Test
    public void propagatesTemporaryTableAcrossScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_temp_table_lineage"), SqlDialect.MYSQL);

        assertEquals(2, results.size());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("app.users", tableNames(results.get(1).getInputTables()).get(0));
        assertEquals("mart.user_summary", tableNames(results.get(1).getOutputTables()).get(0));
        assertEquals(2, results.get(1).getColumnLineage().size());
        assertEquals("mart.user_summary.user_id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals("app.users.id", columnName(results.get(1).getColumnLineage().get(0).getSources().get(0)));
        assertEquals("mart.user_summary.user_name", columnName(results.get(1).getColumnLineage().get(1).getTarget()));
        assertEquals("app.users.name", columnName(results.get(1).getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void dropsTemporaryTableFromScriptContext() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_drop_temp_table"), SqlDialect.MYSQL);

        assertEquals(3, results.size());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, results.get(0).getStatementType());
        assertEquals(StatementType.DROP_TABLE, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals("tmp_users", tableNames(results.get(2).getInputTables()).get(0));
    }

    private static String sqlCase(String caseId) {
        String path = "/sql/mysql/cases/" + caseId + ".sql";
        try {
            return resource(path);
        } catch (IOException e) {
            throw new AssertionError("Failed to read SQL case resource: " + path, e);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = MySqlDialectParserTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            byte[] bytes = readAllBytes(input);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean resourceExists(String path) {
        try (InputStream input = MySqlDialectParserTest.class.getResourceAsStream(path)) {
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

    private static void assertColumnLineage(String caseId, JsonNode expectedNode, LineageResult result) {
        assertEquals(caseId, expectedNode.size(), result.getColumnLineage().size());
        for (int i = 0; i < expectedNode.size(); i++) {
            JsonNode expected = expectedNode.get(i);
            io.github.linesql.core.model.ColumnLineage actual = result.getColumnLineage().get(i);
            assertEquals(caseId, expected.get("target").asText(), columnName(actual.getTarget()));
            List<String> expectedSources = new ArrayList<>();
            expected.get("sources").forEach(node -> expectedSources.add(node.asText()));
            List<String> actualSources = actual.getSources().stream()
                    .map(MySqlDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(MySqlDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(MySqlDialectParserTest::tableName)
                .collect(Collectors.toList());
    }

    private static List<String> collectInputTables(List<LineageResult> results) {
        List<io.github.linesql.core.model.TableRef> tables = new ArrayList<>();
        for (LineageResult result : results) {
            tables.addAll(result.getInputTables());
        }
        return tableNames(tables);
    }

    private static List<String> collectOutputTables(List<LineageResult> results) {
        List<io.github.linesql.core.model.TableRef> tables = new ArrayList<>();
        for (LineageResult result : results) {
            tables.addAll(result.getOutputTables());
        }
        return tableNames(tables);
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

    private static String columnName(io.github.linesql.core.model.ColumnRef column) {
        if (column.getTable() == null) {
            return column.getName();
        }
        return tableName(column.getTable()) + "." + column.getName();
    }
}
