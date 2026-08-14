package io.github.linesql.core.internal;

import io.github.linesql.core.model.DialectCandidate;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SimpleDialectDetector implements DialectDetector {
    @Override
    public List<SqlDialect> detect(String sql) {
        List<SqlDialect> dialects = new ArrayList<SqlDialect>();
        for (DialectCandidate candidate : detectCandidates(sql)) {
            dialects.add(candidate.getDialect());
        }
        return dialects;
    }

    @Override
    public List<DialectCandidate> detectCandidates(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        List<DialectCandidate> candidates = new ArrayList<DialectCandidate>();

        if (normalized.matches("(?s).*\\bupdate\\b.+\\bjoin\\b.+\\bset\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\busing\\b.*")
                || normalized.matches("(?s).*\\bdelete\\b.+\\bfrom\\b.+\\bjoin\\b.*")
                || normalized.matches("(?s)^\\s*replace\\s+into\\b.*")
                || normalized.contains(" on duplicate key ")
                || normalized.matches("(?s).*\\blimit\\s+\\d+\\s*,\\s*\\d+.*")) {
            candidates.add(candidate(SqlDialect.MYSQL, 0.92, "MySQL-specific write, DML, or LIMIT syntax"));
        }
        if (normalized.matches("(?s).*\\brow\\s+format\\b.*")
                || normalized.matches("(?s).*\\bstored\\s+as\\b.*")
                || normalized.matches("(?s).*\\bserdeproperties\\b.*")
                || normalized.matches("(?s).*\\bclustered\\s+by\\b.*")) {
            candidates.add(candidate(SqlDialect.HIVE, 0.90, "Hive storage or table layout syntax"));
        }
        if (normalized.contains("'connector'")
                || normalized.contains("\"connector\"")
                || normalized.matches("(?s).*\\bwatermark\\s+for\\b.*")
                || normalized.matches("(?s).*\\bwith\\s+connector\\b.*")) {
            candidates.add(candidate(SqlDialect.FLINK, 0.93, "Flink connector or watermark syntax"));
        }
        if (normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bduplicate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\baggregate\\s+key\\b.*")
                || normalized.matches("(?s).*\\bcreate\\s+table\\b.+\\bdistributed\\s+by\\s+hash\\b.*")
                || normalized.contains(" properties (\"replication_num\"")) {
            candidates.add(candidate(SqlDialect.STARROCKS, 0.93, "StarRocks key, distribution, or replication syntax"));
        }
        if (normalized.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || normalized.matches("(?s).*\\bconnect\\s+by\\b.*")
                || normalized.matches("(?s).*\\bstart\\s+with\\b.*")) {
            candidates.add(candidate(SqlDialect.ORACLE, 0.90, "Oracle DUAL or hierarchical query syntax"));
        }
        if (normalized.matches("(?s).*\\bselect\\s+top\\s+\\d+\\b.*")
                || normalized.matches("(?s).*\\[[\\p{L}_@#][^\\]]*\\].*")
                || normalized.matches("(?s).*\\bwith\\s*\\(\\s*nolock\\s*\\).*")) {
            candidates.add(candidate(SqlDialect.SQLSERVER, 0.91, "SQL Server TOP, bracketed identifier, or table hint syntax"));
        }
        if (normalized.contains("insert overwrite")
                || normalized.contains("lateral view")
                || normalized.contains("create temporary view")
                || normalized.contains(" using ")) {
            candidates.add(candidate(SqlDialect.SPARK, 0.88, "Spark insert overwrite, lateral view, temporary view, or USING syntax"));
        }
        if (!contains(candidates, SqlDialect.SPARK)) {
            candidates.add(candidate(SqlDialect.SPARK, 0.50, "Dialect-neutral SQL; Spark is the current generic fallback"));
        }
        candidates.sort(Comparator.comparingDouble(DialectCandidate::getConfidence).reversed());
        return candidates;
    }

    private static DialectCandidate candidate(SqlDialect dialect, double confidence, String reason) {
        return new DialectCandidate(dialect, confidence, reason);
    }

    private static boolean contains(List<DialectCandidate> candidates, SqlDialect dialect) {
        for (DialectCandidate candidate : candidates) {
            if (candidate.getDialect() == dialect) {
                return true;
            }
        }
        return false;
    }
}
