package io.github.linesql.dialect.sqlserver;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.sqlserver.antlr.SqlServerParser;
import io.github.linesql.dialect.sqlserver.antlr.SqlServerParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class SqlServerLineageVisitor extends SqlServerParserBaseVisitor<Void> {
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

    SqlServerLineageVisitor(LineageResult result) {
        this.result = result;
    }

    @Override
    public Void visitStatementDefault(SqlServerParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStmt(SqlServerParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStatement(SqlServerParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (SqlServerParser.IdentifierContext id : ctx.columnList.identifier()) {
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
    public Void visitUpdateStmt(SqlServerParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(SqlServerParser.UpdateStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        outputTables.add(target);
        inputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), target);
        }

        // Visit FROM clause if present (SQL Server UPDATE ... FROM syntax)
        if (ctx.fromClause() != null) {
            visitRelationListForInputs(ctx.fromClause().relationList());
        }

        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectSubqueryInputs(ctx.assignmentList());

        // Check if assignments have subqueries; if so, suppress column lineage
        boolean hasSubquery = false;
        for (SqlServerParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            if (containsSubquery(assignment.expression())) {
                hasSubquery = true;
                break;
            }
        }

        if (!hasSubquery) {
            List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), target);
            result.setColumnLineage(assignments);
        }

        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteStmt(SqlServerParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteSimple(SqlServerParser.DeleteSimpleContext ctx) {
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
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteFromJoin(SqlServerParser.DeleteFromJoinContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        // The first multipartIdentifier after DELETE might be an alias
        String deleteTarget = identifierParts(ctx.multipartIdentifier()).get(0).toLowerCase(Locale.ROOT);
        // Visit the relation list to populate tableAliases
        visitRelationListForInputs(ctx.relationList());
        // Resolve: find the relation whose alias matches the delete target
        TableRef target = tableAliases.get(deleteTarget);
        if (target != null) {
            outputTables.add(target);
        }
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeStmt(SqlServerParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStatement(SqlServerParser.MergeStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier(0));
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String targetAlias = tableAlias(ctx.tableAlias(0));
        if (targetAlias != null) {
            tableAliases.put(targetAlias.toLowerCase(Locale.ROOT), target);
        }

        if (ctx.multipartIdentifier().size() > 1) {
            TableRef source = tableRef(ctx.multipartIdentifier(1));
            inputTables.add(source);
            tableAliases.put(source.getName().toLowerCase(Locale.ROOT), source);
            String sourceAlias = tableAlias(ctx.tableAlias(1));
            if (sourceAlias != null) {
                tableAliases.put(sourceAlias.toLowerCase(Locale.ROOT), source);
            }
        }
        if (ctx.query() != null) {
            String alias = tableAlias(ctx.tableAlias(1));
            String relationName = alias == null ? "$merge_source" : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
            addDerivedReference(relationName, ctx.tableAlias(1));
        }

        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        List<ColumnLineage> lineages = new ArrayList<>();
        for (SqlServerParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.mergeMatchedAction() != null) {
                SqlServerParser.MergeMatchedActionContext action = clause.mergeMatchedAction();
                lineages.addAll(readAssignments(action.assignmentList(), target));
                if (action.whereClause() != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(action.whereClause().expression()));
                }
            }
            if (clause.mergeNotMatchedAction() != null) {
                SqlServerParser.MergeNotMatchedActionContext action = clause.mergeNotMatchedAction();
                lineages.addAll(readMergeInsertValues(action, target));
                if (action.whereClause() != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(action.whereClause().expression()));
                }
            }
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void visitRelationListForInputs(SqlServerParser.RelationListContext ctx) {
        for (SqlServerParser.RelationContext relation : ctx.relation()) {
            visitRelationPrimaryForDml(relation.relationPrimary());
            for (SqlServerParser.JoinRelationContext join : relation.joinRelation()) {
                visitRelationPrimaryForDml(join.relationPrimary());
                if (join.joinCriteria() != null) {
                    collectJoinColumnUsages(join.joinCriteria());
                }
            }
        }
    }

    private void visitRelationPrimaryForDml(SqlServerParser.RelationPrimaryContext primary) {
        if (primary instanceof SqlServerParser.TableNameContext) {
            SqlServerParser.TableNameContext tableName = (SqlServerParser.TableNameContext) primary;
            TableRef table = tableRef(tableName.multipartIdentifier());
            if (isCteReference(table)) {
                addDerivedReference(table.getName(), tableName.tableAlias());
                return;
            }
            inputTables.add(table);
            tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
            String alias = tableAlias(tableName.tableAlias());
            if (alias != null) {
                tableAliases.put(alias.toLowerCase(Locale.ROOT), table);
            }
            return;
        }
        if (primary instanceof SqlServerParser.AliasedQueryContext) {
            SqlServerParser.AliasedQueryContext query = (SqlServerParser.AliasedQueryContext) primary;
            String alias = tableAlias(query.tableAlias());
            String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), query.query(), new ArrayList<>());
            addDerivedReference(relationName, query.tableAlias());
        }
    }

    @Override
    public Void visitCreateTableStmt(SqlServerParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(SqlServerParser.CreateTableStatementContext ctx) {
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
    public Void visitCreateViewStmt(SqlServerParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(SqlServerParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (SqlServerParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
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
    public Void visitDropTableStmt(SqlServerParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(SqlServerParser.DropTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(SqlServerParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(SqlServerParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(SqlServerParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(SqlServerParser.AlterTableRenameContext ctx) {
        List<SqlServerParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(SqlServerParser.AlterTableAddColumnContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(SqlServerParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitShowStmt(SqlServerParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(SqlServerParser.ShowStatementContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof SqlServerParser.MultipartIdentifierContext) {
                inputTables.add(tableRef((SqlServerParser.MultipartIdentifierContext) child));
                break;
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitDescribeStmt(SqlServerParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitCommentStmt(SqlServerParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(SqlServerParser.CommentStatementContext ctx) {
        SqlServerParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
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
    public Void visitCtes(SqlServerParser.CtesContext ctx) {
        for (SqlServerParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(SqlServerParser.SetOperationContext ctx) {
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
    public Void visitTableName(SqlServerParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        if (isCteReference(table)) {
            addDerivedReference(table.getName(), ctx.tableAlias());
            return null;
        }
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitAliasedQuery(SqlServerParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(SqlServerParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (SqlServerParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    @Override
    public Void visitSelectClause(SqlServerParser.SelectClauseContext ctx) {
        for (SqlServerParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof SqlServerParser.SelectExpressionContext) {
                Projection projection = projection((SqlServerParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWhereClause(SqlServerParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoinCriteria(SqlServerParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGroupByClause(SqlServerParser.GroupByClauseContext ctx) {
        for (SqlServerParser.ExpressionContext expression : ctx.expression()) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(expression));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(SqlServerParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitWindowSpec(SqlServerParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (SqlServerParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (SqlServerParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(SqlServerParser.QueryOrganizationContext ctx) {
        for (SqlServerParser.SortItemContext sortItem : ctx.sortItem()) {
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

    private void addInputTable(TableRef table, SqlServerParser.TableAliasContext aliasCtx, boolean visibleRelation) {
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

    private void addDerivedReference(String rawName, SqlServerParser.TableAliasContext aliasCtx) {
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

    private void registerDerivedRelation(String name, SqlServerParser.QueryContext query, List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        SqlServerLineageVisitor relationVisitor = new SqlServerLineageVisitor(relationResult);
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

    private LineageResult lineageForQueryTerm(SqlServerParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        SqlServerLineageVisitor queryVisitor = new SqlServerLineageVisitor(queryResult);
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

    private void collectJoinColumnUsages(SqlServerParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(SqlServerParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void addUsingColumnUsages(SqlServerParser.IdentifierListContext ctx, int relationStart) {
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

    private List<ColumnLineage> readAssignments(SqlServerParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (SqlServerParser.AssignmentContext assignment : ctx.assignment()) {
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

    private List<ColumnLineage> readMergeInsertValues(SqlServerParser.MergeNotMatchedActionContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx.identifierList() == null || ctx.expressionList() == null) {
            return lineages;
        }
        List<String> targetColumns = identifierNames(ctx.identifierList());
        List<SqlServerParser.ExpressionContext> expressions = ctx.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            SqlServerParser.ExpressionContext expression = expressions.get(i);
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
        if (tree instanceof SqlServerParser.ScalarSubqueryContext) {
            SqlServerParser.ScalarSubqueryContext subquery = (SqlServerParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof SqlServerParser.ExistsExprContext) {
            SqlServerParser.ExistsExprContext exists = (SqlServerParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof SqlServerParser.PredicateContext) {
            SqlServerParser.PredicateContext predicate = (SqlServerParser.PredicateContext) tree;
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
        if (tree instanceof SqlServerParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof SqlServerParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof SqlServerParser.PredicateContext) {
            SqlServerParser.PredicateContext predicate = (SqlServerParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(SqlServerParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        SqlServerLineageVisitor queryVisitor = new SqlServerLineageVisitor(queryResult);
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

    private void collectTopLevelQueryProjections(SqlServerParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        SqlServerParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        for (SqlServerParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof SqlServerParser.SelectExpressionContext) {
                Projection projection = projection((SqlServerParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
    }

    private static SqlServerParser.QuerySpecificationContext topLevelQuerySpecification(SqlServerParser.QueryContext query) {
        if (!(query.queryTerm() instanceof SqlServerParser.QueryTermDefaultContext)) {
            return null;
        }
        SqlServerParser.QueryPrimaryContext primary =
                ((SqlServerParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof SqlServerParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((SqlServerParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(SqlServerParser.SelectExpressionContext ctx) {
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
        return expression.equals(raw) || expression.endsWith("." + column.name);
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

    private void addScalarSubquerySourceColumns(SqlServerParser.QueryContext query, Set<SourceColumn> columns) {
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(SqlServerParser.QueryContext query) {
        SqlServerLineageVisitor queryVisitor = new SqlServerLineageVisitor(new LineageResult());
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        SqlServerParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (SqlServerParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof SqlServerParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((SqlServerParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof SqlServerParser.ColumnReferenceContext) {
            SqlServerParser.ColumnReferenceContext colRef = (SqlServerParser.ColumnReferenceContext) tree;
            columns.add(new SourceColumn(null, cleanIdentifier(colRef.identifier())));
            return;
        }
        if (tree instanceof SqlServerParser.DereferenceContext) {
            SqlServerParser.DereferenceContext deref = (SqlServerParser.DereferenceContext) tree;
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
        if (tree instanceof SqlServerParser.ScalarSubqueryContext) {
            SqlServerParser.ScalarSubqueryContext subquery = (SqlServerParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof SqlServerParser.ExistsExprContext) {
            SqlServerParser.ExistsExprContext exists = (SqlServerParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof SqlServerParser.PredicateContext) {
            SqlServerParser.PredicateContext predicate = (SqlServerParser.PredicateContext) tree;
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

    private List<String> collectDereferenceParts(SqlServerParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof SqlServerParser.DereferenceContext) {
            SqlServerParser.DereferenceContext deref = (SqlServerParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof SqlServerParser.ColumnReferenceContext) {
            SqlServerParser.ColumnReferenceContext colRef = (SqlServerParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private static TableRef tableRef(SqlServerParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(SqlServerParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (SqlServerParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String tableAlias(SqlServerParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> cteColumnAliases(SqlServerParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (SqlServerParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(SqlServerParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (SqlServerParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(SqlServerParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static String cleanIdentifier(String text) {
        String value = text.trim();
        if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1).replace("]]", "]");
        }
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1).replace("``", "`");
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
