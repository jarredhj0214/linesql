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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SparkDialectParser implements DialectParser {
    private static final Pattern STRING_ESCAPE = Pattern.compile(
            "\\\\(?:u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8}|[01][0-7]{2}|[\\s\\S])");

    private static final String VARIABLE_NAME_PART = "(?:[a-zA-Z_][\\w$]*|`(?:``|[^`])+`)";
    private static final Pattern SESSION_VARIABLE_NAME = Pattern.compile(
            VARIABLE_NAME_PART + "(?:\\." + VARIABLE_NAME_PART + "){0,2}");

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

        boolean originalLiterals = sql.equals(normalizedSql);
        Map<String, String> variables = context == null ? null : context.getSessionStringVariables(SqlDialect.SPARK);
        trackSessionVariables(statement, variables, originalLiterals);
        if (context != null && statement.statement() instanceof SqlBaseParser.SetCatalogContext) {
            SqlBaseParser.SetCatalogContext command = (SqlBaseParser.SetCatalogContext) statement.statement();
            String catalog = originalLiterals ? staticCatalogName(command.expression(), variables) : null;
            if (catalog == null || !catalog.equals(context.getDefaultCatalog())) {
                context.setDefaultCatalog(catalog);
                context.setDefaultSchema(null);
            }
        }
        if (statement.statement() instanceof SqlBaseParser.VisitExecuteImmediateContext) {
            SqlBaseParser.ExecuteImmediateContext execute =
                    ((SqlBaseParser.VisitExecuteImmediateContext) statement.statement()).executeImmediate();
            String literalSql = expandLiteralSql && originalLiterals ? staticExecuteSql(execute, variables) : null;
            invalidateSessionVariables(execute.targetVariable, variables);
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

    private static void trackSessionVariables(SqlBaseParser.SingleStatementContext statement,
                                              Map<String, String> variables, boolean originalLiterals) {
        if (variables == null) {
            return;
        }
        if (statement.statement() instanceof SqlBaseParser.CreateVariableContext) {
            SqlBaseParser.CreateVariableContext declaration = (SqlBaseParser.CreateVariableContext) statement.statement();
            SqlBaseParser.VariableDefaultExpressionContext defaultValue = declaration.variableDefaultExpression();
            String literal = originalLiterals && defaultValue != null ? stringLiteral(defaultValue.expression()) : null;
            boolean stringType = declaration.dataType() == null
                    ? literal != null : "STRING".equalsIgnoreCase(declaration.dataType().getText());
            for (SqlBaseParser.IdentifierReferenceContext target : declaration.identifierReferences) {
                String name = sessionVariableName(target.getText());
                if (name != null) {
                    if (stringType) {
                        variables.put(name, literal);
                    } else {
                        variables.remove(name);
                    }
                }
            }
        } else if (statement.statement() instanceof SqlBaseParser.DropVariableContext) {
            SqlBaseParser.DropVariableContext drop = (SqlBaseParser.DropVariableContext) statement.statement();
            variables.remove(sessionVariableName(drop.identifierReference().getText()));
        } else if (statement.setResetStatement() instanceof SqlBaseParser.SetVariableContext) {
            SqlBaseParser.SetVariableContext set = (SqlBaseParser.SetVariableContext) statement.setResetStatement();
            if (set.assignmentList() == null) {
                invalidateSessionVariables(set.multipartIdentifierList(), variables);
            } else {
                for (SqlBaseParser.AssignmentContext assignment : set.assignmentList().assignment()) {
                    String name = sessionVariableName(assignment.key.getText());
                    if (variables.containsKey(name)) {
                        variables.put(name, originalLiterals ? stringLiteral(assignment.value) : null);
                    }
                }
            }
        }
    }

    private static void invalidateSessionVariables(SqlBaseParser.MultipartIdentifierListContext targets,
                                                   Map<String, String> variables) {
        if (targets != null && variables != null) {
            for (SqlBaseParser.MultipartIdentifierContext target : targets.multipartIdentifier()) {
                String name = sessionVariableName(target.getText());
                if (variables.containsKey(name)) {
                    variables.put(name, null);
                }
            }
        }
    }

    private static String sessionVariableName(String text) {
        if (!SESSION_VARIABLE_NAME.matcher(text).matches()) {
            return null;
        }
        List<String> parts = SparkLineageVisitor.splitIdentifier(text);
        int size = parts.size();
        if ((size > 1 && !"session".equalsIgnoreCase(parts.get(size - 2)))
                || (size == 3 && !"system".equalsIgnoreCase(parts.get(0)))) {
            return null;
        }
        return parts.get(size - 1).toLowerCase(Locale.ROOT);
    }

    private static String staticExecuteSql(SqlBaseParser.ExecuteImmediateContext execute,
                                           Map<String, String> variables) {
        if (execute.executeImmediateUsing() != null) {
            return null;
        }
        String literal = stringLiteral(execute.queryParam);
        return literal != null || variables == null
                ? literal : variables.get(sessionVariableName(execute.queryParam.getText()));
    }

    private static String staticCatalogName(SqlBaseParser.ExpressionContext expression,
                                            Map<String, String> variables) {
        String literal = stringLiteral(expression);
        if (literal != null) {
            return literal;
        }
        String name = sessionVariableName(expression.getText());
        if (name == null) {
            return null;
        }
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        List<String> parts = SparkLineageVisitor.splitIdentifier(expression.getText());
        return parts.size() == 1 ? parts.get(0) : null;
    }

    private static String stringLiteral(SqlBaseParser.ExpressionContext expression) {
        Token token = expression.getStart();
        if (token != expression.getStop() || (token.getType() != SqlBaseLexer.STRING_LITERAL
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
