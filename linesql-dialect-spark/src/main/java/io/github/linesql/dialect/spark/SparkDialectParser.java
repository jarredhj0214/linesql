package io.github.linesql.dialect.spark;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.spark.antlr.SqlBaseLexer;
import io.github.linesql.dialect.spark.antlr.SqlBaseParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class SparkDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.SPARK;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.SPARK);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        String normalizedSql = normalizePlaceholders(sql);
        SqlBaseLexer lexer = new SqlBaseLexer(new UpperCaseCharStream(CharStreams.fromString(normalizedSql)));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        SqlBaseParser parser = new SqlBaseParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        SqlBaseParser.SingleStatementContext statement = parser.singleStatement();
        if (errorListener.hasErrors() || parser.getNumberOfSyntaxErrors() > 0) {
            result.getDiagnostics().add(Diagnostic.error("SPARK_PARSE_ERROR", errorListener.message()));
            return result;
        }

        SparkLineageVisitor visitor = new SparkLineageVisitor(result);
        visitor.setContext(context);
        visitor.visit(statement);
        visitor.addColumnLineageDiagnostics();
        if (visitor.shouldWarnMissingColumnLineage() && result.getColumnLineage().isEmpty()) {
            result.getDiagnostics().add(Diagnostic.warning(
                    "COLUMN_LINEAGE_NOT_IMPLEMENTED",
                    "Spark column lineage is not implemented in this stage."));
        }
        return result;
    }

    private static String normalizePlaceholders(String sql) {
        return sql
                .replaceAll("\\$\\{[^}]+}", "__linesql_placeholder")
                .replaceAll("\\{\\{[^}]+}}", "__linesql_placeholder");
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
            return message == null ? "Spark SQL parse failed." : message;
        }
    }
}
