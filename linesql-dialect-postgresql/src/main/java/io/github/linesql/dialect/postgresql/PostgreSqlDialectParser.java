package io.github.linesql.dialect.postgresql;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;

public class PostgreSqlDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.POSTGRESQL;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.POSTGRESQL);
        result.setDialectConfidence(1.0d);
        result.setStatementType(StatementType.UNKNOWN);
        result.getDiagnostics().add(Diagnostic.warning(
                "POSTGRESQL_STATEMENT_NOT_SUPPORTED",
                "PostgreSQL grammar is planned but not implemented yet."));
        return result;
    }
}
