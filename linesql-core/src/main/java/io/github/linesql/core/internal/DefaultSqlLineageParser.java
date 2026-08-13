package io.github.linesql.core.internal;

import io.github.linesql.core.facade.SqlLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectDetector;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.core.spi.StatementSplitter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class DefaultSqlLineageParser implements SqlLineageParser {
    private final Map<SqlDialect, DialectParser> parsers = new EnumMap<>(SqlDialect.class);
    private final DialectDetector dialectDetector;
    private final StatementSplitter statementSplitter;

    public DefaultSqlLineageParser() {
        this(ServiceLoader.load(DialectParser.class), new SimpleDialectDetector(), new SimpleStatementSplitter());
    }

    DefaultSqlLineageParser(Iterable<DialectParser> dialectParsers,
                            DialectDetector dialectDetector,
                            StatementSplitter statementSplitter) {
        for (DialectParser parser : dialectParsers) {
            this.parsers.put(parser.dialect(), parser);
        }
        this.dialectDetector = dialectDetector;
        this.statementSplitter = statementSplitter;
    }

    @Override
    public List<LineageResult> parseScript(String script) {
        return parseScript(script, ParseOptions.defaults(), new ParseContext());
    }

    @Override
    public List<LineageResult> parseScript(String script, ParseOptions options, ParseContext context) {
        List<LineageResult> results = new ArrayList<>();
        for (String statement : statementSplitter.split(script)) {
            SqlDialect dialect = selectDialect(statement, options);
            results.add(parseStatement(statement, dialect, options, context));
        }
        return results;
    }

    @Override
    public LineageResult parseStatement(String sql, SqlDialect dialect, ParseOptions options, ParseContext context) {
        DialectParser parser = parsers.get(dialect);
        if (parser == null) {
            return LineageResult.error(dialect, "DIALECT_NOT_REGISTERED", "No parser registered for dialect: " + dialect);
        }
        return parser.parse(sql, options, context);
    }

    private SqlDialect selectDialect(String statement, ParseOptions options) {
        if (!options.getDialectHints().isEmpty()) {
            return options.getDialectHints().get(0);
        }
        if (!options.isDialectDetectionEnabled()) {
            return SqlDialect.UNKNOWN;
        }
        List<SqlDialect> candidates = dialectDetector.detect(statement);
        return candidates.isEmpty() ? SqlDialect.UNKNOWN : candidates.get(0);
    }
}
