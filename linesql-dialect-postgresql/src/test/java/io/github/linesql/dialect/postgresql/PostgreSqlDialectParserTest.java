package io.github.linesql.dialect.postgresql;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PostgreSqlDialectParserTest {
    @Test
    public void registersPlannedPostgreSqlParser() {
        LineageResult result = new PostgreSqlDialectParser().parse(
                "insert into public.users(id) values (1) on conflict (id) do nothing",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(SqlDialect.POSTGRESQL, result.getDialect());
        assertEquals(StatementType.UNKNOWN, result.getStatementType());
        assertFalse(result.getDiagnostics().isEmpty());
    }
}
