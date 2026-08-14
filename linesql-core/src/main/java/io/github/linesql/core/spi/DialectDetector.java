package io.github.linesql.core.spi;

import io.github.linesql.core.model.DialectCandidate;
import io.github.linesql.core.model.SqlDialect;

import java.util.ArrayList;
import java.util.List;

public interface DialectDetector {
    List<SqlDialect> detect(String sql);

    default List<DialectCandidate> detectCandidates(String sql) {
        List<DialectCandidate> candidates = new ArrayList<DialectCandidate>();
        for (SqlDialect dialect : detect(sql)) {
            candidates.add(new DialectCandidate(dialect, 1.0, "legacy detector candidate"));
        }
        return candidates;
    }
}
