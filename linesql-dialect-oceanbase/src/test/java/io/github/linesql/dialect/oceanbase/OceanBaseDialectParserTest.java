package io.github.linesql.dialect.oceanbase;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OceanBaseDialectParserTest {
    private final OceanBaseDialectParser parser = new OceanBaseDialectParser();

    @Test
    public void parsesMySqlModeSqlThroughOceanBaseDialect() {
        LineageResult result = parser.parse(
                "select id from app.users",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(SqlDialect.OCEANBASE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("app", result.getInputTables().get(0).getSchema());
        assertFalse(result.getDiagnostics().isEmpty());
    }

    @Test
    public void parsesOracleModeSqlThroughOceanBaseDialect() {
        LineageResult result = parser.parse(
                "select id from dual",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(SqlDialect.OCEANBASE, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertFalse(result.getDiagnostics().isEmpty());
    }
}
