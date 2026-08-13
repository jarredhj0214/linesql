package io.github.linesql.dialect.spark;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.spark.antlr.SparkLineSqlBaseVisitor;
import io.github.linesql.dialect.spark.antlr.SparkLineSqlLexer;
import io.github.linesql.dialect.spark.antlr.SparkLineSqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
        SparkLineSqlLexer lexer = new SparkLineSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        SparkLineSqlParser parser = new SparkLineSqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        SparkLineSqlParser.StatementContext statement = parser.statement();
        if (errorListener.hasErrors()) {
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

    private static class SparkLineageVisitor extends SparkLineSqlBaseVisitor<Void> {
        private final LineageResult result;
        private final Set<TableRef> inputTables = new LinkedHashSet<>();

        SparkLineageVisitor(LineageResult result) {
            this.result = result;
        }

        @Override
        public Void visitStatement(SparkLineSqlParser.StatementContext ctx) {
            if (ctx.insertStatement() != null) {
                result.setStatementType(StatementType.INSERT);
            } else if (ctx.ctasStatement() != null) {
                result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            } else if (ctx.createViewStatement() != null) {
                result.setStatementType(StatementType.CREATE_VIEW);
            } else {
                result.setStatementType(StatementType.SELECT);
            }
            return visitChildren(ctx);
        }

        @Override
        public Void visitInsertStatement(SparkLineSqlParser.InsertStatementContext ctx) {
            result.getOutputTables().add(tableRef(ctx.target));
            return visit(ctx.query());
        }

        @Override
        public Void visitCtasStatement(SparkLineSqlParser.CtasStatementContext ctx) {
            result.getOutputTables().add(tableRef(ctx.target));
            return visit(ctx.query());
        }

        @Override
        public Void visitCreateViewStatement(SparkLineSqlParser.CreateViewStatementContext ctx) {
            result.getOutputTables().add(tableRef(ctx.target));
            return visit(ctx.query());
        }

        @Override
        public Void visitRelation(SparkLineSqlParser.RelationContext ctx) {
            if (ctx.tableName() != null) {
                inputTables.add(tableRef(ctx.tableName()));
                result.setInputTables(inputTables.stream().collect(Collectors.toList()));
                return null;
            }
            return visitChildren(ctx);
        }

        private static TableRef tableRef(SparkLineSqlParser.TableNameContext ctx) {
            String[] parts = ctx.identifier().stream()
                    .map(SparkLineageVisitor::identifierText)
                    .toArray(String[]::new);
            if (parts.length >= 3) {
                return new TableRef(parts[parts.length - 3], parts[parts.length - 2], parts[parts.length - 1]);
            }
            if (parts.length == 2) {
                return new TableRef(null, parts[0], parts[1]);
            }
            return new TableRef(null, null, parts[0]);
        }

        private static String identifierText(SparkLineSqlParser.IdentifierContext ctx) {
            String text = ctx.getText();
            if (text.length() >= 2 && text.startsWith("`") && text.endsWith("`")) {
                return text.substring(1, text.length() - 1).replace("``", "`");
            }
            return text;
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
            return message;
        }
    }
}
