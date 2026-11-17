package io.github.linesql.dialect.mysql;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.PreparedSql;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.core.util.MySqlPreparedSql;
import io.github.linesql.core.util.MySqlExecutableComments;
import io.github.linesql.dialect.mysql.antlr.MySqlLineageLexer;
import io.github.linesql.dialect.mysql.antlr.MySqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;


public class MySqlDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return parse(sql, context, true);
    }

    private LineageResult parse(String sql, ParseContext context, boolean expandPreparedSql) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.MYSQL);
        result.setDialectConfidence(1.0d);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        MySqlLineageLexer lexer = new MySqlLineageLexer(CharStreams.fromString(MySqlExecutableComments.unwrap(sql)));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        MySqlParser parser = new MySqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        MySqlParser.SingleStatementContext statement = parser.singleStatement();
        if (errorListener.hasErrors() || parser.getNumberOfSyntaxErrors() > 0) {
            result.getDiagnostics().add(Diagnostic.error("MYSQL_PARSE_ERROR", errorListener.message()));
            return result;
        }

        Map<String, String> variables = context == null
                ? Collections.emptyMap() : context.getSessionStringVariables(SqlDialect.MYSQL);
        if (context != null) {
            trackUserVariables(statement.statement(), variables);
        }
        MySqlLineageVisitor visitor = new MySqlLineageVisitor(result);
        visitor.setContext(context);
        visitor.visit(statement);
        visitor.finalizeResult();
        if (expandPreparedSql && (context != null || statement.statement() instanceof MySqlParser.PrepareStmtContext)) {
            applyPreparedStatementLineage(statement.statement(), context == null ? new ParseContext() : context,
                    variables, result);
        }
        return result;
    }

    private void applyPreparedStatementLineage(MySqlParser.StatementContext statement, ParseContext context,
                                               Map<String, String> variables, LineageResult result) {
        if (statement instanceof MySqlParser.PrepareStmtContext) {
            Map<String, PreparedSql> preparedStatements = context.getPreparedStatements(SqlDialect.MYSQL);
            MySqlParser.PrepareStatementContext prepare = ((MySqlParser.PrepareStmtContext) statement).prepareStatement();
            String key = preparedName(prepare.identifier());
            preparedStatements.remove(key);
            String text = preparedSqlText(prepare, variables);
            if (text != null) {
                PreparedSql prepared = new PreparedSql(text, context.getDefaultCatalog(), context.getDefaultSchema());
                LineageResult lineage = analyzePreparedSql(prepared, context);
                MySqlPreparedSql.recordLineage(result, lineage);
                if (lineage.getStatementType() != StatementType.UNKNOWN) {
                    preparedStatements.put(key, prepared);
                }
            }
        } else if (statement instanceof MySqlParser.ExecuteStmtContext) {
            MySqlParser.ExecuteStatementContext execute = ((MySqlParser.ExecuteStmtContext) statement).executeStatement();
            PreparedSql prepared = context.getPreparedStatements(SqlDialect.MYSQL).get(preparedName(execute.identifier()));
            if (prepared != null) {
                MySqlPreparedSql.recordLineage(result, analyzePreparedSql(prepared, context));
            }
        } else if (statement instanceof MySqlParser.DeallocatePrepareStmtContext) {
            MySqlParser.IdentifierContext name = ((MySqlParser.DeallocatePrepareStmtContext) statement)
                    .deallocatePrepareStatement().identifier();
            context.getPreparedStatements(SqlDialect.MYSQL).remove(preparedName(name));
        } else if (statement instanceof MySqlParser.CallStmtContext) {
            context.getPreparedStatements(SqlDialect.MYSQL).clear();
        }
    }

    private LineageResult analyzePreparedSql(PreparedSql prepared, ParseContext context) {
        ParseContext snapshot = MySqlPreparedSql.snapshot(context);
        snapshot.setDefaultCatalog(prepared.getCatalog());
        snapshot.setDefaultSchema(prepared.getSchema());
        return parse(prepared.getSql(), snapshot, false);
    }

    private static String preparedSqlText(MySqlParser.PrepareStatementContext statement, Map<String, String> variables) {
        if (statement.literalSql != null) {
            return MySqlPreparedSql.decodeLiteral(statement.literalSql.getText());
        }
        return statement.sqlVariable == null ? null : variables.get(variableKey(statement.sqlVariable));
    }

    private static String preparedName(MySqlParser.IdentifierContext identifier) {
        return MySqlLineageVisitor.cleanIdentifier(identifier).toLowerCase(Locale.ROOT);
    }

    private static void trackUserVariables(MySqlParser.StatementContext statement, Map<String, String> variables) {
        if (statement instanceof MySqlParser.SetStmtContext) {
            for (MySqlParser.SetElementContext element :
                    ((MySqlParser.SetStmtContext) statement).setStatement().setElement()) {
                MySqlParser.SetValueContext value = element.setValue();
                if (value == null) {
                    continue;
                }
                invalidateVariableWrites(value, variables);
                if (element.setVariable() == null || element.setVariable().USER_VARIABLE() == null) {
                    continue;
                }
                String key = variableKey(element.setVariable().USER_VARIABLE().getSymbol());
                String sql = knownVariableValue(value, variables);
                if (sql == null) {
                    variables.remove(key);
                } else {
                    variables.put(key, sql);
                }
            }
        } else if (statement instanceof MySqlParser.CallStmtContext
                || statement instanceof MySqlParser.DoStmtContext
                || statement instanceof MySqlParser.ExecuteStmtContext
                || statement instanceof MySqlParser.CursorStmtContext
                || statement instanceof MySqlParser.DiagnosticStmtContext) {
            variables.clear();
        } else {
            invalidateVariableWrites(statement, variables);
        }
    }

    private static String knownVariableValue(MySqlParser.SetValueContext value, Map<String, String> variables) {
        Token literal = value.getStart();
        if (literal != value.getStop()) {
            return null;
        }
        switch (literal.getType()) {
            case MySqlLineageLexer.STRING_LITERAL:
            case MySqlLineageLexer.DOUBLE_QUOTED_STRING:
            case MySqlLineageLexer.CHARSET_STRING_LITERAL:
            case MySqlLineageLexer.NATIONAL_STRING_LITERAL:
                return MySqlPreparedSql.decodeLiteral(literal.getText());
            case MySqlLineageLexer.USER_VARIABLE:
                return variables.get(variableKey(literal));
            default:
                return null;
        }
    }

    private static void invalidateVariableWrites(ParseTree tree, Map<String, String> variables) {
        if (variables.isEmpty()) {
            return;
        }
        if (tree instanceof MySqlParser.SelectIntoVariableContext) {
            MySqlParser.SelectIntoVariableContext target = (MySqlParser.SelectIntoVariableContext) tree;
            if (target.USER_VARIABLE() != null) {
                variables.remove(variableKey(target.USER_VARIABLE().getSymbol()));
            }
        } else if (tree instanceof MySqlParser.AssignmentExpressionContext) {
            MySqlParser.ValueExpressionContext target = ((MySqlParser.AssignmentExpressionContext) tree).left;
            if (target.getStart() == target.getStop() && target.getStart().getType() == MySqlLineageLexer.USER_VARIABLE) {
                variables.remove(variableKey(target.getStart()));
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            invalidateVariableWrites(tree.getChild(index), variables);
        }
    }

    private static String variableKey(Token variable) {
        return variable.getText().toLowerCase(Locale.ROOT);
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
            return message == null ? "MySQL SQL parse failed." : message;
        }
    }
}
