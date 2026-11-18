package io.github.linesql.dialect.hive;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.hive.antlr.HiveParser;
import io.github.linesql.dialect.hive.antlr.HiveParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class HiveLineageVisitor extends HiveParserBaseVisitor<Void> {
    private final LineageResult result;
    private final ParseContext context;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, HiveParser.WindowSpecContext> namedWindowDefinitions = new LinkedHashMap<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Map<String, List<SourceColumn>> generatedColumns = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final List<Projection> projections = new ArrayList<>();
    private HiveParser.SelectClauseContext setBranchSelect;
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private TableRef currentDmlTarget;
    private int visibleRelationCount;
    private boolean suppressColumnLineage;

    HiveLineageVisitor(LineageResult result, ParseContext context) {
        this.result = result;
        this.context = context;
    }

    @Override
    public Void visitStatementDefault(HiveParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExplainStmt(HiveParser.ExplainStmtContext ctx) {
        return visit(ctx.explainStatement());
    }

    @Override
    public Void visitExplainStatement(HiveParser.ExplainStatementContext ctx) {
        return visit(ctx.explainedStatement());
    }

    @Override
    public Void visitExplainedStatement(HiveParser.ExplainedStatementContext ctx) {
        if (ctx.query() != null) {
            result.setStatementType(StatementType.SELECT);
            return visit(ctx.query());
        }
        if (ctx.insertDirectoryStatement() != null) {
            result.setStatementType(StatementType.INSERT);
            return visit(ctx.insertDirectoryStatement());
        }
        if (ctx.insertStatement() != null) {
            result.setStatementType(StatementType.INSERT);
            return visit(ctx.insertStatement());
        }
        if (ctx.updateStatement() != null) {
            result.setStatementType(StatementType.UPDATE);
            return visit(ctx.updateStatement());
        }
        if (ctx.deleteStatement() != null) {
            result.setStatementType(StatementType.DELETE);
            return visit(ctx.deleteStatement());
        }
        if (ctx.mergeStatement() != null) {
            result.setStatementType(StatementType.MERGE);
            return visit(ctx.mergeStatement());
        }
        if (ctx.createTableStatement() != null) {
            return visit(ctx.createTableStatement());
        }
        if (ctx.createViewStatement() != null) {
            return visit(ctx.createViewStatement());
        }
        return null;
    }

    @Override
    public Void visitInsertStmt(HiveParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMultiInsertStmt(HiveParser.MultiInsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMultiInsertStatement(HiveParser.MultiInsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        visit(ctx.fromClause());
        List<ColumnLineage> lineages = new ArrayList<>();
        for (HiveParser.MultiInsertBodyContext body : ctx.multiInsertBody()) {
            HiveLineageVisitor branch = branchVisitor();
            branch.visit(body);
            branch.finalizeResult();
            inputTables.addAll(branch.inputTables);
            outputTables.addAll(branch.outputTables);
            lineages.addAll(branch.result.getColumnLineage());
            LineageModelUtils.mergeColumnUsages(result, branch.result);
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMultiInsertBody(HiveParser.MultiInsertBodyContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addPartitionModelUsages(ctx, target);
        if (ctx.columnList != null) {
            for (HiveParser.IdentifierContext id : ctx.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        visit(ctx.selectClause());
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        if (ctx.groupByClause() != null) {
            visit(ctx.groupByClause());
        }
        if (ctx.havingClause() != null) {
            visit(ctx.havingClause());
        }
        visit(ctx.queryOrganization());
        refreshColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitInsertDirectoryStmt(HiveParser.InsertDirectoryStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertDirectoryStatement(HiveParser.InsertDirectoryStatementContext ctx) {
        visit(ctx.query());
        refreshColumnLineage();
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitInsertStatement(HiveParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addPartitionModelUsages(ctx, target);
        if (ctx.columnList != null) {
            for (HiveParser.IdentifierContext id : ctx.columnList.identifier()) {
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
    public Void visitUpdateStmt(HiveParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(HiveParser.UpdateStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectSubqueryInputs(ctx.assignmentList());
        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), target);
        result.setColumnLineage(assignments);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteStmt(HiveParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteStatement(HiveParser.DeleteStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeStmt(HiveParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStatement(HiveParser.MergeStatementContext ctx) {
        TableRef target = tableRef(ctx.target);
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String targetAlias = tableAlias(ctx.targetAlias);
        if (targetAlias != null) {
            tableAliases.put(targetAlias.toLowerCase(Locale.ROOT), target);
        }

        visit(ctx.mergeSource());
        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        List<ColumnLineage> lineages = new ArrayList<>();
        for (HiveParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause instanceof HiveParser.MergeMatchedUpdateContext) {
                HiveParser.MergeMatchedUpdateContext update = (HiveParser.MergeMatchedUpdateContext) clause;
                if (update.expression() != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(update.expression()));
                }
                lineages.addAll(readAssignments(update.assignmentList(), target));
            } else if (clause instanceof HiveParser.MergeMatchedDeleteContext) {
                HiveParser.MergeMatchedDeleteContext delete = (HiveParser.MergeMatchedDeleteContext) clause;
                if (delete.expression() != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(delete.expression()));
                }
            } else if (clause instanceof HiveParser.MergeNotMatchedInsertContext) {
                HiveParser.MergeNotMatchedInsertContext insert = (HiveParser.MergeNotMatchedInsertContext) clause;
                if (insert.expression() != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(insert.expression()));
                }
                lineages.addAll(readMergeInsertValues(insert, target));
            }
        }

        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeTableSource(HiveParser.MergeTableSourceContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitMergeQuerySource(HiveParser.MergeQuerySourceContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$merge_source" : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitCreateTableStmt(HiveParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(HiveParser.CreateTableStatementContext ctx) {
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
        addCreateTableModelUsages(ctx, target);
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addCreateTableModelUsages(HiveParser.CreateTableStatementContext ctx, TableRef target) {
        Set<String> columns = new LinkedHashSet<>();
        if (ctx.tableDefinitionList() != null) {
            for (HiveParser.TableDefinitionContext definition : ctx.tableDefinitionList().tableDefinition()) {
                if (definition.tableElement() != null) {
                    HiveParser.TableElementContext element = definition.tableElement();
                    for (HiveParser.ColumnConstraintContext constraint : element.columnConstraint()) {
                        if (constraint.PRIMARY() != null
                                || constraint.UNIQUE() != null
                                || constraint.CHECK() != null) {
                            columns.add(cleanIdentifier(element.identifier()));
                        }
                    }
                } else if (definition.tableConstraint() != null) {
                    HiveParser.TableConstraintContext constraint = definition.tableConstraint();
                    if (constraint.localColumns != null) {
                        for (HiveParser.IdentifierContext identifier : constraint.localColumns.identifier()) {
                            columns.add(cleanIdentifier(identifier));
                        }
                    }
                    if (constraint.expression() != null) {
                        for (SourceColumn source : sourceColumns(constraint.expression())) {
                            columns.add(source.name);
                        }
                    }
                    if (constraint.referencedTable != null) {
                        TableRef referencedTable = tableRef(constraint.referencedTable);
                        inputTables.add(referencedTable);
                        List<ColumnRef> referencedColumns = new ArrayList<>();
                        for (HiveParser.IdentifierContext identifier
                                : constraint.referencedColumns.identifier()) {
                            referencedColumns.add(new ColumnRef(referencedTable, cleanIdentifier(identifier)));
                        }
                        LineageModelUtils.addColumnUsages(
                                result,
                                ColumnUsageType.TABLE_MODEL,
                                referencedColumns);
                    }
                }
            }
        }
        if (ctx.partitionColumns != null) {
            for (HiveParser.TableElementContext element : ctx.partitionColumns.tableElement()) {
                columns.add(cleanIdentifier(element.identifier()));
            }
        }
        if (ctx.partitionColumnNames != null) {
            columns.addAll(identifierNames(ctx.partitionColumnNames));
        }
        if (ctx.partitionTransformSpec() != null) {
            collectPartitionTransformColumns(ctx.partitionTransformSpec(), columns);
        }
        if (ctx.clusteringClause() != null) {
            for (HiveParser.IdentifierContext identifier
                    : ctx.clusteringClause().bucketColumns.identifier()) {
                columns.add(cleanIdentifier(identifier));
            }
            for (HiveParser.SortColumnContext sortColumn : ctx.clusteringClause().sortColumn()) {
                columns.add(cleanIdentifier(sortColumn.identifier()));
            }
        }
        if (ctx.skewedClause() != null) {
            for (HiveParser.IdentifierContext identifier : ctx.skewedClause().skewColumns.identifier()) {
                columns.add(cleanIdentifier(identifier));
            }
        }
        if (ctx.writeOrderClause() != null) {
            HiveParser.WriteOrderClauseContext ordering = ctx.writeOrderClause();
            if (ordering.identifierList() != null) {
                columns.addAll(identifierNames(ordering.identifierList()));
            }
            for (HiveParser.WriteOrderColumnContext column : ordering.writeOrderColumn()) {
                columns.add(cleanIdentifier(column.identifier()));
            }
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : columns) {
            refs.add(new ColumnRef(target, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
    }

    @Override
    public Void visitCreateViewStmt(HiveParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(HiveParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (HiveParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
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
    public Void visitCreateMaterializedViewStmt(HiveParser.CreateMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateMaterializedViewStatement(HiveParser.CreateMaterializedViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(HiveParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(HiveParser.DropViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewStmt(HiveParser.AlterViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterViewAsQuery(HiveParser.AlterViewAsQueryContext ctx) {
        TableRef target = tableRef(ctx.target);
        outputTables.add(target);
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewRename(HiveParser.AlterViewRenameContext ctx) {
        inputTables.add(tableRef(ctx.target));
        outputTables.add(tableRef(ctx.replacement));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewProperties(HiveParser.AlterViewPropertiesContext ctx) {
        outputTables.add(tableRef(ctx.target));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewRewrite(HiveParser.AlterMaterializedViewRewriteContext ctx) {
        outputTables.add(tableRef(ctx.target));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropTableStmt(HiveParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(HiveParser.DropTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(HiveParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(HiveParser.TruncateTableStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        addPartitionModelUsages(ctx, target);
        return null;
    }

    @Override
    public Void visitAlterTableStmt(HiveParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(HiveParser.AlterTableRenameContext ctx) {
        List<HiveParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(HiveParser.AlterTableAddColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        Set<String> columns = new LinkedHashSet<>();
        if (ctx.column != null) {
            columns.add(cleanIdentifier(ctx.column));
        }
        if (ctx.columns != null) {
            for (HiveParser.TableElementContext element : ctx.columns.tableElement()) {
                columns.add(cleanIdentifier(element.identifier()));
            }
        }
        addTableModelColumns(target, columns);
        return null;
    }

    @Override
    public Void visitAlterTableChangeColumn(HiveParser.AlterTableChangeColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        Set<String> columns = new LinkedHashSet<>();
        columns.add(cleanIdentifier(ctx.oldColumn));
        columns.add(cleanIdentifier(ctx.newColumn));
        if (ctx.afterColumn != null) {
            columns.add(cleanIdentifier(ctx.afterColumn));
        }
        addTableModelColumns(target, columns);
        addPartitionModelUsages(ctx, target);
        return null;
    }

    @Override
    public Void visitAlterTableReplaceColumns(HiveParser.AlterTableReplaceColumnsContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        Set<String> columns = new LinkedHashSet<>();
        for (HiveParser.TableElementContext element : ctx.columns.tableElement()) {
            columns.add(cleanIdentifier(element.identifier()));
        }
        addTableModelColumns(target, columns);
        addPartitionModelUsages(ctx, target);
        return null;
    }

    @Override
    public Void visitAlterTableExchangePartition(HiveParser.AlterTableExchangePartitionContext ctx) {
        inputTables.add(tableRef(ctx.source));
        TableRef target = tableRef(ctx.target);
        outputTables.add(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        addPartitionModelUsages(ctx, target);
        return null;
    }

    @Override
    public Void visitAlterTableOther(HiveParser.AlterTableOtherContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        addPartitionModelUsages(ctx, target);
        if (ctx.alterTableAction().DROP() != null
                && ctx.alterTableAction().COLUMN() != null
                && ctx.alterTableAction().identifier() != null) {
            addTableModelColumn(target, cleanIdentifier(ctx.alterTableAction().identifier()));
        }
        if (ctx.alterTableAction().partitionTransformSpec() != null) {
            Set<String> columns = new LinkedHashSet<>();
            collectPartitionTransformColumns(ctx.alterTableAction().partitionTransformSpec(), columns);
            addTableModelColumns(target, columns);
        }
        if (ctx.alterTableAction().clusteringClause() != null) {
            Set<String> columns = new LinkedHashSet<>();
            HiveParser.ClusteringClauseContext clustering =
                    ctx.alterTableAction().clusteringClause();
            for (HiveParser.IdentifierContext identifier : clustering.bucketColumns.identifier()) {
                columns.add(cleanIdentifier(identifier));
            }
            for (HiveParser.SortColumnContext sortColumn : clustering.sortColumn()) {
                columns.add(cleanIdentifier(sortColumn.identifier()));
            }
            addTableModelColumns(target, columns);
        }
        return null;
    }

    private void addTableModelColumn(TableRef target, String column) {
        Set<String> columns = new LinkedHashSet<>();
        columns.add(column);
        addTableModelColumns(target, columns);
    }

    private void addTableModelColumns(TableRef target, Set<String> columns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : columns) {
            refs.add(new ColumnRef(target, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
    }

    private void addPartitionModelUsages(ParseTree tree, TableRef target) {
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(tree, columns);
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : columns) {
            refs.add(new ColumnRef(target, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
    }

    private static void collectPartitionTransformColumns(HiveParser.PartitionTransformSpecContext spec,
                                                         Set<String> columns) {
        for (HiveParser.PartitionTransformContext transform : spec.partitionTransform()) {
            columns.add(cleanIdentifier(transform.identifier()));
        }
    }

    private static void collectPartitionColumns(ParseTree tree, Set<String> columns) {
        if (tree instanceof HiveParser.PartitionSpecContext) {
            for (HiveParser.PartitionValContext value
                    : ((HiveParser.PartitionSpecContext) tree).partitionVal()) {
                columns.add(cleanIdentifier(value.identifier()));
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectPartitionColumns(tree.getChild(i), columns);
        }
    }

    @Override
    public Void visitCreateDatabaseStmt(HiveParser.CreateDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_SCHEMA);
        return null;
    }

    @Override
    public Void visitAlterDatabaseStmt(HiveParser.AlterDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropDatabaseStmt(HiveParser.DropDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.DROP_SCHEMA);
        return null;
    }

    @Override
    public Void visitUseStmt(HiveParser.UseStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
        if (context != null) {
            HiveParser.IdentifierContext database = ctx.useStatement().identifier();
            context.setDefaultSchema(database.getStart().getType() == HiveParser.DEFAULT
                    ? "default" : cleanIdentifier(database));
        }
        return null;
    }

    @Override
    public Void visitSetStmt(HiveParser.SetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitResetStmt(HiveParser.ResetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDfsStmt(HiveParser.DfsStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitResourceStmt(HiveParser.ResourceStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateFunction(HiveParser.CreateFunctionContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        return null;
    }

    @Override
    public Void visitDropFunction(HiveParser.DropFunctionContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitReloadFunction(HiveParser.ReloadFunctionContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitConnectorStmt(HiveParser.ConnectorStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitShowStmt(HiveParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowTableMetadata(HiveParser.ShowTableMetadataContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        inputTables.add(table);
        result.setInputTables(new ArrayList<>(inputTables));
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(ctx, columns);
        addMetadataColumns(table, columns);
        return null;
    }

    @Override
    public Void visitShowColumnsMetadata(HiveParser.ShowColumnsMetadataContext ctx) {
        TableRef table = rawTableRef(ctx.multipartIdentifier());
        if (ctx.namespace != null && table.getSchema() == null) {
            table = new TableRef(null, cleanIdentifier(ctx.namespace), table.getName());
        }
        addInputTable(qualifyTable(table), false);
        return null;
    }

    @Override
    public Void visitShowNamedPartitionMetadata(HiveParser.ShowNamedPartitionMetadataContext ctx) {
        if (ctx.partitionSpec() == null) {
            return null;
        }
        String literal = ctx.string().getText();
        String name = literal.substring(1, literal.length() - 1);
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) {
            return null;
        }
        String schema = ctx.namespace == null ? null : cleanIdentifier(ctx.namespace);
        TableRef table = qualifyTable(new TableRef(null, schema, name));
        addInputTable(table, false);
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(ctx.partitionSpec(), columns);
        addMetadataColumns(table, columns);
        return null;
    }

    @Override
    public Void visitShowPartitionMetadata(HiveParser.ShowPartitionMetadataContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        addInputTable(table, false);
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(ctx, columns);
        addMetadataColumns(table, columns);
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        for (HiveParser.SortItemContext item : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(item.expression()));
        }
        return null;
    }

    @Override
    public Void visitShowLockMetadata(HiveParser.ShowLockMetadataContext ctx) {
        if (ctx.target != null) {
            inputTables.add(tableRef(ctx.target));
            result.setInputTables(new ArrayList<>(inputTables));
        }
        return null;
    }

    @Override
    public Void visitShowGrantMetadata(HiveParser.ShowGrantMetadataContext ctx) {
        if (ctx.privilegeObject() instanceof HiveParser.TablePrivilegeObjectContext) {
            inputTables.add(tableRef(((HiveParser.TablePrivilegeObjectContext) ctx.privilegeObject()).multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
        }
        return null;
    }

    @Override
    public Void visitDescribeStmt(HiveParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDescribeStatement(HiveParser.DescribeStatementContext ctx) {
        if (ctx.table != null) {
            TableRef table = tableRef(ctx.table);
            addInputTable(table, false);
            Set<String> columns = new LinkedHashSet<>();
            collectPartitionColumns(ctx, columns);
            if (ctx.describedColumn != null) {
                List<String> path = new ArrayList<>();
                for (HiveParser.IdentifierContext part : ctx.describedColumn.identifier()) {
                    path.add(cleanIdentifier(part));
                }
                columns.add(String.join(".", path));
            }
            addMetadataColumns(table, columns);
        }
        return null;
    }

    @Override
    public Void visitCommentStmt(HiveParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadDataStmt(HiveParser.LoadDataStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadDataStatement(HiveParser.LoadDataStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        addPartitionModelUsages(ctx, target);
        return null;
    }

    @Override
    public Void visitExportTableStmt(HiveParser.ExportTableStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExportTableStatement(HiveParser.ExportTableStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        inputTables.add(table);
        result.setInputTables(new ArrayList<>(inputTables));
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(ctx, columns);
        addMetadataColumns(table, columns);
        return null;
    }

    @Override
    public Void visitImportTableStmt(HiveParser.ImportTableStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitImportTableStatement(HiveParser.ImportTableStatementContext ctx) {
        if (ctx.multipartIdentifier() != null) {
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRepairTableStmt(HiveParser.RepairTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRepairTableStatement(HiveParser.RepairTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAnalyzeTableStmt(HiveParser.AnalyzeTableStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeTableStatement(HiveParser.AnalyzeTableStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        inputTables.add(table);
        result.setInputTables(new ArrayList<>(inputTables));
        Set<String> columns = new LinkedHashSet<>();
        collectPartitionColumns(ctx, columns);
        if (ctx.identifierList() != null) {
            for (HiveParser.IdentifierContext identifier : ctx.identifierList().identifier()) {
                columns.add(cleanIdentifier(identifier));
            }
        }
        addMetadataColumns(table, columns);
        return null;
    }

    private void addMetadataColumns(TableRef table, Set<String> columns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : columns) {
            refs.add(new ColumnRef(table, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.READ_METADATA, refs);
    }

    @Override
    public Void visitLockTableStmt(HiveParser.LockTableStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLockTableStatement(HiveParser.LockTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitGrantStmt(HiveParser.GrantStmtContext ctx) {
        result.setStatementType(ctx.grantStatement().privilegeObject() instanceof HiveParser.TablePrivilegeObjectContext
                ? StatementType.ALTER_TABLE : StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGrantStatement(HiveParser.GrantStatementContext ctx) {
        if (ctx.privilegeObject() instanceof HiveParser.TablePrivilegeObjectContext) {
            outputTables.add(tableRef(((HiveParser.TablePrivilegeObjectContext) ctx.privilegeObject()).multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRevokeStmt(HiveParser.RevokeStmtContext ctx) {
        result.setStatementType(ctx.revokeStatement().privilegeObject() instanceof HiveParser.TablePrivilegeObjectContext
                ? StatementType.ALTER_TABLE : StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRevokeStatement(HiveParser.RevokeStatementContext ctx) {
        if (ctx.privilegeObject() instanceof HiveParser.TablePrivilegeObjectContext) {
            outputTables.add(tableRef(((HiveParser.TablePrivilegeObjectContext) ctx.privilegeObject()).multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRoleStmt(HiveParser.RoleStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(HiveParser.CtesContext ctx) {
        for (HiveParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
            cteNames.add(cteName);
        }
        return null;
    }

    @Override
    public Void visitSetOperation(HiveParser.SetOperationContext ctx) {
        boolean explicitOutput = setOutputWidth(ctx) > 0;
        LineageResult leftResult = lineageForQueryTerm(ctx.left, explicitOutput);
        LineageResult rightResult = lineageForQueryTerm(ctx.right, explicitOutput);
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
    public Void visitTableName(HiveParser.TableNameContext ctx) {
        TableRef table = rawTableRef(ctx.multipartIdentifier());
        if (isCteReference(table)) {
            addDerivedReference(table.getName(), ctx.tableAlias());
            if (ctx.tableSampleClause() != null) {
                visit(ctx.tableSampleClause());
            }
            return null;
        }
        addInputTable(qualifyTable(table), ctx.tableAlias(), true);
        if (ctx.tableSampleClause() != null) {
            visit(ctx.tableSampleClause());
        }
        return null;
    }

    @Override
    public Void visitTableSampleClause(HiveParser.TableSampleClauseContext ctx) {
        if (ctx.sampleBy != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.sampleBy));
        }
        return null;
    }

    @Override
    public Void visitAliasedQuery(HiveParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(HiveParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (HiveParser.LateralViewContext lateralView : ctx.lateralView()) {
            visit(lateralView);
        }
        for (HiveParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    @Override
    public Void visitLateralView(HiveParser.LateralViewContext ctx) {
        if (ctx.inlineValues() != null) {
            registerInlineValues(ctx.inlineValues(), ctx.tableAlias(), ctx.identifierList());
            return null;
        }
        List<HiveParser.IdentifierContext> identifiers = ctx.identifier();
        if (identifiers.size() < 2) {
            return null;
        }
        String relationAlias = cleanIdentifier(identifiers.get(0));
        String functionName = cleanIdentifier(ctx.functionName().getText()).toLowerCase(Locale.ROOT);
        List<SourceColumn> sources = sourceColumns(ctx.expressionList());
        int outputCount = identifiers.size() - 1;
        for (int i = 1; i < identifiers.size(); i++) {
            List<SourceColumn> outputSources = "stack".equals(functionName)
                    ? stackOutputSources(ctx.expressionList(), i - 1, outputCount)
                    : sources;
            registerGeneratedColumn(relationAlias, cleanIdentifier(identifiers.get(i)), outputSources);
        }
        return null;
    }

    @Override
    public Void visitInlineValuesTable(HiveParser.InlineValuesTableContext ctx) {
        registerInlineValues(ctx.inlineValues(), ctx.tableAlias(), ctx.identifierList());
        return null;
    }

    private void registerInlineValues(HiveParser.InlineValuesContext values, HiveParser.TableAliasContext alias,
                                      HiveParser.IdentifierListContext columnAliases) {
        List<HiveParser.ValuesClauseContext> rows = values.valuesClause();
        List<String> names = columnAliases == null ? Collections.emptyList() : identifierNames(columnAliases);
        int width = rows.get(0).expressionList().expression().size();
        for (int i = 0; i < width; i++) {
            Set<SourceColumn> sources = new LinkedHashSet<>();
            for (HiveParser.ValuesClauseContext row : rows) {
                List<HiveParser.ExpressionContext> items = row.expressionList().expression();
                if (i < items.size()) {
                    sources.addAll(sourceColumns(items.get(i)));
                }
            }
            String name = i < names.size() ? names.get(i) : "col" + (i + 1);
            registerGeneratedColumn(tableAlias(alias), name, new ArrayList<>(sources));
        }
    }

    private List<SourceColumn> stackOutputSources(
            HiveParser.ExpressionListContext expressions,
            int outputIndex,
            int outputCount) {
        List<SourceColumn> sources = new ArrayList<>();
        List<HiveParser.ExpressionContext> values = expressions.expression();
        for (int i = 1 + outputIndex; i < values.size(); i += outputCount) {
            sources.addAll(sourceColumns(values.get(i)));
        }
        return sources;
    }

    @Override
    public Void visitQuerySpecification(HiveParser.QuerySpecificationContext ctx) {
        Map<String, HiveParser.WindowSpecContext> previousWindows = new LinkedHashMap<>(namedWindowDefinitions);
        namedWindowDefinitions.clear();
        if (ctx.windowClause() != null) {
            for (HiveParser.NamedWindowContext window : ctx.windowClause().namedWindow()) {
                String name = cleanIdentifier(window.windowName().getText()).toLowerCase(Locale.ROOT);
                namedWindowDefinitions.put(name, window.windowSpec());
            }
        }
        try {
            return visitChildren(ctx);
        } finally {
            namedWindowDefinitions.clear();
            namedWindowDefinitions.putAll(previousWindows);
        }
    }

    @Override
    public Void visitScalarSubquery(HiveParser.ScalarSubqueryContext ctx) {
        if (setBranchSelect == null) {
            return visitChildren(ctx);
        }
        LineageResult inner = lineageForQuery(ctx.query());
        for (TableRef table : inner.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, inner);
        return null;
    }

    @Override
    public Void visitSelectClause(HiveParser.SelectClauseContext ctx) {
        if (ctx.transformClause() != null) {
            return visit(ctx.transformClause());
        }
        List<HiveParser.SelectItemContext> items = ctx.selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            HiveParser.SelectItemContext item = items.get(i);
            if (item instanceof HiveParser.SelectExpressionContext) {
                HiveParser.SelectExpressionContext selected = (HiveParser.SelectExpressionContext) item;
                Projection projection = projection(selected, i);
                if (projection == null && ctx == setBranchSelect) {
                    HiveParser.ExpressionContext expression = selected.expression();
                    projection = new Projection(sourceColumns(expression), "_c" + i, expression.getText(), i);
                }
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTransformClause(HiveParser.TransformClauseContext ctx) {
        List<SourceColumn> sources = ctx.expressionList() == null
                ? new ArrayList<>()
                : sourceColumns(ctx.expressionList());
        List<String> outputs = identifierNames(ctx.identifierList());
        for (int i = 0; i < outputs.size(); i++) {
            projections.add(new Projection(sources, outputs.get(i), ctx.getText(), i));
        }
        return null;
    }

    @Override
    public Void visitWhereClause(HiveParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoinCriteria(HiveParser.JoinCriteriaContext ctx) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitGroupByClause(HiveParser.GroupByClauseContext ctx) {
        for (HiveParser.GroupByItemContext item : ctx.groupByItem()) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(item));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(HiveParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitQualifyClause(HiveParser.QualifyClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return visitChildren(ctx);
    }

    @Override
    public Void visitWindowClause(HiveParser.WindowClauseContext ctx) {
        Map<String, HiveParser.WindowSpecContext> definitions = new LinkedHashMap<>();
        for (HiveParser.NamedWindowContext namedWindow : ctx.namedWindow()) {
            definitions.put(cleanIdentifier(namedWindow.windowName().getText()).toLowerCase(Locale.ROOT),
                    namedWindow.windowSpec());
        }
        Set<String> references = new LinkedHashSet<>();
        collectWindowReferences(ctx.getParent(), references);
        Set<String> resolved = new LinkedHashSet<>();
        for (String reference : references) {
            resolveNamedWindow(reference, definitions, resolved, new LinkedHashSet<>());
        }
        return null;
    }

    @Override
    public Void visitWindowSpec(HiveParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (HiveParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (HiveParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    private void collectWindowReferences(ParseTree tree, Set<String> references) {
        if (tree instanceof HiveParser.WindowClauseContext) {
            return;
        }
        if (tree instanceof HiveParser.WindowReferenceContext) {
            HiveParser.WindowReferenceContext reference = (HiveParser.WindowReferenceContext) tree;
            if (reference.windowName() != null) {
                references.add(cleanIdentifier(reference.windowName().getText()).toLowerCase(Locale.ROOT));
                return;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectWindowReferences(tree.getChild(i), references);
        }
    }

    private void resolveNamedWindow(
            String name,
            Map<String, HiveParser.WindowSpecContext> definitions,
            Set<String> resolved,
            Set<String> resolving) {
        if (resolved.contains(name) || !resolving.add(name)) {
            return;
        }
        HiveParser.WindowSpecContext specification = definitions.get(name);
        if (specification == null) {
            resolving.remove(name);
            return;
        }
        if (specification.baseWindow != null) {
            resolveNamedWindow(
                    cleanIdentifier(specification.baseWindow.getText()).toLowerCase(Locale.ROOT),
                    definitions,
                    resolved,
                    resolving);
        }
        visit(specification);
        resolving.remove(name);
        resolved.add(name);
    }

    @Override
    public Void visitQueryOrganization(HiveParser.QueryOrganizationContext ctx) {
        HiveParser.QueryTermContext term = ctx.getParent() instanceof HiveParser.QueryContext
                ? ((HiveParser.QueryContext) ctx.getParent()).queryTerm() : null;
        boolean setQuery = term != null && unwrapQueryTerm(term) instanceof HiveParser.SetOperationContext;
        for (HiveParser.SortItemContext sortItem : ctx.sortItem()) {
            if (setQuery) {
                String key = sortItem.expression().getText();
                int ordinal = ctx.sortByItems.contains(sortItem) ? -1 : sortOrdinal(key);
                List<ColumnLineage> lineages = result.getColumnLineage();
                if (ordinal >= 0 && ordinal < lineages.size() && lineages.size() == setOutputWidth(term)) {
                    LineageModelUtils.addColumnUsages(result, ColumnUsageType.ORDER_BY, lineages.get(ordinal).getSources());
                } else if (ordinal < 0) {
                    LineageModelUtils.addSetOrderByUsage(result, cleanIdentifier(key));
                }
            } else {
                addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
            }
        }
        for (HiveParser.ExpressionContext expression : ctx.distributeByExpressions) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(expression));
        }
        for (HiveParser.ExpressionContext expression : ctx.clusterByExpressions) {
            List<SourceColumn> sourceColumns = sourceColumns(expression);
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns);
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns);
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

    private HiveLineageVisitor branchVisitor() {
        HiveLineageVisitor branch = new HiveLineageVisitor(new LineageResult(), context);
        branch.inputTables.addAll(inputTables);
        branch.tableAliases.putAll(tableAliases);
        branch.cteNames.addAll(cteNames);
        branch.derivedColumnLineage.putAll(derivedColumnLineage);
        branch.derivedAliases.putAll(derivedAliases);
        branch.generatedColumns.putAll(generatedColumns);
        branch.derivedReferences.addAll(derivedReferences);
        branch.visibleRelations.addAll(visibleRelations);
        branch.visibleRelationCount = visibleRelationCount;
        return branch;
    }

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, HiveParser.TableAliasContext aliasCtx, boolean visibleRelation) {
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

    private void addDerivedReference(String rawName, HiveParser.TableAliasContext aliasCtx) {
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

    private void registerDerivedRelation(String name, HiveParser.QueryContext query, List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        HiveLineageVisitor relationVisitor = new HiveLineageVisitor(relationResult, context);
        relationVisitor.cteNames.addAll(cteNames);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        relationVisitor.generatedColumns.putAll(generatedColumns);
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

    private LineageResult lineageForQueryTerm(HiveParser.QueryTermContext queryTerm, boolean explicitOutput) {
        LineageResult queryResult = new LineageResult();
        HiveLineageVisitor queryVisitor = new HiveLineageVisitor(queryResult, context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.generatedColumns.putAll(generatedColumns);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        if (explicitOutput) {
            queryVisitor.setBranchSelect = explicitSelect(unwrapQueryTerm(queryTerm));
        }
        queryVisitor.visit(queryTerm);
        queryVisitor.finalizeResult();
        return queryResult;
    }


    private static HiveParser.QueryTermContext unwrapQueryTerm(HiveParser.QueryTermContext term) {
        while (term instanceof HiveParser.QueryTermDefaultContext) {
            HiveParser.QueryPrimaryContext primary = ((HiveParser.QueryTermDefaultContext) term).queryPrimary();
            if (!(primary instanceof HiveParser.SubqueryPrimaryContext)) {
                break;
            }
            term = ((HiveParser.SubqueryPrimaryContext) primary).query().queryTerm();
        }
        return term;
    }

    private static int setOutputWidth(HiveParser.QueryTermContext term) {
        HiveParser.QueryTermContext unwrapped = unwrapQueryTerm(term);
        if (unwrapped instanceof HiveParser.SetOperationContext) {
            HiveParser.SetOperationContext operation = (HiveParser.SetOperationContext) unwrapped;
            int leftWidth = setOutputWidth(operation.left);
            return leftWidth > 0 && leftWidth == setOutputWidth(operation.right) ? leftWidth : -1;
        }
        HiveParser.SelectClauseContext select = explicitSelect(unwrapped);
        return select == null ? -1 : select.selectItemList().selectItem().size();
    }

    private static HiveParser.SelectClauseContext explicitSelect(HiveParser.QueryTermContext term) {
        if (!(term instanceof HiveParser.QueryTermDefaultContext)) {
            return null;
        }
        HiveParser.QueryPrimaryContext primary = ((HiveParser.QueryTermDefaultContext) term).queryPrimary();
        if (!(primary instanceof HiveParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        HiveParser.SelectClauseContext select =
                ((HiveParser.QueryPrimaryDefaultContext) primary).querySpecification().selectClause();
        if (select.selectItemList() == null) {
            return null;
        }
        for (HiveParser.SelectItemContext item : select.selectItemList().selectItem()) {
            if (!(item instanceof HiveParser.SelectExpressionContext)) {
                return null;
            }
        }
        return select;
    }

    private static int sortOrdinal(String key) {
        try {
            return Integer.parseInt(key) - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
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

    private void addUsingColumnUsages(HiveParser.IdentifierListContext ctx) {
        addUsingColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(HiveParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void addUsingColumnUsages(HiveParser.IdentifierListContext ctx, int relationStart) {
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
        refreshColumnLineage(outputTables.size() == 1 ? outputTables.iterator().next() : null);
    }

    private void refreshColumnLineage(TableRef targetTable) {
        if (suppressColumnLineage || projections.isEmpty()) {
            return;
        }
        List<ColumnLineage> columnLineage = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = projections.get(i);
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null) {
                if (setBranchSelect == null) {
                    continue;
                }
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
        return columnRefs(sourceColumns, new LinkedHashSet<>());
    }

    private List<ColumnRef> columnRefs(List<SourceColumn> sourceColumns, Set<String> resolvingGeneratedColumns) {
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
            List<SourceColumn> generatedSources = generatedColumnSources(sourceColumn);
            String generatedKey = generatedColumnKey(sourceColumn);
            if (generatedSources != null && resolvingGeneratedColumns.add(generatedKey)) {
                List<ColumnRef> generatedRefs = columnRefs(generatedSources, resolvingGeneratedColumns);
                if (generatedRefs == null) {
                    return null;
                }
                refs.addAll(generatedRefs);
                resolvingGeneratedColumns.remove(generatedKey);
                continue;
            }
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

    private void registerGeneratedColumn(String relationAlias, String columnName, List<SourceColumn> sources) {
        List<SourceColumn> copiedSources = new ArrayList<>(sources);
        generatedColumns.put(columnName.toLowerCase(Locale.ROOT), copiedSources);
        if (relationAlias != null && !relationAlias.isEmpty()) {
            generatedColumns.put((relationAlias + "." + columnName).toLowerCase(Locale.ROOT), copiedSources);
        }
    }

    private List<SourceColumn> generatedColumnSources(SourceColumn sourceColumn) {
        if (sourceColumn.resolvedRef != null) {
            return null;
        }
        return generatedColumns.get(generatedColumnKey(sourceColumn));
    }

    private static String generatedColumnKey(SourceColumn sourceColumn) {
        String name = sourceColumn.name.toLowerCase(Locale.ROOT);
        if (sourceColumn.qualifier == null) {
            return name;
        }
        return sourceColumn.qualifier.toLowerCase(Locale.ROOT) + "." + name;
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
        List<ColumnRef> refs = columns.get(sourceColumn.name);
        if (refs == null) {
            int dot = sourceColumn.name.indexOf('.');
            if (dot > 0) {
                refs = columns.get(sourceColumn.name.substring(0, dot));
            }
        }
        return refs;
    }

    private boolean isCteReference(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && cteNames.contains(table.getName().toLowerCase(Locale.ROOT));
    }

    private List<ColumnLineage> readAssignments(HiveParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (HiveParser.AssignmentContext assignment : ctx.assignment()) {
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
                continue;
            }
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(table, columnName));
            lineage.setSources(sources);
            lineages.add(lineage);
        }
        return lineages;
    }

    private List<ColumnLineage> readMergeInsertValues(HiveParser.MergeNotMatchedInsertContext ctx, TableRef target) {
        List<String> targetColumns = ctx.columnList == null ? new ArrayList<>() : identifierNames(ctx.columnList);
        List<ColumnLineage> lineages = new ArrayList<>();
        List<HiveParser.ExpressionContext> values = ctx.expressionList().expression();
        for (int i = 0; i < values.size(); i++) {
            HiveParser.ExpressionContext value = values.get(i);
            List<ColumnRef> sources = resolveSources(sourceColumns(value));
            if (sources == null) {
                continue;
            }
            String targetColumn = i < targetColumns.size()
                    ? targetColumns.get(i)
                    : directTargetName(value, i);
            ColumnLineage lineage = new ColumnLineage();
            lineage.setTarget(new ColumnRef(target, targetColumn));
            lineage.setSources(sources);
            lineages.add(lineage);
        }
        return lineages;
    }

    private String directTargetName(HiveParser.ExpressionContext expression, int index) {
        List<SourceColumn> sources = sourceColumns(expression);
        if (sources.size() == 1) {
            return sources.get(0).name;
        }
        return "col" + (index + 1);
    }

    private List<ColumnRef> resolveSources(List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sc : sourceColumns) {
            if (sc.resolvedRef != null) {
                refs.add(sc.resolvedRef);
                continue;
            }
            SourceColumn col = scopedSourceColumn(sc);
            List<SourceColumn> generatedSources = generatedColumnSources(col);
            if (generatedSources != null) {
                List<ColumnRef> generatedRefs = columnRefs(generatedSources);
                if (generatedRefs == null) {
                    return null;
                }
                refs.addAll(generatedRefs);
                continue;
            }
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
        if (tree instanceof HiveParser.ScalarSubqueryContext) {
            HiveParser.ScalarSubqueryContext subquery = (HiveParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof HiveParser.ExistsExprContext) {
            HiveParser.ExistsExprContext exists = (HiveParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof HiveParser.PredicateContext) {
            HiveParser.PredicateContext predicate = (HiveParser.PredicateContext) tree;
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
        if (tree instanceof HiveParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof HiveParser.PredicateContext) {
            HiveParser.PredicateContext predicate = (HiveParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(HiveParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        HiveLineageVisitor queryVisitor = new HiveLineageVisitor(queryResult, context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.generatedColumns.putAll(generatedColumns);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        queryVisitor.collectTopLevelQueryProjections(query);
        queryVisitor.refreshColumnLineage();
        return queryResult;
    }

    private void collectTopLevelQueryProjections(HiveParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        HiveParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        List<HiveParser.SelectItemContext> items = specification.selectClause().selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            HiveParser.SelectItemContext item = items.get(i);
            if (item instanceof HiveParser.SelectExpressionContext) {
                Projection projection = projection((HiveParser.SelectExpressionContext) item, i);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
    }

    private static HiveParser.QuerySpecificationContext topLevelQuerySpecification(HiveParser.QueryContext query) {
        if (!(query.queryTerm() instanceof HiveParser.QueryTermDefaultContext)) {
            return null;
        }
        HiveParser.QueryPrimaryContext primary =
                ((HiveParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof HiveParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((HiveParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(HiveParser.SelectExpressionContext ctx, int ordinal) {
        String expression = ctx.expression().getText();
        List<SourceColumn> sourceColumns = sourceColumns(ctx.expression());
        String explicitTarget = ordinal >= 0 && ordinal < insertTargetColumns.size()
                ? insertTargetColumns.get(ordinal)
                : null;
        String directColumn = sourceColumns.size() == 1 && isDirectColumnExpression(expression, sourceColumns.get(0))
                ? unqualifiedName(sourceColumns.get(0).name)
                : null;
        if (sourceColumns.isEmpty() && ctx.alias == null) {
            return null;
        }
        if (sourceColumns.size() > 1 && ctx.alias == null && explicitTarget == null) {
            return null;
        }
        String targetColumn = ctx.alias == null
                ? (explicitTarget == null ? directColumn : explicitTarget)
                : cleanIdentifier(ctx.alias);
        if (targetColumn == null) {
            return null;
        }
        return new Projection(sourceColumns, targetColumn, expression, ordinal);
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

    private void addScalarSubquerySourceColumns(HiveParser.QueryContext query, Set<SourceColumn> columns) {
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(HiveParser.QueryContext query) {
        HiveLineageVisitor queryVisitor = new HiveLineageVisitor(new LineageResult(), context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        HiveParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (HiveParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof HiveParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((HiveParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectNamedWindowSources(HiveParser.WindowNameContext windowName, Set<SourceColumn> columns) {
        Set<String> visited = new LinkedHashSet<>();
        List<HiveParser.WindowSpecContext> definitions = new ArrayList<>();
        while (windowName != null) {
            String name = cleanIdentifier(windowName.getText()).toLowerCase(Locale.ROOT);
            HiveParser.WindowSpecContext definition = namedWindowDefinitions.get(name);
            if (definition == null || !visited.add(name)) {
                break;
            }
            definitions.add(definition);
            windowName = definition.baseWindow;
        }
        for (int index = definitions.size() - 1; index >= 0; index--) {
            HiveParser.WindowSpecContext definition = definitions.get(index);
            for (int child = 0; child < definition.getChildCount(); child++) {
                collectSourceColumns(definition.getChild(child), columns);
            }
        }
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        HiveParser.WindowNameContext windowName = null;
        if (tree instanceof HiveParser.WindowReferenceContext) {
            windowName = ((HiveParser.WindowReferenceContext) tree).windowName();
        } else if (tree instanceof HiveParser.WindowSpecContext) {
            windowName = ((HiveParser.WindowSpecContext) tree).baseWindow;
        }
        if (windowName != null) {
            collectNamedWindowSources(windowName, columns);
        }
        if (tree instanceof HiveParser.ColumnReferenceContext) {
            HiveParser.ColumnReferenceContext colRef = (HiveParser.ColumnReferenceContext) tree;
            String column = cleanIdentifier(colRef.identifier());
            if (!isHiveGeneratedExpression(column)) {
                columns.add(new SourceColumn(null, column));
            }
            return;
        }
        if (tree instanceof HiveParser.DereferenceContext) {
            HiveParser.DereferenceContext deref = (HiveParser.DereferenceContext) tree;
            List<String> parts = collectDereferenceParts(deref);
            if (!parts.isEmpty()) {
                columns.add(new SourceColumn(null, String.join(".", parts)));
            }
            return;
        }
        if (tree instanceof HiveParser.ScalarSubqueryContext) {
            HiveParser.ScalarSubqueryContext subquery = (HiveParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof HiveParser.ExistsExprContext) {
            HiveParser.ExistsExprContext exists = (HiveParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof HiveParser.PredicateContext) {
            HiveParser.PredicateContext predicate = (HiveParser.PredicateContext) tree;
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

    private List<String> collectDereferenceParts(HiveParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof HiveParser.DereferenceContext) {
            HiveParser.DereferenceContext deref = (HiveParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof HiveParser.ColumnReferenceContext) {
            HiveParser.ColumnReferenceContext colRef = (HiveParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private TableRef tableRef(HiveParser.MultipartIdentifierContext ctx) {
        return qualifyTable(rawTableRef(ctx));
    }

    private TableRef qualifyTable(TableRef table) {
        if (context == null || context.getDefaultSchema() == null || table.getSchema() != null
                || table.getCatalog() != null) {
            return table;
        }
        return new TableRef(null, context.getDefaultSchema(), table.getName());
    }

    private static TableRef rawTableRef(HiveParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(HiveParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (HiveParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String tableAlias(HiveParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> cteColumnAliases(HiveParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (HiveParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(HiveParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (HiveParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(HiveParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static String cleanIdentifier(String text) {
        String value = text.trim();
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1).replace("``", "`");
        }
        return value;
    }

    private static boolean isHiveGeneratedExpression(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return "current_date".equals(normalized)
                || "current_timestamp".equals(normalized)
                || "true".equals(normalized)
                || "false".equals(normalized)
                || "null".equals(normalized)
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
