package io.github.linesql.dialect.starrocks;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.starrocks.antlr.StarRocksLineageLexer;
import io.github.linesql.dialect.starrocks.antlr.StarRocksParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class StarRocksDialectParser implements DialectParser {

    @Override
    public SqlDialect dialect() {
        return SqlDialect.STARROCKS;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.STARROCKS);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        StarRocksLineageLexer lexer = new StarRocksLineageLexer(CharStreams.fromString(sql));
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
            return message == null ? "StarRocks SQL parse failed." : message;
        }
    }
}
