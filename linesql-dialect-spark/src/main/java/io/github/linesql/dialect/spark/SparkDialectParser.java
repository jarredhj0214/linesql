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
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        if (result.getColumnLineage().isEmpty()) {
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

    private static class SparkLineageVisitor extends SqlBaseParserBaseVisitor<Void> {
        private final LineageResult result;
        private final Set<TableRef> inputTables = new LinkedHashSet<>();
        private final Set<TableRef> outputTables = new LinkedHashSet<>();
        private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
        private final Set<String> cteNames = new LinkedHashSet<>();
        private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
        private final Map<String, String> derivedAliases = new LinkedHashMap<>();
        private final Set<String> derivedReferences = new LinkedHashSet<>();
        private final Map<String, List<SourceColumn>> generatedColumns = new LinkedHashMap<>();
        private final List<Projection> projections = new ArrayList<>();
        private final List<String> insertTargetColumns = new ArrayList<>();
        private int visibleRelationCount;
        private int selectExpressionCount;
        private int skippedProjectionCount;
        private boolean suppressColumnLineage;
        private ParseContext context;

        SparkLineageVisitor(LineageResult result) {
            this.result = result;
        }

        void setContext(ParseContext context) {
            this.context = context;
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
        public Void visitMultiInsertQuery(SqlBaseParser.MultiInsertQueryContext ctx) {
            result.setStatementType(StatementType.INSERT);
            suppressColumnLineage = true;
            return visitChildren(ctx);
        }

        @Override
        public Void visitInsertOverwriteTable(SqlBaseParser.InsertOverwriteTableContext ctx) {
            addOutput(ctx.identifierReference());
            addInsertTargetColumns(ctx.identifierList());
            return null;
        }

        @Override
        public Void visitInsertIntoTable(SqlBaseParser.InsertIntoTableContext ctx) {
            addOutput(ctx.identifierReference());
            addInsertTargetColumns(ctx.identifierList());
            return null;
        }

        @Override
        public Void visitInsertOverwriteHiveDir(SqlBaseParser.InsertOverwriteHiveDirContext ctx) {
            result.setStatementType(StatementType.INSERT);
            return visitChildren(ctx);
        }

        @Override
        public Void visitInsertOverwriteDir(SqlBaseParser.InsertOverwriteDirContext ctx) {
            result.setStatementType(StatementType.INSERT);
            return visitChildren(ctx);
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
        public Void visitCreateTableLike(SqlBaseParser.CreateTableLikeContext ctx) {
            result.setStatementType(StatementType.CREATE_TABLE_LIKE);
            addOutput(ctx.target);
            addInput(ctx.source);
            return null;
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
            visitChildren(ctx);
            if (ctx.TEMPORARY() != null) {
                registerTemporaryRelation(ctx.identifierReference());
            }
            return null;
        }

        @Override
        public Void visitDropView(SqlBaseParser.DropViewContext ctx) {
            result.setStatementType(StatementType.DROP_VIEW);
            unregisterTemporaryRelation(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitNamedQuery(SqlBaseParser.NamedQueryContext ctx) {
            String cteName = cleanIdentifier(ctx.name.getText()).toLowerCase(java.util.Locale.ROOT);
            cteNames.add(cteName);

            registerDerivedRelation(cteName, ctx.query(), identifierNames(ctx.columnAliases));
            return null;
        }

        @Override
        public Void visitSetOperation(SqlBaseParser.SetOperationContext ctx) {
            LineageResult left = lineageForQueryTerm(ctx.left);
            LineageResult right = lineageForQueryTerm(ctx.right);
            for (TableRef table : left.getInputTables()) {
                addInputTable(table, false);
            }
            for (TableRef table : right.getInputTables()) {
                addInputTable(table, false);
            }
            result.setColumnLineage(mergeSetColumnLineage(left, right));
            return null;
        }

        @Override
        public Void visitAliasedQuery(SqlBaseParser.AliasedQueryContext ctx) {
            String alias = tableAlias(ctx.tableAlias());
            String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
            registerDerivedRelation(relationName.toLowerCase(java.util.Locale.ROOT), ctx.query());
            addDerivedReference(relationName, ctx.tableAlias());
            return null;
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
        public Void visitLoadData(SqlBaseParser.LoadDataContext ctx) {
            result.setStatementType(StatementType.LOAD_DATA);
            addOutput(ctx.identifierReference());
            return null;
        }

        @Override
        public Void visitTableName(SqlBaseParser.TableNameContext ctx) {
            SqlBaseParser.IdentifierReferenceContext identifier =
                    ctx.temporalTableIdentifierReference().identifierReference();
            TableRef table = tableRef(identifier.getText());
            if (isCteReference(table)) {
                addDerivedReference(table.getName(), ctx.tableAlias());
                return null;
            }
            String temporaryRelationName = temporaryRelationName(table);
            if (temporaryRelationName != null) {
                addTemporaryRelationReference(temporaryRelationName, ctx.tableAlias());
                return null;
            }
            addInputTable(table, ctx.tableAlias(), true);
            return null;
        }

        @Override
        public Void visitChangelogTableName(SqlBaseParser.ChangelogTableNameContext ctx) {
            addInput(ctx.identifierReference(), null);
            return null;
        }

        @Override
        public Void visitSelectClause(SqlBaseParser.SelectClauseContext ctx) {
            for (SqlBaseParser.NamedExpressionContext namedExpression : ctx.namedExpressionSeq().namedExpression()) {
                selectExpressionCount++;
                Projection projection = projection(namedExpression);
                if (projection != null) {
                    projections.add(projection);
                } else {
                    skippedProjectionCount++;
                }
            }
            return visitChildren(ctx);
        }

        @Override
        public Void visitLateralView(SqlBaseParser.LateralViewContext ctx) {
            List<SourceColumn> sources = new ArrayList<>();
            for (SqlBaseParser.ExpressionContext expression : ctx.expression()) {
                sources.addAll(sourceColumns(expression));
            }
            if (!sources.isEmpty()) {
                for (SqlBaseParser.IdentifierContext column : ctx.colName) {
                    generatedColumns.put(cleanIdentifier(column.getText()), sources);
                }
            }
            refreshColumnLineage();
            return visitChildren(ctx);
        }

        void addColumnLineageDiagnostics() {
            refreshColumnLineage();
            if (selectExpressionCount > 0
                    && !result.getColumnLineage().isEmpty()
                    && (skippedProjectionCount > 0 || projections.size() > result.getColumnLineage().size())) {
                result.getDiagnostics().add(Diagnostic.warning(
                        "COLUMN_LINEAGE_PARTIAL",
                        "Spark column lineage was partially extracted; some projections are not supported yet."));
            }
        }

        private void addInput(SqlBaseParser.IdentifierReferenceContext ctx) {
            addInput(ctx, null);
        }

        private void addInput(SqlBaseParser.IdentifierReferenceContext ctx, SqlBaseParser.TableAliasContext aliasContext) {
            TableRef table = tableRef(ctx.getText());
            if (table.getCatalog() == null
                    && table.getSchema() == null
                    && cteNames.contains(table.getName().toLowerCase(java.util.Locale.ROOT))) {
                return;
            }
            addInputTable(table, aliasContext, true);
        }

        private void addInputTable(TableRef table, boolean visibleRelation) {
            addInputTable(table, null, visibleRelation);
        }

        private void addInputTable(TableRef table, SqlBaseParser.TableAliasContext aliasContext, boolean visibleRelation) {
            if (visibleRelation) {
                visibleRelationCount++;
            }
            inputTables.add(table);
            tableAliases.put(table.getName().toLowerCase(java.util.Locale.ROOT), table);
            String alias = tableAlias(aliasContext);
            if (alias != null) {
                tableAliases.put(alias.toLowerCase(java.util.Locale.ROOT), table);
            }
            result.setInputTables(new ArrayList<>(inputTables));
            refreshColumnLineage();
        }

        private void addDerivedReference(String rawName, SqlBaseParser.TableAliasContext aliasContext) {
            visibleRelationCount++;
            String derivedName = rawName.toLowerCase(java.util.Locale.ROOT);
            derivedReferences.add(derivedName);
            derivedAliases.put(derivedName, derivedName);
            String alias = tableAlias(aliasContext);
            if (alias != null) {
                derivedAliases.put(alias.toLowerCase(java.util.Locale.ROOT), derivedName);
            }
            addDerivedInputTables(derivedName);
            refreshColumnLineage();
        }

        private void addDerivedInputTables(String derivedName) {
            Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
            if (columns == null) {
                return;
            }
            for (List<ColumnRef> refs : columns.values()) {
                for (ColumnRef ref : refs) {
                    if (ref.getTable() != null) {
                        addInputTable(ref.getTable(), false);
                    }
                }
            }
        }

        private void registerDerivedRelation(String relationName, SqlBaseParser.QueryContext query) {
            registerDerivedRelation(relationName, query, new ArrayList<String>());
        }

        private void registerDerivedRelation(String relationName,
                                             SqlBaseParser.QueryContext query,
                                             List<String> columnAliases) {
            LineageResult relationResult = new LineageResult();
            SparkLineageVisitor relationVisitor = new SparkLineageVisitor(relationResult);
            relationVisitor.cteNames.addAll(cteNames);
            relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
            relationVisitor.derivedAliases.putAll(derivedAliases);
            relationVisitor.derivedReferences.addAll(derivedReferences);
            relationVisitor.setContext(context);
            relationVisitor.visit(query);
            relationVisitor.refreshColumnLineage();

            Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
            List<ColumnLineage> lineages = relationResult.getColumnLineage();
            for (int i = 0; i < lineages.size(); i++) {
                ColumnLineage lineage = lineages.get(i);
                String columnName = i < columnAliases.size() ? columnAliases.get(i) : lineage.getTarget().getName();
                columns.put(columnName, lineage.getSources());
            }
            derivedColumnLineage.put(relationName, columns);
            for (TableRef table : relationResult.getInputTables()) {
                addInputTable(table, false);
            }
        }

        private LineageResult lineageForQueryTerm(SqlBaseParser.QueryTermContext queryTerm) {
            LineageResult queryResult = new LineageResult();
            SparkLineageVisitor queryVisitor = new SparkLineageVisitor(queryResult);
            queryVisitor.cteNames.addAll(cteNames);
            queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
            queryVisitor.derivedAliases.putAll(derivedAliases);
            queryVisitor.derivedReferences.addAll(derivedReferences);
            queryVisitor.setContext(context);
            queryVisitor.visit(queryTerm);
            queryVisitor.refreshColumnLineage();
            return queryResult;
        }

        private static List<ColumnLineage> mergeSetColumnLineage(LineageResult left, LineageResult right) {
            int size = Math.min(left.getColumnLineage().size(), right.getColumnLineage().size());
            List<ColumnLineage> merged = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ColumnLineage leftColumn = left.getColumnLineage().get(i);
                ColumnLineage rightColumn = right.getColumnLineage().get(i);
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(leftColumn.getTarget());
                lineage.setSources(mergeColumnRefs(leftColumn.getSources(), rightColumn.getSources()));
                lineage.setExpression(leftColumn.getExpression());
                merged.add(lineage);
            }
            return merged;
        }

        private static List<ColumnRef> mergeColumnRefs(List<ColumnRef> left, List<ColumnRef> right) {
            Map<String, ColumnRef> refs = new LinkedHashMap<>();
            for (ColumnRef columnRef : left) {
                refs.put(columnKey(columnRef), columnRef);
            }
            for (ColumnRef columnRef : right) {
                refs.put(columnKey(columnRef), columnRef);
            }
            return new ArrayList<>(refs.values());
        }

        private static String columnKey(ColumnRef columnRef) {
            TableRef table = columnRef.getTable();
            String tableKey = table == null ? "" : relationKey(table);
            return tableKey + "." + columnRef.getName();
        }


        private void registerTemporaryRelation(SqlBaseParser.IdentifierReferenceContext identifier) {
            if (context == null) {
                return;
            }
            refreshColumnLineage();
            context.getTemporaryRelations().put(relationKey(tableRef(identifier.getText())), copyResult(result));
        }

        private void unregisterTemporaryRelation(SqlBaseParser.IdentifierReferenceContext identifier) {
            if (context == null) {
                return;
            }
            context.getTemporaryRelations().remove(relationKey(tableRef(identifier.getText())));
        }

        private void addTemporaryRelationReference(String relationName, SqlBaseParser.TableAliasContext aliasContext) {
            LineageResult relation = context.getTemporaryRelations().get(relationName);
            if (relation == null) {
                return;
            }
            visibleRelationCount++;
            derivedReferences.add(relationName);
            derivedAliases.put(relationName, relationName);
            String alias = tableAlias(aliasContext);
            if (alias != null) {
                derivedAliases.put(alias.toLowerCase(java.util.Locale.ROOT), relationName);
            }
            Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
            for (ColumnLineage lineage : relation.getColumnLineage()) {
                columns.put(lineage.getTarget().getName(), lineage.getSources());
            }
            derivedColumnLineage.put(relationName, columns);
            for (TableRef table : relation.getInputTables()) {
                addInputTable(table, false);
            }
            refreshColumnLineage();
        }

        private String temporaryRelationName(TableRef table) {
            if (context == null) {
                return null;
            }
            String key = relationKey(table);
            if (context.getTemporaryRelations().containsKey(key)) {
                return key;
            }
            return null;
        }

        private static LineageResult copyResult(LineageResult source) {
            LineageResult copy = new LineageResult();
            copy.setDialect(source.getDialect());
            copy.setDialectConfidence(source.getDialectConfidence());
            copy.setStatementType(source.getStatementType());
            copy.setInputTables(new ArrayList<>(source.getInputTables()));
            copy.setOutputTables(new ArrayList<>(source.getOutputTables()));
            copy.setColumnLineage(new ArrayList<>(source.getColumnLineage()));
            copy.setDiagnostics(new ArrayList<>(source.getDiagnostics()));
            return copy;
        }

        private void addOutput(SqlBaseParser.IdentifierReferenceContext ctx) {
            outputTables.add(tableRef(ctx.getText()));
            result.setOutputTables(new ArrayList<>(outputTables));
            refreshColumnLineage();
        }

        private void refreshColumnLineage() {
            if (suppressColumnLineage || inputTables.isEmpty() || projections.isEmpty()) {
                return;
            }
            TableRef targetTable = outputTables.size() == 1 ? outputTables.iterator().next() : null;
            List<ColumnLineage> columnLineage = new ArrayList<>();
            for (Projection projection : projections) {
                List<ColumnRef> sources = columnRefs(projection);
                if (sources == null) {
                    continue;
                }
                String targetColumn = targetColumn(projection, columnLineage.size());
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(new ColumnRef(targetTable, targetColumn));
                lineage.setSources(sources);
                lineage.setExpression(projection.expression);
                columnLineage.add(lineage);
            }
            result.setColumnLineage(columnLineage);
        }

        private String targetColumn(Projection projection, int index) {
            if (index < insertTargetColumns.size()) {
                return insertTargetColumns.get(index);
            }
            return projection.targetColumn;
        }

        private void addInsertTargetColumns(SqlBaseParser.IdentifierListContext ctx) {
            if (ctx == null) {
                return;
            }
            insertTargetColumns.addAll(identifierNames(ctx));
            refreshColumnLineage();
        }

        private static List<String> identifierNames(SqlBaseParser.IdentifierListContext ctx) {
            List<String> names = new ArrayList<>();
            if (ctx == null) {
                return names;
            }
            ctx.identifierSeq().ident.forEach(identifier -> names.add(cleanIdentifier(identifier.getText())));
            return names;
        }

        private List<ColumnRef> columnRefs(Projection projection) {
            return columnRefs(projection.sourceColumns, new LinkedHashSet<String>());
        }

        private List<ColumnRef> columnRefs(List<SourceColumn> sourceColumns, Set<String> resolvingGeneratedColumns) {
            TableRef defaultTable = visibleRelationCount <= 1 && inputTables.size() == 1
                    ? inputTables.iterator().next()
                    : null;
            List<ColumnRef> refs = new ArrayList<>();
            for (SourceColumn rawSourceColumn : sourceColumns) {
                SourceColumn sourceColumn = scopedSourceColumn(rawSourceColumn);
                List<ColumnRef> derivedRefs = derivedColumnRefs(sourceColumn);
                if (derivedRefs != null) {
                    refs.addAll(derivedRefs);
                    continue;
                }
                List<SourceColumn> generatedSources = sourceColumn.qualifier == null
                        ? generatedColumns.get(sourceColumn.name)
                        : null;
                if (generatedSources != null && resolvingGeneratedColumns.add(sourceColumn.name)) {
                    List<ColumnRef> generatedRefs = columnRefs(generatedSources, resolvingGeneratedColumns);
                    if (generatedRefs == null) {
                        return null;
                    }
                    refs.addAll(generatedRefs);
                    continue;
                }
                TableRef table = defaultTable;
                if (sourceColumn.qualifier != null) {
                    table = tableAliases.get(sourceColumn.qualifier.toLowerCase(java.util.Locale.ROOT));
                }
                if (table == null) {
                    return null;
                }
                refs.add(new ColumnRef(table, sourceColumn.name));
            }
            return refs;
        }

        private SourceColumn scopedSourceColumn(SourceColumn sourceColumn) {
            if (sourceColumn.qualifier != null || !sourceColumn.name.contains(".")) {
                return sourceColumn;
            }
            List<String> parts = splitIdentifier(sourceColumn.name);
            if (parts.size() < 2) {
                return sourceColumn;
            }
            String possibleQualifier = parts.get(0).toLowerCase(java.util.Locale.ROOT);
            if (tableAliases.containsKey(possibleQualifier) || derivedAliases.containsKey(possibleQualifier)) {
                return new SourceColumn(parts.get(0), String.join(".", parts.subList(1, parts.size())));
            }
            return sourceColumn;
        }

        private List<ColumnRef> derivedColumnRefs(SourceColumn sourceColumn) {
            String derivedName = null;
            if (sourceColumn.qualifier != null) {
                derivedName = derivedAliases.get(sourceColumn.qualifier.toLowerCase(java.util.Locale.ROOT));
            } else if (visibleRelationCount == 1 && derivedReferences.size() == 1) {
                derivedName = derivedReferences.iterator().next();
            }
            if (derivedName == null) {
                return null;
            }
            Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
            if (columns == null) {
                return null;
            }
            return columns.get(sourceColumn.name);
        }

        private boolean isCteReference(TableRef table) {
            return table.getCatalog() == null
                    && table.getSchema() == null
                    && cteNames.contains(table.getName().toLowerCase(java.util.Locale.ROOT));
        }

        private static Projection projection(SqlBaseParser.NamedExpressionContext ctx) {
            String expression = ctx.expression().getText();
            List<SourceColumn> sourceColumns = sourceColumns(ctx.expression());
            String directColumn = sourceColumns.size() == 1 && isDirectColumnExpression(expression, sourceColumns.get(0))
                    ? sourceColumns.get(0).name
                    : null;
            if (sourceColumns.isEmpty() && ctx.name == null) {
                return null;
            }
            if (sourceColumns.size() > 1 && ctx.name == null) {
                return null;
            }
            String targetColumn = ctx.name == null ? directColumn : cleanIdentifier(ctx.name.getText());
            if (targetColumn == null) {
                return null;
            }
            return new Projection(sourceColumns, targetColumn, expression);
        }

        private static boolean isDirectColumnExpression(String expression, SourceColumn column) {
            return expression.equals(column.name) || expression.endsWith("." + column.name);
        }

        private static List<SourceColumn> sourceColumns(ParseTree tree) {
            Set<SourceColumn> columns = new LinkedHashSet<>();
            collectSourceColumns(tree, columns);
            return new ArrayList<>(columns);
        }

        private static void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
            if (tree instanceof SqlBaseParser.ColumnReferenceContext) {
                columns.add(new SourceColumn(null, cleanIdentifier(tree.getText())));
                return;
            }
            if (tree instanceof SqlBaseParser.DereferenceContext) {
                SqlBaseParser.DereferenceContext dereference = (SqlBaseParser.DereferenceContext) tree;
                columns.add(new SourceColumn(null, String.join(".", splitIdentifier(dereference.getText()))));
                return;
            }
            for (int i = 0; i < tree.getChildCount(); i++) {
                collectSourceColumns(tree.getChild(i), columns);
            }
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

        private static String relationKey(TableRef table) {
            List<String> parts = new ArrayList<>();
            if (table.getCatalog() != null) {
                parts.add(table.getCatalog());
            }
            if (table.getSchema() != null) {
                parts.add(table.getSchema());
            }
            parts.add(table.getName());
            return String.join(".", parts).toLowerCase(java.util.Locale.ROOT);
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

        private static String tableAlias(SqlBaseParser.TableAliasContext ctx) {
            if (ctx == null || ctx.strictIdentifier() == null) {
                return null;
            }
            return cleanIdentifier(ctx.strictIdentifier().getText());
        }

        private static String cleanIdentifier(String text) {
            String value = text.trim();
            if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
                return value.substring(1, value.length() - 1).replace("``", "`");
            }
            return value;
        }

        private static class Projection {
            private final List<SourceColumn> sourceColumns;
            private final String targetColumn;
            private final String expression;

            Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
                this.sourceColumns = sourceColumns;
                this.targetColumn = targetColumn;
                this.expression = expression;
            }
        }

        private static class SourceColumn {
            private final String qualifier;
            private final String name;

            SourceColumn(String qualifier, String name) {
                this.qualifier = qualifier;
                this.name = name;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof SourceColumn)) {
                    return false;
                }
                SourceColumn that = (SourceColumn) other;
                return java.util.Objects.equals(qualifier, that.qualifier)
                        && java.util.Objects.equals(name, that.name);
            }

            @Override
            public int hashCode() {
                return java.util.Objects.hash(qualifier, name);
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
