package io.github.linesql.core.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SqlCaseManifestQualityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> DIALECT_MODULES = dialectModules();

    @Test
    public void manifestsAreStructurallyConsistent() throws IOException {
        for (DialectManifest manifest : manifests()) {
            assertEquals(manifest.dialect, manifest.dialect, manifest.root.get("dialect").asText());
            assertTrue(manifest.dialect + " cases must be an array", manifest.root.get("cases").isArray());

            Set<String> ids = new LinkedHashSet<>();
            for (JsonNode sqlCase : manifest.root.get("cases")) {
                String id = requiredText(manifest, sqlCase, "id");
                assertTrue(manifest.dialect + " duplicate case id: " + id, ids.add(id));

                String file = requiredText(manifest, sqlCase, "file");
                assertTrue(manifest.dialect + " case file should live under cases/: " + id,
                        file.startsWith("cases/") && file.endsWith(".sql"));
                assertTrue(manifest.dialect + " missing SQL case file for " + id + ": " + file,
                        Files.isRegularFile(manifest.sqlRoot.resolve(file)));

                String statementType = requiredText(manifest, sqlCase, "statementType");
                assertValidStatementType(manifest, id, statementType);
                assertArrayField(manifest, sqlCase, id, "inputTables");
                assertArrayField(manifest, sqlCase, id, "outputTables");
                assertFalse(manifest.dialect + " description should not be empty: " + id,
                        requiredText(manifest, sqlCase, "description").trim().isEmpty());

                if (sqlCase.has("columnLineage")) {
                    assertColumnLineageShape(manifest, sqlCase, id);
                }
                if (sqlCase.has("expectedDiagnostics")) {
                    assertArrayField(manifest, sqlCase, id, "expectedDiagnostics");
                }
            }
        }
    }

    @Test
    public void supportedScenarioDocsReferenceEveryCaseId() throws IOException {
        String docs = Files.readString(repoRoot().resolve("docs/supported-scenarios.md"), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (DialectManifest manifest : manifests()) {
            for (JsonNode sqlCase : manifest.root.get("cases")) {
                String id = sqlCase.get("id").asText();
                if (!docs.contains("`" + id + "`")) {
                    missing.add(manifest.dialect + ":" + id);
                }
            }
        }
        assertTrue("Case ids missing from docs/supported-scenarios.md: " + missing, missing.isEmpty());
    }

    @Test
    public void printsCoverageReport() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("\nLineSQL SQL case coverage\n");
        report.append("| Dialect | Cases | Column cases | Diagnostic cases | Statement types |\n");
        report.append("| --- | ---: | ---: | ---: | --- |\n");

        for (DialectManifest manifest : manifests()) {
            Coverage coverage = coverage(manifest);
            report.append("| ")
                    .append(manifest.dialect)
                    .append(" | ")
                    .append(coverage.totalCases)
                    .append(" | ")
                    .append(coverage.columnCases)
                    .append(" | ")
                    .append(coverage.diagnosticCases)
                    .append(" | ")
                    .append(coverage.statementTypes)
                    .append(" |\n");
        }

        String text = report.toString();
        System.out.println(text);
        assertTrue(text.contains("SPARK"));
        assertTrue(text.contains("FLINK"));
    }

    private static void assertValidStatementType(DialectManifest manifest, String id, String statementType) {
        if ("MULTI".equals(statementType) || "ERROR".equals(statementType)) {
            return;
        }
        try {
            StatementType.valueOf(statementType);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(manifest.dialect + " invalid statementType for " + id + ": " + statementType, e);
        }
    }

    private static void assertArrayField(DialectManifest manifest, JsonNode sqlCase, String id, String field) {
        assertTrue(manifest.dialect + " " + id + " missing array field: " + field,
                sqlCase.has(field) && sqlCase.get(field).isArray());
    }

    private static void assertColumnLineageShape(DialectManifest manifest, JsonNode sqlCase, String id) {
        JsonNode lineage = sqlCase.get("columnLineage");
        assertTrue(manifest.dialect + " " + id + " columnLineage must be an array", lineage.isArray());
        for (JsonNode column : lineage) {
            assertFalse(manifest.dialect + " " + id + " column target should not be empty",
                    requiredText(manifest, column, "target").trim().isEmpty());
            assertArrayField(manifest, column, id, "sources");
        }
    }

    private static String requiredText(DialectManifest manifest, JsonNode node, String field) {
        assertTrue(manifest.dialect + " missing text field: " + field,
                node.has(field) && node.get(field).isTextual());
        return node.get(field).asText();
    }

    private static Coverage coverage(DialectManifest manifest) {
        Coverage coverage = new Coverage();
        coverage.statementTypes = new TreeMap<>();
        for (JsonNode sqlCase : manifest.root.get("cases")) {
            coverage.totalCases++;
            if (sqlCase.has("columnLineage") && sqlCase.get("columnLineage").size() > 0) {
                coverage.columnCases++;
            }
            if (sqlCase.has("expectedDiagnostics") && sqlCase.get("expectedDiagnostics").size() > 0) {
                coverage.diagnosticCases++;
            }
            String statementType = sqlCase.get("statementType").asText();
            coverage.statementTypes.put(statementType, coverage.statementTypes.getOrDefault(statementType, 0) + 1);
        }
        return coverage;
    }

    private static List<DialectManifest> manifests() throws IOException {
        Path root = repoRoot();
        List<DialectManifest> manifests = new ArrayList<>();
        for (Map.Entry<String, String> entry : DIALECT_MODULES.entrySet()) {
            String dialect = entry.getKey();
            String dir = entry.getValue();
            Path sqlRoot = root.resolve("linesql-dialect-" + dir)
                    .resolve("src/test/resources/sql")
                    .resolve(dir);
            Path manifestPath = sqlRoot.resolve("manifest.json");
            assertTrue("Missing manifest for " + dialect + ": " + manifestPath, Files.isRegularFile(manifestPath));
            JsonNode manifest = MAPPER.readTree(manifestPath.toFile());
            manifests.add(new DialectManifest(dialect, dir, sqlRoot, manifest));
        }
        return manifests;
    }

    private static Path repoRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("linesql-core"))
                    && Files.isRegularFile(current.resolve("docs/supported-scenarios.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate LineSQL repository root from user.dir=" + System.getProperty("user.dir"));
    }

    private static Map<String, String> dialectModules() {
        Map<String, String> modules = new LinkedHashMap<>();
        modules.put("SPARK", "spark");
        modules.put("HIVE", "hive");
        modules.put("FLINK", "flink");
        modules.put("STARROCKS", "starrocks");
        modules.put("MYSQL", "mysql");
        modules.put("ORACLE", "oracle");
        modules.put("SQLSERVER", "sqlserver");
        return modules;
    }

    private static final class DialectManifest {
        private final String dialect;
        private final String dir;
        private final Path sqlRoot;
        private final JsonNode root;

        private DialectManifest(String dialect, String dir, Path sqlRoot, JsonNode root) {
            this.dialect = dialect;
            this.dir = dir;
            this.sqlRoot = sqlRoot;
            this.root = root;
        }
    }

    private static final class Coverage {
        private int totalCases;
        private int columnCases;
        private int diagnosticCases;
        private Map<String, Integer> statementTypes;
    }
}
