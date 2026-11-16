package io.github.linesql.core.internal;

import io.github.linesql.core.facade.SqlLineageParser;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.DialectCandidate;
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
        DialectCandidate scriptCandidate = selectScriptDialect(script, options);
        for (String statement : splitScript(script, options, scriptCandidate)) {
            DialectCandidate statementCandidate = selectStatementDialect(statement, options);
            DialectCandidate candidate = statementCandidate;
            if (shouldUseScriptDialect(scriptCandidate, statementCandidate, statement)) {
                candidate = new DialectCandidate(
                        scriptCandidate.getDialect(),
                        scriptCandidate.getConfidence(),
                        scriptCandidate.getReason() + "; inherited from script context");
            }
            LineageResult result = parseStatement(statement, candidate.getDialect(), options, context);
            if (candidate != statementCandidate
                    && statementCandidate.getDialect() == SqlDialect.POSTGRESQL
                    && parsers.containsKey(SqlDialect.POSTGRESQL)
                    && result.getDiagnostics().stream().anyMatch(d -> "FLINK_PARSE_ERROR".equals(d.getCode()))) {
                LineageResult fallback = parseStatement(statement, SqlDialect.POSTGRESQL, options, context);
                if (fallback.getDiagnostics().stream().noneMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR)) {
                    result = fallback;
                    candidate = statementCandidate;
                }
            }
            applyDetection(result, candidate);
            results.add(result);
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

    private List<String> splitScript(String script, ParseOptions options, DialectCandidate scriptCandidate) {
        if (statementSplitter.getClass() != SimpleStatementSplitter.class) {
            return statementSplitter.split(script);
        }
        SimpleStatementSplitter splitter = (SimpleStatementSplitter) statementSplitter;
        if (!options.getDialectHints().isEmpty()) {
            SqlDialect dialect = options.getDialectHints().get(0);
            if (dialect == SqlDialect.OCEANBASE) {
                String mode = options.getDialectOptions() == null
                        ? null : options.getDialectOptions().get("oceanbase.compatibilityMode");
                String normalizedMode = mode == null ? "" : mode.trim();
                if ("mysql".equalsIgnoreCase(normalizedMode)) {
                    dialect = SqlDialect.MYSQL;
                } else if ("oracle".equalsIgnoreCase(normalizedMode)
                        || SimpleStatementSplitter.isOracleBlockScript(script)) {
                    dialect = SqlDialect.ORACLE;
                }
            }
            return splitter.split(script, dialect);
        }
        if (scriptCandidate != null && scriptCandidate.getDialect() == SqlDialect.SQLSERVER) {
            return splitter.splitDetectedSqlServerBatches(script);
        }
        if (scriptCandidate != null && scriptCandidate.getDialect() == SqlDialect.ORACLE
                && SimpleStatementSplitter.isOracleBlockScript(script)) {
            return splitter.split(script, SqlDialect.ORACLE);
        }
        return splitter.split(script);
    }

    private DialectCandidate selectStatementDialect(String statement, ParseOptions options) {
        if (!options.getDialectHints().isEmpty()) {
            return new DialectCandidate(options.getDialectHints().get(0), 1.0, "Dialect selected from parse options hint");
        }
        if (!options.isDialectDetectionEnabled()) {
            return new DialectCandidate(SqlDialect.UNKNOWN, 0.0, "Dialect detection is disabled");
        }
        List<DialectCandidate> candidates = dialectDetector.detectCandidates(statement);
        return candidates.isEmpty()
                ? new DialectCandidate(SqlDialect.UNKNOWN, 0.0, "No dialect candidate detected")
                : candidates.get(0);
    }

    private DialectCandidate selectScriptDialect(String script, ParseOptions options) {
        if (!options.getDialectHints().isEmpty() || !options.isDialectDetectionEnabled()) {
            return null;
        }
        List<DialectCandidate> candidates = dialectDetector.detectCandidates(script);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean shouldUseScriptDialect(DialectCandidate scriptCandidate, DialectCandidate statementCandidate,
                                           String statement) {
        if (scriptCandidate == null
                || scriptCandidate.getDialect() == statementCandidate.getDialect()
                || scriptCandidate.getConfidence() < 0.95
                || scriptCandidate.getConfidence() <= statementCandidate.getConfidence()
                || !parsers.containsKey(scriptCandidate.getDialect())) {
            return false;
        }
        return isContextOverridable(statementCandidate.getDialect())
                || (scriptCandidate.getDialect() == SqlDialect.FLINK
                    && statementCandidate.getDialect() == SqlDialect.POSTGRESQL
                    && SimpleDialectDetector.hasSharedConflictDoNothing(statement));
    }

    private static boolean isContextOverridable(SqlDialect dialect) {
        return dialect == SqlDialect.SPARK
                || dialect == SqlDialect.MYSQL
                || dialect == SqlDialect.UNKNOWN;
    }

    private void applyDetection(LineageResult result, DialectCandidate candidate) {
        result.setDialectConfidence(candidate.getConfidence());
        result.setDialectDetectionReason(candidate.getReason());
    }
}
