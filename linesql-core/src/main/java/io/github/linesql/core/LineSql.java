package io.github.linesql.core;

import io.github.linesql.core.facade.SqlLineageParser;
import io.github.linesql.core.internal.DefaultSqlLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;

import java.util.List;

public final class LineSql {
    private static volatile SqlLineageParser parser;

    private LineSql() {
    }

    public static LineageResult parse(String sql) {
        List<LineageResult> results = parseScript(sql, ParseOptions.defaults(), new ParseContext());
        if (results.isEmpty()) {
            return LineageResult.error(SqlDialect.UNKNOWN, "EMPTY_SQL", "SQL input is empty.");
        }
        return results.get(0);
    }

    public static List<LineageResult> parseScript(String script) {
        return parseScript(script, ParseOptions.defaults(), new ParseContext());
    }

    public static List<LineageResult> parseScript(String script, ParseOptions options, ParseContext context) {
        SqlLineageParser current = parser;
        if (current == null) {
            current = new DefaultSqlLineageParser();
            parser = current;
        }
        return current.parseScript(script, options, context);
    }

    public static void configure(SqlLineageParser parser) {
        LineSql.parser = parser;
    }
}
