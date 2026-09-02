package io.github.linesql.dialect.starrocks;

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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StarRocksDialectParserTest {
    private final StarRocksDialectParser parser = new StarRocksDialectParser();

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/starrocks/manifest.json"));

        assertEquals("STARROCKS", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/starrocks/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/starrocks/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/starrocks/" + sqlCase.get("file").asText());
            LineageResult result = parser.parse(sql, ParseOptions.defaults(), new ParseContext());

            assertEquals(caseId, SqlDialect.STARROCKS, result.getDialect());
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
    public void autoDetectsStarRocksDuplicateKeySyntax() throws IOException {
        LineageResult result = LineSql.parse(sqlCase("create_table_duplicate_key"));

        assertEquals(SqlDialect.STARROCKS, result.getDialect());
    }

    @Test
    public void representsTableStarColumnLineageWithoutMetadata() {
        LineageResult result = parser.parse(
                "SELECT * FROM ods.users",
                ParseOptions.defaults(),
                new ParseContext());

        assertColumnLineage(list(
                "* <- ods.users.*"
        ), result);
    }

    @Test
    public void expandsAliasedSubqueryStarColumnLineage() {
        LineageResult result = parser.parse(
                "SELECT q.* FROM (SELECT id AS user_id, name FROM ods.users) q",
                ParseOptions.defaults(),
                new ParseContext());

        assertColumnLineage(list(
                "user_id <- ods.users.id",
                "name <- ods.users.name"
        ), result);
    }

    @Test
    public void resolvesQualifiedColumnsFromDerivedSelectStar() {
        LineageResult result = parser.parse(
                "INSERT INTO ads.users(user_id, name) SELECT q.id, q.name FROM (SELECT * FROM ods.users) q",
                ParseOptions.defaults(),
                new ParseContext());

        assertColumnLineage(list(
                "ads.users.user_id <- ods.users.id",
                "ads.users.name <- ods.users.name"
        ), result);
    }

    @Test
    public void resolvesStructFieldDereferenceFromSingleInputTable() {
        LineageResult result = parser.parse(
                "SELECT data_json.publish_time AS publish_time FROM ods.events",
                ParseOptions.defaults(),
                new ParseContext());

        assertColumnLineage(list(
                "publish_time <- ods.events.data_json.publish_time"
        ), result);
    }

    @Test
    public void infersUnaliasedSingleSourceExpressionTarget() {
        LineageResult result = parser.parse(
                "SELECT ifnull(workshop_code, 'N/A') FROM ods.orders",
                ParseOptions.defaults(),
                new ParseContext());

        assertColumnLineage(list(
                "workshop_code <- ods.orders.workshop_code"
        ), result);
    }

    @Test
    public void expandsWildcardFromPriorCreateTableSchemaInSharedContext() {
        ParseContext context = new ParseContext();
        parser.parse(
                "CREATE TABLE ods.users (id bigint, name varchar(64)) DUPLICATE KEY(id)",
                ParseOptions.defaults(),
                context);
        LineageResult result = parser.parse(
                "INSERT INTO ads.users SELECT * FROM ods.users",
                ParseOptions.defaults(),
                context);

        assertColumnLineage(list(
                "ads.users.id <- ods.users.id",
                "ads.users.name <- ods.users.name"
        ), result);
    }

    @Test
    public void propagatesCreateViewLineageInSharedContext() {
        ParseContext context = new ParseContext();
        parser.parse(
                "CREATE VIEW dwd.active_users AS SELECT id AS user_id, name FROM ods.users WHERE status = 1",
                ParseOptions.defaults(),
                context);
        LineageResult result = parser.parse(
                "SELECT v.* FROM dwd.active_users v",
                ParseOptions.defaults(),
                context);

        assertColumnLineage(list(
                "user_id <- ods.users.id",
                "name <- ods.users.name"
        ), result);
    }

    @Test
    public void parseScriptExpandsWildcardFromPriorCreateTableSchema() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE ods.users (id bigint, name varchar(64)) DUPLICATE KEY(id);"
                        + "INSERT INTO ads.users SELECT * FROM ods.users;",
                SqlDialect.STARROCKS);

        assertEquals(2, results.size());
        assertColumnLineage(list(
                "ads.users.id <- ods.users.id",
                "ads.users.name <- ods.users.name"
        ), results.get(1));
    }

    @Test
    public void parseScriptPropagatesPriorCtasLineage() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE dwd.active_users AS SELECT id AS user_id, name FROM ods.users WHERE status = 1;"
                        + "SELECT t.* FROM dwd.active_users t;",
                SqlDialect.STARROCKS);

        assertEquals(2, results.size());
        assertColumnLineage(list(
                "user_id <- ods.users.id",
                "name <- ods.users.name"
        ), results.get(1));
    }

    @Test
    public void parseScriptExpandsKnownQualifiedStarExclude() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE ods.users (id bigint, name varchar(64), email varchar(128)) DUPLICATE KEY(id);"
                        + "SELECT u.* EXCLUDE (email) FROM ods.users u;",
                SqlDialect.STARROCKS);

        assertEquals(2, results.size());
        assertColumnLineage(list(
                "id <- ods.users.id",
                "name <- ods.users.name"
        ), results.get(1));
    }

    @Test
    public void parseScriptResolvesKnownTableAliasColumnList() {
        List<LineageResult> results = LineSql.parseScript(
                "CREATE TABLE ods.users (id bigint, name varchar(64)) DUPLICATE KEY(id);"
                        + "SELECT u.user_id, u.user_name FROM ods.users AS u(user_id, user_name);",
                SqlDialect.STARROCKS);

        assertEquals(2, results.size());
        assertColumnLineage(list(
                "user_id <- ods.users.id",
                "user_name <- ods.users.name"
        ), results.get(1));
    }

    @Test
    public void appliesDefaultNamespaceFromParseContext() {
        ParseContext context = new ParseContext();
        context.setDefaultCatalog("iceberg");
        context.setDefaultSchema("ods");

        LineageResult result = parser.parse(
                "SELECT user_id FROM users",
                ParseOptions.defaults(),
                context);

        assertEquals(list("iceberg.ods.users"), tableNames(result.getInputTables()));
        assertColumnLineage(list(
                "user_id <- iceberg.ods.users.user_id"
        ), result);
    }

    private static String sqlCase(String caseId) throws IOException {
        return resource("/sql/starrocks/cases/" + caseId + ".sql");
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = StarRocksDialectParserTest.class.getResourceAsStream(path)) {
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
        try (InputStream input = StarRocksDialectParserTest.class.getResourceAsStream(path)) {
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
                    .map(StarRocksDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnLineage(List<String> expected, LineageResult result) {
        List<String> actual = result.getColumnLineage().stream()
                .map(lineage -> columnName(lineage.getTarget()) + " <- " + lineage.getSources().stream()
                        .map(StarRocksDialectParserTest::columnName)
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(StarRocksDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(StarRocksDialectParserTest::tableName)
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

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }
}
