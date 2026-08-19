package io.github.linesql.dialect.postgresql;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlLineageLexer;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class PostgreSqlDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.POSTGRESQL;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.POSTGRESQL);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        PostgreSqlLineageLexer lexer = new PostgreSqlLineageLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        PostgreSqlParser parser = new PostgreSqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        PostgreSqlParser.SingleStatementContext statement = parser.singleStatement();
        if (errorListener.hasErrors() || parser.getNumberOfSyntaxErrors() > 0) {
            result.getDiagnostics().add(Diagnostic.error("POSTGRESQL_PARSE_ERROR", errorListener.message()));
            return result;
        }

        PostgreSqlLineageVisitor visitor = new PostgreSqlLineageVisitor(result);
        visitor.visit(statement);
        visitor.finalizeResult();
        return result;
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
            return message == null ? "PostgreSQL SQL parse failed." : message;
        }
    }
}
