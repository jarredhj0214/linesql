package io.github.linesql.dialect.starrocks;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.PreparedSql;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.core.util.MySqlPreparedSql;
import io.github.linesql.dialect.starrocks.antlr.StarRocksLineageLexer;
import io.github.linesql.dialect.starrocks.antlr.StarRocksParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;

import java.util.Map;

public class StarRocksDialectParser implements DialectParser {

    @Override
    public SqlDialect dialect() {
        return SqlDialect.STARROCKS;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return parse(sql, context, true);
    }

    private LineageResult parse(String sql, ParseContext context, boolean expandPreparedSql) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.STARROCKS);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        CharStream source = CharStreams.fromString(sql);
        StarRocksLineageLexer lexer = new StarRocksLineageLexer(source);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        StarRocksParser parser = new StarRocksParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        StarRocksParser.SingleStatementContext statement = parser.singleStatement();
        if (errorListener.hasErrors() || parser.getNumberOfSyntaxErrors() > 0) {
            result.getDiagnostics().add(Diagnostic.error("STARROCKS_PARSE_ERROR", errorListener.message()));
            return result;
        }

        StarRocksLineageVisitor visitor = new StarRocksLineageVisitor(result);
        visitor.setContext(context);
        visitor.visit(statement);
        visitor.finalizeResult();
        if (expandPreparedSql && statement.statement() instanceof StarRocksParser.PreparedStmtContext) {
            applyPreparedStatement(((StarRocksParser.PreparedStmtContext) statement.statement()).preparedStatement(),
                    source, context, result);
        }
        return result;
    }

    private void applyPreparedStatement(StarRocksParser.PreparedStatementContext statement, CharStream source,
                                        ParseContext context, LineageResult result) {
        String name = statement.identifier().getText();
        if (statement.FROM() != null) {
            registerPreparedQuery(statement, source, context, result, name);
        } else if (context != null) {
            Map<String, PreparedSql> bindings = context.getPreparedStatements(SqlDialect.STARROCKS);
            if (statement.EXECUTE() == null) {
                bindings.remove(name);
                return;
            }
            PreparedSql query = bindings.get(name);
            if (query != null) {
                ParseContext snapshot = MySqlPreparedSql.snapshot(context);
                snapshot.setDefaultCatalog(query.getCatalog());
                snapshot.setDefaultSchema(query.getSchema());
                MySqlPreparedSql.recordLineage(result, parse(query.getSql(), snapshot, false));
            }
        }
    }

    private void registerPreparedQuery(StarRocksParser.PreparedStatementContext statement, CharStream source,
                                       ParseContext context, LineageResult result, String name) {
        String sql;
        if (statement.query() != null) {
            if (context == null) {
                return;
            }
            StarRocksParser.QueryContext query = statement.query();
            sql = source.getText(Interval.of(query.getStart().getStartIndex(), query.getStop().getStopIndex()));
        } else {
            sql = MySqlPreparedSql.decodeLiteral(statement.string().getText());
            if (sql == null) {
                return;
            }
            LineageResult prepared = parse(sql, MySqlPreparedSql.snapshot(context), false);
            if (prepared.getStatementType() != StatementType.SELECT) {
                return;
            }
            MySqlPreparedSql.recordLineage(result, prepared);
        }
        if (context != null) {
            context.getPreparedStatements(SqlDialect.STARROCKS).put(name,
                    new PreparedSql(sql, context.getDefaultCatalog(), context.getDefaultSchema()));
        }
    }

    private static class CollectingErrorListener extends BaseErrorListener {
        private String message;

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            if (message == null) {
                message = "line " + line + ":" + charPositionInLine + " " + msg;
            }
        }

        boolean hasErrors() {
            return message != null;
        }

        String message() {
            return message == null ? "StarRocks SQL parse failed." : message;
        }
    }
}
