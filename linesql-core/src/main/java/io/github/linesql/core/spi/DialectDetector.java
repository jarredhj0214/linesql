package io.github.linesql.core.spi;

import io.github.linesql.core.model.SqlDialect;
import java.util.List;

public interface DialectDetector {
    List<SqlDialect> detect(String sql);
}
