package io.github.linesql.dialect.postgresql;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.PreparedSql;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlLineageLexer;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;

import java.util.Locale;
import java.util.Map;

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
        CharStream source = CharStreams.fromString(sql);
        PostgreSqlLineageLexer lexer = new PostgreSqlLineageLexer(source);
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
        if (context != null) {
            applyPreparedStatementLineage(statement.statement(), source, options, context, result);
        }
        return result;
    }

    private void applyPreparedStatementLineage(PostgreSqlParser.StatementContext statement, CharStream source,
                                               ParseOptions options, ParseContext context, LineageResult result) {
        if (statement instanceof PostgreSqlParser.RoutineControlStmtContext) {
            PostgreSqlParser.RoutineControlStatementContext control =
                    ((PostgreSqlParser.RoutineControlStmtContext) statement).routineControlStatement();
            Map<String, PreparedSql> statements = context.getPreparedStatements(SqlDialect.POSTGRESQL);
            if (control.preparedStatement != null) {
                String name = preparedName(control.identifier());
                String sql = source.getText(Interval.of(control.preparedStatement.getStart().getStartIndex(),
                        control.preparedStatement.getStop().getStopIndex()));
                statements.putIfAbsent(name, new PreparedSql(sql, null, null));
            } else if (control.DEALLOCATE() != null) {
                if (control.ALL() != null) {
                    statements.clear();
                } else {
                    statements.remove(preparedName(control.identifier()));
                }
            } else if (control.executePreparedStatement() != null) {
                copyPreparedExecution(control.executePreparedStatement(), options, statements, result, false);
            }
        } else if (statement instanceof PostgreSqlParser.ExplainStmtContext) {
            PostgreSqlParser.ExplainedStatementContext explained = ((PostgreSqlParser.ExplainStmtContext) statement)
                    .explainStatement().explainedStatement();
            if (explained.executePreparedStatement() != null) {
                copyPreparedExecution(explained.executePreparedStatement(), options,
                        context.getPreparedStatements(SqlDialect.POSTGRESQL), result, true);
            }
        } else if (statement instanceof PostgreSqlParser.DiscardStmtContext
                && ((PostgreSqlParser.DiscardStmtContext) statement).discardStatement().ALL() != null) {
            context.getPreparedStatements(SqlDialect.POSTGRESQL).clear();
        }
    }

    private void copyPreparedExecution(PostgreSqlParser.ExecutePreparedStatementContext execute, ParseOptions options,
                                       Map<String, PreparedSql> statements, LineageResult result, boolean explain) {
        PreparedSql prepared = statements.get(preparedName(execute.identifier()));
        if (prepared == null) {
            return;
        }
        LineageResult lineage = parse(prepared.getSql(), options, null);
        LineageModelUtils.copySuccessfulLineage(result, lineage);
        if (explain && lineage.getStatementType() != StatementType.UNKNOWN) {
            result.setStatementType(lineage.getStatementType());
        }
    }

    private static String preparedName(PostgreSqlParser.IdentifierContext identifier) {
        String name = identifier.getText();
        if (name.startsWith("\"")) {
            return name.substring(1, name.length() - 1).replace("\"\"", "\"");
        }
        return name.toLowerCase(Locale.ROOT);
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
