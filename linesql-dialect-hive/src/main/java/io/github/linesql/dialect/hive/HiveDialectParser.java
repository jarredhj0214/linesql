package io.github.linesql.dialect.hive;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;

public class HiveDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.HIVE;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.HIVE);
        result.setDialectConfidence(1.0);
        result.getDiagnostics().add(Diagnostic.warning(
                "HIVE_PARSER_SCAFFOLD",
                "Hive parser module is registered; lineage extraction will be implemented incrementally."));
        return result;
    }
}
