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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class PostgreSqlLineageVisitor extends PostgreSqlParserBaseVisitor<Void> {
    private static final String EXCLUDED_RELATION = "$on_conflict_excluded";
    private final LineageResult result;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, Set<SourceColumn>> namedWindowSources = new LinkedHashMap<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final Map<PostgreSqlParser.QueryContext, PostgreSqlLineageVisitor> analyzedSubqueries = new IdentityHashMap<>();
    private final Set<String> knownColumnRelations = new LinkedHashSet<>();
    private final List<Projection> projections = new ArrayList<>();
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private final Map<Integer, PostgreSqlParser.ExpressionContext> unnamedSelectExpressions = new LinkedHashMap<>();
    private final Map<PostgreSqlParser.RelationContext, Map<String, List<ColumnRef>>> relationJoinKeys =
            new IdentityHashMap<>();
    private Map<String, List<ColumnRef>> unqualifiedJoinKeys = Collections.emptyMap();
    private TableRef currentDmlTarget;
    private boolean returningSourcesForCte;
    private int visibleRelationCount;
    private int setColumnCount = -1;
    private int selectedColumnCount = -1;
    private int firstUnknownStarOrdinal = Integer.MAX_VALUE;
    private boolean suppressColumnLineage;
    private PostgreSqlParser.SelectClauseContext positionalSelect;
    private final PostgreSqlParser.QueryContext preparedTableQuery;

    PostgreSqlLineageVisitor(LineageResult result) {
        this(result, null);
    }

    PostgreSqlLineageVisitor(LineageResult result, PostgreSqlParser.QueryContext preparedTableQuery) {
        this.result = result;
        this.preparedTableQuery = preparedTableQuery;
    }

    @Override
    public Void visitStatementDefault(PostgreSqlParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExplainStmt(PostgreSqlParser.ExplainStmtContext ctx) {
        return visit(ctx.explainStatement());
    }

    @Override
    public Void visitExplainStatement(PostgreSqlParser.ExplainStatementContext ctx) {
        PostgreSqlParser.ExplainedStatementContext explained = ctx.explainedStatement();
        if (explained.query() != null) {
            result.setStatementType(StatementType.SELECT);
            return visit(explained.query());
        }
        if (explained.insertStatement() != null) {
            result.setStatementType(StatementType.INSERT);
            return visit(explained.insertStatement());
        }
        if (explained.updateStatement() != null) {
            result.setStatementType(StatementType.UPDATE);
            return visit(explained.updateStatement());
        }
        if (explained.deleteStatement() != null) {
            result.setStatementType(StatementType.DELETE);
            return visit(explained.deleteStatement());
        }
        if (explained.mergeStatement() != null) {
            result.setStatementType(StatementType.MERGE);
            return visit(explained.mergeStatement());
        }
        if (explained.declareCursorStatement() != null) {
            result.setStatementType(StatementType.CONTROL);
            return visit(explained.declareCursorStatement());
        }
        if (explained.createTableAsStatement() != null) {
            return visit(explained.createTableAsStatement());
        }
        if (explained.createMaterializedViewStatement() != null) {
            return visit(explained.createMaterializedViewStatement());
        }
        if (explained.executePreparedStatement() != null) {
            result.setStatementType(StatementType.CONTROL);
        }
        return null;
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
        List<PostgreSqlParser.ValuesClauseContext> queryValues = valuesQueryRows(ctx.query());
        if (!queryValues.isEmpty()) {
            result.setColumnLineage(readInsertValues(queryValues, target));
        } else if (ctx.query() != null) {
            applyInsertQueryLineage(ctx.query(), target);
        } else if (!ctx.valuesClause().isEmpty()) {
            result.setColumnLineage(readInsertValues(ctx.valuesClause(), target));
        } else {
            suppressColumnLineage = true;
        }
        currentDmlTarget = target;
        String targetName = ctx.targetAlias == null ? target.getName() : cleanIdentifier(ctx.targetAlias.getText());
        tableAliases.put(targetName.toLowerCase(Locale.ROOT), target);
        if (ctx.targetAlias == null) {
            bindQualifiedTable(ctx.multipartIdentifier(), target);
        }
        if (ctx.onConflictClause() != null) {
            applyConflictLineage(ctx.onConflictClause(), target);
        }
        appendReturningLineage(ctx.returningClause(), target,
                ctx.onConflictClause() != null && ctx.onConflictClause().assignmentList() != null, true);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void applyConflictLineage(PostgreSqlParser.OnConflictClauseContext conflict, TableRef target) {
        PostgreSqlParser.ConflictTargetContext conflictTarget = conflict.conflictTarget();
        if (conflictTarget != null) {
            for (PostgreSqlParser.ConflictIndexElementContext element : conflictTarget.conflictIndexElement()) {
                if (element.identifier() != null) {
                    LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL,
                            Collections.singletonList(new ColumnRef(target, cleanIdentifier(element.identifier()))));
                } else {
                    addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(element.expression()));
                }
            }
        }
        if (conflict.indexPredicate != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(conflict.indexPredicate.expression()));
        }
        if (conflict.assignmentList() == null) {
            return;
        }
        Map<String, List<ColumnRef>> insertedSources = insertedSourcesByColumn();
        String previousAlias = derivedAliases.put("excluded", EXCLUDED_RELATION);
        Map<String, List<ColumnRef>> previousColumns = derivedColumnLineage.put(EXCLUDED_RELATION, insertedSources);
        try {
            collectSubqueryDependencies(conflict.assignmentList(), true);
            List<ColumnLineage> lineages = new ArrayList<>(result.getColumnLineage());
            lineages.addAll(readConflictAssignments(conflict.assignmentList(), target, insertedSources));
            result.setColumnLineage(lineages);
            suppressColumnLineage = true;
            if (conflict.actionPredicate != null) {
                collectSubqueryDependencies(conflict.actionPredicate.expression(), true);
                addColumnUsages(ColumnUsageType.WHERE, conflictSourceColumns(
                        conflict.actionPredicate.expression(), insertedSources));
            }
        } finally {
            if (previousColumns == null) {
                derivedColumnLineage.remove(EXCLUDED_RELATION);
            } else {
                derivedColumnLineage.put(EXCLUDED_RELATION, previousColumns);
            }
            if (previousAlias == null) {
                derivedAliases.remove("excluded");
            } else {
                derivedAliases.put("excluded", previousAlias);
            }
        }
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
            visit(ctx.fromClause());
        }
        currentDmlTarget = firstOutputTable();
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectSubqueryDependencies(ctx.assignmentList(), true);
        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), firstOutputTable(),
                !ctx.relation().joinRelation().isEmpty());
        result.setColumnLineage(assignments);
        appendReturningLineage(ctx.returningClause(), firstOutputTable(), true, true);
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
        TableRef target = tableRef(ctx.inheritanceRelation().multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), target);
        } else {
            bindQualifiedTable(ctx.inheritanceRelation().multipartIdentifier(), target);
        }
        if (ctx.relationList() != null) {
            visit(ctx.relationList());
        }
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        appendReturningLineage(ctx.returningClause(), target, true, false);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void appendReturningLineage(
            PostgreSqlParser.ReturningClauseContext returning,
            TableRef targetTable,
            boolean hasOldRow,
            boolean hasNewRow) {
        appendReturningLineage(returning, targetTable, hasOldRow, hasNewRow, Collections.emptyList());
    }

    private void appendReturningLineage(
            PostgreSqlParser.ReturningClauseContext returning,
            TableRef targetTable,
            boolean hasOldRow,
            boolean hasNewRow,
            List<ColumnRef> sourceWildcard) {
        if (returning == null || targetTable == null) {
            return;
        }
        Map<String, ReturningRow> rows = returningRows(returning, hasOldRow, hasNewRow);
        List<ColumnLineage> lineages = new ArrayList<>(result.getColumnLineage());
        Map<String, List<ColumnRef>> writtenSources = new LinkedHashMap<>();
        if (returningSourcesForCte) {
            for (ColumnLineage lineage : lineages) {
                if (lineage.getTarget() != null && lineage.getTarget().getTable() != null) {
                    writtenSources.merge(LineageModelUtils.columnKey(lineage.getTarget()), lineage.getSources(),
                            LineageModelUtils::mergeColumnRefs);
                }
            }
        }
        for (PostgreSqlParser.ReturningItemContext item : returning.returningItem()) {
            if (item.STAR() != null) {
                List<ColumnRef> sources;
                if (item.qualifiedName() == null) {
                    sources = new ArrayList<>(sourceWildcard);
                    sources.add(new ColumnRef(targetTable, "*"));
                } else {
                    List<String> path = new ArrayList<>();
                    for (PostgreSqlParser.IdentifierContext qualifier : item.qualifiedName().identifier()) {
                        path.add(qualifier.getText());
                    }
                    path.add("*");
                    SourceColumn wildcard = schemaQualifiedColumn(SourceColumn.path(path));
                    if (wildcard == null) {
                        wildcard = new SourceColumn(cleanIdentifier(path.get(path.size() - 2)), "*");
                    }
                    sources = resolveReturningSources(Collections.singletonList(wildcard), targetTable, rows, writtenSources);
                }
                if (sources != null) {
                    lineages.add(LineageModelUtils.columnLineage(null, "*", sources, item.getText()));
                }
                continue;
            }
            collectSubqueryDependencies(item.expression(), true);
            List<SourceColumn> sourceColumns = sourceColumns(item.expression());
            List<ColumnRef> sources = resolveReturningSources(sourceColumns, targetTable, rows, writtenSources);
            if (sources == null) {
                continue;
            }
            String targetColumn = item.alias == null
                    ? returningTargetColumn(item.expression().getText(), sourceColumns)
                    : cleanIdentifier(item.alias);
            if (targetColumn == null) {
                continue;
            }
            lineages.add(LineageModelUtils.columnLineage(
                    null,
                    targetColumn,
                    sources,
                    item.expression().getText()));
        }
        result.setColumnLineage(lineages);
    }

    private Map<String, ReturningRow> returningRows(PostgreSqlParser.ReturningClauseContext returning,
                                                    boolean hasOldRow,
                                                    boolean hasNewRow) {
        Map<String, ReturningRow> rows = new LinkedHashMap<>();
        boolean oldRenamed = false;
        boolean newRenamed = false;
        ReturningRow oldRow = hasOldRow ? ReturningRow.BEFORE_WRITE : ReturningRow.ABSENT;
        ReturningRow newRow = hasNewRow ? ReturningRow.AFTER_WRITE : ReturningRow.ABSENT;
        for (PostgreSqlParser.ReturningRowAliasContext alias : returning.returningRowAlias()) {
            String name = cleanIdentifier(alias.alias).toLowerCase(Locale.ROOT);
            if (alias.OLD() != null) {
                rows.put(name, oldRow);
                oldRenamed = true;
            } else {
                rows.put(name, newRow);
                newRenamed = true;
            }
        }
        if (!oldRenamed && !tableAliases.containsKey("old") && !derivedAliases.containsKey("old")) {
            rows.putIfAbsent("old", oldRow);
        }
        if (!newRenamed && !tableAliases.containsKey("new") && !derivedAliases.containsKey("new")) {
            rows.putIfAbsent("new", newRow);
        }
        return rows;
    }

    private List<ColumnRef> resolveReturningSources(List<SourceColumn> columns,
                                                   TableRef targetTable,
                                                   Map<String, ReturningRow> rows,
                                                   Map<String, List<ColumnRef>> writtenSources) {
        List<ColumnRef> resultSources = new ArrayList<>();
        for (SourceColumn column : columns) {
            int dot = column.name.indexOf('.');
            String qualifier = column.qualifier;
            String name = column.name;
            SourceColumn qualified = schemaQualifiedColumn(column);
            if (qualified != null) {
                qualifier = qualified.qualifier;
                name = qualified.name;
            } else if (column.resolvedRef == null && qualifier == null && dot >= 0) {
                qualifier = name.substring(0, dot);
                name = name.substring(dot + 1);
            }
            ReturningRow row = qualifier == null ? null : rows.get(qualifier.toLowerCase(Locale.ROOT));
            if (row == ReturningRow.ABSENT) {
                continue;
            }
            if (row == ReturningRow.BEFORE_WRITE) {
                resultSources.add(new ColumnRef(targetTable, name));
                continue;
            }
            String derivedName = qualifier == null ? null : derivedAliases.get(qualifier.toLowerCase(Locale.ROOT));
            List<ColumnRef> sources;
            if (row == ReturningRow.AFTER_WRITE) {
                sources = Collections.singletonList(new ColumnRef(targetTable, name));
            } else if ("*".equals(name) && derivedName != null) {
                sources = derivedWildcardSources(derivedName);
            } else {
                sources = resolveSources(Collections.singletonList(column));
            }
            if (sources == null) {
                return null;
            }
            for (ColumnRef source : sources) {
                List<ColumnRef> written = writtenSources.get(LineageModelUtils.columnKey(source));
                resultSources.addAll(written == null ? Collections.singletonList(source) : written);
            }
        }
        return LineageModelUtils.mergeColumnRefs(Collections.emptyList(), resultSources);
    }

    private enum ReturningRow {
        BEFORE_WRITE,
        AFTER_WRITE,
        ABSENT
    }

    private static String returningTargetColumn(String expression, List<SourceColumn> sources) {
        if (sources.size() != 1 || !isDirectColumnExpression(expression, sources.get(0))) {
            return null;
        }
        return unqualifiedName(sources.get(0).name);
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
        TableRef target = tableRef(ctx.targetRelation.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String targetAlias = tableAlias(ctx.tableAlias());
        if (targetAlias != null) {
            tableAliases.put(targetAlias.toLowerCase(Locale.ROOT), target);
        } else {
            bindQualifiedTable(ctx.targetRelation.multipartIdentifier(), target);
        }

        int sourceStart = visibleRelations.size();
        visit(ctx.relationPrimary());

        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));
        collectSubqueryDependencies(ctx.expression(), true);

        List<ColumnLineage> lineages = new ArrayList<>();
        boolean hasOldRow = false;
        boolean hasNewRow = false;
        for (PostgreSqlParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.mergeCondition() != null) {
                addColumnUsages(ColumnUsageType.MERGE_WHEN,
                        sourceColumns(clause.mergeCondition().expression()));
                collectSubqueryDependencies(clause.mergeCondition().expression(), true);
            }
            PostgreSqlParser.MergeMatchedActionContext matched = clause.mergeMatchedAction();
            if (matched != null) {
                hasOldRow |= matched.UPDATE() != null || matched.DELETE() != null;
                if (matched.assignmentList() != null) {
                    hasNewRow = true;
                    collectSubqueryDependencies(matched.assignmentList(), true);
                    lineages.addAll(readAssignments(matched.assignmentList(), target, false));
                }
            }
            PostgreSqlParser.MergeNotMatchedActionContext notMatched = clause.mergeNotMatchedAction();
            if (notMatched != null && notMatched.INSERT() != null) {
                hasNewRow = true;
                if (notMatched.expressionList() != null) {
                    lineages.addAll(readMergeInsertValues(notMatched, target));
                }
            }
        }
        result.setColumnLineage(lineages);
        List<ColumnRef> sourceWildcard = new ArrayList<>();
        if (ctx.returningClause() != null) {
            for (VisibleRelation source : visibleRelationsSince(sourceStart)) {
                if (source.table != null) {
                    sourceWildcard.add(new ColumnRef(source.table, "*"));
                } else {
                    sourceWildcard.addAll(derivedWildcardSources(source.derivedName));
                }
            }
        }
        appendReturningLineage(ctx.returningClause(), target, hasOldRow, hasNewRow, sourceWildcard);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private List<ColumnRef> derivedWildcardSources(String derivedName) {
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null) {
            return Collections.emptyList();
        }
        List<ColumnRef> sources = new ArrayList<>();
        for (List<ColumnRef> columnSources : columns.values()) {
            sources.addAll(columnSources);
        }
        return LineageModelUtils.mergeColumnRefs(Collections.emptyList(), sources);
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
            TableRef table = tableRef(tableName.inheritanceRelation().multipartIdentifier());
            if (isCteReference(table)) {
                addCteReference(table.getName(), tableName.tableAlias());
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
            } else {
                bindQualifiedTable(tableName.inheritanceRelation().multipartIdentifier(), table);
            }
            return;
        }
        if (primary instanceof PostgreSqlParser.AliasedQueryContext) {
            PostgreSqlParser.AliasedQueryContext query = (PostgreSqlParser.AliasedQueryContext) primary;
            String alias = tableAlias(query.tableAlias());
            String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), query.query(), new ArrayList<>());
            addDerivedReference(relationName, query.tableAlias());
            return;
        }
        visit(primary);
    }

    @Override
    public Void visitCreateIndexStmt(PostgreSqlParser.CreateIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStatement(PostgreSqlParser.CreateIndexStatementContext ctx) {
        TableRef table = tableRef(ctx.target);
        outputTables.add(table);
        currentDmlTarget = table;
        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
        for (PostgreSqlParser.IndexElementContext element : ctx.indexElementList().indexElement()) {
            addColumnUsages(ColumnUsageType.INDEX, sourceColumns(element.expression()));
        }
        if (ctx.includeColumns != null) {
            addIdentifierListUsages(table, ctx.includeColumns, ColumnUsageType.INDEX);
        }
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterIndexStmt(PostgreSqlParser.AlterIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterIndexRename(PostgreSqlParser.AlterIndexRenameContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(renamedTable(ctx.source, cleanIdentifier(ctx.target)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterIndexSetSchema(PostgreSqlParser.AlterIndexSetSchemaContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(tableInSchema(ctx.source, cleanIdentifier(ctx.targetSchema)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterIndexOther(PostgreSqlParser.AlterIndexOtherContext ctx) {
        outputTables.add(tableRef(ctx.source));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropIndexStmt(PostgreSqlParser.DropIndexStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitStatisticsStmt(PostgreSqlParser.StatisticsStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        if (ctx.statisticsStatement().table != null) {
            outputTables.add(tableRef(ctx.statisticsStatement().table));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitTypeStmt(PostgreSqlParser.TypeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDomainStmt(PostgreSqlParser.DomainStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    private void addIdentifierListUsages(TableRef table,
                                        PostgreSqlParser.IdentifierListContext ctx,
                                        ColumnUsageType type) {
        if (ctx == null) {
            return;
        }
        List<ColumnRef> columns = new ArrayList<>();
        for (PostgreSqlParser.IdentifierContext identifier : ctx.identifier()) {
            columns.add(new ColumnRef(table, cleanIdentifier(identifier)));
        }
        LineageModelUtils.addColumnUsages(result, type, columns);
    }

    @Override
    public Void visitCreateExtensionStmt(PostgreSqlParser.CreateExtensionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateSchemaStmt(PostgreSqlParser.CreateSchemaStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_SCHEMA);
        return null;
    }

    @Override
    public Void visitDatabaseStmt(PostgreSqlParser.DatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateFunctionStmt(PostgreSqlParser.CreateFunctionStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        PostgreSqlParser.RoutineSqlBodyContext body = ctx.createFunctionStatement().routineSqlBody();
        if (body == null) {
            return null;
        }
        if (body.expression() != null) {
            collectSubqueryDependencies(body.expression(), true);
        }
        for (PostgreSqlParser.RoutineSqlStatementContext statement : body.routineSqlStatement()) {
            if (statement.expression() != null) {
                collectSubqueryDependencies(statement.expression(), true);
                continue;
            }
            LineageResult step = new LineageResult();
            PostgreSqlLineageVisitor visitor = new PostgreSqlLineageVisitor(step);
            visitor.visit(statement.cteBody());
            visitor.finalizeResult();
            inputTables.addAll(step.getInputTables());
            outputTables.addAll(step.getOutputTables());
            for (ColumnLineage lineage : step.getColumnLineage()) {
                if (lineage.getTarget() != null && lineage.getTarget().getTable() != null) {
                    result.getColumnLineage().add(lineage);
                }
            }
            LineageModelUtils.mergeColumnUsages(result, step);
        }
        return null;
    }

    @Override
    public Void visitTriggerStmt(PostgreSqlParser.TriggerStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTrigger(PostgreSqlParser.CreateTriggerContext ctx) {
        result.setStatementType(StatementType.CREATE_TRIGGER);
        outputTables.add(tableRef(ctx.target));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropTrigger(PostgreSqlParser.DropTriggerContext ctx) {
        result.setStatementType(StatementType.DROP_TRIGGER);
        outputTables.add(tableRef(ctx.target));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateEventTrigger(PostgreSqlParser.CreateEventTriggerContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterEventTrigger(PostgreSqlParser.AlterEventTriggerContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropEventTrigger(PostgreSqlParser.DropEventTriggerContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitRuleStmt(PostgreSqlParser.RuleStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateRule(PostgreSqlParser.CreateRuleContext ctx) {
        TableRef target = tableRef(ctx.target);
        result.setStatementType(StatementType.ALTER_TABLE);
        outputTables.add(target);
        if (ctx.expression() != null) {
            TableRef previousTarget = currentDmlTarget;
            TableRef oldAlias = tableAliases.put("old", target);
            TableRef newAlias = tableAliases.put("new", target);
            currentDmlTarget = target;
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
            currentDmlTarget = previousTarget;
            restoreAlias("old", oldAlias);
            restoreAlias("new", newAlias);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropRule(PostgreSqlParser.DropRuleContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        outputTables.add(tableRef(ctx.target));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitSchemaObjectControlStmt(PostgreSqlParser.SchemaObjectControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitPolicyStmt(PostgreSqlParser.PolicyStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitPolicyStatement(PostgreSqlParser.PolicyStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = table;
        outputTables.add(table);
        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
        for (PostgreSqlParser.PolicyPredicateContext predicate : ctx.policyPredicate()) {
            collectSubqueryDependencies(predicate.expression(), true);
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(predicate.expression()));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitPublicationStmt(PostgreSqlParser.PublicationStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitPublicationStatement(PostgreSqlParser.PublicationStatementContext ctx) {
        if (ctx.publicationTarget() != null) {
            for (PostgreSqlParser.PublicationObjectContext object : ctx.publicationTarget().publicationObject()) {
                for (PostgreSqlParser.PublicationTableContext entry : object.publicationTable()) {
                    TableRef table = tableRef(entry.multipartIdentifier());
                    inputTables.add(table);
                    addIdentifierListUsages(table, entry.identifierList(), ColumnUsageType.READ_METADATA);
                    if (entry.expression() != null) {
                        currentDmlTarget = table;
                        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
                        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(entry.expression()));
                    }
                }
            }
        }
        result.setStatementType(inputTables.isEmpty() ? StatementType.CONTROL : StatementType.READ_METADATA);
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitSubscriptionStmt(PostgreSqlParser.SubscriptionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitForeignDataStmt(PostgreSqlParser.ForeignDataStmtContext ctx) {
        return visit(ctx.foreignDataStatement());
    }

    @Override
    public Void visitForeignDataStatement(PostgreSqlParser.ForeignDataStatementContext ctx) {
        String normalized = ctx.getStart().getInputStream()
                .getText(org.antlr.v4.runtime.misc.Interval.of(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.startsWith("create foreign table") || normalized.startsWith("alter foreign table")) {
            result.setStatementType(normalized.startsWith("create") ? StatementType.CREATE_TABLE : StatementType.ALTER_TABLE);
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        if (normalized.startsWith("drop foreign table")) {
            result.setStatementType(StatementType.DROP_TABLE);
            for (PostgreSqlParser.MultipartIdentifierContext table : ctx.multipartIdentifierList().multipartIdentifier()) {
                outputTables.add(tableRef(table));
            }
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateTableStmt(PostgreSqlParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(PostgreSqlParser.CreateTableStatementContext ctx) {
        if (ctx.createTableAsStatement() != null) {
            return visit(ctx.createTableAsStatement());
        }
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
        if (ctx.partitionParent != null) {
            inputTables.add(tableRef(ctx.partitionParent));
        }
        if (ctx.inheritsClause() != null) {
            for (PostgreSqlParser.MultipartIdentifierContext inherited :
                    ctx.inheritsClause().multipartIdentifierList().multipartIdentifier()) {
                inputTables.add(tableRef(inherited));
            }
        }
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else if (ctx.tableElementList() != null && likeSource(ctx.tableElementList()) != null) {
            result.setStatementType(StatementType.CREATE_TABLE_LIKE);
            inputTables.add(tableRef(likeSource(ctx.tableElementList())));
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            currentDmlTarget = target;
            collectCreateTableElements(ctx.tableElementList(), target);
        }
        if (ctx.partitionByClause() != null) {
            currentDmlTarget = target;
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.partitionByClause().expressionList()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateTableAsStatement(PostgreSqlParser.CreateTableAsStatementContext ctx) {
        result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
        PostgreSqlParser.QueryContext query = ctx.query() != null ? ctx.query() : preparedTableQuery;
        visitQueryBackedRelation(ctx.multipartIdentifier(), ctx.identifierList(), query);
        return null;
    }

    @Override
    public Void visitCreateViewStmt(PostgreSqlParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(PostgreSqlParser.CreateViewStatementContext ctx) {
        if (ctx.createMaterializedViewStatement() != null) {
            return visit(ctx.createMaterializedViewStatement());
        }
        visitQueryBackedRelation(ctx.multipartIdentifier(), ctx.viewColumnList, ctx.query());
        return null;
    }

    @Override
    public Void visitCreateMaterializedViewStatement(PostgreSqlParser.CreateMaterializedViewStatementContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        visitQueryBackedRelation(ctx.multipartIdentifier(), ctx.identifierList(), ctx.query());
        return null;
    }

    private void visitQueryBackedRelation(PostgreSqlParser.MultipartIdentifierContext relation,
                                          PostgreSqlParser.IdentifierListContext columnNames,
                                          PostgreSqlParser.QueryContext query) {
        TableRef target = tableRef(relation);
        outputTables.add(target);
        if (columnNames != null) {
            for (PostgreSqlParser.IdentifierContext id : columnNames.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        if (query != null) {
            visit(query);
            refreshColumnLineage();
            retargetColumnLineage(target);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
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
    public Void visitDropSchemaStmt(PostgreSqlParser.DropSchemaStmtContext ctx) {
        result.setStatementType(StatementType.DROP_SCHEMA);
        return null;
    }

    @Override
    public Void visitDropExtensionStmt(PostgreSqlParser.DropExtensionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropFunctionStmt(PostgreSqlParser.DropFunctionStmtContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitRefreshMaterializedViewStmt(PostgreSqlParser.RefreshMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRefreshMaterializedViewStatement(PostgreSqlParser.RefreshMaterializedViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewStmt(PostgreSqlParser.AlterViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterViewRename(PostgreSqlParser.AlterViewRenameContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(renamedTable(ctx.source, cleanIdentifier(ctx.target)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewSetSchema(PostgreSqlParser.AlterViewSetSchemaContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(tableInSchema(ctx.source, cleanIdentifier(ctx.targetSchema)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewOther(PostgreSqlParser.AlterViewOtherContext ctx) {
        outputTables.add(tableRef(ctx.source));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewStmt(PostgreSqlParser.AlterMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterMaterializedViewRename(PostgreSqlParser.AlterMaterializedViewRenameContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(renamedTable(ctx.source, cleanIdentifier(ctx.target)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewSetSchema(PostgreSqlParser.AlterMaterializedViewSetSchemaContext ctx) {
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        outputTables.add(tableInSchema(ctx.source, cleanIdentifier(ctx.targetSchema)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewOther(PostgreSqlParser.AlterMaterializedViewOtherContext ctx) {
        outputTables.add(tableRef(ctx.source));
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
        for (PostgreSqlParser.InheritanceRelationContext relation : ctx.inheritanceRelation()) {
            outputTables.add(tableRef(relation.multipartIdentifier()));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(PostgreSqlParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableAttachPartition(PostgreSqlParser.AlterTableAttachPartitionContext ctx) {
        TableRef parent = tableRef(ctx.parent);
        TableRef child = tableRef(ctx.child);
        inputTables.add(child);
        outputTables.add(parent);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableDetachPartition(PostgreSqlParser.AlterTableDetachPartitionContext ctx) {
        outputTables.add(tableRef(ctx.parent));
        outputTables.add(tableRef(ctx.child));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
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
    public Void visitAlterTableRenameColumn(PostgreSqlParser.AlterTableRenameColumnContext ctx) {
        TableRef table = tableRef(ctx.target);
        outputTables.add(table);
        LineageModelUtils.addColumnUsages(result,
                ColumnUsageType.TABLE_MODEL,
                java.util.Collections.singletonList(new ColumnRef(table, cleanIdentifier(ctx.sourceColumn))));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(PostgreSqlParser.AlterTableAddColumnContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        String column = cleanIdentifier(ctx.columnName);
        currentDmlTarget = table;
        recordAlterColumnUsage(table, column);
        List<ColumnLineage> generated = new ArrayList<>();
        for (PostgreSqlParser.ColumnConstraintContext constraint : ctx.columnConstraint()) {
            collectColumnConstraint(constraint, table, column, generated);
        }
        result.setColumnLineage(generated);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableDropColumn(PostgreSqlParser.AlterTableDropColumnContext ctx) {
        TableRef table = tableRef(ctx.target);
        outputTables.add(table);
        LineageModelUtils.addColumnUsages(result,
                ColumnUsageType.TABLE_MODEL,
                java.util.Collections.singletonList(new ColumnRef(table, cleanIdentifier(ctx.dropColumn))));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableSetGeneratedExpression(PostgreSqlParser.AlterTableSetGeneratedExpressionContext ctx) {
        TableRef table = tableRef(ctx.target);
        String column = cleanIdentifier(ctx.alterColumn);
        currentDmlTarget = table;
        recordAlterColumnUsage(table, column);
        ColumnLineage lineage = generatedColumnLineage(table, column, ctx.generatedExpression);
        result.setColumnLineage(Collections.singletonList(lineage));
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, lineage.getSources());
        return null;
    }

    @Override
    public Void visitAlterTableDropGeneratedExpression(PostgreSqlParser.AlterTableDropGeneratedExpressionContext ctx) {
        recordAlterColumnUsage(tableRef(ctx.target), cleanIdentifier(ctx.alterColumn));
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumnSetDefault(PostgreSqlParser.AlterTableAlterColumnSetDefaultContext ctx) {
        recordAlterColumnUsage(tableRef(ctx.target), cleanIdentifier(ctx.alterColumn));
        addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.expression()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumnDropDefault(PostgreSqlParser.AlterTableAlterColumnDropDefaultContext ctx) {
        recordAlterColumnUsage(tableRef(ctx.target), cleanIdentifier(ctx.alterColumn));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumnSetNotNull(PostgreSqlParser.AlterTableAlterColumnSetNotNullContext ctx) {
        recordAlterColumnUsage(tableRef(ctx.target), cleanIdentifier(ctx.alterColumn));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumnDropNotNull(PostgreSqlParser.AlterTableAlterColumnDropNotNullContext ctx) {
        recordAlterColumnUsage(tableRef(ctx.target), cleanIdentifier(ctx.alterColumn));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumnType(PostgreSqlParser.AlterTableAlterColumnTypeContext ctx) {
        TableRef table = tableRef(ctx.target);
        recordAlterColumnUsage(table, cleanIdentifier(ctx.alterColumn));
        if (ctx.usingExpression != null) {
            TableRef previousTarget = currentDmlTarget;
            currentDmlTarget = table;
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.usingExpression));
            currentDmlTarget = previousTarget;
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddForeignKey(PostgreSqlParser.AlterTableAddForeignKeyContext ctx) {
        TableRef table = tableRef(ctx.target);
        outputTables.add(table);
        inputTables.add(tableRef(ctx.refTable));
        addIdentifierListUsages(table, ctx.localColumns, ColumnUsageType.TABLE_MODEL);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddCheck(PostgreSqlParser.AlterTableAddCheckContext ctx) {
        TableRef target = tableRef(ctx.target);
        TableRef previousTarget = currentDmlTarget;
        currentDmlTarget = target;
        outputTables.add(target);
        addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.checkExpression));
        currentDmlTarget = previousTarget;
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(PostgreSqlParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void recordAlterColumnUsage(TableRef table, String columnName) {
        outputTables.add(table);
        LineageModelUtils.addColumnUsages(result,
                ColumnUsageType.TABLE_MODEL,
                java.util.Collections.singletonList(new ColumnRef(table, columnName)));
    }

    private void restoreAlias(String alias, TableRef previous) {
        if (previous == null) {
            tableAliases.remove(alias);
        } else {
            tableAliases.put(alias, previous);
        }
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
    public Void visitSecurityLabelStmt(PostgreSqlParser.SecurityLabelStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitSecurityLabelStatement(PostgreSqlParser.SecurityLabelStatementContext ctx) {
        PostgreSqlParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
        if (id == null) {
            return null;
        }
        List<String> parts = identifierParts(id);
        if (ctx.COLUMN() != null && parts.size() > 1) {
            TableRef table = LineageModelUtils.tableRefFromParts(parts.subList(0, parts.size() - 1));
            outputTables.add(table);
            LineageModelUtils.addColumnUsages(result,
                    ColumnUsageType.TABLE_MODEL,
                    java.util.Collections.singletonList(new ColumnRef(table, parts.get(parts.size() - 1))));
        } else if (ctx.TABLE() != null) {
            outputTables.add(tableRef(id));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
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
            TableRef table = LineageModelUtils.tableRefFromParts(parts.subList(0, parts.size() - 1));
            outputTables.add(table);
            LineageModelUtils.addColumnUsages(result,
                    ColumnUsageType.TABLE_MODEL,
                    java.util.Collections.singletonList(new ColumnRef(table, parts.get(parts.size() - 1))));
        } else if (ctx.TABLE() != null) {
            outputTables.add(tableRef(id));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCopyStmt(PostgreSqlParser.CopyStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCopyFromTable(PostgreSqlParser.CopyFromTableContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            result.setColumnLineage(readCopyColumns(ctx.columnList, target));
            addIdentifierListUsages(target, ctx.columnList, ColumnUsageType.TABLE_MODEL);
        }
        if (ctx.whereClause() != null) {
            currentDmlTarget = target;
            tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        addCopyOptionColumnUsages(ctx.copyOptions(), target, ColumnUsageType.TABLE_MODEL);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCopyToTable(PostgreSqlParser.CopyToTableContext ctx) {
        result.setStatementType(StatementType.SELECT);
        TableRef source = tableRef(ctx.multipartIdentifier());
        inputTables.add(source);
        tableAliases.put(source.getName().toLowerCase(Locale.ROOT), source);
        if (ctx.columnList != null) {
            result.setColumnLineage(readCopyTableColumns(ctx.columnList, source));
            addIdentifierListUsages(source, ctx.columnList, ColumnUsageType.READ_METADATA);
        }
        addCopyOptionColumnUsages(ctx.copyOptions(), source, ColumnUsageType.READ_METADATA);
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private void addCopyOptionColumnUsages(PostgreSqlParser.CopyOptionsContext options,
                                           TableRef table, ColumnUsageType type) {
        if (options == null) {
            return;
        }
        for (PostgreSqlParser.CopyOptionContext option : options.copyOption()) {
            String name = cleanIdentifier(option.identifier()).toLowerCase(Locale.ROOT);
            switch (name) {
                case "force_quote":
                case "force_not_null":
                case "force_null":
                    if (option.identifierList() != null) {
                        addIdentifierListUsages(table, option.identifierList(), type);
                    } else if (option.STAR() != null) {
                        LineageModelUtils.addColumnUsages(result, type,
                                Collections.singletonList(new ColumnRef(table, "*")));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public Void visitCopyToQuery(PostgreSqlParser.CopyToQueryContext ctx) {
        result.setStatementType(StatementType.SELECT);
        visit(ctx.cteBody());
        refreshColumnLineage();
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitVacuumStmt(PostgreSqlParser.VacuumStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitVacuumStatement(PostgreSqlParser.VacuumStatementContext ctx) {
        for (PostgreSqlParser.MaintenanceRelationContext relation : ctx.maintenanceRelation()) {
            TableRef table = tableRef(relation.multipartIdentifier());
            outputTables.add(table);
            addIdentifierListUsages(table, relation.identifierList(), ColumnUsageType.READ_METADATA);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAnalyzeStmt(PostgreSqlParser.AnalyzeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeStatement(PostgreSqlParser.AnalyzeStatementContext ctx) {
        for (PostgreSqlParser.MaintenanceRelationContext relation : ctx.maintenanceRelation()) {
            TableRef table = tableRef(relation.multipartIdentifier());
            inputTables.add(table);
            addIdentifierListUsages(table, relation.identifierList(), ColumnUsageType.READ_METADATA);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitReindexStmt(PostgreSqlParser.ReindexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitReindexStatement(PostgreSqlParser.ReindexStatementContext ctx) {
        if (ctx.TABLE() != null || ctx.INDEX() != null) {
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitClusterStmt(PostgreSqlParser.ClusterStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitClusterStatement(PostgreSqlParser.ClusterStatementContext ctx) {
        if (ctx.multipartIdentifier() != null && !ctx.multipartIdentifier().isEmpty()) {
            outputTables.add(tableRef(ctx.multipartIdentifier(0)));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitLockStmt(PostgreSqlParser.LockStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLockTarget(PostgreSqlParser.LockTargetContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitRoutineControlStmt(PostgreSqlParser.RoutineControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        PostgreSqlParser.RoutineControlStatementContext statement = ctx.routineControlStatement();
        if (statement.preparedStatement != null && statement.preparedStatement.query() == null) {
            visit(statement.preparedStatement);
            return null;
        }
        if (statement.declareCursorStatement() != null) {
            return visit(statement.declareCursorStatement());
        }
        if (statement.preparedStatement != null) {
            copyControlQueryLineage(statement.preparedStatement.query());
        }
        return null;
    }

    @Override
    public Void visitDeclareCursorStatement(PostgreSqlParser.DeclareCursorStatementContext ctx) {
        copyControlQueryLineage(ctx.query());
        return null;
    }

    private void copyControlQueryLineage(PostgreSqlParser.QueryContext query) {
        LineageResult queryResult = lineageForQuery(query);
        inputTables.addAll(queryResult.getInputTables());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setColumnLineage(queryResult.getColumnLineage());
        result.setColumnUsages(queryResult.getColumnUsages());
    }

    @Override
    public Void visitPrincipalStmt(PostgreSqlParser.PrincipalStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDefaultPrivilegesStmt(PostgreSqlParser.DefaultPrivilegesStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitGrantStmt(PostgreSqlParser.GrantStmtContext ctx) {
        result.setStatementType(ctx.grantStatement().privilegeTarget() instanceof PostgreSqlParser.TablePrivilegeTargetContext
                ? StatementType.ALTER_TABLE : StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGrantStatement(PostgreSqlParser.GrantStatementContext ctx) {
        if (ctx.privilegeTarget() instanceof PostgreSqlParser.TablePrivilegeTargetContext) {
            outputTables.add(tableRef(((PostgreSqlParser.TablePrivilegeTargetContext) ctx.privilegeTarget()).multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRevokeStmt(PostgreSqlParser.RevokeStmtContext ctx) {
        result.setStatementType(ctx.revokeStatement().privilegeTarget() instanceof PostgreSqlParser.TablePrivilegeTargetContext
                ? StatementType.ALTER_TABLE : StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRevokeStatement(PostgreSqlParser.RevokeStatementContext ctx) {
        if (ctx.privilegeTarget() instanceof PostgreSqlParser.TablePrivilegeTargetContext) {
            outputTables.add(tableRef(((PostgreSqlParser.TablePrivilegeTargetContext) ctx.privilegeTarget()).multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRoleGrantStmt(PostgreSqlParser.RoleGrantStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitSetStmt(PostgreSqlParser.SetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitResetStmt(PostgreSqlParser.ResetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDiscardStmt(PostgreSqlParser.DiscardStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitTransactionStmt(PostgreSqlParser.TransactionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(PostgreSqlParser.CtesContext ctx) {
        boolean recursive = ctx.RECURSIVE() != null;
        List<PostgreSqlParser.NamedQueryContext> definitions = ctx.namedQuery();
        if (recursive) {
            for (PostgreSqlParser.NamedQueryContext definition : definitions) {
                cteNames.add(cteName(definition));
            }
            definitions = orderRecursiveCtes(definitions);
        }
        for (PostgreSqlParser.NamedQueryContext namedQuery : definitions) {
            String cteName = cteName(namedQuery);
            PostgreSqlParser.CteBodyContext body = namedQuery.cteBody();
            if (body.query() != null) {
                List<String> aliases = cteColumnAliases(namedQuery);
                if (recursive) {
                    seedRecursiveRelation(cteName, body.query(), aliases);
                }
                registerDerivedRelation(cteName, body.query(), aliases);
                Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(cteName);
                if (namedQuery.searchClause() != null) {
                    PostgreSqlParser.SearchClauseContext search = namedQuery.searchClause();
                    columns.put(cleanIdentifier(search.sequenceColumn), cteKeySources(columns, search.identifierList()));
                }
                if (namedQuery.cycleClause() != null) {
                    PostgreSqlParser.CycleClauseContext cycle = namedQuery.cycleClause();
                    List<ColumnRef> keys = cteKeySources(columns, cycle.identifierList());
                    columns.put(cleanIdentifier(cycle.markColumn), keys);
                    columns.put(cleanIdentifier(cycle.pathColumn), keys);
                }
            } else {
                registerDataModifyingCte(cteName, body, cteColumnAliases(namedQuery));
            }
            cteNames.add(cteName);
        }
        return null;
    }

    private static String cteName(PostgreSqlParser.NamedQueryContext definition) {
        return cleanIdentifier(definition.name).toLowerCase(Locale.ROOT);
    }

    private static List<PostgreSqlParser.NamedQueryContext> orderRecursiveCtes(
            List<PostgreSqlParser.NamedQueryContext> definitions) {
        if (definitions.size() < 2) {
            return definitions;
        }
        Map<PostgreSqlParser.NamedQueryContext, Set<String>> pending = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (PostgreSqlParser.NamedQueryContext definition : definitions) {
            String name = cteName(definition);
            if (!names.add(name)) {
                return definitions;
            }
            Set<String> dependencies = new LinkedHashSet<>();
            collectCteDependencies(definition.cteBody(), Collections.emptySet(), dependencies);
            dependencies.remove(name);
            pending.put(definition, dependencies);
        }

        List<PostgreSqlParser.NamedQueryContext> ordered = new ArrayList<>();
        while (!pending.isEmpty()) {
            int previousSize = pending.size();
            Iterator<Map.Entry<PostgreSqlParser.NamedQueryContext, Set<String>>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<PostgreSqlParser.NamedQueryContext, Set<String>> next = iterator.next();
                if (Collections.disjoint(next.getValue(), names)) {
                    ordered.add(next.getKey());
                    names.remove(cteName(next.getKey()));
                    iterator.remove();
                }
            }
            if (previousSize == pending.size()) {
                ordered.addAll(pending.keySet());
                break;
            }
        }
        return ordered;
    }

    private static void collectCteDependencies(ParseTree tree, Set<String> hidden, Set<String> dependencies) {
        PostgreSqlParser.InheritanceRelationContext relation = null;
        if (tree instanceof PostgreSqlParser.TableNameContext) {
            relation = ((PostgreSqlParser.TableNameContext) tree).inheritanceRelation();
        } else if (tree instanceof PostgreSqlParser.TableQueryContext) {
            PostgreSqlParser.InheritanceRelationContext requested =
                    ((PostgreSqlParser.TableQueryContext) tree).inheritanceRelation();
            if (requested.ONLY() == null && requested.STAR() == null) {
                relation = requested;
            }
        }
        if (relation != null) {
            List<String> parts = identifierParts(relation.multipartIdentifier());
            if (parts.size() == 1) {
                String name = parts.get(0).toLowerCase(Locale.ROOT);
                if (!hidden.contains(name)) {
                    dependencies.add(name);
                }
            }
        }

        Set<String> scope = hidden;
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (child instanceof PostgreSqlParser.CtesContext) {
                scope = collectNestedCteDependencies((PostgreSqlParser.CtesContext) child, scope, dependencies);
            } else if (!(tree instanceof PostgreSqlParser.UpdateStatementContext)
                    || child != ((PostgreSqlParser.UpdateStatementContext) tree).relation()) {
                collectCteDependencies(child, scope, dependencies);
            }
        }
    }

    private static Set<String> collectNestedCteDependencies(PostgreSqlParser.CtesContext ctes,
                                                            Set<String> hidden, Set<String> dependencies) {
        Set<String> scope = new LinkedHashSet<>(hidden);
        if (ctes.RECURSIVE() != null) {
            for (PostgreSqlParser.NamedQueryContext definition : ctes.namedQuery()) {
                scope.add(cteName(definition));
            }
        }
        for (PostgreSqlParser.NamedQueryContext definition : ctes.namedQuery()) {
            collectCteDependencies(definition.cteBody(), scope, dependencies);
            scope.add(cteName(definition));
        }
        return scope;
    }

    private void seedRecursiveRelation(String name, PostgreSqlParser.QueryContext query, List<String> aliases) {
        if (query.ctes() != null || !(query.queryTerm() instanceof PostgreSqlParser.SetOperationContext)) {
            return;
        }
        PostgreSqlParser.SetOperationContext union = (PostgreSqlParser.SetOperationContext) query.queryTerm();
        if (union.UNION() == null) {
            return;
        }
        List<ColumnLineage> anchor = analyzeSetOperand(union.left).result.getColumnLineage();
        LineageModelUtils.registerRecursiveColumnSources(derivedColumnLineage, name, anchor, aliases,
                () -> analyzeSetOperand(union.right).result.getColumnLineage());
    }

    private static List<ColumnRef> cteKeySources(Map<String, List<ColumnRef>> columns,
                                                PostgreSqlParser.IdentifierListContext keys) {
        List<ColumnRef> sources = new ArrayList<>();
        for (String key : identifierNames(keys)) {
            sources.addAll(columns.getOrDefault(key, Collections.emptyList()));
        }
        return LineageModelUtils.mergeColumnRefs(Collections.emptyList(), sources);
    }

    @Override
    public Void visitSetOperation(PostgreSqlParser.SetOperationContext ctx) {
        PostgreSqlLineageVisitor left = analyzeSetOperand(ctx.left);
        PostgreSqlLineageVisitor right = analyzeSetOperand(ctx.right);
        LineageResult leftResult = left.result;
        LineageResult rightResult = right.result;
        int width = left.knownOutputWidth(ctx.left);
        setColumnCount = width >= 0 && width == right.knownOutputWidth(ctx.right)
                && width == leftResult.getColumnLineage().size() && width == rightResult.getColumnLineage().size()
                ? width : -1;
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
    public Void visitQuantifiedComparison(PostgreSqlParser.QuantifiedComparisonContext ctx) {
        if (ctx.query() == null) {
            return visitChildren(ctx);
        }
        addSubqueryDependencies(ctx.query(), true);
        visit(ctx.left);
        return null;
    }

    @Override
    public Void visitTableQuery(PostgreSqlParser.TableQueryContext ctx) {
        TableRef table = tableRef(ctx.inheritanceRelation().multipartIdentifier());
        if (ctx.inheritanceRelation().ONLY() == null && ctx.inheritanceRelation().STAR() == null
                && isCteReference(table)) {
            String name = table.getName().toLowerCase(Locale.ROOT);
            addDerivedReference(table.getName(), null);
            Map<String, List<ColumnRef>> columns = derivedColumnLineage.getOrDefault(name, Collections.emptyMap());
            List<ColumnLineage> lineages = new ArrayList<>();
            for (Map.Entry<String, List<ColumnRef>> column : columns.entrySet()) {
                lineages.add(LineageModelUtils.columnLineage(null, column.getKey(), column.getValue(), column.getKey()));
            }
            result.setColumnLineage(lineages);
        } else {
            addInputTable(table, false);
        }
        return null;
    }

    @Override
    public Void visitTableName(PostgreSqlParser.TableNameContext ctx) {
        PostgreSqlParser.MultipartIdentifierContext name = ctx.inheritanceRelation().multipartIdentifier();
        TableRef table = tableRef(name);
        if (isCteReference(table)) {
            addCteReference(table.getName(), ctx.tableAlias());
            return null;
        }
        if (tableAlias(ctx.tableAlias()) == null) {
            bindQualifiedTable(name, table);
        }
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitAliasedQuery(PostgreSqlParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumnNames(ctx.tableAlias()));
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitAliasedRelation(PostgreSqlParser.AliasedRelationContext ctx) {
        if (tableAlias(ctx.tableAlias()) == null) {
            return visit(ctx.relation());
        }
        int relationStart = visibleRelations.size();
        int priorCount = visibleRelationCount;
        Map<String, TableRef> enclosingTables = new LinkedHashMap<>(tableAliases);
        Map<String, String> enclosingDerived = new LinkedHashMap<>(derivedAliases);
        Set<String> enclosingReferences = new LinkedHashSet<>(derivedReferences);
        Map<String, Map<String, List<ColumnRef>>> enclosingColumns = new LinkedHashMap<>(derivedColumnLineage);
        Set<String> enclosingKnownRelations = new LinkedHashSet<>(knownColumnRelations);
        visit(ctx.relation());

        // A joined relation alias exposes the join result as one relation.
        visibleRelations.subList(relationStart, visibleRelations.size()).clear();
        visibleRelationCount = priorCount;
        tableAliases.clear();
        tableAliases.putAll(enclosingTables);
        derivedAliases.clear();
        derivedAliases.putAll(enclosingDerived);
        derivedReferences.clear();
        derivedReferences.addAll(enclosingReferences);
        derivedColumnLineage.clear();
        derivedColumnLineage.putAll(enclosingColumns);
        knownColumnRelations.clear();
        knownColumnRelations.addAll(enclosingKnownRelations);

        Map<String, List<ColumnRef>> joinKeys = relationJoinKeys.getOrDefault(
                ctx.relation(), Collections.emptyMap());
        List<String> aliases = tableAliasColumnNames(ctx.tableAlias());
        if (!aliases.isEmpty()) {
            joinKeys = renameLeadingJoinKeys(ctx.relation(), joinKeys, aliases);
            relationJoinKeys.put(ctx.relation(), joinKeys);
        }
        String relation = "$joined_relation_" + derivedColumnLineage.size();
        derivedColumnLineage.put(relation, joinKeys);
        addDerivedReference(relation, ctx.tableAlias());
        return null;
    }

    private Map<String, List<ColumnRef>> renameLeadingJoinKeys(PostgreSqlParser.RelationContext ctx,
                                                                Map<String, List<ColumnRef>> joinKeys,
                                                                List<String> aliases) {
        List<PostgreSqlParser.JoinRelationContext> joins = ctx.joinRelation();
        for (int i = joins.size() - 1; i >= 0; i--) {
            PostgreSqlParser.JoinRelationContext join = joins.get(i);
            if (join.NATURAL() != null) {
                break;
            }
            PostgreSqlParser.JoinCriteriaContext criteria = join.joinCriteria();
            if (criteria != null && criteria.identifierList() != null) {
                Map<String, List<ColumnRef>> renamed = new LinkedHashMap<>();
                List<String> keys = identifierNames(criteria.identifierList());
                for (int index = 0; index < keys.size(); index++) {
                    String key = keys.get(index);
                    String name = index < aliases.size() ? aliases.get(index) : key;
                    renamed.put(name, joinKeys.getOrDefault(key, Collections.emptyList()));
                }
                return renamed;
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public Void visitLateralQuery(PostgreSqlParser.LateralQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$lateral" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumnNames(ctx.tableAlias()));
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitJsonTableRelation(PostgreSqlParser.JsonTableRelationContext ctx) {
        List<SourceColumn> rowSources = sourceColumns(ctx.expression(0));
        rowSources.addAll(sourceColumns(ctx.expression(1)));
        if (ctx.jsonPassingClause() != null) {
            rowSources.addAll(sourceColumns(ctx.jsonPassingClause()));
        }
        addColumnUsages(ColumnUsageType.JOIN_ON, rowSources);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        collectJsonTableColumns(ctx.jsonTableColumns(), rowSources, columns);
        registerNamedFunctionColumns(ctx.tableAlias(), "json_table", columns);
        return null;
    }

    @Override
    public Void visitXmlTableRelation(PostgreSqlParser.XmlTableRelationContext ctx) {
        collectSubqueryDependencies(ctx, true);
        List<SourceColumn> rowSources = sourceColumns(ctx.rowPath);
        rowSources.addAll(sourceColumns(ctx.document));
        if (ctx.xmlNamespaces() != null) {
            for (PostgreSqlParser.XmlNamespaceContext namespace : ctx.xmlNamespaces().xmlNamespace()) {
                rowSources.addAll(sourceColumns(namespace.expression()));
            }
        }
        addColumnUsages(ColumnUsageType.JOIN_ON, rowSources);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (PostgreSqlParser.XmlTableColumnContext column : ctx.xmlTableColumn()) {
            List<ColumnRef> refs = Collections.emptyList();
            if (column.ORDINALITY() == null) {
                List<SourceColumn> sources = new ArrayList<>(rowSources);
                for (PostgreSqlParser.XmlTableColumnOptionContext option : column.xmlTableColumnOption()) {
                    if (option.expression() != null) {
                        sources.addAll(sourceColumns(option.expression()));
                    }
                }
                List<ColumnRef> resolved = columnUsageRefs(sources);
                if (resolved != null) {
                    refs = LineageModelUtils.mergeColumnRefs(Collections.emptyList(), resolved);
                }
            }
            columns.put(cleanIdentifier(column.name), refs);
        }
        registerNamedFunctionColumns(ctx.tableAlias(), "xmltable", columns);
        return null;
    }

    private void registerNamedFunctionColumns(PostgreSqlParser.TableAliasContext aliasContext,
                                              String defaultName, Map<String, List<ColumnRef>> columns) {
        List<String> aliasColumns = tableAliasColumnNames(aliasContext);
        Map<String, List<ColumnRef>> namedColumns = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, List<ColumnRef>> entry : columns.entrySet()) {
            String name = index < aliasColumns.size() ? aliasColumns.get(index) : entry.getKey();
            namedColumns.put(name, entry.getValue());
            index++;
        }
        String alias = tableAlias(aliasContext);
        registerFunctionRelation(alias == null ? defaultName : alias, namedColumns,
                namedColumns.size() == columns.size());
    }

    private void registerFunctionRelation(String name, Map<String, List<ColumnRef>> columns, boolean completeShape) {
        String key = name.toLowerCase(Locale.ROOT);
        if (completeShape) {
            knownColumnRelations.add(key);
        } else {
            knownColumnRelations.remove(key);
        }
        derivedColumnLineage.put(key, columns);
        addDerivedReference(name, null);
    }

    private void collectJsonTableColumns(PostgreSqlParser.JsonTableColumnsContext ctx,
                                         List<SourceColumn> rowSources,
                                         Map<String, List<ColumnRef>> columns) {
        for (PostgreSqlParser.JsonTableColumnContext column : ctx.jsonTableColumn()) {
            if (column.ORDINALITY() != null) {
                columns.put(cleanIdentifier(column.name), Collections.emptyList());
                continue;
            }
            List<SourceColumn> sources = new ArrayList<>(rowSources);
            if (column.expression() != null) {
                sources.addAll(sourceColumns(column.expression()));
            }
            if (column.jsonTableColumns() != null) {
                collectJsonTableColumns(column.jsonTableColumns(), sources, columns);
                continue;
            }
            for (PostgreSqlParser.JsonQueryBehaviorContext behavior : column.jsonQueryBehavior()) {
                sources.addAll(sourceColumns(behavior));
            }
            List<ColumnRef> refs = columnUsageRefs(sources);
            columns.put(cleanIdentifier(column.name), refs == null
                    ? Collections.emptyList()
                    : LineageModelUtils.mergeColumnRefs(Collections.emptyList(), refs));
        }
    }

    @Override
    public Void visitTableFunction(PostgreSqlParser.TableFunctionContext ctx) {
        List<SourceColumn> sources = ctx.functionArguments() == null
                ? new ArrayList<>()
                : sourceColumns(ctx.functionArguments());
        addColumnUsages(ColumnUsageType.JOIN_ON, sources);
        if (ctx.recordColumn().isEmpty()) {
            if (!registerUnnestColumns(ctx) && !registerJsonFunctionColumns(ctx, sources)) {
                registerGeneratedRelation(ctx.tableAlias(), sources, ctx.ORDINALITY() != null);
            }
        } else {
            String alias = ctx.recordAlias == null
                    ? unqualifiedName(cleanIdentifier(ctx.functionName().getText()))
                    : cleanIdentifier(ctx.recordAlias.getText());
            List<String> columnNames = new ArrayList<>();
            for (PostgreSqlParser.RecordColumnContext column : ctx.recordColumn()) {
                columnNames.add(cleanIdentifier(column.identifier()));
            }
            registerGeneratedRelation(alias, columnNames, sources, false, true);
        }
        return null;
    }

    private boolean registerJsonFunctionColumns(PostgreSqlParser.TableFunctionContext ctx,
                                                List<SourceColumn> sources) {
        String function = postgresBuiltinName(ctx.functionName());
        List<String> names = jsonFunctionColumnNames(function);
        boolean withOrdinality = ctx.ORDINALITY() != null;
        if (names.isEmpty() || tableAliasColumnNames(ctx.tableAlias()).size() > names.size() + (withOrdinality ? 1 : 0)) {
            return false;
        }
        List<ColumnRef> refs = columnUsageRefs(sources);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (String name : names) {
            columns.put(name, refs == null ? Collections.emptyList() : refs);
        }
        if (withOrdinality) {
            columns.put("ordinality", Collections.emptyList());
        }
        registerNamedFunctionColumns(ctx.tableAlias(), function, columns);
        return true;
    }

    private boolean registerUnnestColumns(PostgreSqlParser.TableFunctionContext ctx) {
        if (ctx.functionArguments() == null || !"unnest".equals(postgresBuiltinName(ctx.functionName()))) {
            return false;
        }

        List<PostgreSqlParser.CallArgumentContext> arguments = ctx.functionArguments().callArgument();
        List<String> aliasColumns = tableAliasColumnNames(ctx.tableAlias());
        boolean withOrdinality = ctx.ORDINALITY() != null;
        int valueCount = arguments.size();
        if (aliasColumns.size() > valueCount + (withOrdinality ? 1 : 0)) {
            return false;
        }

        String alias = tableAlias(ctx.tableAlias());
        String relation = alias == null ? "unnest" : alias;
        String defaultColumn = valueCount == 1 ? relation : "unnest";
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (int i = 0; i < valueCount; i++) {
            String column = i < aliasColumns.size() ? aliasColumns.get(i) : defaultColumn;
            List<ColumnRef> refs = columnUsageRefs(sourceColumns(arguments.get(i).expression()));
            columns.merge(column, refs == null ? Collections.emptyList() : refs, LineageModelUtils::mergeColumnRefs);
        }
        if (withOrdinality) {
            String ordinal = aliasColumns.size() > valueCount ? aliasColumns.get(valueCount) : "ordinality";
            columns.putIfAbsent(ordinal, Collections.emptyList());
        }
        registerFunctionRelation(relation, columns,
                columns.size() == valueCount + (withOrdinality ? 1 : 0));
        return true;
    }

    @Override
    public Void visitRowsFromTableFunction(PostgreSqlParser.RowsFromTableFunctionContext ctx) {
        List<SourceColumn> sources = new ArrayList<>();
        List<String> aliasColumns = tableAliasColumnNames(ctx.tableAlias());
        String alias = tableAlias(ctx.tableAlias());
        String scalarAlias = ctx.rowsFromItem().size() == 1 ? alias : null;
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        boolean knownShape = true;
        int index = 0;
        for (PostgreSqlParser.RowsFromItemContext item : ctx.rowsFromItem()) {
            List<SourceColumn> itemSources = sourceColumns(item.functionArguments());
            sources.addAll(itemSources);
            List<String> names = rowsFromColumnNames(item, scalarAlias);
            if (names.isEmpty()) {
                knownShape = false;
                continue;
            }
            List<ColumnRef> refs = columnUsageRefs(itemSources);
            for (String name : names) {
                String column = index < aliasColumns.size() ? aliasColumns.get(index) : name;
                columns.merge(column, refs == null ? Collections.emptyList() : refs,
                        LineageModelUtils::mergeColumnRefs);
                index++;
            }
        }
        addColumnUsages(ColumnUsageType.JOIN_ON, sources);
        if (!knownShape || aliasColumns.size() > index + (ctx.ORDINALITY() == null ? 0 : 1)) {
            registerGeneratedRelation(ctx.tableAlias(), sources, ctx.ORDINALITY() != null);
            return null;
        }
        if (ctx.ORDINALITY() != null) {
            columns.putIfAbsent(index < aliasColumns.size() ? aliasColumns.get(index) : "ordinality",
                    Collections.emptyList());
        }
        String firstFunction = unqualifiedName(cleanIdentifier(ctx.rowsFromItem(0).functionName().getText()));
        registerFunctionRelation(alias == null ? firstFunction : alias, columns,
                columns.size() == index + (ctx.ORDINALITY() == null ? 0 : 1));
        return null;
    }

    private List<String> rowsFromColumnNames(PostgreSqlParser.RowsFromItemContext item, String scalarAlias) {
        List<String> names = new ArrayList<>();
        for (PostgreSqlParser.RecordColumnContext column : item.recordColumn()) {
            names.add(cleanIdentifier(column.identifier()));
        }
        if (!names.isEmpty()) {
            return names;
        }

        String function = postgresBuiltinName(item.functionName());
        names.addAll(jsonFunctionColumnNames(function));
        if ("unnest".equals(function) && item.functionArguments() != null
                && item.functionArguments().callArgument().size() == 1) {
            names.add(scalarAlias == null ? function : scalarAlias);
        }
        if (item.identifierList() != null) {
            for (int i = 0; i < Math.min(names.size(), item.identifierList().identifier().size()); i++) {
                names.set(i, cleanIdentifier(item.identifierList().identifier(i)));
            }
        }
        return names;
    }

    private static String postgresBuiltinName(PostgreSqlParser.FunctionNameContext ctx) {
        String qualifiedName = ctx.getText().toLowerCase(Locale.ROOT);
        return qualifiedName.startsWith("pg_catalog.")
                ? qualifiedName.substring("pg_catalog.".length())
                : qualifiedName;
    }

    private static List<String> jsonFunctionColumnNames(String function) {
        switch (function) {
            case "json_array_elements":
            case "json_array_elements_text":
            case "jsonb_array_elements":
            case "jsonb_array_elements_text":
                return Collections.singletonList("value");
            case "json_each":
            case "json_each_text":
            case "jsonb_each":
            case "jsonb_each_text":
                return Arrays.asList("key", "value");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public Void visitValuesQuery(PostgreSqlParser.ValuesQueryContext ctx) {
        Map<String, List<ColumnRef>> columns = valueColumns(ctx.valuesClause(), Collections.emptyList());
        List<ColumnLineage> lineage = new ArrayList<>();
        for (Map.Entry<String, List<ColumnRef>> column : columns.entrySet()) {
            lineage.add(LineageModelUtils.columnLineage(null, column.getKey(), column.getValue(), null));
        }
        result.setColumnLineage(lineage);
        return null;
    }

    @Override
    public Void visitValuesTable(PostgreSqlParser.ValuesTableContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String name = alias == null ? "$values" + derivedColumnLineage.size() : alias;
        Map<String, List<ColumnRef>> columns = valueColumns(ctx.valuesClause(), tableAliasColumnNames(ctx.tableAlias()));
        derivedColumnLineage.put(name.toLowerCase(Locale.ROOT), columns);
        knownColumnRelations.add(name.toLowerCase(Locale.ROOT));
        addDerivedReference(name, null);
        return null;
    }

    private Map<String, List<ColumnRef>> valueColumns(List<PostgreSqlParser.ValuesClauseContext> rows,
                                                      List<String> names) {
        int width = rows.get(0).expressionList().expression().size();
        List<List<ColumnRef>> refs = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            refs.add(Collections.emptyList());
        }
        for (PostgreSqlParser.ValuesClauseContext row : rows) {
            List<PostgreSqlParser.ExpressionContext> expressions = row.expressionList().expression();
            for (int i = 0; i < Math.min(width, expressions.size()); i++) {
                PostgreSqlParser.ExpressionContext expression = expressions.get(i);
                collectSubqueryInputs(expression);
                List<ColumnRef> sources = columnRefs(sourceColumns(expression));
                if (sources != null) {
                    refs.set(i, LineageModelUtils.mergeColumnRefs(refs.get(i), sources));
                }
            }
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (int i = 0; i < width; i++) {
            String name = i < names.size() ? names.get(i) : "column" + (i + 1);
            columns.put(name, refs.get(i));
        }
        return columns;
    }

    private void addJoinPredicateUsages(ParseTree predicate, Map<String, List<ColumnRef>> joinKeys) {
        List<SourceColumn> sources = sourceColumns(predicate);
        if (joinKeys.isEmpty()) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sources);
            return;
        }
        List<SourceColumn> resolved = new ArrayList<>();
        for (SourceColumn source : sources) {
            List<ColumnRef> key = source.resolvedRef == null && source.qualifier == null
                    ? joinKeys.get(source.name) : null;
            if (key == null) {
                resolved.add(source);
            } else {
                for (ColumnRef ref : key) {
                    resolved.add(SourceColumn.resolved(ref));
                }
            }
        }
        addColumnUsages(ColumnUsageType.JOIN_ON, resolved);
    }

    @Override
    public Void visitRelation(PostgreSqlParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        Map<String, List<ColumnRef>> keys = new LinkedHashMap<>(nestedJoinKeys(ctx.relationPrimary()));
        for (PostgreSqlParser.JoinRelationContext join : ctx.joinRelation()) {
            int rightStart = visibleRelations.size();
            visit(join.relationPrimary());
            PostgreSqlParser.JoinCriteriaContext criteria = join.joinCriteria();
            if (join.NATURAL() != null || (criteria != null && criteria.identifierList() != null)) {
                Map<String, List<ColumnRef>> rightKeys = nestedJoinKeys(join.relationPrimary());
                List<VisibleRelation> left = visibleRelations.subList(relationStart, rightStart);
                List<VisibleRelation> right = visibleRelations.subList(rightStart, visibleRelations.size());
                List<String> columns = criteria == null
                        ? naturalJoinColumns(left, right, keys, rightKeys)
                        : identifierNames(criteria.identifierList());
                Map<String, List<ColumnRef>> merged = collectMergedJoinKeys(
                        joinDirection(join), columns, left, right, keys, rightKeys);
                if (criteria != null) {
                    registerUsingAlias(criteria, merged);
                }
                keys.putAll(rightKeys);
                keys.putAll(merged);
            } else {
                keys.putAll(nestedJoinKeys(join.relationPrimary()));
                if (criteria != null) {
                    addJoinPredicateUsages(criteria.expression(), keys);
                }
            }
        }
        if (!keys.isEmpty()) {
            relationJoinKeys.put(ctx, keys);
        }
        return null;
    }

    @Override
    public Void visitRelationList(PostgreSqlParser.RelationListContext ctx) {
        visitChildren(ctx);
        List<PostgreSqlParser.RelationContext> relations = ctx.relation();
        unqualifiedJoinKeys = relations.size() == 1
                ? relationJoinKeys.getOrDefault(relations.get(0), Collections.emptyMap())
                : Collections.emptyMap();
        return null;
    }

    private Map<String, List<ColumnRef>> nestedJoinKeys(PostgreSqlParser.RelationPrimaryContext ctx) {
        if (ctx instanceof PostgreSqlParser.AliasedRelationContext) {
            return relationJoinKeys.getOrDefault(
                    ((PostgreSqlParser.AliasedRelationContext) ctx).relation(), Collections.emptyMap());
        }
        return Collections.emptyMap();
    }

    private List<String> naturalJoinColumns(List<VisibleRelation> left, List<VisibleRelation> right,
                                            Map<String, List<ColumnRef>> leftKeys,
                                            Map<String, List<ColumnRef>> rightKeys) {
        Set<String> columns = knownJoinOperandColumns(left, leftKeys);
        columns.retainAll(knownJoinOperandColumns(right, rightKeys));
        return new ArrayList<>(columns);
    }

    private Set<String> knownJoinOperandColumns(List<VisibleRelation> relations,
                                                Map<String, List<ColumnRef>> joinKeys) {
        Set<String> columns = new LinkedHashSet<>(joinKeys.keySet());
        for (VisibleRelation relation : relations) {
            if (!knownColumnRelations.contains(relation.derivedName)) {
                return new LinkedHashSet<>();
            }
            for (String column : derivedColumnLineage.get(relation.derivedName).keySet()) {
                if (!columns.add(column) && !joinKeys.containsKey(column)) {
                    return new LinkedHashSet<>();
                }
            }
        }
        return columns;
    }

    private static int joinDirection(PostgreSqlParser.JoinRelationContext join) {
        ParserRuleContext type = join.joinType();
        if (type == null) {
            type = join.naturalJoinType();
        }
        return type == null ? 0 : type.getStart().getType();
    }

    private Map<String, List<ColumnRef>> collectMergedJoinKeys(int direction,
                                                               List<String> columns,
                                                               List<VisibleRelation> left,
                                                               List<VisibleRelation> right,
                                                               Map<String, List<ColumnRef>> leftKeys,
                                                               Map<String, List<ColumnRef>> rightKeys) {
        Map<String, List<ColumnRef>> merged = new LinkedHashMap<>();
        for (String column : columns) {
            List<ColumnRef> leftRefs = joinKeySources(left, leftKeys, column);
            List<ColumnRef> rightRefs = joinKeySources(right, rightKeys, column);
            List<ColumnRef> predicateRefs = LineageModelUtils.mergeColumnRefs(leftRefs, rightRefs);
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.JOIN_ON, predicateRefs);
            List<ColumnRef> sources = predicateRefs;
            if (direction == PostgreSqlParser.LEFT) {
                sources = leftRefs;
            } else if (direction == PostgreSqlParser.RIGHT) {
                sources = rightRefs;
            }
            merged.put(column, sources);
        }
        return merged;
    }

    private List<ColumnRef> joinKeySources(List<VisibleRelation> relations,
                                          Map<String, List<ColumnRef>> joinedKeys, String column) {
        List<ColumnRef> refs = joinedKeys.get(column);
        if (refs != null) {
            return refs;
        }
        refs = new ArrayList<>();
        for (VisibleRelation relation : relations) {
            refs.addAll(usingColumnRefs(relation, column));
        }
        return refs;
    }

    @Override
    public Void visitArrayConstructorQuery(PostgreSqlParser.ArrayConstructorQueryContext ctx) {
        addSubqueryDependencies(ctx.query(), true);
        return null;
    }

    @Override
    public Void visitScalarSubquery(PostgreSqlParser.ScalarSubqueryContext ctx) {
        addSubqueryDependencies(ctx.query(), true);
        return null;
    }

    @Override
    public Void visitExistsExpr(PostgreSqlParser.ExistsExprContext ctx) {
        addSubqueryDependencies(ctx.query(), true);
        return null;
    }

    @Override
    public Void visitPredicate(PostgreSqlParser.PredicateContext ctx) {
        if (ctx.query() == null) {
            return visitChildren(ctx);
        }
        addSubqueryDependencies(ctx.query(), true);
        return null;
    }

    @Override
    public Void visitSelectClause(PostgreSqlParser.SelectClauseContext ctx) {
        int ordinal = 0;
        for (PostgreSqlParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof PostgreSqlParser.SelectExpressionContext) {
                PostgreSqlParser.SelectExpressionContext select = (PostgreSqlParser.SelectExpressionContext) item;
                Projection projection = projection(select, ordinal);
                if (projection == null && ctx == positionalSelect) {
                    PostgreSqlParser.ExpressionContext expression = select.expression();
                    projection = new Projection(sourceColumns(expression), defaultSetColumnName(expression),
                            expression.getText(), ordinal);
                }
                if (projection != null) {
                    projections.add(projection);
                } else {
                    unnamedSelectExpressions.put(ordinal, select.expression());
                }
                ordinal++;
            } else {
                int width = addKnownStarProjections(item, ordinal);
                if (width == 0) {
                    firstUnknownStarOrdinal = Math.min(firstUnknownStarOrdinal, ordinal);
                }
                ordinal += Math.max(1, width);
            }
        }
        selectedColumnCount = firstUnknownStarOrdinal == Integer.MAX_VALUE ? ordinal : -1;
        return visitChildren(ctx);
    }

    private int addKnownStarProjections(PostgreSqlParser.SelectItemContext item, int ordinal) {
        if (item instanceof PostgreSqlParser.SelectQualifiedStarContext) {
            PostgreSqlParser.SelectQualifiedStarContext star = (PostgreSqlParser.SelectQualifiedStarContext) item;
            List<PostgreSqlParser.IdentifierContext> parts = star.qualifiedName().identifier();
            String qualifier = cleanIdentifier(parts.get(parts.size() - 1)).toLowerCase(Locale.ROOT);
            String relation = derivedAliases.get(qualifier);
            return knownColumnRelations.contains(relation)
                    ? addKnownRelationProjections(relation, qualifier, item.getText(), ordinal, Collections.emptySet())
                    : 0;
        }
        if (visibleRelations.stream().anyMatch(relation -> !knownColumnRelations.contains(relation.derivedName))) {
            return 0;
        }
        Set<String> mergedKeys = unqualifiedJoinKeys.keySet();
        if (!relationJoinKeys.isEmpty()
                && (visibleRelations.size() != 2 || relationJoinKeys.size() != 1 || mergedKeys.isEmpty())) {
            return 0;
        }
        int width = 0;
        for (String key : mergedKeys) {
            projections.add(new Projection(Collections.singletonList(new SourceColumn(null, key)),
                    key, item.getText(), ordinal + width++));
        }
        for (VisibleRelation relation : visibleRelations) {
            width += addKnownRelationProjections(relation.derivedName, relation.derivedName, item.getText(),
                    ordinal + width, mergedKeys);
        }
        return width;
    }

    private int addKnownRelationProjections(String relation, String qualifier, String expression, int ordinal,
                                            Set<String> excludedColumns) {
        int index = ordinal;
        for (String column : derivedColumnLineage.get(relation).keySet()) {
            if (!excludedColumns.contains(column)) {
                projections.add(new Projection(Collections.singletonList(new SourceColumn(qualifier, column)),
                        column, expression, index++));
            }
        }
        return index - ordinal;
    }

    @Override
    public Void visitSelectIntoClause(PostgreSqlParser.SelectIntoClauseContext ctx) {
        result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
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
        for (PostgreSqlParser.GroupByItemContext item : ctx.groupByItem()) {
            addGroupingColumnUsages(item);
        }
        return visitChildren(ctx);
    }

    private void addGroupingColumnUsages(PostgreSqlParser.GroupByItemContext item) {
        if (item.expression() != null) {
            addGroupingExpressionUsage(item.expression());
        } else if (item.expressionList() != null) {
            for (PostgreSqlParser.ExpressionContext expression : item.expressionList().expression()) {
                addGroupingExpressionUsage(expression);
            }
        } else {
            for (PostgreSqlParser.GroupByItemContext element : item.groupByItem()) {
                addGroupingColumnUsages(element);
            }
        }
    }

    private void addGroupingExpressionUsage(PostgreSqlParser.ExpressionContext expression) {
        expression = unwrapParenthesizedExpression(expression);
        int ordinal = outputOrdinal(expression.getText());
        if (ordinal >= 0) {
            addOrdinalColumnUsages(ColumnUsageType.GROUP_BY, ordinal);
            return;
        }
        List<SourceColumn> sources = sourceColumns(expression);
        if (primaryExpression(expression) instanceof PostgreSqlParser.ColumnReferenceContext && !sources.isEmpty()) {
            SourceColumn column = sources.get(0);
            List<ColumnRef> input = unqualifiedJoinKeys.get(column.name);
            if (input == null) {
                input = unqualifiedDerivedColumnRefs(column);
            }
            if (input != null) {
                LineageModelUtils.addColumnUsages(result, ColumnUsageType.GROUP_BY, input);
            } else {
                addColumnUsages(ColumnUsageType.GROUP_BY, sources);
            }
        } else {
            addInputColumnUsages(ColumnUsageType.GROUP_BY, sources);
        }
    }

    private List<ColumnRef> unqualifiedDerivedColumnRefs(SourceColumn column) {
        if (visibleRelations.size() <= 1) {
            return derivedColumnRefs(column);
        }
        List<ColumnRef> matching = null;
        for (VisibleRelation relation : visibleRelations) {
            if (!knownColumnRelations.contains(relation.derivedName)) {
                return null;
            }
            List<ColumnRef> refs = derivedColumnLineage.get(relation.derivedName).get(column.name);
            if (refs != null) {
                if (matching != null) {
                    return null;
                }
                matching = refs;
            }
        }
        return matching;
    }

    @Override
    public Void visitHavingClause(PostgreSqlParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuerySpecification(PostgreSqlParser.QuerySpecificationContext ctx) {
        Map<String, Set<SourceColumn>> previousWindows = new LinkedHashMap<>(namedWindowSources);
        namedWindowSources.clear();
        if (ctx.windowClause() != null) {
            for (PostgreSqlParser.NamedWindowContext window : ctx.windowClause().namedWindow()) {
                Set<SourceColumn> columns = new LinkedHashSet<>();
                collectSourceColumns(window.windowSpec(), columns);
                namedWindowSources.put(cleanIdentifier(window.identifier()).toLowerCase(Locale.ROOT), columns);
            }
        }
        try {
            if (ctx.fromClause() != null) {
                boolean visitFromFirst = hasSelectStar(ctx.selectClause())
                        || containsArrayConstructorQuery(ctx.selectClause());
                if (visitFromFirst || containsQuery(ctx.selectClause())) {
                    return visitWithSelectScope(ctx, !visitFromFirst);
                }
            }
            return visitChildren(ctx);
        } finally {
            namedWindowSources.clear();
            namedWindowSources.putAll(previousWindows);
        }
    }

    private Void visitWithSelectScope(PostgreSqlParser.QuerySpecificationContext ctx, boolean preserveInputOrder) {
        Set<TableRef> previousInputs = new LinkedHashSet<>(inputTables);
        visit(ctx.fromClause());
        Set<TableRef> fromInputs = new LinkedHashSet<>(inputTables);
        if (preserveInputOrder) {
            inputTables.retainAll(previousInputs);
        }
        for (ParseTree child : ctx.children) {
            if (child != ctx.fromClause()) {
                visit(child);
            } else if (preserveInputOrder) {
                inputTables.addAll(fromInputs);
                result.setInputTables(new ArrayList<>(inputTables));
            }
        }
        return null;
    }

    private static boolean containsQuery(ParseTree tree) {
        if (tree instanceof PostgreSqlParser.QueryContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsQuery(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSelectStar(PostgreSqlParser.SelectClauseContext select) {
        return select.selectItemList().selectItem().stream()
                .anyMatch(item -> !(item instanceof PostgreSqlParser.SelectExpressionContext));
    }

    private static boolean containsArrayConstructorQuery(ParseTree tree) {
        if (tree instanceof PostgreSqlParser.ArrayConstructorQueryContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsArrayConstructorQuery(tree.getChild(i))) {
                return true;
            }
        }
        return false;
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
    public Void visitAggregateOrderBy(PostgreSqlParser.AggregateOrderByContext ctx) {
        for (PostgreSqlParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWithinGroupClause(PostgreSqlParser.WithinGroupClauseContext ctx) {
        for (PostgreSqlParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitFilterClause(PostgreSqlParser.FilterClauseContext ctx) {
        pendingColumnUsages.add(new PendingColumnUsage(
                ColumnUsageType.WHERE,
                sourceColumns(ctx.expression())));
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(PostgreSqlParser.QueryOrganizationContext ctx) {
        PostgreSqlParser.QueryContext query = (PostgreSqlParser.QueryContext) ctx.getParent();
        PostgreSqlParser.QueryTermContext term = unwrappedQueryTerm(query.queryTerm());
        PostgreSqlParser.QueryPrimaryContext primary = queryPrimary(term);
        boolean outputOrder = term instanceof PostgreSqlParser.SetOperationContext
                || primary instanceof PostgreSqlParser.ValuesQueryContext;
        boolean tableOrder = primary instanceof PostgreSqlParser.TableQueryContext;
        for (PostgreSqlParser.SortItemContext sortItem : ctx.sortItem()) {
            PostgreSqlParser.ExpressionContext expression = unwrapParenthesizedExpression(sortItem.expression());
            String sortKey = expression.getText();
            int ordinal = outputOrdinal(sortKey);
            if ((outputOrder || tableOrder) && ordinal >= 0) {
                List<ColumnLineage> lineages = result.getColumnLineage();
                if (ordinal < lineages.size() && lineages.size() == knownOutputWidth(term)) {
                    LineageModelUtils.addColumnUsages(result, ColumnUsageType.ORDER_BY, lineages.get(ordinal).getSources());
                }
            } else if (outputOrder) {
                LineageModelUtils.addSetOrderByUsage(result, cleanIdentifier(sortKey));
            } else if (ordinal >= 0) {
                addOrdinalColumnUsages(ColumnUsageType.ORDER_BY, ordinal);
            } else if (primaryExpression(expression) instanceof PostgreSqlParser.ColumnReferenceContext) {
                addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(expression));
            } else {
                addInputColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(expression));
            }
        }
        return visitChildren(ctx);
    }

    private int knownOutputWidth(PostgreSqlParser.QueryTermContext term) {
        PostgreSqlParser.QueryTermContext unwrapped = unwrappedQueryTerm(term);
        if (unwrapped instanceof PostgreSqlParser.SetOperationContext) {
            return setColumnCount;
        }
        PostgreSqlParser.QueryPrimaryContext primary = queryPrimary(unwrapped);
        if (primary instanceof PostgreSqlParser.TableQueryContext) {
            PostgreSqlParser.InheritanceRelationContext relation =
                    ((PostgreSqlParser.TableQueryContext) primary).inheritanceRelation();
            TableRef table = tableRef(relation.multipartIdentifier());
            String name = table.getName().toLowerCase(Locale.ROOT);
            if (relation.ONLY() == null && relation.STAR() == null
                    && isCteReference(table) && knownColumnRelations.contains(name)) {
                return derivedColumnLineage.get(name).size();
            }
            return -1;
        }
        if (primary instanceof PostgreSqlParser.QueryPrimaryDefaultContext) {
            return selectedColumnCount;
        }
        if (primary instanceof PostgreSqlParser.ValuesQueryContext) {
            return ((PostgreSqlParser.ValuesQueryContext) primary).valuesClause(0).expressionList().expression().size();
        }
        return -1;
    }

    private static PostgreSqlParser.QueryPrimaryContext queryPrimary(PostgreSqlParser.QueryTermContext term) {
        return term instanceof PostgreSqlParser.QueryTermDefaultContext
                ? ((PostgreSqlParser.QueryTermDefaultContext) term).queryPrimary() : null;
    }

    private static PostgreSqlParser.QueryTermContext unwrappedQueryTerm(PostgreSqlParser.QueryTermContext term) {
        PostgreSqlParser.QueryPrimaryContext primary = queryPrimary(term);
        while (primary instanceof PostgreSqlParser.SubqueryPrimaryContext) {
            term = ((PostgreSqlParser.SubqueryPrimaryContext) primary).query().queryTerm();
            primary = queryPrimary(term);
        }
        return term;
    }

    private void addOrdinalColumnUsages(ColumnUsageType type, int ordinal) {
        if (ordinal >= firstUnknownStarOrdinal) {
            return;
        }
        for (Projection projection : projections) {
            if (projection.ordinal == ordinal) {
                addInputColumnUsages(type, projection.sourceColumns);
                return;
            }
        }
        PostgreSqlParser.ExpressionContext expression = unnamedSelectExpressions.get(ordinal);
        if (expression != null) {
            addInputColumnUsages(type, sourceColumns(expression));
        }
    }

    private void addInputColumnUsages(ColumnUsageType type, List<SourceColumn> sources) {
        List<ColumnRef> refs = columnRefs(sources);
        if (refs != null) {
            LineageModelUtils.addColumnUsages(result, type, refs);
        }
    }

    private static PostgreSqlParser.ExpressionContext unwrapParenthesizedExpression(
            PostgreSqlParser.ExpressionContext expression) {
        PostgreSqlParser.PrimaryExpressionContext primary = primaryExpression(expression);
        while (primary instanceof PostgreSqlParser.ParenthesizedExpressionContext) {
            expression = ((PostgreSqlParser.ParenthesizedExpressionContext) primary).expression();
            primary = primaryExpression(expression);
        }
        return expression;
    }

    private static PostgreSqlParser.PrimaryExpressionContext primaryExpression(
            PostgreSqlParser.ExpressionContext expression) {
        if (!(expression.booleanExpression() instanceof PostgreSqlParser.PredicatedExprContext)) {
            return null;
        }
        PostgreSqlParser.PredicatedExprContext value =
                (PostgreSqlParser.PredicatedExprContext) expression.booleanExpression();
        if (value.predicate() != null
                || !(value.valueExpression() instanceof PostgreSqlParser.ValueExpressionDefaultContext)) {
            return null;
        }
        return ((PostgreSqlParser.ValueExpressionDefaultContext) value.valueExpression()).primaryExpression();
    }

    private static int outputOrdinal(String expression) {
        try {
            return Integer.parseInt(expression) - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
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
        String baseName = table.getName().toLowerCase(Locale.ROOT);
        String alias = tableAlias(aliasCtx);
        if (alias != null || !visibleRelation) {
            tableAliases.putIfAbsent(baseName, table);
        }
        if (visibleRelation) {
            String binding = alias == null ? baseName : alias.toLowerCase(Locale.ROOT);
            tableAliases.put(binding, table);
            derivedAliases.remove(binding);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        refreshColumnLineage();
    }

    private void bindQualifiedTable(PostgreSqlParser.MultipartIdentifierContext name, TableRef table) {
        List<String> parts = new ArrayList<>();
        for (PostgreSqlParser.IdentifierContext part : name.identifier()) {
            parts.add(part.getText());
        }
        int size = parts.size();
        if (size >= 2) {
            tableAliases.put(qualifiedTableKey(parts.subList(size - 2, size)), table);
        }
        if (size >= 3) {
            tableAliases.put(qualifiedTableKey(parts.subList(size - 3, size)), table);
        }
    }

    private static String qualifiedTableKey(List<String> parts) {
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            // NUL separates components because quoted SQL identifiers can contain dots.
            key.append('\0').append(part.charAt(0) == '"' || part.charAt(0) == '`'
                    ? cleanIdentifier(part) : part.toLowerCase(Locale.ROOT));
        }
        return key.toString();
    }

    private TableRef tableForQualifier(String qualifier) {
        return tableAliases.get(qualifier.startsWith("\0") ? qualifier : qualifier.toLowerCase(Locale.ROOT));
    }

    private void addCteReference(String name, PostgreSqlParser.TableAliasContext alias) {
        String relation = name.toLowerCase(Locale.ROOT);
        List<String> columnNames = tableAliasColumnNames(alias);
        if (columnNames.isEmpty() || !knownColumnRelations.contains(relation)) {
            addDerivedReference(name, alias);
            return;
        }
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(relation);
        Map<String, List<ColumnRef>> renamed = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, List<ColumnRef>> entry : columns.entrySet()) {
            String columnName = index < columnNames.size() ? columnNames.get(index) : entry.getKey();
            renamed.put(columnName, entry.getValue());
            index++;
        }
        String aliasRelation = "$cte_alias_" + derivedColumnLineage.size();
        derivedColumnLineage.put(aliasRelation, renamed);
        if (renamed.size() == columns.size()) {
            knownColumnRelations.add(aliasRelation);
        }
        addDerivedReference(aliasRelation, alias);
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

    private void registerGeneratedRelation(PostgreSqlParser.TableAliasContext aliasCtx,
                                           List<SourceColumn> sources,
                                           boolean withOrdinality) {
        registerGeneratedRelation(tableAlias(aliasCtx), tableAliasColumnNames(aliasCtx), sources, withOrdinality, false);
    }

    private void registerGeneratedRelation(String alias, List<String> aliasColumns,
                                           List<SourceColumn> sources, boolean withOrdinality, boolean completeShape) {
        if (alias == null) {
            return;
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        List<ColumnRef> refs = columnUsageRefs(sources);
        for (int i = 0; i < aliasColumns.size(); i++) {
            boolean ordinalityColumn = withOrdinality && i == aliasColumns.size() - 1;
            columns.put(aliasColumns.get(i), ordinalityColumn || refs == null ? new ArrayList<>() : new ArrayList<>(refs));
        }
        registerFunctionRelation(alias, columns, completeShape && columns.size() == aliasColumns.size());
    }

    private static List<String> tableAliasColumnNames(PostgreSqlParser.TableAliasContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx != null && ctx.identifierList() != null) {
            names.addAll(identifierNames(ctx.identifierList()));
        }
        return names;
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
        relationVisitor.tableAliases.putAll(tableAliases);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.knownColumnRelations.addAll(knownColumnRelations);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        relationVisitor.derivedReferences.addAll(derivedReferences);
        if (!columnAliases.isEmpty()) {
            relationVisitor.positionalSelect = fullyExplicitSelect(query.queryTerm());
        }
        relationVisitor.visit(query);
        relationVisitor.finalizeResult();

        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        List<ColumnLineage> lineages = relationResult.getColumnLineage();
        for (int i = 0; i < lineages.size(); i++) {
            ColumnLineage lineage = lineages.get(i);
            String columnName = i < columnAliases.size() ? columnAliases.get(i) : lineage.getTarget().getName();
            columns.put(columnName, lineage.getSources());
        }
        derivedColumnLineage.put(name, columns);
        int width = relationVisitor.knownOutputWidth(query.queryTerm());
        if (width > 0 && lineages.size() == width && columns.size() == width) {
            knownColumnRelations.add(name);
        } else {
            knownColumnRelations.remove(name);
        }
        for (TableRef table : relationResult.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relationResult);
    }

    private void registerDataModifyingCte(
            String name,
            PostgreSqlParser.CteBodyContext body,
            List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        PostgreSqlLineageVisitor relationVisitor = new PostgreSqlLineageVisitor(relationResult);
        relationVisitor.cteNames.addAll(cteNames);
        relationVisitor.tableAliases.putAll(tableAliases);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.knownColumnRelations.addAll(knownColumnRelations);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        relationVisitor.derivedReferences.addAll(derivedReferences);
        relationVisitor.returningSourcesForCte = true;
        relationVisitor.visit(body);
        relationVisitor.finalizeResult();

        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        int returnIndex = 0;
        for (ColumnLineage lineage : relationResult.getColumnLineage()) {
            if (lineage.getTarget() == null || lineage.getTarget().getTable() != null) {
                continue;
            }
            String columnName = returnIndex < columnAliases.size()
                    ? columnAliases.get(returnIndex)
                    : lineage.getTarget().getName();
            columns.put(columnName, lineage.getSources());
            returnIndex++;
        }
        derivedColumnLineage.put(name, columns);
        for (TableRef table : relationResult.getInputTables()) {
            addInputTable(table, false);
        }
        outputTables.addAll(relationResult.getOutputTables());
        LineageModelUtils.mergeColumnUsages(result, relationResult);
    }

    private PostgreSqlLineageVisitor analyzeSetOperand(PostgreSqlParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.knownColumnRelations.addAll(knownColumnRelations);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.positionalSelect = fullyExplicitSelect(queryTerm);
        queryVisitor.visit(queryTerm);
        queryVisitor.finalizeResult();
        return queryVisitor;
    }

    private static PostgreSqlParser.SelectClauseContext fullyExplicitSelect(PostgreSqlParser.QueryTermContext term) {
        PostgreSqlParser.QueryPrimaryContext primary = queryPrimary(unwrappedQueryTerm(term));
        if (!(primary instanceof PostgreSqlParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        PostgreSqlParser.SelectClauseContext select =
                ((PostgreSqlParser.QueryPrimaryDefaultContext) primary).querySpecification().selectClause();
        return hasSelectStar(select) ? null : select;
    }

    private void addColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = columnUsageRefs(sourceColumns, type == ColumnUsageType.ORDER_BY);
        if (refs != null) {
            LineageModelUtils.addColumnUsages(result, type, refs);
        }
    }

    private List<ColumnRef> columnUsageRefs(List<SourceColumn> sourceColumns) {
        return columnUsageRefs(sourceColumns, false);
    }

    private List<ColumnRef> columnUsageRefs(List<SourceColumn> sourceColumns, boolean preferOutputAlias) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            if (!preferOutputAlias && sourceColumn.resolvedRef == null && sourceColumn.qualifier == null) {
                List<ColumnRef> joinRefs = unqualifiedJoinKeys.get(sourceColumn.name);
                if (joinRefs != null) {
                    refs.addAll(joinRefs);
                    continue;
                }
            }
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
            addUsingColumnUsages(ctx, relationStart);
        }
    }

    private void addUsingColumnUsages(PostgreSqlParser.JoinCriteriaContext ctx, int relationStart) {
        List<VisibleRelation> relations = visibleRelationsSince(relationStart);
        if (ctx.identifierList() == null || relations.size() < 2) {
            return;
        }
        Map<String, List<ColumnRef>> joinColumns = new LinkedHashMap<>();
        for (String columnName : identifierNames(ctx.identifierList())) {
            List<ColumnRef> refs = new ArrayList<>();
            for (VisibleRelation relation : relations) {
                refs.addAll(usingColumnRefs(relation, columnName));
            }
            joinColumns.put(columnName, refs);
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.JOIN_ON, refs);
        }
        registerUsingAlias(ctx, joinColumns);
    }

    private void registerUsingAlias(PostgreSqlParser.JoinCriteriaContext ctx,
                                    Map<String, List<ColumnRef>> joinColumns) {
        if (ctx.usingAlias != null) {
            String alias = cleanIdentifier(ctx.usingAlias.getText()).toLowerCase(Locale.ROOT);
            String name = "$join_using_" + derivedColumnLineage.size();
            derivedColumnLineage.put(name, joinColumns);
            derivedAliases.put(alias, name);
            knownColumnRelations.add(name);
        }
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
        StatementType type = result.getStatementType();
        boolean projectionWritesTable = type == StatementType.CREATE_TABLE_AS_SELECT
                || type == StatementType.CREATE_VIEW;
        refreshColumnLineage(projectionWritesTable && outputTables.size() == 1
                ? outputTables.iterator().next()
                : null);
    }

    private void refreshColumnLineage(TableRef targetTable) {
        if (suppressColumnLineage || projections.isEmpty()) {
            return;
        }
        List<ColumnLineage> columnLineage = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = projections.get(i);
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null && positionalSelect == null) {
                continue;
            }
            if (sources == null) {
                sources = Collections.emptyList();
            }
            String targetColumn = targetColumn(projection, projection.ordinal);
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
        if (!projections.isEmpty()) {
            refreshColumnLineage(targetTable);
            return;
        }
        result.setColumnLineage(LineageModelUtils.retargetColumnLineage(
                result.getColumnLineage(),
                targetTable,
                insertTargetColumns));
    }

    private String targetColumn(Projection projection, int index) {
        if (index >= 0 && index < insertTargetColumns.size()) {
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
        TableRef defaultTable = visibleRelations.size() == 1 ? visibleRelations.get(0).table : null;
        if (defaultTable == null && visibleRelationCount <= 1 && inputTables.size() == 1) {
            defaultTable = inputTables.iterator().next();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn rawSourceColumn : sourceColumns) {
            if (rawSourceColumn.resolvedRef != null) {
                refs.add(rawSourceColumn.resolvedRef);
                continue;
            }
            SourceColumn sourceColumn = scopedSourceColumn(rawSourceColumn);
            List<ColumnRef> joinRefs = sourceColumn.qualifier == null
                    ? unqualifiedJoinKeys.get(sourceColumn.name) : null;
            if (joinRefs != null) {
                refs.addAll(joinRefs);
                continue;
            }
            List<ColumnRef> derivedRefs = sourceColumn.qualifier == null
                    ? unqualifiedDerivedColumnRefs(sourceColumn)
                    : derivedColumnRefs(sourceColumn);
            if (derivedRefs != null) {
                refs.addAll(derivedRefs);
                continue;
            }
            TableRef table = defaultTable;
            if (sourceColumn.qualifier != null) {
                table = tableForQualifier(sourceColumn.qualifier);
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
        SourceColumn qualified = schemaQualifiedColumn(sourceColumn);
        if (qualified != null) {
            return qualified;
        }
        int dot = sourceColumn.name.indexOf('.');
        String possibleQualifier = sourceColumn.name.substring(0, dot).toLowerCase(Locale.ROOT);
        if (tableAliases.containsKey(possibleQualifier) || derivedAliases.containsKey(possibleQualifier)) {
            return new SourceColumn(sourceColumn.name.substring(0, dot), sourceColumn.name.substring(dot + 1));
        }
        int lastDot = sourceColumn.name.lastIndexOf('.');
        if (lastDot > dot) {
            int previousDot = sourceColumn.name.lastIndexOf('.', lastDot - 1);
            String tableQualifier = sourceColumn.name.substring(previousDot + 1, lastDot);
            String tableKey = tableQualifier.toLowerCase(Locale.ROOT);
            if (tableAliases.containsKey(tableKey) || derivedAliases.containsKey(tableKey)) {
                return new SourceColumn(tableQualifier, sourceColumn.name.substring(lastDot + 1));
            }
        }
        return sourceColumn;
    }

    private SourceColumn schemaQualifiedColumn(SourceColumn sourceColumn) {
        List<String> path = sourceColumn.path;
        if (path.size() < 3) {
            return null;
        }
        String qualifier = qualifiedTableKey(path.subList(0, path.size() - 1));
        return tableAliases.containsKey(qualifier) ? new SourceColumn(qualifier, cleanIdentifier(path.get(path.size() - 1))) : null;
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
        List<ColumnRef> refs = columns.get(sourceColumn.name);
        if (refs == null) {
            int dot = sourceColumn.name.indexOf('.');
            if (dot > 0) {
                refs = columns.get(sourceColumn.name.substring(0, dot));
            }
        }
        return refs == null && EXCLUDED_RELATION.equals(derivedName) ? Collections.emptyList() : refs;
    }

    private boolean isCteReference(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && cteNames.contains(table.getName().toLowerCase(Locale.ROOT));
    }

    private List<ColumnLineage> readAssignments(PostgreSqlParser.AssignmentListContext ctx, TableRef defaultTarget,
                                                 boolean qualifiedTargets) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (PostgreSqlParser.AssignmentContext assignment : ctx.assignment()) {
            if (assignment.query() != null) {
                lineages.addAll(readRowQueryAssignment(assignment, defaultTarget));
            } else if (assignment.expressionList() != null) {
                lineages.addAll(readRowAssignments(assignment, defaultTarget));
            } else {
                List<ColumnRef> sources = resolveSources(sourceColumns(assignment.expression()));
                lineages.add(assignedColumnLineage(assignment.assignmentTarget(0), defaultTarget,
                        sources, null, qualifiedTargets));
            }
        }
        return lineages;
    }

    private ColumnLineage assignedColumnLineage(PostgreSqlParser.AssignmentTargetContext target,
                                                  TableRef table, List<ColumnRef> valueSources,
                                                  String expression, boolean qualifiedTarget) {
        List<String> parts = identifierParts(target.multipartIdentifier());
        int columnIndex = 0;
        if (qualifiedTarget && parts.size() >= 2) {
            TableRef resolved = tableAliases.get(parts.get(parts.size() - 2).toLowerCase(Locale.ROOT));
            if (resolved != null) {
                table = resolved;
                columnIndex = parts.size() - 1;
            }
        }
        String name = parts.get(columnIndex);
        List<ColumnRef> sources = valueSources == null ? Collections.emptyList() : valueSources;
        if (parts.size() > columnIndex + 1 || !target.assignmentSubscript().isEmpty()) {
            List<ColumnRef> selected = Collections.singletonList(new ColumnRef(table, name));
            for (PostgreSqlParser.AssignmentSubscriptContext selector : target.assignmentSubscript()) {
                List<ColumnRef> selectorSources = resolveSources(sourceColumns(selector));
                if (selectorSources != null) {
                    selected = LineageModelUtils.mergeColumnRefs(selected, selectorSources);
                }
            }
            sources = LineageModelUtils.mergeColumnRefs(selected, sources);
        }
        return LineageModelUtils.columnLineage(table, name, sources, expression);
    }

    private List<ColumnLineage> readRowQueryAssignment(
            PostgreSqlParser.AssignmentContext assignment,
            TableRef defaultTarget) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.knownColumnRelations.addAll(knownColumnRelations);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(assignment.query());
        queryVisitor.collectTopLevelQueryProjections(assignment.query());
        queryVisitor.finalizeResult();
        inputTables.addAll(queryResult.getInputTables());
        LineageModelUtils.mergeColumnUsages(result, queryResult);
        List<PostgreSqlParser.AssignmentTargetContext> targets = assignment.assignmentTarget();
        PostgreSqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(assignment.query());
        List<ColumnLineage> lineages = new ArrayList<>();
        if (specification == null || specification.selectClause() == null) {
            List<ColumnLineage> sources = queryResult.getColumnLineage();
            int width = queryVisitor.knownOutputWidth(assignment.query().queryTerm());
            if (width == targets.size() && sources.size() == width) {
                for (int i = 0; i < width; i++) {
                    lineages.add(assignedColumnLineage(targets.get(i), defaultTarget,
                            sources.get(i).getSources(), sources.get(i).getExpression(), false));
                }
            }
            return lineages;
        }
        if (hasSelectStar(specification.selectClause())) {
            int width = Math.min(targets.size(), queryVisitor.firstUnknownStarOrdinal);
            for (Projection projection : queryVisitor.projections) {
                if (projection.ordinal < width) {
                    List<ColumnRef> sources = queryVisitor.columnRefs(projection);
                    if (sources != null) {
                        lineages.add(assignedColumnLineage(targets.get(projection.ordinal), defaultTarget,
                                sources, projection.expression, false));
                    }
                }
            }
            return lineages;
        }
        List<PostgreSqlParser.SelectItemContext> items =
                specification.selectClause().selectItemList().selectItem();
        int count = Math.min(targets.size(), items.size());
        for (int i = 0; i < count; i++) {
            PostgreSqlParser.SelectItemContext item = items.get(i);
            if (!(item instanceof PostgreSqlParser.SelectExpressionContext)) {
                continue;
            }
            PostgreSqlParser.ExpressionContext expression =
                    ((PostgreSqlParser.SelectExpressionContext) item).expression();
            List<ColumnRef> sources = queryVisitor.resolveSources(queryVisitor.sourceColumns(expression));
            if (sources == null) {
                continue;
            }
            lineages.add(assignedColumnLineage(targets.get(i), defaultTarget, sources,
                    expression.getText(), false));
        }
        return lineages;
    }

    private List<ColumnLineage> readRowAssignments(PostgreSqlParser.AssignmentContext assignment, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        List<PostgreSqlParser.AssignmentTargetContext> targets = assignment.assignmentTarget();
        List<PostgreSqlParser.ExpressionContext> expressions = assignment.expressionList().expression();
        int count = Math.min(targets.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            PostgreSqlParser.ExpressionContext expression = expressions.get(i);
            List<ColumnRef> sources = resolveSources(sourceColumns(expression));
            if (sources == null) {
                sources = new ArrayList<>();
            }
            lineages.add(assignedColumnLineage(targets.get(i), defaultTarget, sources,
                    expression.getText(), false));
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

    private List<ColumnLineage> readConflictAssignments(PostgreSqlParser.AssignmentListContext ctx,
                                                            TableRef defaultTarget,
                                                            Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (PostgreSqlParser.AssignmentContext assignment : ctx.assignment()) {
            if (assignment.query() != null) {
                lineages.addAll(readRowQueryAssignment(assignment, defaultTarget));
            } else if (assignment.expressionList() != null) {
                lineages.addAll(readConflictRowAssignments(assignment, defaultTarget, insertedSourcesByColumn));
            } else {
                List<ColumnRef> sources = resolveConflictSources(assignment.expression(), insertedSourcesByColumn);
                lineages.add(assignedColumnLineage(assignment.assignmentTarget(0), defaultTarget,
                        sources, assignment.expression().getText(), false));
            }
        }
        return lineages;
    }

    private List<ColumnLineage> readConflictRowAssignments(PostgreSqlParser.AssignmentContext assignment,
                                                               TableRef defaultTarget,
                                                               Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        List<ColumnLineage> lineages = new ArrayList<>();
        List<PostgreSqlParser.AssignmentTargetContext> targets = assignment.assignmentTarget();
        List<PostgreSqlParser.ExpressionContext> expressions = assignment.expressionList().expression();
        int count = Math.min(targets.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            PostgreSqlParser.ExpressionContext expression = expressions.get(i);
            List<ColumnRef> sources = resolveConflictSources(expression, insertedSourcesByColumn);
            if (sources == null) {
                sources = new ArrayList<>();
            }
            lineages.add(assignedColumnLineage(targets.get(i), defaultTarget, sources,
                    expression.getText(), false));
        }
        return lineages;
    }

    private List<ColumnRef> resolveConflictSources(PostgreSqlParser.ExpressionContext expression,
                                                Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        String valuesColumn = valuesFunctionColumn(expression);
        if (valuesColumn != null) {
            List<ColumnRef> sources = insertedSourcesByColumn.get(valuesColumn.toLowerCase(Locale.ROOT));
            return sources == null ? new ArrayList<ColumnRef>() : new ArrayList<>(sources);
        }
        return resolveSources(conflictSourceColumns(expression, insertedSourcesByColumn));
    }

    private List<SourceColumn> conflictSourceColumns(PostgreSqlParser.ExpressionContext expression,
                                                     Map<String, List<ColumnRef>> insertedSources) {
        List<SourceColumn> columns = new ArrayList<>();
        for (SourceColumn source : sourceColumns(expression)) {
            String name = source.name.toLowerCase(Locale.ROOT);
            if (source.resolvedRef == null && name.startsWith("excluded.")) {
                for (ColumnRef ref : insertedSources.getOrDefault(name.substring("excluded.".length()),
                        Collections.emptyList())) {
                    columns.add(SourceColumn.resolved(ref));
                }
            } else {
                columns.add(source);
            }
        }
        return columns;
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

    private List<ColumnLineage> readInsertValues(List<PostgreSqlParser.ValuesClauseContext> valueRows,
                                                 TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        int columnCount = insertTargetColumns.size();
        List<Set<ColumnRef>> sourcesByColumn = new ArrayList<>();
        List<String> expressionsByColumn = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            sourcesByColumn.add(new LinkedHashSet<>());
            expressionsByColumn.add(null);
        }
        for (PostgreSqlParser.ValuesClauseContext row : valueRows) {
            collectSubqueryInputs(row);
            List<PostgreSqlParser.ExpressionContext> expressions = row.expressionList().expression();
            int count = Math.min(columnCount, expressions.size());
            for (int i = 0; i < count; i++) {
                PostgreSqlParser.ExpressionContext expression = expressions.get(i);
                List<ColumnRef> sources = resolveSources(sourceColumns(expression));
                if (sources != null) {
                    sourcesByColumn.get(i).addAll(sources);
                }
                if (expressionsByColumn.get(i) == null) {
                    expressionsByColumn.set(i, expression.getText());
                }
            }
        }
        for (int i = 0; i < columnCount; i++) {
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(target, insertTargetColumns.get(i)));
            lineage.setSources(new ArrayList<>(sourcesByColumn.get(i)));
            lineage.setExpression(expressionsByColumn.get(i));
            lineages.add(lineage);
        }
        return lineages;
    }

    private static List<PostgreSqlParser.ValuesClauseContext> valuesQueryRows(PostgreSqlParser.QueryContext query) {
        if (query == null || !(query.queryTerm() instanceof PostgreSqlParser.QueryTermDefaultContext)) {
            return new ArrayList<>();
        }
        PostgreSqlParser.QueryPrimaryContext primary =
                ((PostgreSqlParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof PostgreSqlParser.ValuesQueryContext)) {
            return new ArrayList<>();
        }
        return ((PostgreSqlParser.ValuesQueryContext) primary).valuesClause();
    }

    private List<ColumnLineage> readCopyColumns(PostgreSqlParser.IdentifierListContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(target, columnName));
            lineage.setSources(new ArrayList<>());
            lineages.add(lineage);
        }
        return lineages;
    }

    private List<ColumnLineage> readCopyTableColumns(PostgreSqlParser.IdentifierListContext ctx, TableRef source) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            List<ColumnRef> sources = new ArrayList<>();
            sources.add(new ColumnRef(source, columnName));
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(null, columnName));
            lineage.setSources(sources);
            lineages.add(lineage);
        }
        return lineages;
    }

    private PostgreSqlParser.MultipartIdentifierContext likeSource(PostgreSqlParser.TableElementListContext ctx) {
        for (PostgreSqlParser.TableElementContext element : ctx.tableElement()) {
            if (element.LIKE() != null && element.multipartIdentifier() != null) {
                return element.multipartIdentifier();
            }
        }
        return null;
    }

    private void collectCreateTableElements(PostgreSqlParser.TableElementListContext ctx, TableRef target) {
        if (ctx == null) {
            return;
        }
        List<ColumnLineage> generatedColumns = new ArrayList<>();
        for (PostgreSqlParser.TableElementContext element : ctx.tableElement()) {
            if (element.columnName != null) {
                String columnName = cleanIdentifier(element.columnName);
                for (PostgreSqlParser.ColumnConstraintContext constraint : element.columnConstraint()) {
                    collectColumnConstraint(constraint, target, columnName, generatedColumns);
                }
            }
            if (element.tableConstraint() != null) {
                collectTableConstraint(element.tableConstraint());
            }
        }
        if (!generatedColumns.isEmpty()) {
            result.setColumnLineage(generatedColumns);
        }
    }

    private void collectColumnConstraint(PostgreSqlParser.ColumnConstraintContext ctx,
                                         TableRef target,
                                         String columnName,
                                         List<ColumnLineage> generatedColumns) {
        if (ctx == null) {
            return;
        }
        if (ctx.refTable != null) {
            inputTables.add(tableRef(ctx.refTable));
        }
        if (ctx.generatedExpression != null) {
            generatedColumns.add(generatedColumnLineage(target, columnName, ctx.generatedExpression));
        }
        if (ctx.checkExpression != null) {
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.checkExpression));
        }
        if (ctx.nestedColumnConstraint != null) {
            collectColumnConstraint(ctx.nestedColumnConstraint, target, columnName, generatedColumns);
        }
    }

    private ColumnLineage generatedColumnLineage(TableRef table, String column, PostgreSqlParser.ExpressionContext expression) {
        List<ColumnRef> sources = resolveSources(sourceColumns(expression));
        return LineageModelUtils.columnLineage(table, column,
                sources == null ? Collections.emptyList() : sources, expression.getText());
    }

    private void collectTableConstraint(PostgreSqlParser.TableConstraintContext ctx) {
        if (ctx.refTable != null) {
            inputTables.add(tableRef(ctx.refTable));
        }
        if (ctx.checkExpression != null) {
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(ctx.checkExpression));
        }
    }

    private List<ColumnRef> resolveSources(List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sc : sourceColumns) {
            if (sc.resolvedRef != null) {
                refs.add(sc.resolvedRef);
                continue;
            }
            SourceColumn col = scopedSourceColumn(sc);
            List<ColumnRef> joinRefs = col.qualifier == null ? unqualifiedJoinKeys.get(col.name) : null;
            if (joinRefs != null) {
                refs.addAll(joinRefs);
                continue;
            }
            List<ColumnRef> derivedRefs = derivedColumnRefs(col);
            if (derivedRefs != null) {
                refs.addAll(derivedRefs);
                continue;
            }
            TableRef table = null;
            if (col.qualifier != null) {
                table = tableForQualifier(col.qualifier);
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
        collectSubqueryDependencies(tree, false);
    }

    private void collectSubqueryDependencies(ParseTree tree, boolean includeColumnUsages) {
        if (tree instanceof PostgreSqlParser.ArrayConstructorQueryContext) {
            addSubqueryDependencies(((PostgreSqlParser.ArrayConstructorQueryContext) tree).query(), true);
            return;
        }
        if (tree instanceof PostgreSqlParser.ScalarSubqueryContext) {
            addSubqueryDependencies(((PostgreSqlParser.ScalarSubqueryContext) tree).query(), includeColumnUsages);
            return;
        }
        if (tree instanceof PostgreSqlParser.ExistsExprContext) {
            addSubqueryDependencies(((PostgreSqlParser.ExistsExprContext) tree).query(), includeColumnUsages);
            return;
        }
        if (tree instanceof PostgreSqlParser.QuantifiedComparisonContext) {
            PostgreSqlParser.QuantifiedComparisonContext comparison =
                    (PostgreSqlParser.QuantifiedComparisonContext) tree;
            if (comparison.query() != null) {
                addSubqueryDependencies(comparison.query(), includeColumnUsages);
                collectSubqueryDependencies(comparison.left, includeColumnUsages);
                return;
            }
        }
        if (tree instanceof PostgreSqlParser.PredicateContext) {
            PostgreSqlParser.QueryContext query = ((PostgreSqlParser.PredicateContext) tree).query();
            if (query != null) {
                addSubqueryDependencies(query, includeColumnUsages);
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSubqueryDependencies(tree.getChild(i), includeColumnUsages);
        }
    }

    private void addSubqueryDependencies(PostgreSqlParser.QueryContext query, boolean includeColumnUsages) {
        LineageResult subquery = lineageForQuery(query);
        if (inputTables.addAll(subquery.getInputTables())) {
            result.setInputTables(new ArrayList<>(inputTables));
        }
        if (includeColumnUsages) {
            LineageModelUtils.mergeColumnUsages(result, subquery);
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree instanceof PostgreSqlParser.ArrayConstructorQueryContext) {
            return false;
        }
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
        return subqueryVisitor(query).result;
    }

    private PostgreSqlLineageVisitor subqueryVisitor(PostgreSqlParser.QueryContext query) {
        return analyzedSubqueries.computeIfAbsent(query, this::analyzeSubquery);
    }

    private PostgreSqlLineageVisitor analyzeSubquery(PostgreSqlParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.knownColumnRelations.addAll(knownColumnRelations);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        queryVisitor.collectTopLevelQueryProjections(query);
        queryVisitor.finalizeResult();
        return queryVisitor;
    }

    private void applyInsertQueryLineage(PostgreSqlParser.QueryContext query, TableRef target) {
        LineageResult queryResult = new LineageResult();
        PostgreSqlLineageVisitor queryVisitor = new PostgreSqlLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.knownColumnRelations.addAll(knownColumnRelations);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        queryVisitor.collectTopLevelQueryProjections(query);
        queryVisitor.finalizeResult();
        inputTables.addAll(queryResult.getInputTables());
        LineageModelUtils.mergeColumnUsages(result, queryResult);

        PostgreSqlParser.SelectClauseContext select = insertTargetColumns.isEmpty()
                ? null : fullyExplicitSelect(query.queryTerm());
        if (select != null) {
            result.setColumnLineage(queryVisitor.positionalInsertLineage(select, target, insertTargetColumns));
            return;
        }
        if (queryVisitor.projections.isEmpty()) {
            result.setColumnLineage(LineageModelUtils.retargetColumnLineage(
                    queryResult.getColumnLineage(),
                    target,
                    insertTargetColumns));
            return;
        }
        List<ColumnLineage> lineages = new ArrayList<>();
        for (Projection projection : queryVisitor.projections) {
            if (!insertTargetColumns.isEmpty() && projection.ordinal >= queryVisitor.firstUnknownStarOrdinal) {
                break;
            }
            List<ColumnRef> sources = queryVisitor.columnRefs(projection);
            if (sources == null || sources.isEmpty()) {
                continue;
            }
            String targetColumn = targetColumn(projection, projection.ordinal);
            lineages.add(LineageModelUtils.columnLineage(target, targetColumn, sources, projection.expression));
        }
        result.setColumnLineage(lineages);
    }

    private List<ColumnLineage> positionalInsertLineage(PostgreSqlParser.SelectClauseContext select,
                                                         TableRef target,
                                                         List<String> targetColumns) {
        List<PostgreSqlParser.SelectItemContext> items = select.selectItemList().selectItem();
        List<ColumnLineage> lineages = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), targetColumns.size()); i++) {
            PostgreSqlParser.ExpressionContext expression =
                    ((PostgreSqlParser.SelectExpressionContext) items.get(i)).expression();
            List<ColumnRef> sources = columnRefs(sourceColumns(expression));
            if (sources != null && !sources.isEmpty()) {
                lineages.add(LineageModelUtils.columnLineage(target, targetColumns.get(i), sources, expression.getText()));
            }
        }
        return lineages;
    }

    private void collectTopLevelQueryProjections(PostgreSqlParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        PostgreSqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        List<PostgreSqlParser.SelectItemContext> items = specification.selectClause().selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            PostgreSqlParser.SelectItemContext item = items.get(i);
            if (item instanceof PostgreSqlParser.SelectExpressionContext) {
                Projection projection = projection((PostgreSqlParser.SelectExpressionContext) item, i);
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

    private Projection projection(PostgreSqlParser.SelectExpressionContext ctx, int ordinal) {
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
        return new Projection(sourceColumns, targetColumn, expression, ordinal);
    }

    private static String defaultSetColumnName(PostgreSqlParser.ExpressionContext expression) {
        PostgreSqlParser.PrimaryExpressionContext primary = primaryExpression(expression);
        if (primary instanceof PostgreSqlParser.FunctionCallContext) {
            return unqualifiedName(((PostgreSqlParser.FunctionCallContext) primary).functionName().getText());
        }
        if (primary instanceof PostgreSqlParser.FunctionCallStarContext) {
            return unqualifiedName(((PostgreSqlParser.FunctionCallStarContext) primary).functionName().getText());
        }
        if (primary instanceof PostgreSqlParser.FunctionCallEmptyContext) {
            return unqualifiedName(((PostgreSqlParser.FunctionCallEmptyContext) primary).functionName().getText());
        }
        if (primary instanceof PostgreSqlParser.JsonObjectExpressionContext
                || primary instanceof PostgreSqlParser.JsonArrayExpressionContext
                || primary instanceof PostgreSqlParser.JsonArrayQueryExpressionContext
                || primary instanceof PostgreSqlParser.JsonConstructorExpressionContext
                || primary instanceof PostgreSqlParser.JsonSerializeExpressionContext
                || primary instanceof PostgreSqlParser.XmlParseExpressionContext
                || primary instanceof PostgreSqlParser.XmlSerializeExpressionContext
                || primary instanceof PostgreSqlParser.XmlExistsExpressionContext
                || primary instanceof PostgreSqlParser.XmlElementExpressionContext
                || primary instanceof PostgreSqlParser.XmlForestExpressionContext
                || primary instanceof PostgreSqlParser.NormalizeExpressionContext) {
            return primary.getStart().getText().toLowerCase(Locale.ROOT);
        }
        return "?column?";
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
        PostgreSqlLineageVisitor queryVisitor = subqueryVisitor(query);
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
            String column = cleanIdentifier(colRef.identifier());
            if (!isPostgreSqlGeneratedExpression(column)) {
                columns.add(new SourceColumn(null, column));
            }
            return;
        }
        if (tree instanceof PostgreSqlParser.DereferenceContext) {
            PostgreSqlParser.DereferenceContext deref = (PostgreSqlParser.DereferenceContext) tree;
            List<String> parts = collectDereferenceParts(deref);
            if (!parts.isEmpty()) {
                columns.add(SourceColumn.path(parts));
            }
            return;
        }
        if (tree instanceof PostgreSqlParser.ArrayConstructorQueryContext) {
            addScalarSubquerySourceColumns(((PostgreSqlParser.ArrayConstructorQueryContext) tree).query(), columns);
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
        if (tree instanceof PostgreSqlParser.QuantifiedComparisonContext) {
            PostgreSqlParser.QuantifiedComparisonContext comparison =
                    (PostgreSqlParser.QuantifiedComparisonContext) tree;
            if (comparison.query() != null) {
                collectSourceColumns(comparison.left, columns);
                addScalarSubquerySourceColumns(comparison.query(), columns);
                return;
            }
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
        if (tree instanceof PostgreSqlParser.WindowRefContext) {
            PostgreSqlParser.WindowRefContext windowRef = (PostgreSqlParser.WindowRefContext) tree;
            if (windowRef.identifier() != null) {
                String name = cleanIdentifier(windowRef.identifier()).toLowerCase(Locale.ROOT);
                columns.addAll(namedWindowSources.getOrDefault(name, Collections.emptySet()));
                return;
            }
        }
        if (tree instanceof PostgreSqlParser.WindowSpecContext) {
            PostgreSqlParser.WindowSpecContext spec = (PostgreSqlParser.WindowSpecContext) tree;
            if (spec.identifier() != null) {
                String name = cleanIdentifier(spec.identifier()).toLowerCase(Locale.ROOT);
                columns.addAll(namedWindowSources.getOrDefault(name, Collections.emptySet()));
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
        parts.add(ctx.identifier().getText());
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof PostgreSqlParser.DereferenceContext) {
            PostgreSqlParser.DereferenceContext deref = (PostgreSqlParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(deref.identifier().getText());
        } else if (tree instanceof PostgreSqlParser.ColumnReferenceContext) {
            PostgreSqlParser.ColumnReferenceContext colRef = (PostgreSqlParser.ColumnReferenceContext) tree;
            parts.add(colRef.identifier().getText());
        }
    }

    // ============ Utility ============

    private static TableRef tableRef(PostgreSqlParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static TableRef renamedTable(PostgreSqlParser.MultipartIdentifierContext ctx, String name) {
        List<String> parts = identifierParts(ctx);
        if (parts.isEmpty()) {
            parts.add(name);
        } else {
            parts.set(parts.size() - 1, name);
        }
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static TableRef tableInSchema(PostgreSqlParser.MultipartIdentifierContext ctx, String schema) {
        List<String> parts = identifierParts(ctx);
        if (parts.size() >= 3) {
            List<String> relocated = new ArrayList<>();
            relocated.add(parts.get(parts.size() - 3));
            relocated.add(schema);
            relocated.add(parts.get(parts.size() - 1));
            return LineageModelUtils.tableRefFromParts(relocated);
        }
        List<String> relocated = new ArrayList<>();
        relocated.add(schema);
        relocated.add(parts.isEmpty() ? "" : parts.get(parts.size() - 1));
        return LineageModelUtils.tableRefFromParts(relocated);
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

    private static boolean isPostgreSqlGeneratedExpression(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.matches("\\$[0-9]+")
                || "current_date".equals(normalized)
                || "current_time".equals(normalized)
                || "current_timestamp".equals(normalized)
                || "current_user".equals(normalized)
                || "localtime".equals(normalized)
                || "localtimestamp".equals(normalized)
                || "true".equals(normalized)
                || "false".equals(normalized)
                || "null".equals(normalized)
                || "unknown".equals(normalized)
                || "default".equals(normalized);
    }

    private static class Projection {
        final List<SourceColumn> sourceColumns;
        final String targetColumn;
        final String expression;
        final int ordinal;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, int ordinal) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
            this.ordinal = ordinal;
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
        final List<String> path;

        SourceColumn(String qualifier, String name) {
            this(null, qualifier, name, Collections.emptyList());
        }

        private SourceColumn(ColumnRef resolvedRef, String qualifier, String name, List<String> path) {
            this.resolvedRef = resolvedRef;
            this.qualifier = qualifier;
            this.name = name;
            this.path = path;
        }

        static SourceColumn resolved(ColumnRef ref) {
            return new SourceColumn(ref, null, ref.getName(), Collections.emptyList());
        }

        static SourceColumn path(List<String> parts) {
            List<String> names = new ArrayList<>();
            for (String part : parts) {
                names.add(cleanIdentifier(part));
            }
            return new SourceColumn(null, null, String.join(".", names), parts);
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
                    && Objects.equals(name, that.name)
                    && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolvedRef, qualifier, name, path);
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
