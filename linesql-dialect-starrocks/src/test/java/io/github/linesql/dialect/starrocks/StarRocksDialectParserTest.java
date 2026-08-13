package io.github.linesql.dialect.starrocks;

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

public class StarRocksDialectParserTest {
    @Test
    public void returnsScaffoldDiagnostic() throws IOException {
        LineageResult result = new StarRocksDialectParser().parse(sqlCase(), ParseOptions.defaults(), new ParseContext());

        assertEquals(SqlDialect.STARROCKS, result.getDialect());
        assertFalse(result.getDiagnostics().isEmpty());
        assertEquals("STARROCKS_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void autoDetectsStarRocksDuplicateKeySyntax() throws IOException {
        LineageResult result = LineSql.parse(sqlCase());

        assertEquals(SqlDialect.STARROCKS, result.getDialect());
        assertEquals("STARROCKS_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    private static String sqlCase() throws IOException {
        try (InputStream input = StarRocksDialectParserTest.class.getResourceAsStream(
                "/sql/starrocks/cases/create_table_duplicate_key.sql")) {
            if (input == null) {
                throw new AssertionError("Missing StarRocks scaffold SQL case.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
