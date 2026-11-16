package io.github.linesql.dialect.sqlserver;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.sqlserver.antlr.SqlServerLineageLexer;
import io.github.linesql.dialect.sqlserver.antlr.SqlServerParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

public class SqlServerDialectParser implements DialectParser {

    @Override
    public SqlDialect dialect() {
        return SqlDialect.SQLSERVER;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return parse(sql, options, context, true);
    }

    private LineageResult parse(String sql, ParseOptions options, ParseContext context, boolean expandLiteralSql) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.SQLSERVER);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        SqlServerLineageLexer lexer = new SqlServerLineageLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlServerParser parser = new SqlServerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        SqlServerParser.SingleStatementContext statement = parser.singleStatement();
        if (errorListener.hasErrors() || parser.getNumberOfSyntaxErrors() > 0) {
            result.getDiagnostics().add(Diagnostic.error("SQLSERVER_PARSE_ERROR", errorListener.message()));
            return result;
        }

        if (statement.statement() instanceof SqlServerParser.ExecuteStmtContext) {
            SqlServerParser.ExecuteStatementContext execute =
                    ((SqlServerParser.ExecuteStmtContext) statement.statement()).executeStatement();
            int sqlArgument = spExecuteSqlArgument(tokens, execute);
            if (sqlArgument >= 0) {
                int lastToken = execute.getStop().getTokenIndex();
                if (expandLiteralSql && sqlArgument <= lastToken
                        && tokens.get(sqlArgument).getType() == SqlServerLineageLexer.STRING_LITERAL
                        && tokens.get(sqlArgument).getText().charAt(0) == 'N'
                        && (sqlArgument == lastToken || tokens.get(sqlArgument + 1).getType() == SqlServerLineageLexer.COMMA)) {
                    String literal = tokens.get(sqlArgument).getText();
                    String innerSql = literal.substring(2, literal.length() - 1).replace("''", "'");
                    LineageResult expanded = parse(innerSql, options, context, false);
                    if (expanded.getStatementType() != StatementType.UNKNOWN
                            && expanded.getDiagnostics().stream().noneMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR)) {
                        return expanded;
                    }
                }
                result.getDiagnostics().add(Diagnostic.warning(
                        "DYNAMIC_SQL_NOT_EXPANDED",
                        "SQL Server sp_executesql lineage needs a direct Unicode literal containing one supported statement."));
            }
        }

        SqlServerLineageVisitor visitor = new SqlServerLineageVisitor(result);
        visitor.visit(statement);
        visitor.finalizeResult();
        return result;
    }

    private static int spExecuteSqlArgument(CommonTokenStream tokens, SqlServerParser.ExecuteStatementContext execute) {
        int index = execute.getStart().getTokenIndex() + 1;
        int end = execute.getStop().getTokenIndex();
        if (index + 1 <= end && tokens.get(index).getType() == SqlServerLineageLexer.VARIABLE_IDENTIFIER
                && tokens.get(index + 1).getType() == SqlServerLineageLexer.EQ) {
            index += 2;
        }
        if (index + 2 <= end && namedIdentifier(tokens.get(index), "sys")
                && tokens.get(index + 1).getType() == SqlServerLineageLexer.DOT) {
            index += 2;
        }
        if (index > end || !namedIdentifier(tokens.get(index), "sp_executesql")
                || (index + 1 <= end && tokens.get(index + 1).getType() == SqlServerLineageLexer.DOT)) {
            return -1;
        }
        index++;
        if (index + 1 <= end && "@stmt".equalsIgnoreCase(tokens.get(index).getText())
                && tokens.get(index + 1).getType() == SqlServerLineageLexer.EQ) {
            index += 2;
        }
        return index;
    }

    private static boolean namedIdentifier(Token token, String name) {
        String text = token.getText();
        return name.equalsIgnoreCase(text) || ("[" + name + "]").equalsIgnoreCase(text)
                || ("\"" + name + "\"").equalsIgnoreCase(text);
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
            return message == null ? "SQL Server SQL parse failed." : message;
        }
    }
}
