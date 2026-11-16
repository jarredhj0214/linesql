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
import org.antlr.v4.runtime.Token;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SparkDialectParser implements DialectParser {
    private static final Pattern STRING_ESCAPE = Pattern.compile(
            "\\\\(?:u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8}|[01][0-7]{2}|[\\s\\S])");

    @Override
    public SqlDialect dialect() {
        return SqlDialect.SPARK;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return parse(sql, context, true);
    }

    private LineageResult parse(String sql, ParseContext context, boolean expandLiteralSql) {
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
            SparkPartialLineageExtractor.extract(normalizedSql, result);
            result.getDiagnostics().add(Diagnostic.error("SPARK_PARSE_ERROR", errorListener.message()));
            return result;
        }

        if (expandLiteralSql && sql.equals(normalizedSql)
                && statement.statement() instanceof SqlBaseParser.VisitExecuteImmediateContext) {
            SqlBaseParser.ExecuteImmediateContext execute =
                    ((SqlBaseParser.VisitExecuteImmediateContext) statement.statement()).executeImmediate();
            String literalSql = staticExecuteSql(execute);
            if (literalSql != null) {
                return parse(literalSql, context, false);
            }
        }

        SparkLineageVisitor visitor = new SparkLineageVisitor(result);
        visitor.setContext(context);
        visitor.visit(statement);
        visitor.finalizeResult();
        visitor.addColumnLineageDiagnostics();
        if (visitor.shouldWarnMissingColumnLineage() && result.getColumnLineage().isEmpty()) {
            result.getDiagnostics().add(Diagnostic.warning(
                    "COLUMN_LINEAGE_NOT_IMPLEMENTED",
                    "Spark column lineage was not produced for this projection-capable statement shape."));
        }
        return result;
    }

    private static String staticExecuteSql(SqlBaseParser.ExecuteImmediateContext execute) {
        Token token = execute.queryParam.getStart();
        if (execute.executeImmediateUsing() != null || token != execute.queryParam.getStop()
                || (token.getType() != SqlBaseLexer.STRING_LITERAL
                && token.getType() != SqlBaseLexer.DOUBLEQUOTED_STRING)) {
            return null;
        }
        String literal = token.getText();
        boolean raw = literal.charAt(0) == 'R' || literal.charAt(0) == 'r';
        String body = literal.substring(raw ? 2 : 1, literal.length() - 1);
        if (raw || body.indexOf('\\') < 0) {
            return body;
        }

        StringBuilder decoded = new StringBuilder(body.length());
        Matcher escapes = STRING_ESCAPE.matcher(body);
        int start = 0;
        while (escapes.find()) {
            decoded.append(body, start, escapes.start());
            String escape = escapes.group();
            char code = escape.charAt(1);
            if (escape.length() > 2) {
                long value = code == 'u' || code == 'U'
                        ? Long.parseLong(escape.substring(2), 16)
                        : Long.parseLong(escape.substring(1), 8);
                if (value > Character.MAX_CODE_POINT) {
                    return null;
                }
                decoded.appendCodePoint((int) value);
            } else {
                switch (code) {
                    case '0': decoded.append((char) 0); break;
                    case 'b': decoded.append('\b'); break;
                    case 'n': decoded.append('\n'); break;
                    case 'r': decoded.append('\r'); break;
                    case 't': decoded.append('\t'); break;
                    case 'Z': decoded.append((char) 26); break;
                    case '%':
                    case '_': decoded.append(escape); break;
                    default: decoded.append(code);
                }
            }
            start = escapes.end();
        }
        return decoded.append(body, start, body.length()).toString();
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
