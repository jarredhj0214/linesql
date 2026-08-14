package io.github.linesql.core;

import io.github.linesql.core.facade.SqlLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LineSqlTest {
    @Test
    public void parseAcceptsExplicitDialect() {
        try {
            LineSql.configure(new RecordingParser());

            LineageResult result = LineSql.parse("select id from ods.users", SqlDialect.MYSQL);

            assertEquals(SqlDialect.MYSQL, result.getDialect());
            assertEquals(1.0, result.getDialectConfidence(), 0.001);
        } finally {
            LineSql.configure(null);
        }
    }

    @Test
    public void parseAcceptsParseOptions() {
        try {
            LineSql.configure(new RecordingParser());
            ParseOptions options = ParseOptions.builder()
                    .dialectHints(Collections.singletonList(SqlDialect.ORACLE))
                    .build();

            LineageResult result = LineSql.parse("select id from dual", options);

            assertEquals(SqlDialect.ORACLE, result.getDialect());
        } finally {
            LineSql.configure(null);
        }
    }

    @Test
    public void parseScriptAcceptsExplicitDialect() {
        try {
            LineSql.configure(new RecordingParser());

            List<LineageResult> results = LineSql.parseScript("select id from dbo.users", SqlDialect.SQLSERVER);

            assertEquals(1, results.size());
            assertEquals(SqlDialect.SQLSERVER, results.get(0).getDialect());
        } finally {
            LineSql.configure(null);
        }
    }

    private static class RecordingParser implements SqlLineageParser {
        @Override
        public List<LineageResult> parseScript(String script) {
            return parseScript(script, ParseOptions.defaults(), new ParseContext());
        }

        @Override
        public List<LineageResult> parseScript(String script, ParseOptions options, ParseContext context) {
            SqlDialect dialect = options.getDialectHints().isEmpty()
                    ? SqlDialect.SPARK
                    : options.getDialectHints().get(0);
            return Collections.singletonList(parseStatement(script, dialect, options, context));
        }

        @Override
        public LineageResult parseStatement(String sql, SqlDialect dialect, ParseOptions options, ParseContext context) {
            LineageResult result = new LineageResult();
            result.setDialect(dialect);
            result.setDialectConfidence(1.0);
            result.setStatementType(StatementType.SELECT);
            return result;
        }
    }
}
