package io.github.linesql.dialect.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.model.ColumnUsage;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlLineageLexer;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlParser;
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

public class PostgreSqlDialectParserTest {
    private final PostgreSqlDialectParser parser = new PostgreSqlDialectParser();

    @Test
    public void intersectBindsMoreTightlyThanUnionAndExcept() {
        String sql = "SELECT a FROM t1 UNION SELECT b FROM t2 INTERSECT ALL SELECT c FROM t3 EXCEPT SELECT d FROM t4";
        PostgreSqlLineageLexer lexer = new PostgreSqlLineageLexer(CharStreams.fromString(sql));
        PostgreSqlParser grammar = new PostgreSqlParser(new CommonTokenStream(lexer));
        PostgreSqlParser.SetOperationContext except =
                (PostgreSqlParser.SetOperationContext) grammar.query().queryTerm();
        PostgreSqlParser.SetOperationContext union = (PostgreSqlParser.SetOperationContext) except.left;
        PostgreSqlParser.SetOperationContext intersect = (PostgreSqlParser.SetOperationContext) union.right;

        assertEquals(0, grammar.getNumberOfSyntaxErrors());
        assertTrue(except.EXCEPT() != null);
        assertTrue(union.UNION() != null);
        assertTrue(intersect.INTERSECT() != null);
        assertEquals("ALL", intersect.setQuantifier().getText());
    }

    @Test
    public void skipsNestedCommentsAcrossScriptStatements() {
        String script = "/* outer /* inner */ select id from ignored; */; "
                + "SELECT id FROM ods.a /* outer /* inner */ ; */ WHERE active = true; "
                + "SELECT id FROM ods.b";
        List<LineageResult> results = io.github.linesql.core.LineSql.parseScript(script, SqlDialect.POSTGRESQL);
        assertEquals(2, results.size());
        assertEquals("a", results.get(0).getInputTables().get(0).getName());
        assertEquals("b", results.get(1).getInputTables().get(0).getName());
        assertEquals(ColumnUsageType.WHERE, results.get(0).getColumnUsages().get(0).getType());
    }

    @Test
    public void splitsTaggedDoBlockBeforeFollowingQuery() {
        String script = "DO $Body$ BEGIN PERFORM '$body$;'; END; $Body$; SELECT id FROM ods.a;";
        List<LineageResult> results = io.github.linesql.core.LineSql.parseScript(script, SqlDialect.POSTGRESQL);
        assertEquals(2, results.size());
        assertEquals(StatementType.CONTROL, results.get(0).getStatementType());
        assertTrue(results.get(0).getDiagnostics().isEmpty());
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals(java.util.Collections.singletonList("ods.a"), tableNames(results.get(1).getInputTables()));
    }

    @Test
    public void rejectsUnterminatedTaggedDollarQuote() {
        LineageResult result = parser.parse("SELECT $Tag$text$tag$", ParseOptions.defaults(), new ParseContext());
        assertEquals("POSTGRESQL_PARSE_ERROR", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void separatesQuotedDotsInQualifiedColumnReferences() throws IOException {
        String sql = resource("/sql/postgresql/cases/select_quoted_dotted_identifier_usage.sql");
        LineageResult result = parser.parse(sql, ParseOptions.defaults(), new ParseContext());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(new TableRef(null, "a.b", "c"),
                result.getColumnLineage().get(0).getSources().get(0).getTable());
        assertEquals(new TableRef(null, "a", "b.c"),
                result.getColumnLineage().get(1).getSources().get(0).getTable());
        assertEquals(2, result.getColumnUsages().size());
        assertEquals(new TableRef(null, "a.b", "c"), result.getColumnUsages().get(0).getColumn().getTable());
        assertEquals(new TableRef(null, "a", "b.c"), result.getColumnUsages().get(1).getColumn().getTable());
    }

    @Test
    public void distinguishesQuotedQualifiedNamesFromFoldedIdentifiers() {
        LineageResult result = parser.parse("SELECT \"Sales\".events.id AS first_id, SALES.events.id AS second_id "
                + "FROM \"Sales\".events JOIN sales.events ON \"Sales\".events.id = SALES.events.source_id",
                ParseOptions.defaults(), new ParseContext());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals("Sales", result.getColumnLineage().get(0).getSources().get(0).getTable().getSchema());
        assertEquals("sales", result.getColumnLineage().get(1).getSources().get(0).getTable().getSchema());
    }

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/postgresql/manifest.json"));

        assertEquals("POSTGRESQL", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/postgresql/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/postgresql/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/postgresql/" + sqlCase.get("file").asText());
            JsonNode statements = sqlCase.get("statements");
            if (statements == null) {
                assertCaseLineage(caseId, sqlCase, parser.parse(sql, ParseOptions.defaults(), new ParseContext()));
            } else {
                List<LineageResult> results = LineSql.parseScript(sql, SqlDialect.POSTGRESQL);
                assertEquals(caseId, statements.size(), results.size());
                for (int index = 0; index < results.size(); index++) {
                    assertCaseLineage(caseId + "#" + (index + 1), statements.get(index), results.get(index));
                }
            }
        }
    }

    private static void assertCaseLineage(String caseId, JsonNode expected, LineageResult result) {
        assertEquals(caseId, SqlDialect.POSTGRESQL, result.getDialect());
        String diagnostics = result.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getCode() + ": " + diagnostic.getMessage())
                .collect(Collectors.joining("; "));
        assertEquals(caseId + " diagnostics=" + diagnostics,
                StatementType.valueOf(expected.get("statementType").asText()), result.getStatementType());
        assertTables(caseId, expected.get("inputTables"), tableNames(result.getInputTables()));
        assertTables(caseId, expected.get("outputTables"), tableNames(result.getOutputTables()));
        if (expected.has("columnLineage")) {
            assertColumnLineage(caseId, expected.get("columnLineage"), result);
        }
        if (expected.has("columnUsages")) {
            assertColumnUsages(caseId, expected.get("columnUsages"), result);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = PostgreSqlDialectParserTest.class.getResourceAsStream(path)) {
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
        try (InputStream input = PostgreSqlDialectParserTest.class.getResourceAsStream(path)) {
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
        List<String> actualSummary = result.getColumnLineage().stream()
                .map(lineage -> columnName(lineage.getTarget()) + " <- " + lineage.getSources().stream()
                        .map(PostgreSqlDialectParserTest::columnName)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        assertEquals(caseId + " actual=" + actualSummary,
                expectedNode.size(), result.getColumnLineage().size());
        for (int i = 0; i < expectedNode.size(); i++) {
            JsonNode expected = expectedNode.get(i);
            io.github.linesql.core.model.ColumnLineage actual = result.getColumnLineage().get(i);
            assertEquals(caseId, expected.get("target").asText(), columnName(actual.getTarget()));
            List<String> expectedSources = new ArrayList<>();
            expected.get("sources").forEach(node -> expectedSources.add(node.asText()));
            List<String> actualSources = actual.getSources().stream()
                    .map(PostgreSqlDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(PostgreSqlDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(PostgreSqlDialectParserTest::tableName)
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

    private static String columnName(io.github.linesql.core.model.ColumnRef column) {
        if (column.getTable() == null) {
            return column.getName();
        }
        return tableName(column.getTable()) + "." + column.getName();
    }
}
