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

        if (normalized.matches("(?s).*\\bupdate\\b.+\\bjoin\\b.+\\bset\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\busing\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\bfrom\\b.+\\bjoin\\b.*")
                || normalized.matches("(?s)^\\s*replace\\s+into\\b.*")
                || normalized.contains(" on duplicate key ")
                || normalized.matches("(?s).*\\blimit\\s+\\d+\\s*,\\s*\\d+.*")) {
            candidates.add(SqlDialect.MYSQL);
        }
        if (normalized.matches("(?s).*\\brow\\s+format\\b.*")
                || normalized.matches("(?s).*\\bstored\\s+as\\b.*")
                || normalized.matches("(?s).*\\bserdeproperties\\b.*")
                || normalized.matches("(?s).*\\bclustered\\s+by\\b.*")) {
            candidates.add(SqlDialect.HIVE);
        }
        if (normalized.contains("'connector'")
                || normalized.contains("\"connector\"")
                || normalized.matches("(?s).*\\bwatermark\\s+for\\b.*")
                || normalized.matches("(?s).*\\bwith\\s+connector\\b.*")) {
            candidates.add(SqlDialect.FLINK);
        }
        if (normalized.matches("(?s).*\\bduplicate\\s+key\\b.*")
                || normalized.matches("(?s).*\\baggregate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bdistributed\\s+by\\s+hash\\b.*")
                || normalized.contains(" properties (\"replication_num\"")) {
            candidates.add(SqlDialect.STARROCKS);
        }
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
