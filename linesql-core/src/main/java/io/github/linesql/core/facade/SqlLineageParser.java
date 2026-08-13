package io.github.linesql.core.facade;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;

import java.util.List;

public interface SqlLineageParser {
    List<LineageResult> parseScript(String script);

    List<LineageResult> parseScript(String script, ParseOptions options, ParseContext context);

    LineageResult parseStatement(String sql, SqlDialect dialect, ParseOptions options, ParseContext context);
}
