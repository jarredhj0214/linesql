package io.github.linesql.dialect.postgresql;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlParser;
import io.github.linesql.dialect.postgresql.antlr.PostgreSqlParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class PostgreSqlLineageVisitor extends PostgreSqlParserBaseVisitor<Void> {
    private final LineageResult result;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final List<Projection> projections = new ArrayList<>();
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private TableRef currentDmlTarget;
    private int visibleRelationCount;
    private boolean suppressColumnLineage;

    PostgreSqlLineageVisitor(LineageResult result) {
        this.result = result;
    }

    @Override
    public Void visitStatementDefault(PostgreSqlParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStmt(PostgreSqlParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStatement(PostgreSqlParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (PostgreSqlParser.IdentifierContext id : ctx.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        if (ctx.query() != null) {
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            suppressColumnLineage = true;
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitUpdateStmt(PostgreSqlParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(PostgreSqlParser.UpdateStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        visitRelationForUpdate(ctx.relation());
        if (ctx.fromClause() != null) {
            visitRelationListForInputs(ctx.fromClause().relationList());
        }
        currentDmlTarget = firstOutputTable();
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectSubqueryInputs(ctx.assignmentList());
        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), firstOutputTable());
        result.setColumnLineage(assignments);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void visitRelationForUpdate(PostgreSqlParser.RelationContext ctx) {
        visitRelationPrimaryForDml(ctx.relationPrimary(), true);
        for (PostgreSqlParser.JoinRelationContext join : ctx.joinRelation()) {
            visitRelationPrimaryForDml(join.relationPrimary(), false);
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria());
            }
        }
    }

    @Override
    public Void visitDeleteStmt(PostgreSqlParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteFrom(PostgreSqlParser.DeleteFromContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), target);
        }
        if (ctx.relationList() != null) {
            visitRelationListForInputs(ctx.relationList());
        }
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteAlias(PostgreSqlParser.DeleteAliasContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        String deleteAlias = identifierParts(ctx.multipartIdentifier()).get(0).toLowerCase(Locale.ROOT);
        visitRelationListForInputs(ctx.relationList());
        TableRef target = tableAliases.get(deleteAlias);
        if (target != null) {
            outputTables.add(target);
            currentDmlTarget = target;
        }
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeStmt(PostgreSqlParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStatement(PostgreSqlParser.MergeStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier(0));
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String targetAlias = tableAlias(ctx.tableAlias(0));
        if (targetAlias != null) {
            tableAliases.put(targetAlias.toLowerCase(Locale.ROOT), target);
        }

        if (ctx.multipartIdentifier().size() > 1) {
            TableRef source = tableRef(ctx.multipartIdentifier(1));
            if (isCteReference(source)) {
                addDerivedReference(source.getName(), ctx.tableAlias(1));
            } else {
                inputTables.add(source);
                tableAliases.put(source.getName().toLowerCase(Locale.ROOT), source);
                String sourceAlias = tableAlias(ctx.tableAlias(1));
                if (sourceAlias != null) {
                    tableAliases.put(sourceAlias.toLowerCase(Locale.ROOT), source);
                }
            }
        } else if (ctx.query() != null) {
            String alias = tableAlias(ctx.tableAlias(1));
            String relationName = alias == null ? "$merge_source" : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
            addDerivedReference(relationName, ctx.tableAlias(1));
        }

        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        List<ColumnLineage> lineages = new ArrayList<>();
        for (PostgreSqlParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.mergeCondition() != null) {
                addColumnUsages(ColumnUsageType.MERGE_WHEN,
                        sourceColumns(clause.mergeCondition().expression()));
            }
            if (clause.mergeMatchedAction() != null
                    && clause.mergeMatchedAction().assignmentList() != null) {
                lineages.addAll(readAssignments(clause.mergeMatchedAction().assignmentList(), target));
            }
            if (clause.mergeNotMatchedAction() != null
                    && clause.mergeNotMatchedAction().expressionList() != null) {
                lineages.addAll(readMergeInsertValues(clause.mergeNotMatchedAction(), target));
            }
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void visitRelationListForInputs(PostgreSqlParser.RelationListContext ctx) {
        for (PostgreSqlParser.RelationContext relation : ctx.relation()) {
            visitRelationPrimaryForDml(relation.relationPrimary(), false);
            for (PostgreSqlParser.JoinRelationContext join : relation.joinRelation()) {
                visitRelationPrimaryForDml(join.relationPrimary(), false);
                if (join.joinCriteria() != null) {
                    collectJoinColumnUsages(join.joinCriteria());
                }
            }
        }
    }

    private void visitRelationPrimaryForDml(PostgreSqlParser.RelationPrimaryContext primary, boolean output) {
        if (primary instanceof PostgreSqlParser.TableNameContext) {
            PostgreSqlParser.TableNameContext tableName = (PostgreSqlParser.TableNameContext) primary;
            TableRef table = tableRef(tableName.multipartIdentifier());
            if (isCteReference(table)) {
                addDerivedReference(table.getName(), tableName.tableAlias());
                return;
            }
            inputTables.add(table);
            if (output) {
                outputTables.add(table);
            }
            tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
            String alias = tableAlias(tableName.tableAlias());
            if (alias != null) {
                tableAliases.put(alias.toLowerCase(Locale.ROOT), table);
            }
            return;
        }
        if (primary instanceof PostgreSqlParser.AliasedQueryContext) {
            PostgreSqlParser.AliasedQueryContext query = (PostgreSqlParser.AliasedQueryContext) primary;
            String alias = tableAlias(query.tableAlias());
            String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), query.query(), new ArrayList<>());
            addDerivedReference(relationName, query.tableAlias());
        }
    }

    @Override
    public Void visitCreateTableStmt(PostgreSqlParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(PostgreSqlParser.CreateTableStatementContext ctx) {
        if (ctx.source != null) {
            result.setStatementType(StatementType.CREATE_TABLE_LIKE);
            outputTables.add(tableRef(ctx.target));
            inputTables.add(tableRef(ctx.source));
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        TableRef target = tableRef(ctx.multipartIdentifier(0));
        outputTables.add(target);
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            result.setStatementType(StatementType.UNKNOWN);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateViewStmt(PostgreSqlParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(PostgreSqlParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (PostgreSqlParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropTableStmt(PostgreSqlParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(PostgreSqlParser.DropTableStatementContext ctx) {
        for (PostgreSqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            outputTables.add(tableRef(identifier));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(PostgreSqlParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(PostgreSqlParser.DropViewStatementContext ctx) {
        for (PostgreSqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            outputTables.add(tableRef(identifier));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(PostgreSqlParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(PostgreSqlParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(PostgreSqlParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(PostgreSqlParser.AlterTableRenameContext ctx) {
        List<PostgreSqlParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(PostgreSqlParser.AlterTableAddColumnContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(PostgreSqlParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitShowStmt(PostgreSqlParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(PostgreSqlParser.ShowStatementContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof PostgreSqlParser.MultipartIdentifierContext) {
                inputTables.add(tableRef((PostgreSqlParser.MultipartIdentifierContext) child));
                break;
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitDescribeStmt(PostgreSqlParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitCommentStmt(PostgreSqlParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(PostgreSqlParser.CommentStatementContext ctx) {
        PostgreSqlParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
        if (id == null) {
            return null;
        }
        List<String> parts = identifierParts(id);
        if (ctx.COLUMN() != null && parts.size() > 1) {
            outputTables.add(LineageModelUtils.tableRefFromParts(parts.subList(0, parts.size() - 1)));
        } else if (ctx.TABLE() != null) {
            outputTables.add(tableRef(id));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(PostgreSqlParser.CtesContext ctx) {
        for (PostgreSqlParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(PostgreSqlParser.SetOperationContext ctx) {
        LineageResult leftResult = lineageForQueryTerm(ctx.left);
        LineageResult rightResult = lineageForQueryTerm(ctx.right);
        for (TableRef table : leftResult.getInputTables()) {
            addInputTable(table, false);
        }
        for (TableRef table : rightResult.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, leftResult);
        LineageModelUtils.mergeColumnUsages(result, rightResult);
        result.setColumnLineage(LineageModelUtils.mergeSetColumnLineage(leftResult, rightResult));
        return null;
    }

    @Override
    public Void visitTableName(PostgreSqlParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        if (isCteReference(table)) {
            addDerivedReference(table.getName(), ctx.tableAlias());
            return null;
        }
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitAliasedQuery(PostgreSqlParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(PostgreSqlParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (PostgreSqlParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    @Override
    public Void visitSelectClause(PostgreSqlParser.SelectClauseContext ctx) {
        for (PostgreSqlParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof PostgreSqlParser.SelectExpressionContext) {
                Projection projection = projection((PostgreSqlParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWhereClause(PostgreSqlParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoinCriteria(PostgreSqlParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGroupByClause(PostgreSqlParser.GroupByClauseContext ctx) {
        for (PostgreSqlParser.ExpressionContext expression : ctx.expression()) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(expression));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(PostgreSqlParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitWindowSpec(PostgreSqlParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (PostgreSqlParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (PostgreSqlParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(PostgreSqlParser.QueryOrganizationContext ctx) {
        for (PostgreSqlParser.SortItemContext sortItem : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
        return visitChildren(ctx);
    }

    // ============ Internal helpers ============

    void finalizeResult() {
        refreshColumnLineage();
        flushPendingColumnUsages();
        if (result.getInputTables().isEmpty()) {
            result.setInputTables(new ArrayList<>(inputTables));
        }
        if (result.getOutputTables().isEmpty()) {
            result.setOutputTables(new ArrayList<>(outputTables));
        }
    }

    private TableRef firstOutputTable() {
        return outputTables.isEmpty() ? null : outputTables.iterator().next();
    }

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, PostgreSqlParser.TableAliasContext aliasCtx, boolean visibleRelation) {
        if (visibleRelation) {
            visibleRelationCount++;
            visibleRelations.add(VisibleRelation.table(table));
        }
        inputTables.add(table);
        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), table);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        refreshColumnLineage();
    }

    private void addDerivedReference(String rawName, PostgreSqlParser.TableAliasContext aliasCtx) {
        visibleRelationCount++;
        String derivedName = rawName.toLowerCase(Locale.ROOT);
        visibleRelations.add(VisibleRelation.derived(derivedName));
        derivedReferences.add(derivedName);
        derivedAliases.put(derivedName, derivedName);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), derivedName);
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

    private void registerDerivedRelation(String name, PostgreSqlParser.QueryContext query, List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        PostgreSqlLineageVisitor relationVisitor = new PostgreSqlLineageVisitor(relationResult);
        relationVisitor.cteNames.addAll(cteNames);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        relationVisitor.visit(query);
        relationVisitor.refreshColumnLineage();

        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        List<ColumnLineage> lineages = relationResult.getColumnLineage();
        for (int i = 0; i < lineages.size(); i++) {
            ColumnLineage lineage = lineages.get(i);
            String columnName = i < columnAliases.size() ? columnAliases.get(i) : lineage.getTarget().getName();
            columns.put(columnName, lineage.getSources());
        }
        derivedColumnLineage.put(name, columns);
        for (TableRef table : relationResult.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relationResult);
    }

    private LineageResult lineageForQueryTerm(PostgreSqlParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(queryTerm);
        queryVisitor.refreshColumnLineage();
        return queryResult;
    }

    private void addColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = columnUsageRefs(sourceColumns);
        if (refs != null) {
            LineageModelUtils.addColumnUsages(result, type, refs);
        }
    }

    private List<ColumnRef> columnUsageRefs(List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            List<ColumnRef> projectionRefs = projectionAliasColumnRefs(sourceColumn);
            if (projectionRefs != null) {
                refs.addAll(projectionRefs);
                continue;
            }
            List<SourceColumn> singleton = new ArrayList<>();
            singleton.add(sourceColumn);
            List<ColumnRef> resolved = columnRefs(singleton);
            if (resolved == null) {
                return null;
            }
            refs.addAll(resolved);
        }
        return refs;
    }

    private List<ColumnRef> projectionAliasColumnRefs(SourceColumn sourceColumn) {
        if (sourceColumn.resolvedRef != null || sourceColumn.qualifier != null) {
            return null;
        }
        for (Projection projection : projections) {
            if (projection.targetColumn.equalsIgnoreCase(sourceColumn.name)) {
                return columnRefs(projection.sourceColumns);
            }
        }
        return null;
    }

    private void collectJoinColumnUsages(PostgreSqlParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(PostgreSqlParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void addUsingColumnUsages(PostgreSqlParser.IdentifierListContext ctx, int relationStart) {
        List<VisibleRelation> relations = visibleRelationsSince(relationStart);
        if (ctx == null || relations.size() < 2) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            for (VisibleRelation relation : relations) {
                refs.addAll(usingColumnRefs(relation, columnName));
            }
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.JOIN_ON, refs);
    }

    private List<VisibleRelation> visibleRelationsSince(int relationStart) {
        List<VisibleRelation> relations = new ArrayList<>();
        for (int i = Math.max(0, relationStart); i < visibleRelations.size(); i++) {
            relations.add(visibleRelations.get(i));
        }
        return relations;
    }

    private List<ColumnRef> usingColumnRefs(VisibleRelation relation, String columnName) {
        if (relation.table != null) {
            List<ColumnRef> refs = new ArrayList<>();
            refs.add(new ColumnRef(relation.table, columnName));
            return refs;
        }
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(relation.derivedName);
        if (columns == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = columns.get(columnName);
        if (refs != null) {
            return refs;
        }
        List<ColumnRef> wildcard = columns.get("*");
        if (wildcard != null && wildcard.size() == 1 && wildcard.get(0).getTable() != null) {
            List<ColumnRef> fallback = new ArrayList<>();
            fallback.add(new ColumnRef(wildcard.get(0).getTable(), columnName));
            return fallback;
        }
        return new ArrayList<>();
    }

    private void refreshColumnLineage() {
        if (suppressColumnLineage || projections.isEmpty()) {
            return;
        }
        TableRef targetTable = outputTables.size() == 1 ? outputTables.iterator().next() : null;
        List<ColumnLineage> columnLineage = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = projections.get(i);
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null) {
                continue;
            }
            String targetColumn = targetColumn(projection, columnLineage.size());
            columnLineage.add(LineageModelUtils.columnLineage(targetTable, targetColumn, sources, projection.expression));
        }
        result.setColumnLineage(columnLineage);
    }

    private void flushPendingColumnUsages() {
        for (PendingColumnUsage usage : pendingColumnUsages) {
            addColumnUsages(usage.type, usage.sourceColumns);
        }
    }

    private void retargetColumnLineage(TableRef targetTable) {
        result.setColumnLineage(LineageModelUtils.retargetColumnLineage(
                result.getColumnLineage(),
                targetTable,
                insertTargetColumns));
    }

    private String targetColumn(Projection projection, int index) {
        if (index < insertTargetColumns.size()) {
            return insertTargetColumns.get(index);
        }
        return projection.targetColumn;
    }

    private List<ColumnRef> columnRefs(Projection projection) {
        return columnRefs(projection.sourceColumns);
    }

    private static String columnKey(ColumnRef column) {
        List<String> parts = new ArrayList<>();
        TableRef table = column.getTable();
        if (table != null) {
            if (table.getCatalog() != null) {
                parts.add(table.getCatalog());
            }
            if (table.getSchema() != null) {
                parts.add(table.getSchema());
            }
            parts.add(table.getName());
        }
        parts.add(column.getName());
        return String.join(".", parts).toLowerCase(Locale.ROOT);
    }

    private List<ColumnRef> columnRefs(List<SourceColumn> sourceColumns) {
        TableRef defaultTable = visibleRelationCount <= 1 && inputTables.size() == 1
                ? inputTables.iterator().next()
                : null;
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn rawSourceColumn : sourceColumns) {
            if (rawSourceColumn.resolvedRef != null) {
                refs.add(rawSourceColumn.resolvedRef);
                continue;
            }
            SourceColumn sourceColumn = scopedSourceColumn(rawSourceColumn);
            List<ColumnRef> derivedRefs = derivedColumnRefs(sourceColumn);
            if (derivedRefs != null) {
                refs.addAll(derivedRefs);
                continue;
            }
            TableRef table = defaultTable;
            if (sourceColumn.qualifier != null) {
                table = tableAliases.get(sourceColumn.qualifier.toLowerCase(Locale.ROOT));
            } else if (currentDmlTarget != null) {
                table = currentDmlTarget;
            }
            if (table == null) {
                return null;
            }
            refs.add(new ColumnRef(table, sourceColumn.name));
        }
        return refs;
    }

    private SourceColumn scopedSourceColumn(SourceColumn sourceColumn) {
        if (sourceColumn.resolvedRef != null) {
            return sourceColumn;
        }
        if (sourceColumn.qualifier != null || !sourceColumn.name.contains(".")) {
            return sourceColumn;
        }
        int dot = sourceColumn.name.indexOf('.');
        String possibleQualifier = sourceColumn.name.substring(0, dot).toLowerCase(Locale.ROOT);
        if (tableAliases.containsKey(possibleQualifier) || derivedAliases.containsKey(possibleQualifier)) {
            return new SourceColumn(sourceColumn.name.substring(0, dot), sourceColumn.name.substring(dot + 1));
        }
        return sourceColumn;
    }

    private List<ColumnRef> derivedColumnRefs(SourceColumn sourceColumn) {
        String derivedName = null;
        if (sourceColumn.qualifier != null) {
            derivedName = derivedAliases.get(sourceColumn.qualifier.toLowerCase(Locale.ROOT));
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
                && cteNames.contains(table.getName().toLowerCase(Locale.ROOT));
    }

    private List<ColumnLineage> readAssignments(PostgreSqlParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (PostgreSqlParser.AssignmentContext assignment : ctx.assignment()) {
            if (containsSubquery(assignment.expression())) {
                continue;
            }
            List<String> parts = identifierParts(assignment.multipartIdentifier());
            String columnName = parts.get(parts.size() - 1);
            TableRef table = defaultTarget;
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2).toLowerCase(Locale.ROOT);
                TableRef resolved = tableAliases.get(qualifier);
                if (resolved != null) {
                    table = resolved;
                }
            }
            List<SourceColumn> sourceColumns = sourceColumns(assignment.expression());
            List<ColumnRef> sources = resolveSources(sourceColumns);
            if (sources == null) {
                sources = new ArrayList<>();
            }
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(table, columnName));
            lineage.setSources(sources);
            lineages.add(lineage);
        }
        return lineages;
    }

    private Map<String, List<ColumnRef>> insertedSourcesByColumn() {
        Map<String, List<ColumnRef>> sourcesByColumn = new LinkedHashMap<>();
        for (ColumnLineage lineage : result.getColumnLineage()) {
            if (lineage.getTarget() != null) {
                sourcesByColumn.put(lineage.getTarget().getName().toLowerCase(Locale.ROOT), lineage.getSources());
            }
        }
        return sourcesByColumn;
    }

    private List<ColumnLineage> readDuplicateKeyAssignments(PostgreSqlParser.AssignmentListContext ctx,
                                                            TableRef defaultTarget,
                                                            Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (PostgreSqlParser.AssignmentContext assignment : ctx.assignment()) {
            if (containsSubquery(assignment.expression())) {
                continue;
            }
            List<String> parts = identifierParts(assignment.multipartIdentifier());
            String columnName = parts.get(parts.size() - 1);
            TableRef table = defaultTarget;
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2).toLowerCase(Locale.ROOT);
                TableRef resolved = tableAliases.get(qualifier);
                if (resolved != null) {
                    table = resolved;
                }
            }
            List<ColumnRef> sources = duplicateKeySources(assignment.expression(), insertedSourcesByColumn);
            if (sources == null) {
                sources = new ArrayList<>();
            }
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(table, columnName));
            lineage.setSources(sources);
            lineage.setExpression(assignment.expression().getText());
            lineages.add(lineage);
        }
        return lineages;
    }

    private List<ColumnRef> duplicateKeySources(PostgreSqlParser.ExpressionContext expression,
                                                Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        String valuesColumn = valuesFunctionColumn(expression);
        if (valuesColumn != null) {
            List<ColumnRef> sources = insertedSourcesByColumn.get(valuesColumn.toLowerCase(Locale.ROOT));
            return sources == null ? new ArrayList<ColumnRef>() : new ArrayList<>(sources);
        }
        return resolveSources(sourceColumns(expression));
    }

    private static String valuesFunctionColumn(PostgreSqlParser.ExpressionContext expression) {
        String text = expression.getText().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("values(") || !text.endsWith(")")) {
            return null;
        }
        String inner = text.substring("values(".length(), text.length() - 1).trim();
        if (inner.isEmpty() || inner.indexOf('(') >= 0 || inner.indexOf(',') >= 0) {
            return null;
        }
        int dot = inner.lastIndexOf('.');
        if (dot >= 0) {
            inner = inner.substring(dot + 1);
        }
        return cleanIdentifier(inner);
    }

    private List<ColumnLineage> readMergeInsertValues(PostgreSqlParser.MergeNotMatchedActionContext ctx,
                                                      TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx.identifierList() == null || ctx.expressionList() == null) {
            return lineages;
        }
        List<String> targetColumns = identifierNames(ctx.identifierList());
        List<PostgreSqlParser.ExpressionContext> expressions = ctx.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            PostgreSqlParser.ExpressionContext expression = expressions.get(i);
            if (containsSubquery(expression)) {
                continue;
            }
            List<ColumnRef> sources = resolveSources(sourceColumns(expression));
            if (sources == null) {
                sources = new ArrayList<>();
            }
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(target, targetColumns.get(i)));
            lineage.setSources(sources);
            lineage.setExpression(expression.getText());
            lineages.add(lineage);
        }
        return lineages;
    }

    private List<ColumnRef> resolveSources(List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sc : sourceColumns) {
            if (sc.resolvedRef != null) {
                refs.add(sc.resolvedRef);
                continue;
            }
            SourceColumn col = scopedSourceColumn(sc);
            List<ColumnRef> derivedRefs = derivedColumnRefs(col);
            if (derivedRefs != null) {
                refs.addAll(derivedRefs);
                continue;
            }
            TableRef table = null;
            if (col.qualifier != null) {
                table = tableAliases.get(col.qualifier.toLowerCase(Locale.ROOT));
            } else if (currentDmlTarget != null) {
                table = currentDmlTarget;
            } else if (inputTables.size() == 1) {
                table = inputTables.iterator().next();
            }
            if (table == null) {
                return null;
            }
            refs.add(new ColumnRef(table, col.name));
        }
        return refs;
    }

    private void collectSubqueryInputs(ParseTree tree) {
        if (tree instanceof PostgreSqlParser.ScalarSubqueryContext) {
            PostgreSqlParser.ScalarSubqueryContext subquery = (PostgreSqlParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof PostgreSqlParser.ExistsExprContext) {
            PostgreSqlParser.ExistsExprContext exists = (PostgreSqlParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof PostgreSqlParser.PredicateContext) {
            PostgreSqlParser.PredicateContext predicate = (PostgreSqlParser.PredicateContext) tree;
            if (predicate.query() != null) {
                LineageResult subResult = lineageForQuery(predicate.query());
                inputTables.addAll(subResult.getInputTables());
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSubqueryInputs(tree.getChild(i));
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree instanceof PostgreSqlParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof PostgreSqlParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof PostgreSqlParser.PredicateContext) {
            PostgreSqlParser.PredicateContext predicate = (PostgreSqlParser.PredicateContext) tree;
            if (predicate.query() != null) {
                return true;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsSubquery(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private LineageResult lineageForQuery(PostgreSqlParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        queryVisitor.collectTopLevelQueryProjections(query);
        queryVisitor.refreshColumnLineage();
        return queryResult;
    }

    private void collectTopLevelQueryProjections(PostgreSqlParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        PostgreSqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        for (PostgreSqlParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof PostgreSqlParser.SelectExpressionContext) {
                Projection projection = projection((PostgreSqlParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
    }

    private static PostgreSqlParser.QuerySpecificationContext topLevelQuerySpecification(PostgreSqlParser.QueryContext query) {
        if (!(query.queryTerm() instanceof PostgreSqlParser.QueryTermDefaultContext)) {
            return null;
        }
        PostgreSqlParser.QueryPrimaryContext primary =
                ((PostgreSqlParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof PostgreSqlParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((PostgreSqlParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(PostgreSqlParser.SelectExpressionContext ctx) {
        String expression = ctx.expression().getText();
        List<SourceColumn> sourceColumns = sourceColumns(ctx.expression());
        String directColumn = sourceColumns.size() == 1 && isDirectColumnExpression(expression, sourceColumns.get(0))
                ? unqualifiedName(sourceColumns.get(0).name)
                : null;
        if (sourceColumns.isEmpty() && ctx.alias == null) {
            return null;
        }
        if (sourceColumns.size() > 1 && ctx.alias == null) {
            return null;
        }
        String targetColumn = ctx.alias == null ? directColumn : cleanIdentifier(ctx.alias);
        if (targetColumn == null) {
            return null;
        }
        return new Projection(sourceColumns, targetColumn, expression);
    }

    private static boolean isDirectColumnExpression(String expression, SourceColumn column) {
        String raw = column.qualifier != null ? column.qualifier + "." + column.name : column.name;
        String cleaned = cleanIdentifier(expression);
        return expression.equals(raw)
                || cleaned.equals(raw)
                || expression.endsWith("." + column.name)
                || cleaned.endsWith("." + column.name);
    }

    private static String unqualifiedName(String raw) {
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    private List<SourceColumn> sourceColumns(ParseTree tree) {
        Set<SourceColumn> columns = new LinkedHashSet<>();
        collectSourceColumns(tree, columns);
        return new ArrayList<>(columns);
    }

    private void addScalarSubquerySourceColumns(PostgreSqlParser.QueryContext query, Set<SourceColumn> columns) {
        LineageResult subResult = lineageForQuery(query);
        int before = columns.size();
        for (ColumnLineage lineage : subResult.getColumnLineage()) {
            for (ColumnRef source : lineage.getSources()) {
                if (source.getTable() != null) {
                    columns.add(SourceColumn.resolved(source));
                }
            }
        }
        if (columns.size() > before) {
            return;
        }
        for (ColumnRef source : scalarSubqueryProjectionRefs(query)) {
            if (source.getTable() != null) {
                columns.add(SourceColumn.resolved(source));
            }
        }
    }

    private List<ColumnRef> scalarSubqueryProjectionRefs(PostgreSqlParser.QueryContext query) {
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(new LineageResult());
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        PostgreSqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (PostgreSqlParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof PostgreSqlParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((PostgreSqlParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof PostgreSqlParser.ColumnReferenceContext) {
            PostgreSqlParser.ColumnReferenceContext colRef = (PostgreSqlParser.ColumnReferenceContext) tree;
            columns.add(new SourceColumn(null, cleanIdentifier(colRef.identifier())));
            return;
        }
        if (tree instanceof PostgreSqlParser.DereferenceContext) {
            PostgreSqlParser.DereferenceContext deref = (PostgreSqlParser.DereferenceContext) tree;
            List<String> parts = collectDereferenceParts(deref);
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2);
                String name = parts.get(parts.size() - 1);
                columns.add(new SourceColumn(qualifier, name));
            } else if (parts.size() == 1) {
                columns.add(new SourceColumn(null, parts.get(0)));
            }
            return;
        }
        if (tree instanceof PostgreSqlParser.ScalarSubqueryContext) {
            PostgreSqlParser.ScalarSubqueryContext subquery = (PostgreSqlParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof PostgreSqlParser.ExistsExprContext) {
            PostgreSqlParser.ExistsExprContext exists = (PostgreSqlParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof PostgreSqlParser.PredicateContext) {
            PostgreSqlParser.PredicateContext predicate = (PostgreSqlParser.PredicateContext) tree;
            if (predicate.query() != null) {
                LineageResult subResult = lineageForQuery(predicate.query());
                for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                    if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                        columns.add(SourceColumn.resolved(usage.getColumn()));
                    }
                }
                for (int i = 0; i < tree.getChildCount(); i++) {
                    ParseTree child = tree.getChild(i);
                    if (child != predicate.query()) {
                        collectSourceColumns(child, columns);
                    }
                }
                for (ColumnRef source : scalarSubqueryProjectionRefs(predicate.query())) {
                    if (source.getTable() != null) {
                        columns.add(SourceColumn.resolved(source));
                    }
                }
                return;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSourceColumns(tree.getChild(i), columns);
        }
    }

    private List<String> collectDereferenceParts(PostgreSqlParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof PostgreSqlParser.DereferenceContext) {
            PostgreSqlParser.DereferenceContext deref = (PostgreSqlParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof PostgreSqlParser.ColumnReferenceContext) {
            PostgreSqlParser.ColumnReferenceContext colRef = (PostgreSqlParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private static TableRef tableRef(PostgreSqlParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(PostgreSqlParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (PostgreSqlParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String tableAlias(PostgreSqlParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> cteColumnAliases(PostgreSqlParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (PostgreSqlParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(PostgreSqlParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (PostgreSqlParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(PostgreSqlParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static String cleanIdentifier(String text) {
        String value = text.trim();
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1).replace("``", "`");
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    private static class Projection {
        final List<SourceColumn> sourceColumns;
        final String targetColumn;
        final String expression;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
        }
    }

    private static class PendingColumnUsage {
        final ColumnUsageType type;
        final List<SourceColumn> sourceColumns;

        PendingColumnUsage(ColumnUsageType type, List<SourceColumn> sourceColumns) {
            this.type = type;
            this.sourceColumns = sourceColumns;
        }
    }

    static class SourceColumn {
        final ColumnRef resolvedRef;
        final String qualifier;
        final String name;

        SourceColumn(String qualifier, String name) {
            this(null, qualifier, name);
        }

        private SourceColumn(ColumnRef resolvedRef, String qualifier, String name) {
            this.resolvedRef = resolvedRef;
            this.qualifier = qualifier;
            this.name = name;
        }

        static SourceColumn resolved(ColumnRef ref) {
            return new SourceColumn(ref, null, ref.getName());
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
            return Objects.equals(resolvedRef, that.resolvedRef)
                    && Objects.equals(qualifier, that.qualifier)
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolvedRef, qualifier, name);
        }
    }

    private static class VisibleRelation {
        final TableRef table;
        final String derivedName;

        private VisibleRelation(TableRef table, String derivedName) {
            this.table = table;
            this.derivedName = derivedName;
        }

        static VisibleRelation table(TableRef table) {
            return new VisibleRelation(table, null);
        }

        static VisibleRelation derived(String derivedName) {
            return new VisibleRelation(null, derivedName);
        }
    }
}
