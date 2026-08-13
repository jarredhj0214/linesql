package io.github.linesql.dialect.spark;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.spark.antlr.SqlBaseLexer;
import io.github.linesql.dialect.spark.antlr.SqlBaseParser;
import io.github.linesql.dialect.spark.antlr.SqlBaseParserBaseVisitor;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        SqlBaseLexer lexer = new SqlBaseLexer(new UpperCaseCharStream(CharStreams.fromString(sql)));
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
        visitor.visit(statement);
        if (result.getColumnLineage().isEmpty()) {
            result.getDiagnostics().add(Diagnostic.warning(
                    "COLUMN_LINEAGE_NOT_IMPLEMENTED",
                    "Spark column lineage is not implemented in this stage."));
        }
        return result;
    }

    private static class SparkLineageVisitor extends SqlBaseParserBaseVisitor<Void> {
        private final LineageResult result;
        private final Set<TableRef> inputTables = new LinkedHashSet<>();
        private final Set<TableRef> outputTables = new LinkedHashSet<>();
        private final Set<String> cteNames = new LinkedHashSet<>();
        private final List<Projection> projections = new ArrayList<>();

        SparkLineageVisitor(LineageResult result) {
            this.result = result;
        }

        @Override
        public Void visitStatementDefault(SqlBaseParser.StatementDefaultContext ctx) {
            result.setStatementType(StatementType.SELECT);
            return visitChildren(ctx);
        }

        @Override
        public Void visitSingleInsertQuery(SqlBaseParser.SingleInsertQueryContext ctx) {
            result.setStatementType(StatementType.INSERT);
            return visitChildren(ctx);
        }

        @Override
        public Void visitInsertOverwriteTable(SqlBaseParser.InsertOverwriteTableContext ctx) {
            addOutput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitInsertIntoTable(SqlBaseParser.InsertIntoTableContext ctx) {
            addOutput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitInsertIntoReplaceBooleanCond(SqlBaseParser.InsertIntoReplaceBooleanCondContext ctx) {
            addOutput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitInsertIntoReplaceUsing(SqlBaseParser.InsertIntoReplaceUsingContext ctx) {
            addOutput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitCreateTable(SqlBaseParser.CreateTableContext ctx) {
            if (ctx.query() != null) {
                result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            } else {
                result.setStatementType(StatementType.UNKNOWN);
            }
            addOutput(ctx.createTableHeader().identifierReference());
            return visitChildren(ctx);
        }

        @Override
        public Void visitReplaceTable(SqlBaseParser.ReplaceTableContext ctx) {
            if (ctx.query() != null) {
                result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            } else {
                result.setStatementType(StatementType.UNKNOWN);
            }
            addOutput(ctx.replaceTableHeader().identifierReference());
            return visitChildren(ctx);
        }

        @Override
        public Void visitCreateView(SqlBaseParser.CreateViewContext ctx) {
            result.setStatementType(StatementType.CREATE_VIEW);
            addOutput(ctx.identifierReference());
            return visitChildren(ctx);
        }

        @Override
        public Void visitNamedQuery(SqlBaseParser.NamedQueryContext ctx) {
            cteNames.add(cleanIdentifier(ctx.name.getText()).toLowerCase(java.util.Locale.ROOT));
            return visitChildren(ctx);
        }

        @Override
        public Void visitMergeIntoTable(SqlBaseParser.MergeIntoTableContext ctx) {
            result.setStatementType(StatementType.MERGE);
            addOutput(ctx.target);
            if (ctx.source != null) {
                addInput(ctx.source);
            }
            return visitChildren(ctx);
        }

        @Override
        public Void visitDeleteFromTable(SqlBaseParser.DeleteFromTableContext ctx) {
            result.setStatementType(StatementType.DELETE);
            addOutput(ctx.identifierReference());
            return visitChildren(ctx);
        }

        @Override
        public Void visitUpdateTable(SqlBaseParser.UpdateTableContext ctx) {
            result.setStatementType(StatementType.UPDATE);
            addOutput(ctx.identifierReference());
            return visitChildren(ctx);
        }

        @Override
        public Void visitTableName(SqlBaseParser.TableNameContext ctx) {
            addInput(ctx.temporalTableIdentifierReference().identifierReference());
            return null;
        }

        @Override
        public Void visitChangelogTableName(SqlBaseParser.ChangelogTableNameContext ctx) {
            addInput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitSelectClause(SqlBaseParser.SelectClauseContext ctx) {
            for (SqlBaseParser.NamedExpressionContext namedExpression : ctx.namedExpressionSeq().namedExpression()) {
                Projection projection = projection(namedExpression);
                if (projection != null) {
                    projections.add(projection);
                }
            }
            return visitChildren(ctx);
        }

        private void addInput(SqlBaseParser.IdentifierReferenceContext ctx) {
            TableRef table = tableRef(ctx.getText());
            if (table.getCatalog() == null
                    && table.getSchema() == null
                    && cteNames.contains(table.getName().toLowerCase(java.util.Locale.ROOT))) {
                return;
            }
            inputTables.add(table);
            result.setInputTables(new ArrayList<>(inputTables));
            refreshColumnLineage();
        }

        private void addOutput(SqlBaseParser.IdentifierReferenceContext ctx) {
            outputTables.add(tableRef(ctx.getText()));
            result.setOutputTables(new ArrayList<>(outputTables));
            refreshColumnLineage();
        }

        private void refreshColumnLineage() {
            if (inputTables.size() != 1 || projections.isEmpty()) {
                return;
            }
            TableRef sourceTable = inputTables.iterator().next();
            TableRef targetTable = outputTables.size() == 1 ? outputTables.iterator().next() : null;
            List<ColumnLineage> columnLineage = new ArrayList<>();
            for (Projection projection : projections) {
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(new ColumnRef(targetTable, projection.targetColumn));
                lineage.setSources(Collections.singletonList(new ColumnRef(sourceTable, projection.sourceColumn)));
                lineage.setExpression(projection.expression);
                columnLineage.add(lineage);
            }
            result.setColumnLineage(columnLineage);
        }

        private static Projection projection(SqlBaseParser.NamedExpressionContext ctx) {
            String expression = ctx.expression().getText();
            String sourceColumn = directColumnName(ctx.expression());
            if (sourceColumn == null) {
                return null;
            }
            String targetColumn = ctx.name == null ? sourceColumn : cleanIdentifier(ctx.name.getText());
            return new Projection(sourceColumn, targetColumn, expression);
        }

        private static String directColumnName(SqlBaseParser.ExpressionContext ctx) {
            if (!(ctx.booleanExpression() instanceof SqlBaseParser.PredicatedContext)) {
                return null;
            }
            SqlBaseParser.PredicatedContext predicated = (SqlBaseParser.PredicatedContext) ctx.booleanExpression();
            if (predicated.predicate() != null
                    || !(predicated.valueExpression() instanceof SqlBaseParser.ValueExpressionDefaultContext)) {
                return null;
            }
            SqlBaseParser.ValueExpressionDefaultContext value =
                    (SqlBaseParser.ValueExpressionDefaultContext) predicated.valueExpression();
            if (value.primaryExpression() instanceof SqlBaseParser.ColumnReferenceContext) {
                return cleanIdentifier(value.primaryExpression().getText());
            }
            if (value.primaryExpression() instanceof SqlBaseParser.DereferenceContext) {
                SqlBaseParser.DereferenceContext dereference =
                        (SqlBaseParser.DereferenceContext) value.primaryExpression();
                return cleanIdentifier(dereference.fieldName.getText());
            }
            return null;
        }

        private static TableRef tableRef(String raw) {
            List<String> partsList = splitIdentifier(raw);
            String[] parts = partsList.toArray(new String[0]);
            if (parts.length >= 3) {
                return new TableRef(parts[parts.length - 3], parts[parts.length - 2], parts[parts.length - 1]);
            }
            if (parts.length == 2) {
                return new TableRef(null, parts[0], parts[1]);
            }
            return new TableRef(null, null, parts[0]);
        }

        private static List<String> splitIdentifier(String raw) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '`') {
                    quoted = !quoted;
                    current.append(c);
                } else if (c == '.' && !quoted) {
                    parts.add(cleanIdentifier(current.toString()));
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            if (current.length() > 0) {
                parts.add(cleanIdentifier(current.toString()));
            }
            return parts;
        }

        private static String cleanIdentifier(String text) {
            String value = text.trim();
            if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
                return value.substring(1, value.length() - 1).replace("``", "`");
            }
            return value;
        }

        private static class Projection {
            private final String sourceColumn;
            private final String targetColumn;
            private final String expression;

            Projection(String sourceColumn, String targetColumn, String expression) {
                this.sourceColumn = sourceColumn;
                this.targetColumn = targetColumn;
                this.expression = expression;
            }
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
            return message == null ? "Spark SQL parse failed." : message;
        }
    }
}
