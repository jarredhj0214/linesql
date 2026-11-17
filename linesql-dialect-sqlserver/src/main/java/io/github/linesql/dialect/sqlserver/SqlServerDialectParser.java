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
        return parse(sql, context, true, -1);
    }

    private LineageResult parse(String sql, ParseContext context, boolean expandLiteralSql, int insertWidth) {
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

        SqlServerParser.StatementContext body = statement.statement();
        SqlServerLineageVisitor visitor = new SqlServerLineageVisitor(result, context);
        if (insertWidth >= 0) {
            if (body instanceof SqlServerParser.StatementDefaultContext) {
                visitor.visitExecutedSelect((SqlServerParser.StatementDefaultContext) body, insertWidth);
            }
            return result;
        }
        SqlServerParser.ExecuteStatementContext execute = null;
        int targetWidth = -1;
        if (body instanceof SqlServerParser.ExecuteStmtContext) {
            execute = ((SqlServerParser.ExecuteStmtContext) body).executeStatement();
        } else if (body instanceof SqlServerParser.InsertStmtContext) {
            SqlServerParser.InsertStatementContext insert =
                    ((SqlServerParser.InsertStmtContext) body).insertStatement();
            execute = insert.executeStatement();
            targetWidth = insert.columnList == null ? 0 : insert.columnList.identifier().size();
        }
        int sqlArgument = execute == null ? -1 : spExecuteSqlArgument(tokens, execute);
        if (sqlArgument >= 0) {
            String innerSql = expandLiteralSql ? unicodeSqlLiteral(tokens, execute, sqlArgument) : null;
            LineageResult expanded = innerSql == null ? null
                    : parse(innerSql, dynamicSqlContext(context), false, targetWidth);
            if (expanded != null && expanded.getStatementType() != StatementType.UNKNOWN
                    && expanded.getDiagnostics().stream().noneMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR)
                    && (targetWidth < 0 || expanded.getStatementType() == StatementType.SELECT)) {
                if (targetWidth < 0) {
                    return expanded;
                }
                visitor.setExecutedSelect(expanded);
            } else {
                result.getDiagnostics().add(Diagnostic.warning(
                        "DYNAMIC_SQL_NOT_EXPANDED",
                        targetWidth < 0
                                ? "SQL Server sp_executesql lineage needs a direct Unicode literal containing one supported statement."
                                : "SQL Server INSERT EXEC sp_executesql lineage needs a direct Unicode literal containing one SELECT."));
            }
        }
        visitor.visit(statement);
        visitor.finalizeResult();
        return result;
    }

    private static String unicodeSqlLiteral(CommonTokenStream tokens, SqlServerParser.ExecuteStatementContext execute,
                                            int argument) {
        int last = execute.getStop().getTokenIndex();
        if (argument > last || tokens.get(argument).getType() != SqlServerLineageLexer.STRING_LITERAL
                || (argument < last && tokens.get(argument + 1).getType() != SqlServerLineageLexer.COMMA)) {
            return null;
        }
        String literal = tokens.get(argument).getText();
        return literal.charAt(0) == 'N' ? literal.substring(2, literal.length() - 1).replace("''", "'") : null;
    }

    private static ParseContext dynamicSqlContext(ParseContext context) {
        if (context == null) {
            return null;
        }
        ParseContext innerContext = new ParseContext();
        innerContext.setDefaultCatalog(context.getDefaultCatalog());
        innerContext.setDefaultSchema(context.getDefaultSchema());
        return innerContext;
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
