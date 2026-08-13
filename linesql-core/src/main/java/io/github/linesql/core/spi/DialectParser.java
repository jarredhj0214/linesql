package io.github.linesql.core.spi;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;

public interface DialectParser {
    SqlDialect dialect();

    LineageResult parse(String sql);
}
