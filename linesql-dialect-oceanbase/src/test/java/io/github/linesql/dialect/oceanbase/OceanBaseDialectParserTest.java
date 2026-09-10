package io.github.linesql.dialect.oceanbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.model.ColumnUsage;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
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

public class OceanBaseDialectParserTest {
    private final OceanBaseDialectParser parser = new OceanBaseDialectParser();

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/oceanbase/manifest.json"));

        assertEquals("OCEANBASE", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/oceanbase/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/oceanbase/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/oceanbase/" + sqlCase.get("file").asText());
            LineageResult result = parser.parse(sql, parseOptions(sqlCase), new ParseContext());

            assertEquals(caseId, SqlDialect.OCEANBASE, result.getDialect());
            assertEquals(caseId, StatementType.valueOf(sqlCase.get("statementType").asText()), result.getStatementType());
            assertTables(caseId, sqlCase.get("inputTables"), tableNames(result.getInputTables()));
            assertTables(caseId, sqlCase.get("outputTables"), tableNames(result.getOutputTables()));
            if (sqlCase.has("columnLineage")) {
                assertColumnLineage(caseId, sqlCase.get("columnLineage"), result);
            }
            if (sqlCase.has("columnUsages")) {
                assertColumnUsages(caseId, sqlCase.get("columnUsages"), result);
            }
        }
    }

    @Test
    public void reportsInferredCompatibilityModeWithoutChangingPublicDialect() {
        LineageResult mysqlMode = parser.parse(
                "select /*+ read_consistency(weak) */ id from app.users",
                ParseOptions.defaults(),
                new ParseContext());
        LineageResult oracleMode = parser.parse(
                "select id as org_id from app.org_units start with parent_id is null connect by prior id = parent_id",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(SqlDialect.OCEANBASE, mysqlMode.getDialect());
        assertEquals(SqlDialect.OCEANBASE, oracleMode.getDialect());
        assertTrue(mysqlMode.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getMessage().contains("MySQL compatibility mode")));
        assertTrue(oracleMode.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getMessage().contains("Oracle compatibility mode")));
    }

    @Test
    public void acceptsExplicitCompatibilityModeOption() {
        ParseOptions oracleOptions = ParseOptions.builder()
                .dialectOption(OceanBaseDialectParser.COMPATIBILITY_MODE_OPTION, "oracle")
                .build();
        ParseOptions mysqlOptions = ParseOptions.builder()
                .dialectOption(OceanBaseDialectParser.COMPATIBILITY_MODE_OPTION, "mysql")
                .build();

        LineageResult oracleMode = parser.parse("select id from dual", oracleOptions, new ParseContext());
        LineageResult mysqlMode = parser.parse("select id from app.users", mysqlOptions, new ParseContext());

        assertEquals(SqlDialect.OCEANBASE, oracleMode.getDialect());
        assertEquals(SqlDialect.OCEANBASE, mysqlMode.getDialect());
        assertTrue(oracleMode.getDiagnostics().stream()
                .anyMatch(diagnostic -> "OCEANBASE_COMPATIBILITY_MODE_EXPLICIT".equals(diagnostic.getCode())
                        && diagnostic.getMessage().contains("Oracle compatibility mode")));
        assertTrue(mysqlMode.getDiagnostics().stream()
                .anyMatch(diagnostic -> "OCEANBASE_COMPATIBILITY_MODE_EXPLICIT".equals(diagnostic.getCode())
                        && diagnostic.getMessage().contains("MySQL compatibility mode")));
    }

    @Test
    public void explicitOracleCompatibilityModePreservesDescribeTableReference() {
        ParseOptions options = ParseOptions.builder()
                .dialectOption(OceanBaseDialectParser.COMPATIBILITY_MODE_OPTION, "oracle")
                .build();

        LineageResult result = parser.parse("desc hr.employees salary", options, new ParseContext());

        assertEquals(SqlDialect.OCEANBASE, result.getDialect());
        assertEquals(StatementType.READ_METADATA, result.getStatementType());
        assertTables("explicit_oracle_describe", tableArray("hr.employees"), tableNames(result.getInputTables()));
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = OceanBaseDialectParserTest.class.getResourceAsStream(path)) {
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
        try (InputStream input = OceanBaseDialectParserTest.class.getResourceAsStream(path)) {
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

    private static JsonNode tableArray(String tableName) {
        return new ObjectMapper().createArrayNode().add(tableName);
    }

    private static ParseOptions parseOptions(JsonNode sqlCase) {
        if (sqlCase.has("compatibilityMode")) {
            return ParseOptions.builder()
                    .dialectOption(OceanBaseDialectParser.COMPATIBILITY_MODE_OPTION, sqlCase.get("compatibilityMode").asText())
                    .build();
        }
        return ParseOptions.defaults();
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
                    .map(OceanBaseDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(OceanBaseDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(OceanBaseDialectParserTest::tableName)
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
