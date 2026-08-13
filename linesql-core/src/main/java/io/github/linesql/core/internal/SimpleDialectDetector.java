package io.github.linesql.core.internal;

import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimpleDialectDetector implements DialectDetector {
    @Override
    public List<SqlDialect> detect(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        List<SqlDialect> candidates = new ArrayList<>();

        if (normalized.contains("insert overwrite")
                || normalized.contains("lateral view")
                || normalized.contains("create temporary view")
                || normalized.contains(" using ")) {
            candidates.add(SqlDialect.SPARK);
        }
        if (!candidates.contains(SqlDialect.SPARK)) {
            candidates.add(SqlDialect.SPARK);
        }
        return candidates;
    }
}
