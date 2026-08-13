package io.github.linesql.dialect.hive;

import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HiveDialectParserTest {
    @Test
    public void returnsScaffoldDiagnostic() throws IOException {
        LineageResult result = new HiveDialectParser().parse(sqlCase(), ParseOptions.defaults(), new ParseContext());

        assertEquals(SqlDialect.HIVE, result.getDialect());
        assertFalse(result.getDiagnostics().isEmpty());
        assertEquals("HIVE_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void autoDetectsHiveStoredAsSyntax() throws IOException {
        LineageResult result = LineSql.parse(sqlCase());

        assertEquals(SqlDialect.HIVE, result.getDialect());
        assertEquals("HIVE_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    private static String sqlCase() throws IOException {
        try (InputStream input = HiveDialectParserTest.class.getResourceAsStream(
                "/sql/hive/cases/create_table_stored_as.sql")) {
            if (input == null) {
                throw new AssertionError("Missing Hive scaffold SQL case.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
