package io.github.linesql.dialect.flink;

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

public class FlinkDialectParserTest {
    @Test
    public void returnsScaffoldDiagnostic() throws IOException {
        LineageResult result = new FlinkDialectParser().parse(sqlCase(), ParseOptions.defaults(), new ParseContext());

        assertEquals(SqlDialect.FLINK, result.getDialect());
        assertFalse(result.getDiagnostics().isEmpty());
        assertEquals("FLINK_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void autoDetectsFlinkConnectorSyntax() throws IOException {
        LineageResult result = LineSql.parse(sqlCase());

        assertEquals(SqlDialect.FLINK, result.getDialect());
        assertEquals("FLINK_PARSER_SCAFFOLD", result.getDiagnostics().get(0).getCode());
    }

    private static String sqlCase() throws IOException {
        try (InputStream input = FlinkDialectParserTest.class.getResourceAsStream(
                "/sql/flink/cases/create_table_connector.sql")) {
            if (input == null) {
                throw new AssertionError("Missing Flink scaffold SQL case.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
