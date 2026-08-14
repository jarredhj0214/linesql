package io.github.linesql.core.internal;

import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultSqlLineageParserTest {
    @Test
    public void appliesDetectedDialectMetadataToResult() {
        DefaultSqlLineageParser parser = new DefaultSqlLineageParser(
                Collections.singletonList(new FakeParser(SqlDialect.FLINK)),
                new SimpleDialectDetector(),
                Collections::singletonList);

        List<LineageResult> results = parser.parseScript(
                "create table ods_users(id bigint) with ('connector' = 'kafka')",
                ParseOptions.defaults(),
                new ParseContext());

        assertEquals(1, results.size());
        assertEquals(SqlDialect.FLINK, results.get(0).getDialect());
        assertEquals(0.93, results.get(0).getDialectConfidence(), 0.001);
        assertTrue(results.get(0).getDialectDetectionReason().contains("Flink"));
    }

    @Test
    public void appliesDialectHintMetadataToResult() {
        DefaultSqlLineageParser parser = new DefaultSqlLineageParser(
                Collections.singletonList(new FakeParser(SqlDialect.MYSQL)),
                new SimpleDialectDetector(),
                Collections::singletonList);

        ParseOptions options = ParseOptions.builder()
                .dialectHints(Collections.singletonList(SqlDialect.MYSQL))
                .build();
        List<LineageResult> results = parser.parseScript("select id from ods.users", options, new ParseContext());

        assertEquals(1, results.size());
        assertEquals(SqlDialect.MYSQL, results.get(0).getDialect());
        assertEquals(1.0, results.get(0).getDialectConfidence(), 0.001);
        assertTrue(results.get(0).getDialectDetectionReason().contains("hint"));
    }

    private static class FakeParser implements DialectParser {
        private final SqlDialect dialect;

        FakeParser(SqlDialect dialect) {
            this.dialect = dialect;
        }

        @Override
        public SqlDialect dialect() {
            return dialect;
        }

        @Override
        public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
            LineageResult result = new LineageResult();
            result.setDialect(dialect);
            result.setDialectConfidence(1.0);
            result.setStatementType(StatementType.SELECT);
            return result;
        }
    }
}
