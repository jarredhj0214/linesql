package io.github.linesql.core;

import io.github.linesql.core.facade.SqlLineageParser;
import io.github.linesql.core.internal.DefaultSqlLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;

import java.util.Collections;
import java.util.List;

public final class LineSql {
    private static volatile SqlLineageParser parser;

    private LineSql() {
    }

    public static LineageResult parse(String sql) {
        List<LineageResult> results = parseScript(sql, ParseOptions.defaults(), new ParseContext());
        return firstOrEmpty(results);
    }

    public static LineageResult parse(String sql, ParseOptions options) {
        List<LineageResult> results = parseScript(sql, options, new ParseContext());
        return firstOrEmpty(results);
    }

    public static LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        List<LineageResult> results = parseScript(sql, options, context);
        return firstOrEmpty(results);
    }

    public static LineageResult parse(String sql, SqlDialect dialect) {
        return parse(sql, dialect, new ParseContext());
    }

    public static LineageResult parse(String sql, SqlDialect dialect, ParseContext context) {
        ParseOptions options = ParseOptions.builder()
                .dialectHints(Collections.singletonList(dialect))
                .build();
        List<LineageResult> results = parseScript(sql, options, context);
        return firstOrEmpty(results);
    }

    private static LineageResult firstOrEmpty(List<LineageResult> results) {
        if (results.isEmpty()) {
            return LineageResult.error(SqlDialect.UNKNOWN, "EMPTY_SQL", "SQL input is empty.");
        }
        return results.get(0);
    }

    public static List<LineageResult> parseScript(String script) {
        return parseScript(script, ParseOptions.defaults(), new ParseContext());
    }

    public static List<LineageResult> parseScript(String script, ParseOptions options) {
        return parseScript(script, options, new ParseContext());
    }

    public static List<LineageResult> parseScript(String script, SqlDialect dialect) {
        return parseScript(script, dialect, new ParseContext());
    }

    public static List<LineageResult> parseScript(String script, SqlDialect dialect, ParseContext context) {
        ParseOptions options = ParseOptions.builder()
                .dialectHints(Collections.singletonList(dialect))
                .build();
        return parseScript(script, options, context);
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
