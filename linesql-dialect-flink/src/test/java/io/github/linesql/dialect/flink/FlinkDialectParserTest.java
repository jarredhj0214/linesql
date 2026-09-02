package io.github.linesql.dialect.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.LineSql;
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

public class FlinkDialectParserTest {
    private final FlinkDialectParser parser = new FlinkDialectParser();

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/flink/manifest.json"));

        assertEquals("FLINK", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/flink/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/flink/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/flink/" + sqlCase.get("file").asText());
            LineageResult result = parser.parse(sql, ParseOptions.defaults(), new ParseContext());

            assertEquals(caseId, SqlDialect.FLINK, result.getDialect());
            assertEquals(caseId, StatementType.valueOf(sqlCase.get("statementType").asText()), result.getStatementType());
            assertTables(caseId, sqlCase.get("inputTables"), tableNames(result.getInputTables()));
            assertTables(caseId, sqlCase.get("outputTables"), tableNames(result.getOutputTables()));
            assertColumnLineage(caseId, sqlCase.get("columnLineage"), result);
            if (sqlCase.has("columnUsages")) {
                assertColumnUsages(caseId, sqlCase.get("columnUsages"), result);
            }
        }
    }

    @Test
    public void autoDetectsFlinkConnectorSyntax() throws IOException {
        LineageResult result = LineSql.parse(sqlCase("create_table_connector"));

        assertEquals(SqlDialect.FLINK, result.getDialect());
    }

    @Test
    public void propagatesWildcardLineageAcrossTemporaryViewScript() throws IOException {
        List<LineageResult> results = LineSql.parseScript(
                resource("/sql/flink/scripts/temp_view_select_star_propagation.sql"),
                SqlDialect.FLINK);

        assertEquals(2, results.size());
        assertEquals(2, results.get(0).getColumnLineage().size());
        assertEquals("ads.orders_copy.id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        List<String> sources = results.get(1).getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList());
        assertEquals(list("ods.orders.id"), sources);
    }

    @Test
    public void expandsWildcardLineageFromScriptCreateTableSchema() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE ods.orders (id BIGINT, amount DECIMAL(10, 2)) WITH ('connector' = 'datagen');"
                        + "CREATE TABLE ads.orders_copy (id BIGINT, amount DECIMAL(10, 2)) WITH ('connector' = 'blackhole');"
                        + "INSERT INTO ads.orders_copy SELECT * FROM ods.orders;",
                SqlDialect.FLINK);

        assertEquals(3, results.size());
        assertEquals("ads.orders_copy.id", columnName(results.get(2).getColumnLineage().get(0).getTarget()));
        List<String> sources = results.get(2).getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList());
        assertEquals(list("ods.orders.id"), sources);
    }

    @Test
    public void resolvesQualifiedColumnsFromDerivedSelectStar() {
        LineageResult result = parser.parse(
                "INSERT INTO ads.orders_copy (id, amount) "
                        + "SELECT s.id, s.amount FROM (SELECT * FROM ods.orders) s",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("ads.orders_copy.id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals(list("ods.orders.id"), result.getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList()));
        assertEquals("ads.orders_copy.amount", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals(list("ods.orders.amount"), result.getColumnLineage().get(1).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList()));
    }

    @Test
    public void infersUnaliasedSingleSourceExpressionTarget() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TEMPORARY VIEW v AS SELECT ifnull(workshop_code, 'N/A'), count(1) num_pass "
                        + "FROM ods.orders GROUP BY ifnull(workshop_code, 'N/A');"
                        + "INSERT INTO ads.order_summary SELECT * FROM v;",
                SqlDialect.FLINK);

        assertEquals(2, results.size());
        assertEquals("v.workshop_code", columnName(results.get(0).getColumnLineage().get(0).getTarget()));
        assertEquals(list("ods.orders.workshop_code"), results.get(0).getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList()));
        assertEquals("ads.order_summary.workshop_code", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals(list("ods.orders.workshop_code"), results.get(1).getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList()));
    }

    @Test
    public void resolvesStructFieldDereferenceFromSingleInputTable() {
        LineageResult result = parser.parse(
                "CREATE TEMPORARY VIEW v AS "
                        + "SELECT DATE_FORMAT(data_json.publish_time, 'yyyyMMdd') AS dt, "
                        + "data_json.work_id AS work_id "
                        + "FROM lake_stage_rt.topic_events "
                        + "WHERE data_json.publish_time >= CURRENT_TIMESTAMP - INTERVAL '48' HOUR",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("v.dt", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals(list("lake_stage_rt.topic_events.data_json.publish_time"),
                result.getColumnLineage().get(0).getSources().stream()
                        .map(FlinkDialectParserTest::columnName)
                        .collect(Collectors.toList()));
        assertEquals("v.work_id", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals(list("lake_stage_rt.topic_events.data_json.work_id"),
                result.getColumnLineage().get(1).getSources().stream()
                        .map(FlinkDialectParserTest::columnName)
                        .collect(Collectors.toList()));
    }

    @Test
    public void expandsQualifiedStarFromKnownTemporaryViewColumns() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TEMPORARY VIEW v1 AS SELECT id AS order_id, amount FROM ods.orders;"
                        + "CREATE TEMPORARY VIEW passthrough AS SELECT *, PROCTIME() AS proc FROM v1;"
                        + "CREATE TEMPORARY VIEW v2 AS SELECT p.*, dim.name AS customer_name "
                        + "FROM passthrough p LEFT JOIN dim.customer dim ON p.order_id = dim.order_id;"
                        + "INSERT INTO ads.orders SELECT order_id, customer_name FROM v2;",
                SqlDialect.FLINK);

        assertEquals(4, results.size());
        List<String> v2Targets = results.get(2).getColumnLineage().stream()
                .map(lineage -> columnName(lineage.getTarget()))
                .collect(Collectors.toList());
        assertTrue(v2Targets.contains("v2.order_id"));
        assertTrue(v2Targets.contains("v2.amount"));
        assertTrue(v2Targets.contains("v2.customer_name"));
        assertEquals("ads.orders.order_id", columnName(results.get(3).getColumnLineage().get(0).getTarget()));
        assertEquals(list("ods.orders.id"), results.get(3).getColumnLineage().get(0).getSources().stream()
                .map(FlinkDialectParserTest::columnName)
                .collect(Collectors.toList()));
    }

    private static String sqlCase(String caseId) throws IOException {
        return resource("/sql/flink/cases/" + caseId + ".sql");
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = FlinkDialectParserTest.class.getResourceAsStream(path)) {
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
        try (InputStream input = FlinkDialectParserTest.class.getResourceAsStream(path)) {
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

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
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
                    .map(FlinkDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(FlinkDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(FlinkDialectParserTest::tableName)
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
