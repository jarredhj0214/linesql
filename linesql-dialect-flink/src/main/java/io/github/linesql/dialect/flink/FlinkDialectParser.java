package io.github.linesql.dialect.flink;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;

public class FlinkDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.FLINK;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.FLINK);
        result.setDialectConfidence(1.0);
        result.getDiagnostics().add(Diagnostic.warning(
                "FLINK_PARSER_SCAFFOLD",
                "Flink parser module is registered; lineage extraction will be implemented incrementally."));
        return result;
    }
}
