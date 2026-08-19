package io.github.linesql.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void acceptsExplicitDialectOption() throws Exception {
        RunResult result = run("--dialect", "MYSQL", "select id from ods.users");

        assertEquals(0, result.exitCode);
        JsonNode json = mapper.readTree(result.stdout);
        assertEquals("MYSQL", json.get(0).get("dialect").asText());
        assertTrue(json.get(0).get("dialectDetectionReason").asText().contains("hint"));
    }

    @Test
    public void acceptsDialectEqualsOption() throws Exception {
        RunResult result = run("--dialect=SQLSERVER", "select top 10 id from dbo.users");

        assertEquals(0, result.exitCode);
        JsonNode json = mapper.readTree(result.stdout);
        assertEquals("SQLSERVER", json.get(0).get("dialect").asText());
    }

    @Test
    public void acceptsCommonDialectAliases() throws Exception {
        RunResult postgres = run("--dialect", "postgres", "select id from public.users");
        assertEquals(0, postgres.exitCode);
        JsonNode postgresJson = mapper.readTree(postgres.stdout);
        assertEquals("POSTGRESQL", postgresJson.get(0).get("dialect").asText());

        RunResult sqlServer = run("--dialect=sql-server", "select top 10 id from dbo.users");
        assertEquals(0, sqlServer.exitCode);
        JsonNode sqlServerJson = mapper.readTree(sqlServer.stdout);
        assertEquals("SQLSERVER", sqlServerJson.get(0).get("dialect").asText());
    }

    @Test
    public void readsSqlFromStdinWhenNoSqlArgsProvided() throws Exception {
        RunResult result = runWithInput("select id from dual", "--dialect", "ORACLE");

        assertEquals(0, result.exitCode);
        JsonNode json = mapper.readTree(result.stdout);
        assertEquals("ORACLE", json.get(0).get("dialect").asText());
    }

    @Test
    public void rejectsUnknownDialect() throws Exception {
        RunResult result = run("--dialect", "db2", "select 1");

        assertEquals(2, result.exitCode);
        assertTrue(result.stderr.contains("Unsupported dialect"));
    }

    private static RunResult run(String... args) throws Exception {
        return runWithInput("", args);
    }

    private static RunResult runWithInput(String input, String... args) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = Main.run(
                args,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8.name()),
                new PrintStream(stderr, true, StandardCharsets.UTF_8.name()));
        return new RunResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8.name()),
                stderr.toString(StandardCharsets.UTF_8.name()));
    }

    private static class RunResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        RunResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
