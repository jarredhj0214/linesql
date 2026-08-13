package io.github.linesql.core.spi;

import io.github.linesql.core.model.SqlDialect;

public interface DialectDetector {
    SqlDialect detect(String sql);
}
