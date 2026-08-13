package io.github.linesql.core.facade;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;

import java.util.List;

public interface SqlLineageParser {
    List<LineageResult> parseScript(String script);

    LineageResult parseStatement(String sql, SqlDialect dialect);
}
