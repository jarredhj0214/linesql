package io.github.linesql.dialect.flink;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.flink.antlr.FlinkParser;
import io.github.linesql.dialect.flink.antlr.FlinkParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class FlinkLineageVisitor extends FlinkParserBaseVisitor<Void> {
    private final LineageResult result;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, FlinkParser.WindowSpecContext> namedWindows = new LinkedHashMap<>();
    private final Set<FlinkParser.WindowSpecContext> activeWindows = new LinkedHashSet<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Set<String> incompleteDerivedSchemas = new LinkedHashSet<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final List<Projection> projections = new ArrayList<>();
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private final Map<FlinkParser.RelationContext, Map<String, List<ColumnRef>>> relationJoinKeys =
            new IdentityHashMap<>();
    private Map<String, List<ColumnRef>> usingJoinKeys = Collections.emptyMap();
    private boolean hasNonConcatenatingJoin;
    private TableRef currentDmlTarget;
    private int visibleRelationCount;
    private int setOutputWidth = -1;
    private boolean suppressColumnLineage;
    private FlinkParser.SelectClauseContext positionalProjectionSelect;
    private FlinkParser.SelectClauseContext positionalInsertSelect;
    private Map<FlinkParser.QueryContext, LineageResult> projectionSubqueries;
    private boolean correlatedQuery;
    private ParseContext context;

    FlinkLineageVisitor(LineageResult result) {
        this.result = result;
    }

    void setContext(ParseContext context) {
        this.context = context;
    }

    @Override
    public Void visitStatementDefault(FlinkParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStmt(FlinkParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitHiveMultiInsertStmt(FlinkParser.HiveMultiInsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertDirectoryStmt(FlinkParser.InsertDirectoryStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertDirectoryStatement(FlinkParser.InsertDirectoryStatementContext ctx) {
        visit(ctx.query());
        refreshColumnLineage();
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitHiveMultiInsertStatement(FlinkParser.HiveMultiInsertStatementContext ctx) {
        visit(ctx.relation());
        result.setInputTables(new ArrayList<>(inputTables));
        for (FlinkParser.HiveInsertBranchContext branch : ctx.hiveInsertBranch()) {
            processHiveInsertBranch(branch);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void processHiveInsertBranch(FlinkParser.HiveInsertBranchContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        LineageResult branchResult = new LineageResult();
        FlinkLineageVisitor branchVisitor = new FlinkLineageVisitor(branchResult);
        branchVisitor.setContext(context);
        branchVisitor.cteNames.addAll(cteNames);
        branchVisitor.inputTables.addAll(inputTables);
        branchVisitor.tableAliases.putAll(tableAliases);
        branchVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        branchVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        branchVisitor.derivedAliases.putAll(derivedAliases);
        branchVisitor.derivedReferences.addAll(derivedReferences);
        branchVisitor.visibleRelationCount = visibleRelationCount;
        branchVisitor.visibleRelations.addAll(visibleRelations);
        if (ctx.columnList != null) {
            for (FlinkParser.IdentifierContext id : ctx.columnList.identifier()) {
                branchVisitor.insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        branchVisitor.visit(ctx.querySpecification());
        branchVisitor.refreshColumnLineage();
        branchVisitor.retargetColumnLineage(target);
        branchVisitor.recordPartitionWrites(target, ctx.partitionSpec());
        result.getColumnLineage().addAll(branchResult.getColumnLineage());
        LineageModelUtils.mergeColumnUsages(result, branchResult);
    }

    @Override
    public Void visitInsertStatement(FlinkParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (FlinkParser.IdentifierContext id : ctx.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        if (ctx.query() != null) {
            positionalInsertSelect = insertTargetColumns.isEmpty()
                    ? null : selectClause(ctx.query().queryTerm());
            try {
                visit(ctx.query());
            } finally {
                positionalInsertSelect = null;
            }
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            if (!insertTargetColumns.isEmpty()) {
                List<ColumnLineage> lineages = valuesLineage(ctx.valuesClause(), insertTargetColumns);
                result.setColumnLineage(LineageModelUtils.retargetColumnLineage(lineages, target, insertTargetColumns));
            }
            suppressColumnLineage = true;
        }
        if (ctx.partitionSpec() != null) {
            suppressColumnLineage = true;
            recordPartitionWrites(target, ctx.partitionSpec());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitUpdateStmt(FlinkParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(FlinkParser.UpdateStatementContext ctx) {
        TableRef target = bindDmlTarget(ctx.multipartIdentifier());

        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        collectSubqueryDependencies(ctx, true);

        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), target);
        result.setColumnLineage(assignments);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteStmt(FlinkParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStmt(FlinkParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadDataStmt(FlinkParser.LoadDataStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadDataStatement(FlinkParser.LoadDataStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        recordPartitionWrites(target, ctx.partitionSpec());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void recordPartitionWrites(TableRef target, FlinkParser.PartitionSpecContext partition) {
        if (partition == null) {
            return;
        }
        List<ColumnRef> keys = new ArrayList<>();
        for (FlinkParser.PartitionSpecItemContext item : partition.partitionSpecItem()) {
            String column = cleanIdentifier(item.identifier());
            keys.add(new ColumnRef(target, column));
            if (item.expression() != null) {
                result.getColumnLineage().add(LineageModelUtils.columnLineage(
                        target, column, Collections.emptyList(), item.expression().getText()));
            }
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, keys);
    }

    @Override
    public Void visitMergeStatement(FlinkParser.MergeStatementContext ctx) {
        // Output table = MERGE INTO target
        TableRef target = tableRef(ctx.multipartIdentifier(0));
        outputTables.add(target);
        addInputTable(target, ctx.tableAlias(0), true);

        int sourceIndex = visibleRelations.size();
        if (ctx.query() == null) {
            readNamedRelation(ctx.multipartIdentifier(1), ctx.tableAlias(1));
        } else {
            addDerivedQueryReference(ctx.query(), ctx.tableAlias(1));
        }
        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        // Collect column lineage from WHEN MATCHED THEN UPDATE SET assignments
        TableRef sourceTable = visibleRelations.get(sourceIndex).table;
        TableRef previousTarget = currentDmlTarget;
        List<ColumnLineage> assignments = new ArrayList<>();
        for (FlinkParser.MergeClauseContext clause : ctx.mergeClause()) {
            FlinkParser.MergeNotMatchedActionContext notMatched = clause.mergeNotMatchedAction();
            // An unmatched insert reads unqualified columns from the source row.
            currentDmlTarget = notMatched == null ? previousTarget : sourceTable;
            if (clause.expression() != null) {
                addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(clause.expression()));
            }
            if (clause.mergeMatchedAction() != null
                    && clause.mergeMatchedAction().assignmentList() != null) {
                FlinkParser.AssignmentListContext updates = clause.mergeMatchedAction().assignmentList();
                collectSubqueryDependencies(updates, true);
                assignments.addAll(readAssignments(updates, target));
            }
            if (notMatched != null) {
                assignments.addAll(readMergeInsertValues(notMatched, target));
            }
        }
        currentDmlTarget = previousTarget;
        if (!assignments.isEmpty()) {
            result.setColumnLineage(assignments);
        }

        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeClause(FlinkParser.MergeClauseContext ctx) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(ctx.expression()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteStatement(FlinkParser.DeleteStatementContext ctx) {
        bindDmlTarget(ctx.multipartIdentifier());

        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }

        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private TableRef bindDmlTarget(FlinkParser.MultipartIdentifierContext name) {
        TableRef target = tableRef(name);
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        tableAliases.put(localBindingKey(target.getName()), target);
        for (String qualified : qualifiedTableBindings(target)) {
            tableAliases.put(qualified, target);
        }
        return target;
    }

    @Override
    public Void visitCreateTableStmt(FlinkParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(FlinkParser.CreateTableStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableModelSemantics(target, ctx.tableElementList(), ctx.distributionClause(), ctx.partitionedByClause());
        if (ctx.likeClause() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_LIKE);
            TableRef source = tableRef(ctx.likeClause().source);
            inputTables.add(source);
            registerLikeTableSchema(target, source, ctx);
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            List<String> names = visitCreateTableQuery(ctx.query(), target, ctx.tableElementList());
            if (names.isEmpty() && context != null && ctx.REPLACE() != null && ctx.existsKeyword() == null) {
                registeredRelations(false).remove(storedRelationKey(target, false));
            } else {
                registerTableSchema(target, names, !ctx.TEMPORARY().isEmpty(), ctx.existsKeyword() != null);
            }
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            registerTableSchema(target, ctx.tableElementList(), !ctx.TEMPORARY().isEmpty(), ctx.existsKeyword() != null);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateCatalogStmt(FlinkParser.CreateCatalogStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        if (context != null) {
            FlinkParser.CreateCatalogStatementContext catalog = ctx.createCatalogStatement();
            String name = cleanIdentifier(catalog.identifier());
            if (catalog.existsKeyword() == null || !catalogDefaults().containsKey(name)) {
                catalogDefaults().put(name, catalogDefaultSchema(catalog.propertyList()));
            }
        }
        return null;
    }

    @Override
    public Void visitCreateDatabaseStmt(FlinkParser.CreateDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_SCHEMA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateFunctionStmt(FlinkParser.CreateFunctionStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        return null;
    }

    @Override
    public Void visitCreateModelStmt(FlinkParser.CreateModelStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateMaterializedTableStmt(FlinkParser.CreateMaterializedTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateMaterializedTableStatement(FlinkParser.CreateMaterializedTableStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableModelSemantics(target, ctx.tableElementList(), ctx.distributionClause(), ctx.partitionedByClause());
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            List<String> columns = visitCreateTableQuery(ctx.query(), target, ctx.tableElementList());
            boolean ifNotExists = ctx.existsKeyword() != null;
            if (ctx.ALTER() != null && !ifNotExists) {
                replaceMaterializedTableSchema(target, columns);
            } else {
                registerTableSchema(target, columns, false, ifNotExists);
            }
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            registerTableSchema(target, ctx.tableElementList(), false, ctx.existsKeyword() != null);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateViewStmt(FlinkParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(FlinkParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (FlinkParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        positionalProjectionSelect = explicitSelectClause(ctx.query().queryTerm());
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        registerCurrentView(target, ctx.TEMPORARY() != null, ctx.existsKeyword() != null);
        return null;
    }

    @Override
    public Void visitDropTableStmt(FlinkParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(FlinkParser.DropTableStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        outputTables.add(table);
        dropRegisteredRelation(table, ctx.TEMPORARY() != null, false);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(FlinkParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(FlinkParser.DropViewStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        outputTables.add(table);
        dropRegisteredRelation(table, ctx.TEMPORARY() != null, true);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropDatabaseStmt(FlinkParser.DropDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.DROP_SCHEMA);
        return null;
    }

    @Override
    public Void visitDropFunctionStmt(FlinkParser.DropFunctionStmtContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitDropCatalogStmt(FlinkParser.DropCatalogStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        if (context != null) {
            String name = cleanIdentifier(ctx.dropCatalogStatement().identifier());
            if (!name.equals(context.getDefaultCatalog())) {
                catalogDefaults().remove(name);
            }
        }
        return null;
    }

    @Override
    public Void visitDropModelStmt(FlinkParser.DropModelStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropMaterializedTableStmt(FlinkParser.DropMaterializedTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropMaterializedTableStatement(FlinkParser.DropMaterializedTableStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        dropRegisteredRelation(target, false, false);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(FlinkParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(FlinkParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(FlinkParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(FlinkParser.AlterTableRenameContext ctx) {
        List<FlinkParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        TableRef source = tableRef(ids.get(0));
        TableRef target = renamedRelation(source, ids.get(1));
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(source);
        outputTables.add(target);
        renameRegisteredTable(source, target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(FlinkParser.AlterTableAddColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.tableElement());
        addSchemaColumn(target, ctx.tableElement(), ctx.columnPosition());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableChangeColumn(FlinkParser.AlterTableChangeColumnContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableRenameColumn(FlinkParser.AlterTableRenameColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        List<String> columns = registeredTableColumns(target);
        if (columns != null) {
            int index = columns.indexOf(cleanIdentifier(ctx.identifier(0)));
            String newName = cleanIdentifier(ctx.identifier(1));
            if (index >= 0 && !columns.contains(newName)) {
                columns.set(index, newName);
                registerTableSchema(target, columns, false, false);
            }
        }
        return addAlteredTable(target);
    }

    @Override
    public Void visitAlterTableAddConstraint(FlinkParser.AlterTableAddConstraintContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addIdentifierListUsages(target, ctx.identifierList());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddHiveColumns(FlinkParser.AlterTableAddHiveColumnsContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableAddColumns(FlinkParser.AlterTableAddColumnsContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.alterTableElementList());
        addSchemaColumns(target, ctx.alterTableElementList());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableReplaceColumns(FlinkParser.AlterTableReplaceColumnsContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableModifyColumn(FlinkParser.AlterTableModifyColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.tableElement());
        moveSchemaColumn(target, ctx.tableElement(), ctx.columnPosition());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableModifyColumns(FlinkParser.AlterTableModifyColumnsContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.alterTableElementList());
        moveSchemaColumns(target, ctx.alterTableElementList());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableDropColumn(FlinkParser.AlterTableDropColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        dropSchemaColumns(target, Collections.singletonList(cleanIdentifier(ctx.identifier())));
        return addAlteredTable(target);
    }

    @Override
    public Void visitAlterTableDropColumns(FlinkParser.AlterTableDropColumnsContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        dropSchemaColumns(target, identifierNames(ctx.identifierList()));
        return addAlteredTable(target);
    }

    @Override
    public Void visitAlterTableAddPartition(FlinkParser.AlterTableAddPartitionContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableDropPartition(FlinkParser.AlterTableDropPartitionContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableRenamePartition(FlinkParser.AlterTableRenamePartitionContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTablePartitionSetLocation(FlinkParser.AlterTablePartitionSetLocationContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTablePartitionSetFileFormat(FlinkParser.AlterTablePartitionSetFileFormatContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableAddDistribution(FlinkParser.AlterTableAddDistributionContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.distributionClause() != null) {
            addDistributionUsages(target, ctx.distributionClause());
        } else {
            addIdentifierListUsages(target, firstIdentifierList(ctx.alterDistributionClause()));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableDropDistribution(FlinkParser.AlterTableDropDistributionContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableDropPrimaryKey(FlinkParser.AlterTableDropPrimaryKeyContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableDropConstraint(FlinkParser.AlterTableDropConstraintContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableDropWatermark(FlinkParser.AlterTableDropWatermarkContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableResetProperties(FlinkParser.AlterTableResetPropertiesContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableOther(FlinkParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewStmt(FlinkParser.AlterViewStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterViewRename(FlinkParser.AlterViewRenameContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        TableRef source = tableRef(ctx.multipartIdentifier(0));
        TableRef target = renamedRelation(source, ctx.multipartIdentifier(1));
        inputTables.add(source);
        outputTables.add(target);
        renameRegisteredView(source, target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewAsQuery(FlinkParser.AlterViewAsQueryContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        positionalProjectionSelect = explicitSelectClause(ctx.query().queryTerm());
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        if (context != null && !registeredRelations(true).containsKey(storedRelationKey(target, true))) {
            LineageResult previous = registeredRelations(false).get(storedRelationKey(target, false));
            if (previous == null || previous.getStatementType() == StatementType.CREATE_VIEW) {
                registerCurrentView(target, false, false);
            }
        }
        return null;
    }

    @Override
    public Void visitAlterDatabaseStmt(FlinkParser.AlterDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterFunctionStmt(FlinkParser.AlterFunctionStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_ROUTINE);
        return null;
    }

    @Override
    public Void visitAlterCatalogStmt(FlinkParser.AlterCatalogStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        if (context != null) {
            FlinkParser.AlterCatalogStatementContext catalog = ctx.alterCatalogStatement();
            String name = cleanIdentifier(catalog.identifier());
            if (catalog.propertyList() != null) {
                String schema = catalogDefaultSchema(catalog.propertyList());
                if (schema != null) {
                    catalogDefaults().put(name, schema);
                }
            } else if (catalog.stringList() != null && catalog.stringList().string().stream()
                    .map(FlinkLineageVisitor::catalogOptionText).anyMatch("default-database"::equals)) {
                catalogDefaults().put(name, null);
            }
        }
        return null;
    }

    @Override
    public Void visitAlterModelStmt(FlinkParser.AlterModelStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterMaterializedTableStmt(FlinkParser.AlterMaterializedTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterMaterializedTableStatement(FlinkParser.AlterMaterializedTableStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        FlinkParser.MaterializedTableActionContext action = ctx.materializedTableAction();
        addTableElementSemantics(target, action.tableElement());
        addTableElementSemantics(target, action.alterTableElementList());
        addDistributionUsages(target, action.distributionClause());
        addIdentifierListUsages(target, firstIdentifierList(action.alterDistributionClause()));
        if (action.partitionSpec() != null) {
            List<ColumnRef> columns = new ArrayList<>();
            for (FlinkParser.PartitionSpecItemContext item : action.partitionSpec().partitionSpecItem()) {
                columns.add(new ColumnRef(target, cleanIdentifier(item.identifier())));
            }
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, columns);
        }
        if (action.query() != null) {
            replaceMaterializedTableSchema(target, visitCreateTableQuery(action.query(), target, null));
        } else if (action.ADD() != null && action.tableElement() != null) {
            addSchemaColumn(target, action.tableElement(), action.columnPosition());
        } else if (action.ADD() != null && action.alterTableElementList() != null) {
            addSchemaColumns(target, action.alterTableElementList());
        } else if (action.MODIFY() != null && action.tableElement() != null) {
            moveSchemaColumn(target, action.tableElement(), action.columnPosition());
        } else if (action.MODIFY() != null && action.alterTableElementList() != null) {
            moveSchemaColumns(target, action.alterTableElementList());
        } else if (action.columnName != null) {
            dropSchemaColumns(target, Collections.singletonList(cleanIdentifier(action.columnName)));
        } else if (action.DROP() != null && action.identifierList() != null) {
            dropSchemaColumns(target, identifierNames(action.identifierList()));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private Map<String, String> catalogDefaults() {
        return context.getCatalogDefaultSchemas(SqlDialect.FLINK);
    }

    private static String catalogDefaultSchema(FlinkParser.PropertyListContext properties) {
        for (FlinkParser.PropertyContext property : properties.property()) {
            if ("default-database".equals(catalogOptionText(property.string(0)))) {
                String schema = catalogOptionText(property.string(1));
                return schema.isEmpty() ? null : schema;
            }
        }
        return null;
    }

    private static String catalogOptionText(FlinkParser.StringContext literal) {
        String value = literal.getText();
        String quote = value.substring(0, 1);
        return value.substring(1, value.length() - 1).replace(quote + quote, quote);
    }

    @Override
    public Void visitUseStmt(FlinkParser.UseStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
        if (context == null) {
            return null;
        }
        FlinkParser.UseStatementContext statement = ctx.useStatement();
        if (statement.CATALOG() != null) {
            String catalog = cleanIdentifier(statement.identifier());
            if (!catalog.equals(context.getDefaultCatalog())) {
                context.setDefaultCatalog(catalog);
                context.setDefaultSchema(catalogDefaults().get(catalog));
            }
        } else {
            List<String> namespace = identifierParts(statement.multipartIdentifier());
            if (namespace.size() == 2) {
                context.setDefaultCatalog(namespace.get(0));
            }
            if (namespace.size() <= 2) {
                context.setDefaultSchema(namespace.get(namespace.size() - 1));
            }
        }
        return null;
    }

    @Override
    public Void visitShowStmt(FlinkParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(FlinkParser.ShowStatementContext ctx) {
        if (shouldCollectShowTableInput(ctx)) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof FlinkParser.MultipartIdentifierContext) {
                    TableRef table = tableRef((FlinkParser.MultipartIdentifierContext) child);
                    inputTables.add(table);
                    addPartitionMetadataUsages(table, ctx.partitionSpec());
                    break;
                }
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private boolean shouldCollectShowTableInput(FlinkParser.ShowStatementContext ctx) {
        String text = ctx.getText().toLowerCase(Locale.ROOT);
        return text.startsWith("showcreatetable")
                || text.startsWith("showcolumns")
                || text.startsWith("showpartitions")
                || text.startsWith("showcreateview")
                || text.startsWith("showcreatematerializedtable")
                || text.startsWith("showcreateoraltermaterializedtable");
    }

    @Override
    public Void visitDescribeStmt(FlinkParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeStmt(FlinkParser.AnalyzeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeStatement(FlinkParser.AnalyzeStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        inputTables.add(table);
        addPartitionMetadataUsages(table, ctx.partitionSpec());
        List<String> columns = Collections.emptyList();
        if (ctx.identifierList() != null) {
            columns = identifierNames(ctx.identifierList());
        } else if (ctx.ALL() != null) {
            columns = registeredTableColumns(table);
            if (columns == null) {
                columns = Collections.singletonList("*");
            }
        }
        addMetadataColumnUsages(table, columns);
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private void addPartitionMetadataUsages(TableRef table, FlinkParser.PartitionSpecContext partition) {
        if (partition != null) {
            List<String> names = new ArrayList<>();
            for (FlinkParser.PartitionSpecItemContext item : partition.partitionSpecItem()) {
                names.add(cleanIdentifier(item.identifier()));
            }
            addMetadataColumnUsages(table, names);
        }
    }

    private void addMetadataColumnUsages(TableRef table, List<String> columns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : columns) {
            refs.add(new ColumnRef(table, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.READ_METADATA, refs);
    }

    @Override
    public Void visitDescribeStatement(FlinkParser.DescribeStatementContext ctx) {
        if (ctx.multipartIdentifier() != null && shouldCollectDescribeTableInput(ctx)) {
            inputTables.add(tableRef(ctx.multipartIdentifier()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private boolean shouldCollectDescribeTableInput(FlinkParser.DescribeStatementContext ctx) {
        String text = ctx.getText().toLowerCase(Locale.ROOT);
        return !text.startsWith("describecatalog")
                && !text.startsWith("desccatalog")
                && !text.startsWith("describefunction")
                && !text.startsWith("descfunction")
                && !text.startsWith("describemodel")
                && !text.startsWith("descmodel")
                && !text.startsWith("describejob")
                && !text.startsWith("descjob");
    }

    @Override
    public Void visitCommentStmt(FlinkParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(FlinkParser.CommentStatementContext ctx) {
        FlinkParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
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

    @Override
    public Void visitExplainStmt(FlinkParser.ExplainStmtContext ctx) {
        visitChildren(ctx);
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitPlanStmt(FlinkParser.PlanStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCompilePlanStatement(FlinkParser.CompilePlanStatementContext ctx) {
        visit(ctx.statement());
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitCompileAndExecutePlanStatement(FlinkParser.CompileAndExecutePlanStatementContext ctx) {
        result.setStatementType(StatementType.INSERT);
        if (ctx.insertStatement() != null) {
            return visit(ctx.insertStatement());
        }
        return visit(ctx.executeStatementSet());
    }

    @Override
    public Void visitExecutePlanStatement(FlinkParser.ExecutePlanStatementContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCallStmt(FlinkParser.CallStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitJobStmt(FlinkParser.JobStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitUtilityStmt(FlinkParser.UtilityStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitUtilityStatement(FlinkParser.UtilityStatementContext ctx) {
        if (ctx.SHOW() != null) {
            result.setStatementType(StatementType.READ_METADATA);
            if (ctx.multipartIdentifier() != null) {
                inputTables.add(tableRef(ctx.multipartIdentifier()));
                result.setInputTables(new ArrayList<>(inputTables));
            }
        } else {
            result.setStatementType(StatementType.CONTROL);
        }
        return null;
    }

    @Override
    public Void visitExecuteStmtSet(FlinkParser.ExecuteStmtSetContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExecuteStatementSet(FlinkParser.ExecuteStatementSetContext ctx) {
        List<FlinkParser.InsertStatementContext> inserts = ctx.insertStatement();
        if (inserts != null && !inserts.isEmpty()) {
            for (FlinkParser.InsertStatementContext insert : inserts) {
                processStatementSetInsert(insert);
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void processStatementSetInsert(FlinkParser.InsertStatementContext insert) {
        LineageResult branchResult = new LineageResult();
        FlinkLineageVisitor branchVisitor = new FlinkLineageVisitor(branchResult);
        branchVisitor.setContext(context);
        branchVisitor.cteNames.addAll(cteNames);
        branchVisitor.tableAliases.putAll(tableAliases);
        branchVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        branchVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        branchVisitor.derivedAliases.putAll(derivedAliases);
        branchVisitor.derivedReferences.addAll(derivedReferences);
        branchVisitor.visibleRelationCount = visibleRelationCount;
        branchVisitor.visibleRelations.addAll(visibleRelations);
        branchVisitor.visitInsertStatement(insert);

        inputTables.addAll(branchResult.getInputTables());
        outputTables.addAll(branchResult.getOutputTables());
        result.getColumnLineage().addAll(branchResult.getColumnLineage());
        LineageModelUtils.mergeColumnUsages(result, branchResult);
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(FlinkParser.CtesContext ctx) {
        for (FlinkParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = localBindingKey(cleanIdentifier(namedQuery.name));
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
            cteNames.add(cteName);
        }
        return null;
    }

    @Override
    public Void visitSetOperation(FlinkParser.SetOperationContext ctx) {
        FlinkLineageVisitor left = analyzeSetOperand(ctx.left);
        FlinkLineageVisitor right = analyzeSetOperand(ctx.right);
        LineageResult leftResult = left.result;
        LineageResult rightResult = right.result;
        int width = left.knownQueryOutputWidth(ctx.left);
        setOutputWidth = width >= 0 && width == right.knownQueryOutputWidth(ctx.right)
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
        boolean unresolvedWildcard = setOutputWidth < 0
                && (hasWildcardOutput(leftResult) || hasWildcardOutput(rightResult));
        result.setColumnLineage(unresolvedWildcard
                ? mergeUnresolvedSetLineage(leftResult, rightResult)
                : LineageModelUtils.mergeSetColumnLineage(leftResult, rightResult));
        return null;
    }

    private static boolean hasWildcardOutput(LineageResult operand) {
        for (ColumnLineage column : operand.getColumnLineage()) {
            String expression = column.getExpression();
            if ("*".equals(column.getTarget().getName())
                    && (expression == null || "*".equals(expression) || expression.endsWith(".*"))) {
                return true;
            }
        }
        return false;
    }

    private static List<ColumnLineage> mergeUnresolvedSetLineage(LineageResult left, LineageResult right) {
        List<ColumnRef> rightSources = setProjectionSources(right);
        if (hasWildcardOutput(left)) {
            List<ColumnRef> sources = LineageModelUtils.mergeColumnRefs(setProjectionSources(left), rightSources);
            return Collections.singletonList(LineageModelUtils.columnLineage(null, "*", sources, null));
        }

        // The left operand supplies output names. The right wildcard has no reliable column positions.
        List<ColumnRef> rightWildcards = new ArrayList<>();
        for (ColumnRef source : rightSources) {
            if (source.getTable() != null) {
                rightWildcards.add(new ColumnRef(source.getTable(), "*"));
            }
        }
        List<ColumnRef> distinct = LineageModelUtils.mergeColumnRefs(Collections.emptyList(), rightWildcards);
        List<ColumnLineage> columns = new ArrayList<>();
        for (ColumnLineage column : left.getColumnLineage()) {
            List<ColumnRef> sources = LineageModelUtils.mergeColumnRefs(column.getSources(), distinct);
            columns.add(LineageModelUtils.columnLineage(column.getTarget(), sources, column.getExpression()));
        }
        return columns;
    }

    private static List<ColumnRef> setProjectionSources(LineageResult operand) {
        List<ColumnRef> sources = new ArrayList<>();
        for (ColumnLineage column : operand.getColumnLineage()) {
            sources.addAll(column.getSources());
        }
        return sources;
    }

    @Override
    public Void visitTableName(FlinkParser.TableNameContext ctx) {
        readNamedRelation(ctx.multipartIdentifier(), ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitTableQuery(FlinkParser.TableQueryContext ctx) {
        projections.add(wildcardProjection(null, "*", 0));
        readNamedRelation(ctx.multipartIdentifier(), null);
        return null;
    }

    private void readNamedRelation(FlinkParser.MultipartIdentifierContext name, FlinkParser.TableAliasContext alias) {
        String cte = cteReference(name);
        if (cte != null) {
            addCteReference(cte, alias);
            return;
        }
        TableRef table = tableRef(name);
        LineageResult relation = registeredRelation(table);
        if (relation != null) {
            addRegisteredRelationReference(table, alias, relation);
            return;
        }
        addInputTable(table, alias, true);
    }

    @Override
    public Void visitTemporalTableName(FlinkParser.TemporalTableNameContext ctx) {
        readNamedRelation(ctx.multipartIdentifier(), ctx.tableAlias());
        addTemporalColumnUsages(ctx.temporalClause());
        return null;
    }

    @Override
    public Void visitTemporalClause(FlinkParser.TemporalClauseContext ctx) {
        addTemporalColumnUsages(ctx);
        return null;
    }

    @Override
    public Void visitMatchRecognizeRelation(FlinkParser.MatchRecognizeRelationContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        addInputTable(table, false);
        FlinkParser.MatchRecognizeClauseContext clause = ctx.matchRecognizeClause();
        if (clause.expressionList() != null) {
            addPatternColumnUsages(ColumnUsageType.WINDOW_PARTITION_BY,
                    sourceColumns(clause.expressionList()),
                    table);
        }
        for (FlinkParser.SortItemContext sortItem : clause.sortItem()) {
            addPatternColumnUsages(ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression()),
                    table);
        }
        for (FlinkParser.MatchDefineContext define : clause.matchDefine()) {
            addPatternColumnUsages(ColumnUsageType.WHERE,
                    sourceColumns(define.expression()),
                    table);
        }
        String alias = tableAlias(ctx.tableAlias());
        if (alias == null) {
            return null;
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        if (clause.expressionList() != null) {
            for (SourceColumn partitionColumn : sourceColumns(clause.expressionList())) {
                List<ColumnRef> refs = new ArrayList<>();
                refs.add(new ColumnRef(table, partitionColumn.name));
                columns.put(partitionColumn.name, refs);
            }
        }
        for (FlinkParser.MatchMeasureContext measure : clause.matchMeasure()) {
            List<ColumnRef> refs = patternMeasureRefs(measure.expression(), table);
            columns.put(cleanIdentifier(measure.identifier()), refs);
        }
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        derivedColumnLineage.put(normalizedAlias, columns);
        incompleteDerivedSchemas.add(normalizedAlias);
        derivedReferences.add(normalizedAlias);
        derivedAliases.put(normalizedAlias, normalizedAlias);
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(normalizedAlias));
        refreshColumnLineage();
        return null;
    }

    @Override
    public Void visitValuesQuery(FlinkParser.ValuesQueryContext ctx) {
        result.setColumnLineage(valuesLineage(ctx.valuesClause(), Collections.emptyList()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private List<ColumnLineage> valuesLineage(List<FlinkParser.ValuesClauseContext> rows, List<String> names) {
        int width = names.isEmpty() ? rows.get(0).expressionList().expression().size() : names.size();
        List<List<ColumnRef>> sources = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            sources.add(Collections.emptyList());
        }
        for (FlinkParser.ValuesClauseContext row : rows) {
            collectSubqueryInputs(row);
            List<FlinkParser.ExpressionContext> expressions = row.expressionList().expression();
            for (int i = 0; i < Math.min(width, expressions.size()); i++) {
                List<ColumnRef> refs = resolveSources(sourceColumns(expressions.get(i)));
                if (refs != null) {
                    sources.set(i, LineageModelUtils.mergeColumnRefs(sources.get(i), refs));
                }
            }
        }
        List<ColumnLineage> lineages = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            String name = names.isEmpty() ? "EXPR$" + i : names.get(i);
            lineages.add(LineageModelUtils.columnLineage(null, name, sources.get(i), null));
        }
        return lineages;
    }

    @Override
    public Void visitAliasedQuery(FlinkParser.AliasedQueryContext ctx) {
        addDerivedQueryReference(ctx.query(), ctx.tableAlias());
        return null;
    }

    private void addDerivedQueryReference(FlinkParser.QueryContext query, FlinkParser.TableAliasContext aliasCtx) {
        String alias = tableAlias(aliasCtx);
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : localBindingKey(alias);
        registerDerivedRelation(relationName, query, tableAliasColumnAliases(aliasCtx));
        addDerivedReference(relationName, aliasCtx);
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
                    ? joinKeys.get(source.name.toLowerCase(Locale.ROOT)) : null;
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
    public Void visitRelation(FlinkParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        Map<String, List<ColumnRef>> keys = new LinkedHashMap<>(nestedJoinKeys(ctx.relationPrimary()));
        for (FlinkParser.LateralViewClauseContext lateralView : ctx.lateralViewClause()) {
            visit(lateralView);
        }
        for (FlinkParser.JoinRelationContext join : ctx.joinRelation()) {
            FlinkParser.JoinTypeContext type = join.joinType();
            hasNonConcatenatingJoin |= join.NATURAL() != null
                    || (type != null && (type.SEMI_JOIN() != null || type.ANTI() != null));
            int rightStart = visibleRelations.size();
            visit(join.relationPrimary());
            if (join.temporalClause() != null) {
                visit(join.temporalClause());
            }
            Map<String, List<ColumnRef>> rightKeys = nestedJoinKeys(join.relationPrimary());
            FlinkParser.JoinCriteriaContext criteria = join.joinCriteria();
            if (criteria != null && criteria.identifierList() != null) {
                List<VisibleRelation> left = visibleRelations.subList(relationStart, rightStart);
                List<VisibleRelation> right = visibleRelations.subList(rightStart, visibleRelations.size());
                Map<String, List<ColumnRef>> merged = mergedJoinKeys(join, left, right, keys, rightKeys);
                keys.putAll(rightKeys);
                keys.putAll(merged);
            } else {
                keys.putAll(rightKeys);
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
    public Void visitRelationList(FlinkParser.RelationListContext ctx) {
        visitChildren(ctx);
        usingJoinKeys = ctx.relation().size() == 1
                ? relationJoinKeys.getOrDefault(ctx.relation(0), Collections.emptyMap())
                : Collections.emptyMap();
        return null;
    }

    private Map<String, List<ColumnRef>> nestedJoinKeys(FlinkParser.RelationPrimaryContext primary) {
        if (primary instanceof FlinkParser.AliasedRelationContext) {
            return relationJoinKeys.getOrDefault(
                    ((FlinkParser.AliasedRelationContext) primary).relation(), Collections.emptyMap());
        }
        return Collections.emptyMap();
    }

    private Map<String, List<ColumnRef>> mergedJoinKeys(FlinkParser.JoinRelationContext join,
                                                         List<VisibleRelation> left,
                                                         List<VisibleRelation> right,
                                                         Map<String, List<ColumnRef>> leftKeys,
                                                         Map<String, List<ColumnRef>> rightKeys) {
        Map<String, List<ColumnRef>> merged = new LinkedHashMap<>();
        FlinkParser.JoinTypeContext type = join.joinType();
        for (String column : identifierNames(join.joinCriteria().identifierList())) {
            String name = column.toLowerCase(Locale.ROOT);
            List<ColumnRef> leftRefs = joinKeySources(left, leftKeys, column, name);
            List<ColumnRef> rightRefs = joinKeySources(right, rightKeys, column, name);
            List<ColumnRef> both = LineageModelUtils.mergeColumnRefs(leftRefs, rightRefs);
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.JOIN_ON, both);
            List<ColumnRef> sources = both;
            if (type != null && type.RIGHT() != null) {
                sources = rightRefs;
            } else if (type != null && (type.LEFT() != null || type.SEMI_JOIN() != null || type.ANTI() != null)) {
                sources = leftRefs;
            }
            merged.put(name, sources);
        }
        return merged;
    }

    private List<ColumnRef> joinKeySources(List<VisibleRelation> relations,
                                         Map<String, List<ColumnRef>> keys, String column, String name) {
        List<ColumnRef> refs = keys.get(name);
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
    public Void visitLateralViewClause(FlinkParser.LateralViewClauseContext ctx) {
        registerGeneratedRelation(cleanIdentifier(ctx.strictIdentifier().getText()),
                identifierNames(ctx.identifierList()),
                sourceColumns(ctx.expressionList()));
        return null;
    }

    @Override
    public Void visitTableFunction(FlinkParser.TableFunctionContext ctx) {
        if (registerKnownTableFunction(ctx.functionName(), ctx.tableFunctionArgList(), ctx.tableAlias())) {
            return null;
        }
        visitChildren(ctx);
        TableRef source = firstTableFunctionInput(ctx.tableFunctionArgList());
        String alias = tableAlias(ctx.tableAlias());
        if (source != null && alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), source);
            visibleRelationCount++;
            visibleRelations.add(VisibleRelation.table(source));
            refreshColumnLineage();
        }
        return null;
    }

    @Override
    public Void visitBareTableFunction(FlinkParser.BareTableFunctionContext ctx) {
        if (registerKnownTableFunction(ctx.functionName(), ctx.tableFunctionArgList(), ctx.tableAlias())) {
            return null;
        }
        registerGeneratedRelation(ctx.tableAlias(), sourceColumns(ctx.tableFunctionArgList()));
        if (ctx.tableFunctionArgList() != null) {
            visit(ctx.tableFunctionArgList());
        }
        return null;
    }

    @Override
    public Void visitLateralTableFunction(FlinkParser.LateralTableFunctionContext ctx) {
        if (registerKnownTableFunction(ctx.functionName(), ctx.tableFunctionArgList(), ctx.tableAlias())) {
            return null;
        }
        registerGeneratedRelation(ctx.tableAlias(), sourceColumns(ctx.tableFunctionArgList()));
        if (ctx.tableFunctionArgList() != null) {
            visit(ctx.tableFunctionArgList());
        }
        return null;
    }

    @Override
    public Void visitUnnestTableFunction(FlinkParser.UnnestTableFunctionContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            List<String> aliases = tableAliasColumnAliases(ctx.tableAlias());
            List<SourceColumn> sources = sourceColumns(ctx.expression());
            if (ctx.ORDINALITY() == null) {
                registerGeneratedRelation(alias, aliases, sources);
            } else {
                List<ColumnRef> refs = resolveSources(sources);
                Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
                for (int i = 0; i < aliases.size(); i++) {
                    boolean ordinal = i == aliases.size() - 1 && aliases.size() > 1;
                    columns.put(aliases.get(i), ordinal || refs == null ? Collections.emptyList() : refs);
                }
                if (aliases.size() < 2) {
                    columns.put("ordinality", Collections.emptyList());
                }
                if (aliases.isEmpty()) {
                    columns.put("*", refs == null ? Collections.emptyList() : refs);
                }
                registerGeneratedRelation(alias, columns);
            }
        }
        return null;
    }

    @Override
    public Void visitTvfTableArg(FlinkParser.TvfTableArgContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        addInputTable(table, true);
        return null;
    }

    @Override
    public Void visitNamedTableFunctionArg(FlinkParser.NamedTableFunctionArgContext ctx) {
        return visit(ctx.tableFunctionArg());
    }

    @Override
    public Void visitQuerySpecification(FlinkParser.QuerySpecificationContext ctx) {
        Map<String, FlinkParser.WindowSpecContext> previousWindows = new LinkedHashMap<>(namedWindows);
        namedWindows.clear();
        if (ctx.windowClause() != null) {
            for (FlinkParser.NamedWindowContext window : ctx.windowClause().namedWindow()) {
                String name = cleanIdentifier(window.identifier()).toLowerCase(Locale.ROOT);
                namedWindows.put(name, window.windowSpec());
            }
        }
        try {
            if (ctx.fromClause() == null || !containsSubquery(ctx.selectClause())) {
                return visitChildren(ctx);
            }
            // Expose outer bindings while keeping the existing projection-first input order.
            Set<TableRef> previousInputs = new LinkedHashSet<>(inputTables);
            visit(ctx.fromClause());
            Set<TableRef> fromInputs = new LinkedHashSet<>(inputTables);
            inputTables.retainAll(previousInputs);
            for (ParseTree child : ctx.children) {
                if (child != ctx.fromClause()) {
                    visit(child);
                } else {
                    inputTables.addAll(fromInputs);
                    result.setInputTables(new ArrayList<>(inputTables));
                }
            }
            return null;
        } finally {
            namedWindows.clear();
            namedWindows.putAll(previousWindows);
        }
    }

    @Override
    public Void visitScalarSubquery(FlinkParser.ScalarSubqueryContext ctx) {
        mergeSubqueryDependencies(ctx.query());
        return null;
    }

    @Override
    public Void visitExistsExpr(FlinkParser.ExistsExprContext ctx) {
        mergeSubqueryDependencies(ctx.query());
        return null;
    }

    @Override
    public Void visitPredicate(FlinkParser.PredicateContext ctx) {
        if (ctx.query() == null) {
            return visitChildren(ctx);
        }
        mergeSubqueryDependencies(ctx.query());
        return null;
    }

    private void mergeSubqueryDependencies(FlinkParser.QueryContext query) {
        LineageResult inner = lineageForQuery(query);
        inputTables.addAll(inner.getInputTables());
        result.setInputTables(new ArrayList<>(inputTables));
        LineageModelUtils.mergeColumnUsages(result, inner);
    }

    @Override
    public Void visitSelectClause(FlinkParser.SelectClauseContext ctx) {
        if (!containsSubquery(ctx)) {
            collectSelectClauseProjections(ctx);
            return visitChildren(ctx);
        }
        // Projection sources and clause usages read the same subquery in this scope.
        Map<FlinkParser.QueryContext, LineageResult> enclosingSubqueries = projectionSubqueries;
        projectionSubqueries = new IdentityHashMap<>();
        try {
            collectSelectClauseProjections(ctx);
            return visitChildren(ctx);
        } finally {
            projectionSubqueries = enclosingSubqueries;
        }
    }

    @Override
    public Void visitWhereClause(FlinkParser.WhereClauseContext ctx) {
        addInputColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitJoinCriteria(FlinkParser.JoinCriteriaContext ctx) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
            collectSubqueryInputs(ctx.expression());
        } else {
            addUsingColumnUsages(ctx.identifierList());
        }
        return null;
    }

    @Override
    public Void visitGroupByClause(FlinkParser.GroupByClauseContext ctx) {
        for (FlinkParser.GroupingElementContext element : ctx.groupingElement()) {
            addInputColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(element));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(FlinkParser.HavingClauseContext ctx) {
        addInputColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitQualifyClause(FlinkParser.QualifyClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression(), true));
        return visit(ctx.expression());
    }

    @Override
    public Void visitWindowSpec(FlinkParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (FlinkParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (FlinkParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(FlinkParser.QueryOrganizationContext ctx) {
        FlinkParser.QueryTermContext term = unwrappedQueryTerm(
                ((FlinkParser.QueryContext) ctx.getParent()).queryTerm());
        boolean setQuery = term instanceof FlinkParser.SetOperationContext;
        for (FlinkParser.SortItemContext sortItem : ctx.sortItem()) {
            String key = sortItem.expression().getText();
            if (key.matches("[0-9]+")) {
                addOrdinalOrderByUsage(term, key);
            } else if (setQuery) {
                LineageModelUtils.addSetOrderByUsage(result, cleanIdentifier(key), true);
            } else {
                addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression(), true));
            }
        }
        return visitChildren(ctx);
    }

    private void addOrdinalOrderByUsage(FlinkParser.QueryTermContext term, String key) {
        int index;
        try {
            index = Integer.parseInt(key) - 1;
        } catch (NumberFormatException ignored) {
            return;
        }
        if (index < 0) {
            return;
        }
        if (term instanceof FlinkParser.SetOperationContext) {
            List<ColumnLineage> columns = result.getColumnLineage();
            if (index < columns.size() && columns.size() == setOutputWidth) {
                LineageModelUtils.addColumnUsages(result, ColumnUsageType.ORDER_BY, columns.get(index).getSources());
            }
            return;
        }
        if (queryPrimary(term) instanceof FlinkParser.TableQueryContext) {
            List<Map.Entry<String, List<ColumnRef>>> columns = knownOrdinalWildcard(wildcardProjection(null, "*", 0));
            if (columns != null) {
                addOrdinalWildcardUsage(columns, index);
            }
            return;
        }
        for (FlinkParser.SelectItemContext item : selectItems(term)) {
            if (item instanceof FlinkParser.SelectExpressionContext) {
                if (index-- == 0) {
                    FlinkParser.SelectExpressionContext selected = (FlinkParser.SelectExpressionContext) item;
                    addInputColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(selected.expression()));
                    return;
                }
                continue;
            }
            Projection wildcard = wildcardProjection(item, -1);
            if (wildcard == null) {
                return;
            }
            List<Map.Entry<String, List<ColumnRef>>> columns = knownOrdinalWildcard(wildcard);
            if (columns == null) {
                return;
            }
            if (index < columns.size()) {
                addOrdinalWildcardUsage(columns, index);
                return;
            }
            index -= columns.size();
        }
    }

    private List<Map.Entry<String, List<ColumnRef>>> knownOrdinalWildcard(Projection wildcard) {
        if (wildcard.sourceColumns.size() == 1 && wildcard.sourceColumns.get(0).qualifier == null
                && canCombineWildcardInputs()) {
            return concatenatedWildcardEntries();
        }
        Map<String, List<ColumnRef>> columns = wildcardProjectionColumns(wildcard, true);
        return columns == null || columns.containsKey("*") ? null : new ArrayList<>(columns.entrySet());
    }

    private void addOrdinalWildcardUsage(List<Map.Entry<String, List<ColumnRef>>> columns, int index) {
        if (index < columns.size()) {
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.ORDER_BY, columns.get(index).getValue());
        }
    }

    private int knownQueryOutputWidth(FlinkParser.QueryTermContext term) {
        FlinkParser.QueryTermContext unwrapped = unwrappedQueryTerm(term);
        if (unwrapped instanceof FlinkParser.SetOperationContext) {
            return setOutputWidth;
        }
        int explicitWidth = explicitSetOutputWidth(unwrapped);
        if (explicitWidth >= 0) {
            return explicitWidth;
        }
        if (queryPrimary(unwrapped) instanceof FlinkParser.TableQueryContext) {
            List<Map.Entry<String, List<ColumnRef>>> columns = knownOrdinalWildcard(wildcardProjection(null, "*", 0));
            return columns == null ? -1 : columns.size();
        }
        List<FlinkParser.SelectItemContext> items = selectItems(unwrapped);
        if (items.isEmpty()) {
            return -1;
        }
        int width = 0;
        for (FlinkParser.SelectItemContext item : items) {
            if (item instanceof FlinkParser.SelectExpressionContext) {
                width++;
                continue;
            }
            List<Map.Entry<String, List<ColumnRef>>> columns = knownOrdinalWildcard(wildcardProjection(item, -1));
            if (columns == null) {
                return -1;
            }
            width += columns.size();
        }
        return width;
    }

    private static int explicitSetOutputWidth(FlinkParser.QueryTermContext term) {
        if (term instanceof FlinkParser.SetOperationContext) {
            FlinkParser.SetOperationContext set = (FlinkParser.SetOperationContext) term;
            int width = explicitSetOutputWidth(set.left);
            return width == explicitSetOutputWidth(set.right) ? width : -1;
        }
        if (!(term instanceof FlinkParser.QueryTermDefaultContext)) {
            return -1;
        }
        FlinkParser.QueryPrimaryContext primary = ((FlinkParser.QueryTermDefaultContext) term).queryPrimary();
        if (primary instanceof FlinkParser.SubqueryPrimaryContext) {
            return explicitSetOutputWidth(((FlinkParser.SubqueryPrimaryContext) primary).query().queryTerm());
        }
        if (primary instanceof FlinkParser.ValuesQueryContext) {
            List<FlinkParser.ValuesClauseContext> rows = ((FlinkParser.ValuesQueryContext) primary).valuesClause();
            int width = rows.get(0).expressionList().expression().size();
            return rows.stream().allMatch(row -> row.expressionList().expression().size() == width) ? width : -1;
        }
        FlinkParser.SelectClauseContext select = explicitSelectClause(term);
        return select == null ? -1 : select.selectItemList().selectItem().size();
    }

    private static List<FlinkParser.SelectItemContext> selectItems(FlinkParser.QueryTermContext term) {
        FlinkParser.SelectClauseContext select = selectClause(term);
        if (select == null || select.selectItemList() == null) {
            return Collections.emptyList();
        }
        return select.selectItemList().selectItem();
    }

    @Override
    public Void visitHiveQueryOrganization(FlinkParser.HiveQueryOrganizationContext ctx) {
        if (ctx.expressionList() != null) {
            for (FlinkParser.ExpressionContext expression : ctx.expressionList().expression()) {
                addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(expression));
            }
        }
        for (FlinkParser.SortItemContext sortItem : ctx.sortItem()) {
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

    private Void addAlteredTable(FlinkParser.MultipartIdentifierContext ctx) {
        return addAlteredTable(tableRef(ctx));
    }

    private Void addAlteredTable(TableRef target) {
        outputTables.add(target);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addTableModelSemantics(
            TableRef target,
            FlinkParser.TableElementListContext tableElements,
            FlinkParser.DistributionClauseContext distribution,
            FlinkParser.PartitionedByClauseContext partitioning) {
        addTableElementSemantics(target, tableElements);
        addDistributionUsages(target, distribution);
        addPartitionUsages(target, partitioning);
    }

    private void addTableElementSemantics(TableRef target, FlinkParser.TableElementListContext ctx) {
        if (ctx == null) {
            return;
        }
        for (FlinkParser.TableElementContext element : ctx.tableElement()) {
            addTableElementSemantics(target, element);
        }
    }

    private void addTableElementSemantics(TableRef target, FlinkParser.AlterTableElementListContext ctx) {
        if (ctx == null) {
            return;
        }
        for (FlinkParser.AlterTableElementContext element : ctx.alterTableElement()) {
            addTableElementSemantics(target, element.tableElement());
        }
    }

    private void addTableElementSemantics(TableRef target, FlinkParser.TableElementContext element) {
        if (target == null || element == null) {
            return;
        }
        if (element instanceof FlinkParser.ComputedColumnDefinitionContext) {
            FlinkParser.ComputedColumnDefinitionContext computed =
                    (FlinkParser.ComputedColumnDefinitionContext) element;
            addSameTableLineage(target,
                    cleanIdentifier(computed.identifier()),
                    sourceColumns(computed.expression()),
                    computed.expression().getText());
        } else if (element instanceof FlinkParser.WatermarkDefinitionContext) {
            FlinkParser.WatermarkDefinitionContext watermark =
                    (FlinkParser.WatermarkDefinitionContext) element;
            List<ColumnRef> refs = sameTableRefs(target, sourceColumns(watermark.expression()));
            refs.add(new ColumnRef(target, cleanIdentifier(watermark.identifier())));
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, distinctColumnRefs(refs));
        } else if (element instanceof FlinkParser.PrimaryKeyDefinitionContext) {
            FlinkParser.PrimaryKeyDefinitionContext primaryKey =
                    (FlinkParser.PrimaryKeyDefinitionContext) element;
            addIdentifierListUsages(target, primaryKey.identifierList());
        } else if (element instanceof FlinkParser.ColumnDefinitionContext) {
            FlinkParser.ColumnDefinitionContext column = (FlinkParser.ColumnDefinitionContext) element;
            if (hasInlinePrimaryKey(column)) {
                List<ColumnRef> refs = new ArrayList<>();
                refs.add(new ColumnRef(target, cleanIdentifier(column.identifier())));
                LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
            }
        }
    }

    private void addDistributionUsages(TableRef target, FlinkParser.DistributionClauseContext ctx) {
        if (ctx == null || ctx.identifierList() == null) {
            return;
        }
        addIdentifierListUsages(target, ctx.identifierList());
    }

    private FlinkParser.IdentifierListContext firstIdentifierList(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (tree instanceof FlinkParser.IdentifierListContext) {
            return (FlinkParser.IdentifierListContext) tree;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            FlinkParser.IdentifierListContext found = firstIdentifierList(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void addPartitionUsages(TableRef target, FlinkParser.PartitionedByClauseContext ctx) {
        if (ctx == null || ctx.tableElementList() == null) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (FlinkParser.TableElementContext element : ctx.tableElementList().tableElement()) {
            if (element instanceof FlinkParser.ColumnDefinitionContext) {
                refs.add(new ColumnRef(target,
                        cleanIdentifier(((FlinkParser.ColumnDefinitionContext) element).identifier())));
            } else if (element instanceof FlinkParser.ColumnNameOnlyDefinitionContext) {
                refs.add(new ColumnRef(target,
                        cleanIdentifier(((FlinkParser.ColumnNameOnlyDefinitionContext) element).identifier())));
            } else if (element instanceof FlinkParser.ComputedColumnDefinitionContext) {
                refs.add(new ColumnRef(target,
                        cleanIdentifier(((FlinkParser.ComputedColumnDefinitionContext) element).identifier())));
            }
        }
        if (!refs.isEmpty()) {
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
        }
    }

    private void addIdentifierListUsages(TableRef target, FlinkParser.IdentifierListContext ctx) {
        if (target == null || ctx == null) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String name : identifierNames(ctx)) {
            refs.add(new ColumnRef(target, name));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
    }

    private void addSameTableLineage(
            TableRef target,
            String targetColumn,
            List<SourceColumn> sourceColumns,
            String expression) {
        List<ColumnRef> sources = sameTableRefs(target, sourceColumns);
        if (sources.isEmpty()) {
            return;
        }
        ColumnLineage lineage = LineageModelUtils.columnLineage(target, targetColumn, sources, expression);
        List<ColumnLineage> lineages = new ArrayList<>(result.getColumnLineage());
        lineages.add(lineage);
        result.setColumnLineage(lineages);
    }

    private List<ColumnRef> sameTableRefs(TableRef target, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            if (sourceColumn.resolvedRef != null) {
                refs.add(sourceColumn.resolvedRef);
                continue;
            }
            String name = sourceColumn.qualifier == null
                    ? sourceColumn.name
                    : sourceColumn.qualifier + "." + sourceColumn.name;
            refs.add(new ColumnRef(target, name));
        }
        return refs;
    }

    private static boolean hasInlinePrimaryKey(FlinkParser.ColumnDefinitionContext ctx) {
        for (FlinkParser.ColumnConstraintContext constraint : ctx.columnConstraint()) {
            if (constraint.PRIMARY() != null) {
                return true;
            }
        }
        return false;
    }

    private TableRef firstOutputTable() {
        return outputTables.isEmpty() ? null : outputTables.iterator().next();
    }

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, FlinkParser.TableAliasContext aliasCtx, boolean visibleRelation) {
        if (visibleRelation) {
            visibleRelationCount++;
            visibleRelations.add(VisibleRelation.table(table));
        }
        inputTables.add(table);
        String name = table.getName().toLowerCase(Locale.ROOT);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            if (correlatedQuery) {
                tableAliases.putIfAbsent(name, table);
            } else {
                tableAliases.put(name, table);
            }
        }
        String binding = alias == null ? name : alias.toLowerCase(Locale.ROOT);
        tableAliases.put(binding, table);
        if (visibleRelation) {
            String exact = localBindingKey(alias == null ? table.getName() : alias);
            tableAliases.put(exact, table);
            derivedAliases.remove(exact);
        }
        if (visibleRelation && alias == null) {
            for (String qualified : qualifiedTableBindings(table)) {
                tableAliases.put(qualified, table);
                derivedAliases.remove(qualified);
            }
        }
        if (correlatedQuery && visibleRelation) {
            derivedAliases.remove(binding);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        refreshColumnLineage();
    }

    private void addCteReference(String relation, FlinkParser.TableAliasContext alias) {
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(relation);
        if (!incompleteDerivedSchemas.contains(relation)) {
            Map<String, List<ColumnRef>> renamed = applyReferenceColumnAliases(columns, alias);
            if (renamed != columns) {
                relation = "$reference_" + derivedColumnLineage.size();
                derivedColumnLineage.put(relation, renamed);
            }
        }
        addDerivedReference(relation, alias);
    }

    private static Map<String, List<ColumnRef>> applyReferenceColumnAliases(
            Map<String, List<ColumnRef>> columns, FlinkParser.TableAliasContext alias) {
        List<String> names = tableAliasColumnAliases(alias);
        if (columns == null || names.isEmpty() || names.size() != columns.size() || columns.containsKey("*")) {
            return columns;
        }
        Map<String, List<ColumnRef>> renamed = new LinkedHashMap<>();
        int position = 0;
        for (List<ColumnRef> sources : columns.values()) {
            renamed.put(names.get(position++), sources);
        }
        return renamed.size() == columns.size() ? renamed : columns;
    }

    private void addDerivedReference(String rawName, FlinkParser.TableAliasContext aliasCtx) {
        visibleRelationCount++;
        String derivedName = bindingLookupKey(rawName);
        visibleRelations.add(VisibleRelation.derived(derivedName));
        derivedReferences.add(derivedName);
        derivedAliases.put(derivedName, derivedName);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), derivedName);
            derivedAliases.put(localBindingKey(alias), derivedName);
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

    private void registerGeneratedRelation(FlinkParser.TableAliasContext aliasCtx, List<SourceColumn> sources) {
        String alias = tableAlias(aliasCtx);
        if (alias == null) {
            return;
        }
        registerGeneratedRelation(alias, tableAliasColumnAliases(aliasCtx), sources);
    }

    private void registerGeneratedRelation(String alias, List<String> columnAliases, List<SourceColumn> sources) {
        List<ColumnRef> refs = resolveSources(sources);
        if (refs == null) {
            refs = new ArrayList<>();
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        if (columnAliases.isEmpty()) {
            columns.put("*", refs);
        } else {
            for (String columnAlias : columnAliases) {
                columns.put(columnAlias, refs);
            }
        }
        registerGeneratedRelation(alias, columns);
    }

    private void registerGeneratedRelation(String alias, Map<String, List<ColumnRef>> columns) {
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        derivedColumnLineage.put(normalizedAlias, columns);
        incompleteDerivedSchemas.remove(normalizedAlias);
        derivedReferences.add(normalizedAlias);
        derivedAliases.put(normalizedAlias, normalizedAlias);
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(normalizedAlias));
        refreshColumnLineage();
    }

    private static FlinkParser.TvfTableArgContext firstTableFunctionArgument(
            FlinkParser.TableFunctionArgListContext ctx) {
        if (ctx == null) {
            return null;
        }
        for (FlinkParser.TableFunctionArgContext arg : ctx.tableFunctionArg()) {
            FlinkParser.TvfTableArgContext table = tableFunctionArgument(arg);
            if (table != null) {
                return table;
            }
        }
        return null;
    }

    private static FlinkParser.TvfTableArgContext tableFunctionArgument(FlinkParser.TableFunctionArgContext ctx) {
        if (ctx instanceof FlinkParser.NamedTableFunctionArgContext) {
            return tableFunctionArgument(((FlinkParser.NamedTableFunctionArgContext) ctx).tableFunctionArg());
        }
        return ctx instanceof FlinkParser.TvfTableArgContext ? (FlinkParser.TvfTableArgContext) ctx : null;
    }

    private TableRef firstTableFunctionInput(FlinkParser.TableFunctionArgListContext ctx) {
        FlinkParser.TvfTableArgContext table = firstTableFunctionArgument(ctx);
        return table == null ? null : tableRef(table.multipartIdentifier());
    }

    private static List<String> tableFunctionDescriptors(FlinkParser.TableFunctionArgListContext ctx) {
        List<String> descriptors = new ArrayList<>();
        if (ctx == null) {
            return descriptors;
        }
        for (FlinkParser.TableFunctionArgContext arg : ctx.tableFunctionArg()) {
            collectTableFunctionDescriptors(arg, descriptors);
        }
        return descriptors;
    }

    private static void collectTableFunctionDescriptors(FlinkParser.TableFunctionArgContext ctx,
                                                        List<String> descriptors) {
        if (ctx instanceof FlinkParser.TvfDescriptorArgContext) {
            FlinkParser.TvfDescriptorArgContext descriptor = (FlinkParser.TvfDescriptorArgContext) ctx;
            descriptors.add(cleanIdentifier(descriptor.identifier()));
            if (descriptor.identifierList() != null) {
                descriptors.addAll(identifierNames(descriptor.identifierList()));
            }
            return;
        }
        if (ctx instanceof FlinkParser.NamedTableFunctionArgContext) {
            collectTableFunctionDescriptors(((FlinkParser.NamedTableFunctionArgContext) ctx).tableFunctionArg(),
                    descriptors);
        }
    }

    private void registerDerivedRelation(String name, FlinkParser.QueryContext query, List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        FlinkLineageVisitor relationVisitor = new FlinkLineageVisitor(relationResult);
        relationVisitor.setContext(context);
        relationVisitor.cteNames.addAll(cteNames);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        relationVisitor.positionalProjectionSelect = explicitSelectClause(query.queryTerm());
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
        if (relationVisitor.knownQueryOutputWidth(query.queryTerm()) == columns.size()) {
            incompleteDerivedSchemas.remove(name);
        } else {
            incompleteDerivedSchemas.add(name);
        }
        for (TableRef table : relationResult.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relationResult);
    }

    private FlinkLineageVisitor analyzeSetOperand(FlinkParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(queryResult);
        queryVisitor.setContext(context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.positionalProjectionSelect = selectClause(queryTerm);
        queryVisitor.visit(queryTerm);
        queryVisitor.finalizeResult();
        return queryVisitor;
    }

    private static FlinkParser.SelectClauseContext explicitSelectClause(FlinkParser.QueryTermContext term) {
        FlinkParser.SelectClauseContext select = selectClause(term);
        if (select != null && select.selectItemList() != null && select.selectItemList().selectItem().stream()
                .allMatch(item -> item instanceof FlinkParser.SelectExpressionContext)) {
            return select;
        }
        return null;
    }

    private static FlinkParser.SelectClauseContext selectClause(FlinkParser.QueryTermContext term) {
        FlinkParser.QueryPrimaryContext primary = queryPrimary(term);
        return primary instanceof FlinkParser.QueryPrimaryDefaultContext
                ? ((FlinkParser.QueryPrimaryDefaultContext) primary).querySpecification().selectClause() : null;
    }

    private static FlinkParser.QueryPrimaryContext queryPrimary(FlinkParser.QueryTermContext term) {
        FlinkParser.QueryTermContext unwrapped = unwrappedQueryTerm(term);
        return unwrapped instanceof FlinkParser.QueryTermDefaultContext
                ? ((FlinkParser.QueryTermDefaultContext) unwrapped).queryPrimary() : null;
    }

    private static FlinkParser.QueryTermContext unwrappedQueryTerm(FlinkParser.QueryTermContext term) {
        while (term instanceof FlinkParser.QueryTermDefaultContext) {
            FlinkParser.QueryPrimaryContext primary = ((FlinkParser.QueryTermDefaultContext) term).queryPrimary();
            if (!(primary instanceof FlinkParser.SubqueryPrimaryContext)) {
                break;
            }
            term = ((FlinkParser.SubqueryPrimaryContext) primary).query().queryTerm();
        }
        return term;
    }

    private void addColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = columnUsageRefs(sourceColumns);
        if (refs != null) {
            LineageModelUtils.addColumnUsages(result, type, refs);
        }
    }

    private void addInputColumnUsages(ColumnUsageType type, List<SourceColumn> sources) {
        List<ColumnRef> refs = columnRefs(sources);
        if (refs != null) {
            LineageModelUtils.addColumnUsages(result, type, refs);
        }
    }

    private void addPatternColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns, TableRef table) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            refs.add(new ColumnRef(table, sourceColumn.name));
        }
        if (!refs.isEmpty()) {
            LineageModelUtils.addColumnUsages(result, type, distinctColumnRefs(refs));
        }
    }

    private void addTemporalColumnUsages(FlinkParser.TemporalClauseContext ctx) {
        if (ctx != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        }
    }

    private boolean registerKnownTableFunction(FlinkParser.FunctionNameContext functionName,
                                               FlinkParser.TableFunctionArgListContext args,
                                               FlinkParser.TableAliasContext aliasContext) {
        return registerWindowTableFunction(functionName, args, aliasContext)
                || registerProjectionTableFunction(functionName, args, aliasContext);
    }

    private boolean registerProjectionTableFunction(FlinkParser.FunctionNameContext functionName,
                                                    FlinkParser.TableFunctionArgListContext args,
                                                    FlinkParser.TableAliasContext aliasContext) {
        String name = cleanIdentifier(functionName.getText()).toUpperCase(Locale.ROOT);
        boolean model = "ML_PREDICT".equals(name);
        boolean toChangelog = "TO_CHANGELOG".equals(name);
        if (!model && !toChangelog && !"FROM_CHANGELOG".equals(name)) {
            return false;
        }
        FlinkParser.TvfTableArgContext table = firstTableFunctionArgument(args);
        if (table == null) {
            return false;
        }
        TableRef input = tableRef(table.multipartIdentifier());
        String cte = cteReference(table.multipartIdentifier());
        LineageResult relation = cte == null ? registeredRelation(input) : null;
        Map<String, List<ColumnRef>> inputColumns;
        if (cte != null) {
            inputColumns = incompleteDerivedSchemas.contains(cte) ? null : derivedColumnLineage.get(cte);
        } else {
            inputColumns = tableColumnNames(relation) == null ? null : temporaryRelationColumns(relation);
        }
        if (inputColumns == null || inputColumns.isEmpty() || inputColumns.containsKey("*")) {
            return false;
        }
        List<String> descriptors = tableFunctionDescriptors(args);
        Map<String, List<ColumnRef>> outputs = model
                ? modelPredictionColumns(inputColumns, descriptors, tableAliasColumnAliases(aliasContext))
                : changelogColumns(inputColumns, descriptors, toChangelog);
        if (outputs == null) {
            return false;
        }
        if (cte != null) {
            addDerivedInputTables(cte);
        } else {
            for (TableRef source : relation.getInputTables()) {
                addInputTable(source, false);
            }
            LineageModelUtils.mergeColumnUsages(result, relation);
        }
        String alias = tableAlias(aliasContext);
        registerGeneratedRelation(alias == null ? "$changelog" + derivedColumnLineage.size() : alias,
                model ? outputs : applyReferenceColumnAliases(outputs, aliasContext));
        return true;
    }

    private static Map<String, List<ColumnRef>> modelPredictionColumns(Map<String, List<ColumnRef>> inputColumns,
                                                                      List<String> descriptors, List<String> aliases) {
        if (aliases.size() <= inputColumns.size() || descriptors.isEmpty()
                || !inputColumns.keySet().containsAll(descriptors)) {
            return null;
        }
        List<ColumnRef> modelSources = new ArrayList<>();
        for (String descriptor : descriptors) {
            modelSources.addAll(inputColumns.get(descriptor));
        }
        List<ColumnRef> prediction = distinctColumnRefs(modelSources);
        Map<String, List<ColumnRef>> outputs = new LinkedHashMap<>();
        int position = 0;
        for (List<ColumnRef> inputSources : inputColumns.values()) {
            outputs.put(aliases.get(position++), inputSources);
        }
        while (position < aliases.size()) {
            outputs.put(aliases.get(position++), prediction);
        }
        return outputs;
    }

    private static Map<String, List<ColumnRef>> changelogColumns(Map<String, List<ColumnRef>> inputColumns,
                                                                List<String> descriptors, boolean toChangelog) {
        String operation = descriptors.isEmpty() ? "op" : descriptors.get(0);
        if (descriptors.size() > 1 || inputColumns.containsKey(operation) == toChangelog) {
            return null;
        }
        Map<String, List<ColumnRef>> outputs = new LinkedHashMap<>();
        if (toChangelog) {
            outputs.put(operation, Collections.emptyList());
        }
        outputs.putAll(inputColumns);
        if (!toChangelog) {
            outputs.remove(operation);
        }
        return outputs;
    }

    private boolean registerWindowTableFunction(FlinkParser.FunctionNameContext functionName,
                                                FlinkParser.TableFunctionArgListContext args,
                                                FlinkParser.TableAliasContext aliasContext) {
        if (!isWindowTableFunction(functionName)) {
            return false;
        }
        FlinkParser.TvfTableArgContext table = firstTableFunctionArgument(args);
        if (table == null) {
            return false;
        }
        TableRef input = tableRef(table.multipartIdentifier());
        String cte = cteReference(table.multipartIdentifier());
        LineageResult relation = cte == null ? registeredRelation(input) : null;
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        if (cte != null) {
            columns.putAll(derivedColumnLineage.getOrDefault(cte, Collections.emptyMap()));
            addDerivedInputTables(cte);
        } else if (relation != null) {
            columns.putAll(temporaryRelationColumns(relation));
            for (TableRef tableRef : relation.getInputTables()) {
                addInputTable(tableRef, false);
            }
            LineageModelUtils.mergeColumnUsages(result, relation);
        } else {
            addInputTable(input, false);
            columns.put("*", Collections.singletonList(new ColumnRef(input, "*")));
        }

        List<ColumnRef> timeSources = new ArrayList<>();
        for (String descriptor : tableFunctionDescriptors(args)) {
            timeSources.addAll(windowInputColumnRefs(columns, descriptor));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.WINDOW_ORDER_BY, distinctColumnRefs(timeSources));
        List<ColumnRef> windowSources = new ArrayList<>(timeSources);
        if (table.partitionKeys != null) {
            List<ColumnRef> partitionSources = new ArrayList<>();
            for (String key : identifierNames(table.partitionKeys.identifierList())) {
                partitionSources.addAll(windowInputColumnRefs(columns, key));
            }
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.WINDOW_PARTITION_BY,
                    distinctColumnRefs(partitionSources));
            windowSources.addAll(partitionSources);
        }
        List<ColumnRef> dependencies = distinctColumnRefs(windowSources);
        columns.put("window_start", dependencies);
        columns.put("window_end", dependencies);
        columns.put("window_time", dependencies);
        List<String> aliases = tableAliasColumnAliases(aliasContext);
        if (!aliases.isEmpty() && !columns.containsKey("*")) {
            Map<String, List<ColumnRef>> renamed = new LinkedHashMap<>();
            int position = 0;
            for (Map.Entry<String, List<ColumnRef>> column : columns.entrySet()) {
                renamed.put(position < aliases.size() ? aliases.get(position) : column.getKey(), column.getValue());
                position++;
            }
            columns = renamed;
        }
        String alias = tableAlias(aliasContext);
        registerGeneratedRelation(alias == null ? "$window" + derivedColumnLineage.size() : alias, columns);
        return true;
    }

    private static List<ColumnRef> windowInputColumnRefs(Map<String, List<ColumnRef>> columns, String name) {
        List<ColumnRef> refs = columns.get(name);
        if (refs == null) {
            refs = wildcardDerivedColumnRefs(columns.get("*"), name);
        }
        return refs == null ? Collections.emptyList() : refs;
    }

    private static boolean isWindowTableFunction(FlinkParser.FunctionNameContext functionName) {
        String name = cleanIdentifier(functionName.getText()).toUpperCase(Locale.ROOT);
        return "TUMBLE".equals(name)
                || "HOP".equals(name)
                || "CUMULATE".equals(name)
                || "SESSION".equals(name);
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
        if (sourceColumn.inputOnly || sourceColumn.resolvedRef != null || sourceColumn.qualifier != null) {
            return null;
        }
        for (Projection projection : projections) {
            if (projection.targetColumn.equals(sourceColumn.name)) {
                return columnRefs(projection.sourceColumns);
            }
        }
        return null;
    }

    private void addUsingColumnUsages(FlinkParser.IdentifierListContext ctx) {
        addUsingColumnUsages(ctx, 0);
    }

    private void addUsingColumnUsages(FlinkParser.IdentifierListContext ctx, int relationStart) {
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
        int expandedColumns = 0;
        boolean knownPosition = true;
        for (Projection projection : projections) {
            int ordinal = knownPosition && projection.ordinal >= 0 ? projection.ordinal + expandedColumns : -1;
            if (projection.wildcard) {
                Map<String, List<ColumnRef>> columns = wildcardProjectionColumns(projection);
                if (columns != null && !columns.containsKey("*")) {
                    addWildcardProjectionLineage(targetTable, projection, columns.entrySet(), ordinal, columnLineage);
                    expandedColumns += columns.size() - 1;
                    continue;
                }
                if (columns == null && ordinal >= 0 && ordinal < insertTargetColumns.size()) {
                    List<Map.Entry<String, List<ColumnRef>>> ordered = knownOrdinalWildcard(projection);
                    if (ordered != null && ordered.size() <= insertTargetColumns.size() - ordinal) {
                        addWildcardProjectionLineage(targetTable, projection, ordered, ordinal, columnLineage);
                        expandedColumns += ordered.size() - 1;
                        continue;
                    }
                }
                knownPosition = false;
                if (columns != null) {
                    addWildcardProjectionLineage(targetTable, projection, columns.entrySet(), -1, columnLineage);
                    continue;
                }
            } else if (!knownPosition && !insertTargetColumns.isEmpty()) {
                // A preceding wildcard has no known width, so a target column cannot be assigned by position.
                continue;
            }
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null) {
                if (positionalProjectionSelect == null) {
                    continue;
                }
                sources = Collections.emptyList();
            }
            String targetColumn = targetColumn(projection, ordinal);
            columnLineage.add(LineageModelUtils.columnLineage(targetTable, targetColumn, sources, projection.expression));
        }
        result.setColumnLineage(columnLineage);
    }

    private void addWildcardProjectionLineage(
            TableRef targetTable,
            Projection projection,
            Iterable<Map.Entry<String, List<ColumnRef>>> columns,
            int firstOrdinal,
            List<ColumnLineage> columnLineage) {
        int ordinal = firstOrdinal;
        for (Map.Entry<String, List<ColumnRef>> entry : columns) {
            if ("*".equals(entry.getKey()) && entry.getValue().stream().noneMatch(ref -> "*".equals(ref.getName()))) {
                continue;
            }
            String targetColumn = ordinal >= 0 && ordinal < insertTargetColumns.size()
                    ? insertTargetColumns.get(ordinal) : entry.getKey();
            columnLineage.add(LineageModelUtils.columnLineage(
                    targetTable, targetColumn, entry.getValue(), projection.expression));
            if (ordinal >= 0) {
                ordinal++;
            }
        }
    }

    private Map<String, List<ColumnRef>> wildcardProjectionColumns(Projection projection) {
        return wildcardProjectionColumns(projection, false);
    }

    private Map<String, List<ColumnRef>> wildcardProjectionColumns(Projection projection, boolean complete) {
        if (projection.sourceColumns.size() != 1) {
            return null;
        }
        SourceColumn wildcard = scopedSourceColumn(projection.sourceColumns.get(0));
        if (!"*".equals(wildcard.name)) {
            return null;
        }
        String derivedName = null;
        if (wildcard.qualifier != null) {
            derivedName = derivedAliases.get(bindingLookupKey(wildcard.qualifier));
        } else if (visibleRelations.size() == 1) {
            derivedName = visibleRelations.get(0).derivedName;
        } else if (canCombineWildcardInputs()) {
            return concatenatedWildcardColumns();
        }
        return relationWildcardColumns(derivedName, complete);
    }

    private boolean canCombineWildcardInputs() {
        return visibleRelations.size() > 1 && !hasNonConcatenatingJoin
                && (relationJoinKeys.isEmpty() || (visibleRelations.size() == 2
                        && relationJoinKeys.size() == 1 && !usingJoinKeys.isEmpty()));
    }

    private Map<String, List<ColumnRef>> concatenatedWildcardColumns() {
        List<Map.Entry<String, List<ColumnRef>>> entries = concatenatedWildcardEntries();
        if (entries == null) {
            return null;
        }
        Map<String, List<ColumnRef>> combined = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, List<ColumnRef>> column : entries) {
            if (!names.add(column.getKey().toLowerCase(Locale.ROOT))) {
                return null;
            }
            combined.put(column.getKey(), column.getValue());
        }
        return combined;
    }

    private List<Map.Entry<String, List<ColumnRef>>> concatenatedWildcardEntries() {
        List<Map.Entry<String, List<ColumnRef>>> combined = new ArrayList<>();
        for (VisibleRelation relation : visibleRelations) {
            Map<String, List<ColumnRef>> columns = relationWildcardColumns(relation.derivedName, true);
            if (columns == null || columns.containsKey("*")) {
                return null;
            }
            combined.addAll(columns.entrySet());
        }
        return usingJoinKeys.isEmpty() ? combined : mergeUsingWildcardEntries(combined);
    }

    private List<Map.Entry<String, List<ColumnRef>>> mergeUsingWildcardEntries(
            List<Map.Entry<String, List<ColumnRef>>> inputs) {
        Map<String, String> inputNames = new LinkedHashMap<>();
        List<Map.Entry<String, List<ColumnRef>>> output = new ArrayList<>();
        for (Map.Entry<String, List<ColumnRef>> column : inputs) {
            inputNames.putIfAbsent(column.getKey().toLowerCase(Locale.ROOT), column.getKey());
        }
        for (Map.Entry<String, List<ColumnRef>> key : usingJoinKeys.entrySet()) {
            String name = inputNames.get(key.getKey());
            if (name == null) {
                return null;
            }
            output.add(new AbstractMap.SimpleImmutableEntry<>(name, key.getValue()));
        }
        for (Map.Entry<String, List<ColumnRef>> column : inputs) {
            if (!usingJoinKeys.containsKey(column.getKey().toLowerCase(Locale.ROOT))) {
                output.add(column);
            }
        }
        return output;
    }

    private Map<String, List<ColumnRef>> relationWildcardColumns(String derivedName, boolean complete) {
        if (derivedName == null || (complete && incompleteDerivedSchemas.contains(derivedName))) {
            return null;
        }
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null || columns.isEmpty() || (columns.size() == 1 && columns.containsKey("*"))) {
            return null;
        }
        return columns;
    }

    private void flushPendingColumnUsages() {
        for (PendingColumnUsage usage : pendingColumnUsages) {
            addInputColumnUsages(usage.type, usage.sourceColumns);
        }
    }

    private void retargetColumnLineage(TableRef targetTable) {
        if (!projections.isEmpty()) {
            refreshColumnLineage(targetTable);
            return;
        }
        List<String> targets = hasWildcardOutput(result) ? Collections.emptyList() : insertTargetColumns;
        result.setColumnLineage(LineageModelUtils.retargetColumnLineage(result.getColumnLineage(), targetTable, targets));
    }

    private List<String> visitCreateTableQuery(FlinkParser.QueryContext query, TableRef target,
                                               FlinkParser.TableElementListContext declarations) {
        List<ColumnLineage> computedColumns = result.getColumnLineage();
        result.setColumnLineage(new ArrayList<>());
        visit(query);
        refreshColumnLineage();
        retargetColumnLineage(target);

        List<ColumnLineage> projectedColumns = result.getColumnLineage();
        Map<String, ColumnLineage> projectionsByName = new LinkedHashMap<>();
        for (ColumnLineage column : projectedColumns) {
            projectionsByName.put(column.getTarget().getName(), column);
        }
        List<String> names = columnNameOnlyTargets(declarations);
        if (!names.isEmpty()) {
            List<ColumnLineage> reordered = new ArrayList<>();
            for (String name : names) {
                ColumnLineage column = projectionsByName.get(name);
                if (column != null) {
                    reordered.add(column);
                }
            }
            result.setColumnLineage(reordered);
            suppressColumnLineage = true;
            return reordered.size() == names.size() ? names : Collections.emptyList();
        }

        if (!computedColumns.isEmpty()) {
            List<ColumnLineage> lineage = new ArrayList<>();
            for (ColumnLineage computed : computedColumns) {
                List<ColumnRef> sources = Collections.emptyList();
                for (ColumnRef dependency : computed.getSources()) {
                    ColumnLineage projected = projectionsByName.get(dependency.getName());
                    List<ColumnRef> refs = projected == null
                            ? Collections.singletonList(dependency) : projected.getSources();
                    sources = LineageModelUtils.mergeColumnRefs(sources, refs);
                }
                lineage.add(LineageModelUtils.columnLineage(computed.getTarget(), sources, computed.getExpression()));
            }
            lineage.addAll(projectedColumns);
            result.setColumnLineage(lineage);
            suppressColumnLineage = true;
        }

        int width = knownQueryOutputWidth(query.queryTerm());
        if (width <= 0 || width != projectionsByName.size() || width != projectedColumns.size()
                || hasWildcardOutput(result)) {
            return Collections.emptyList();
        }
        List<String> layout = declarations == null ? new ArrayList<>() : declaredColumnNames(declarations);
        layout.removeAll(projectionsByName.keySet());
        layout.addAll(projectionsByName.keySet());
        return layout;
    }

    private static List<String> columnNameOnlyTargets(FlinkParser.TableElementListContext ctx) {
        List<String> columns = new ArrayList<>();
        if (ctx != null) {
            for (FlinkParser.TableElementContext element : ctx.tableElement()) {
                if (element instanceof FlinkParser.ColumnNameOnlyDefinitionContext) {
                    FlinkParser.ColumnNameOnlyDefinitionContext column =
                            (FlinkParser.ColumnNameOnlyDefinitionContext) element;
                    columns.add(cleanIdentifier(column.identifier()));
                }
            }
        }
        return columns;
    }

    private String targetColumn(Projection projection, int index) {
        if (projection.wildcard) {
            return "*";
        }
        if (index >= 0 && index < insertTargetColumns.size()) {
            return insertTargetColumns.get(index);
        }
        return projection.targetColumn;
    }

    private List<ColumnRef> columnRefs(Projection projection) {
        return columnRefs(projection.sourceColumns);
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
                    ? usingJoinKeys.get(sourceColumn.name.toLowerCase(Locale.ROOT)) : null;
            if (joinRefs != null) {
                refs.addAll(joinRefs);
                continue;
            }
            if ("*".equals(sourceColumn.name)) {
                List<ColumnRef> wildcardRefs = wildcardColumnRefs(sourceColumn.qualifier);
                if (wildcardRefs == null) {
                    return null;
                }
                refs.addAll(wildcardRefs);
                continue;
            }
            List<ColumnRef> derivedRefs = derivedColumnRefs(sourceColumn);
            if (derivedRefs != null) {
                refs.addAll(derivedRefs);
                continue;
            }
            TableRef table = defaultTable;
            if (sourceColumn.qualifier != null) {
                table = tableAliases.get(bindingLookupKey(sourceColumn.qualifier));
                if (table == null && defaultTable != null) {
                    refs.add(new ColumnRef(defaultTable, sourceColumn.qualifier + "." + sourceColumn.name));
                    continue;
                }
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

    private List<ColumnRef> wildcardColumnRefs(String qualifier) {
        List<ColumnRef> refs = new ArrayList<>();
        if (qualifier != null) {
            String key = bindingLookupKey(qualifier);
            String derivedName = derivedAliases.get(key);
            if (derivedName != null) {
                addDerivedWildcardRefs(refs, derivedName);
                return refs.isEmpty() ? null : distinctColumnRefs(refs);
            }
            TableRef table = tableAliases.get(key);
            if (table != null) {
                refs.add(new ColumnRef(table, "*"));
                return refs;
            }
            return null;
        }
        for (VisibleRelation relation : visibleRelations) {
            if (relation.table != null) {
                refs.add(new ColumnRef(relation.table, "*"));
            } else {
                addDerivedWildcardRefs(refs, relation.derivedName);
            }
        }
        if (refs.isEmpty() && inputTables.size() == 1) {
            refs.add(new ColumnRef(inputTables.iterator().next(), "*"));
        }
        return refs.isEmpty() ? null : distinctColumnRefs(refs);
    }

    private void addDerivedWildcardRefs(List<ColumnRef> refs, String derivedName) {
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null) {
            return;
        }
        List<ColumnRef> wildcard = columns.get("*");
        if (wildcard != null) {
            refs.addAll(wildcard);
            return;
        }
        for (List<ColumnRef> columnRefs : columns.values()) {
            refs.addAll(columnRefs);
        }
    }

    private static List<ColumnRef> distinctColumnRefs(List<ColumnRef> refs) {
        return LineageModelUtils.mergeColumnRefs(Collections.emptyList(), refs);
    }

    private SourceColumn scopedSourceColumn(SourceColumn sourceColumn) {
        if (sourceColumn.resolvedRef != null) {
            return sourceColumn;
        }
        if (sourceColumn.qualifiedKey != null
                && (tableAliases.containsKey(sourceColumn.qualifiedKey)
                    || derivedAliases.containsKey(sourceColumn.qualifiedKey))) {
            return new SourceColumn(sourceColumn.qualifiedKey, sourceColumn.name);
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
            derivedName = derivedAliases.get(bindingLookupKey(sourceColumn.qualifier));
        } else if (visibleRelationCount == 1 && derivedReferences.size() == 1) {
            derivedName = derivedReferences.iterator().next();
        } else {
            List<ColumnRef> unique = null;
            for (VisibleRelation relation : visibleRelations) {
                if (relation.derivedName == null) {
                    continue;
                }
                Map<String, List<ColumnRef>> relationColumns = derivedColumnLineage.get(relation.derivedName);
                if (relationColumns == null || !relationColumns.containsKey(sourceColumn.name)) {
                    continue;
                }
                if (unique != null) {
                    return null;
                }
                unique = relationColumns.get(sourceColumn.name);
            }
            if (unique != null) {
                return unique;
            }
        }
        if (derivedName == null) {
            return null;
        }
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null) {
            return null;
        }
        List<ColumnRef> refs = columns.get(sourceColumn.name);
        if (refs != null) {
            return refs;
        }
        return wildcardDerivedColumnRefs(columns.get("*"), sourceColumn.name);
    }

    private static List<ColumnRef> wildcardDerivedColumnRefs(List<ColumnRef> wildcardRefs, String columnName) {
        if (wildcardRefs == null || wildcardRefs.isEmpty()) {
            return null;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (ColumnRef wildcardRef : wildcardRefs) {
            if (wildcardRef.getTable() == null) {
                continue;
            }
            if ("*".equals(wildcardRef.getName())) {
                refs.add(new ColumnRef(wildcardRef.getTable(), columnName));
            } else if (wildcardRef.getName().equalsIgnoreCase(columnName)) {
                refs.add(wildcardRef);
            }
        }
        return refs.isEmpty() ? null : refs;
    }

    private void registerRelation(TableRef table, boolean temporary, boolean ifNotExists, LineageResult value) {
        if (context == null) {
            return;
        }
        Map<String, LineageResult> relations = registeredRelations(temporary);
        String storedKey = storedRelationKey(table, temporary);
        if (ifNotExists && relations.get(storedKey) != null) {
            return;
        }
        String key = currentRelationKey(table, relations);
        if (!key.equals(storedKey)) {
            relations.remove(storedKey);
        }
        relations.put(key, value);
    }

    private void registerLikeTableSchema(TableRef target, TableRef source,
                                         FlinkParser.CreateTableStatementContext create) {
        List<String> columns = tableColumnNames(registeredRelation(source));
        if (columns == null || !likePreservesColumnKinds(create.likeClause())) {
            return;
        }
        if (create.tableElementList() != null) {
            for (String column : declaredColumnNames(create.tableElementList())) {
                if (!columns.contains(column)) {
                    columns.add(column);
                }
            }
        }
        registerTableSchema(target, columns, !create.TEMPORARY().isEmpty(), create.existsKeyword() != null);
    }

    private static boolean likePreservesColumnKinds(FlinkParser.LikeClauseContext like) {
        Map<String, Boolean> excludes = new LinkedHashMap<>();
        for (FlinkParser.LikeOptionContext option : like.likeOption()) {
            if (option.identifier() != null) {
                excludes.put(option.identifier().getText().toUpperCase(Locale.ROOT), option.EXCLUDING() != null);
            }
        }
        boolean excludesAll = excludes.getOrDefault("ALL", false);
        return !excludes.getOrDefault("GENERATED", excludesAll)
                && !excludes.getOrDefault("METADATA", excludesAll);
    }

    private void replaceMaterializedTableSchema(TableRef table, List<String> columnNames) {
        if (columnNames.isEmpty()) {
            dropRegisteredRelation(table, false, false);
        } else {
            registerTableSchema(table, columnNames, false, false);
        }
    }

    private void registerTableSchema(TableRef table, FlinkParser.TableElementListContext tableElements,
                                     boolean temporary, boolean ifNotExists) {
        if (context == null || tableElements == null) {
            return;
        }
        registerTableSchema(table, declaredColumnNames(tableElements), temporary, ifNotExists);
    }

    private void registerTableSchema(TableRef table, List<String> columnNames, boolean temporary, boolean ifNotExists) {
        if (columnNames.isEmpty()) {
            return;
        }
        LineageResult schema = new LineageResult();
        schema.setDialect(result.getDialect());
        schema.setDialectConfidence(result.getDialectConfidence());
        schema.setStatementType(StatementType.CREATE_TABLE);
        List<TableRef> input = new ArrayList<>();
        input.add(table);
        schema.setInputTables(input);
        List<ColumnLineage> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            List<ColumnRef> sources = new ArrayList<>();
            sources.add(new ColumnRef(table, columnName));
            columns.add(LineageModelUtils.columnLineage(table, columnName, sources, columnName));
        }
        schema.setColumnLineage(columns);
        registerRelation(table, temporary, ifNotExists, schema);
    }

    private static List<String> declaredColumnNames(FlinkParser.TableElementListContext ctx) {
        List<String> columns = new ArrayList<>();
        for (FlinkParser.TableElementContext element : ctx.tableElement()) {
            String name = declaredColumnName(element);
            if (name != null) {
                columns.add(name);
            }
        }
        return columns;
    }

    private static String declaredColumnName(FlinkParser.TableElementContext element) {
        if (element instanceof FlinkParser.ColumnDefinitionContext) {
            return cleanIdentifier(((FlinkParser.ColumnDefinitionContext) element).identifier());
        }
        if (element instanceof FlinkParser.ColumnNameOnlyDefinitionContext) {
            return cleanIdentifier(((FlinkParser.ColumnNameOnlyDefinitionContext) element).identifier());
        }
        if (element instanceof FlinkParser.ComputedColumnDefinitionContext) {
            return cleanIdentifier(((FlinkParser.ComputedColumnDefinitionContext) element).identifier());
        }
        return null;
    }

    private List<String> registeredTableColumns(TableRef table) {
        if (context == null || registeredRelations(true).containsKey(storedRelationKey(table, true))) {
            return null;
        }
        return tableColumnNames(registeredRelations(false).get(storedRelationKey(table, false)));
    }

    private static List<String> tableColumnNames(LineageResult schema) {
        if (schema == null || schema.getStatementType() != StatementType.CREATE_TABLE
                || schema.getColumnLineage().isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (ColumnLineage column : schema.getColumnLineage()) {
            String name = column.getTarget().getName();
            if ("*".equals(name)) {
                return null;
            }
            names.add(name);
        }
        return names;
    }

    private void addSchemaColumns(TableRef target, FlinkParser.AlterTableElementListContext declarations) {
        List<String> columns = registeredTableColumns(target);
        if (columns == null) {
            return;
        }
        for (FlinkParser.AlterTableElementContext declaration : declarations.alterTableElement()) {
            if (!addSchemaColumn(columns, declaration.tableElement(), declaration.columnPosition())) {
                return;
            }
        }
        registerTableSchema(target, columns, false, false);
    }

    private void addSchemaColumn(TableRef target, FlinkParser.TableElementContext element,
                                  FlinkParser.ColumnPositionContext position) {
        List<String> columns = registeredTableColumns(target);
        if (columns != null && addSchemaColumn(columns, element, position)) {
            registerTableSchema(target, columns, false, false);
        }
    }

    private static boolean addSchemaColumn(List<String> columns, FlinkParser.TableElementContext element,
                                           FlinkParser.ColumnPositionContext position) {
        String name = declaredColumnName(element);
        if (name == null) {
            return true;
        }
        if (columns.contains(name)) {
            return false;
        }
        return insertSchemaColumn(columns, name, position);
    }

    private static boolean insertSchemaColumn(List<String> columns, String name,
                                              FlinkParser.ColumnPositionContext position) {
        int index = columns.size();
        if (position != null) {
            if (position.FIRST() != null) {
                index = 0;
            } else {
                int previous = columns.indexOf(cleanIdentifier(position.identifier()));
                if (previous < 0) {
                    return false;
                }
                index = previous + 1;
            }
        }
        columns.add(index, name);
        return true;
    }

    private void moveSchemaColumns(TableRef target, FlinkParser.AlterTableElementListContext declarations) {
        List<String> columns = registeredTableColumns(target);
        if (columns == null) {
            return;
        }
        for (FlinkParser.AlterTableElementContext declaration : declarations.alterTableElement()) {
            if (!moveSchemaColumn(columns, declaration.tableElement(), declaration.columnPosition())) {
                return;
            }
        }
        registerTableSchema(target, columns, false, false);
    }

    private void moveSchemaColumn(TableRef target, FlinkParser.TableElementContext element,
                                  FlinkParser.ColumnPositionContext position) {
        List<String> columns = registeredTableColumns(target);
        if (columns != null && moveSchemaColumn(columns, element, position)) {
            registerTableSchema(target, columns, false, false);
        }
    }

    private static boolean moveSchemaColumn(List<String> columns, FlinkParser.TableElementContext element,
                                            FlinkParser.ColumnPositionContext position) {
        String name = declaredColumnName(element);
        if (name == null) {
            return true;
        }
        if (position == null) {
            return columns.contains(name);
        }
        return columns.remove(name) && insertSchemaColumn(columns, name, position);
    }

    private void registerCurrentView(TableRef target, boolean temporary, boolean ifNotExists) {
        if (context == null) {
            return;
        }
        flushPendingColumnUsages();
        LineageResult view = copyResult(result);
        view.setStatementType(StatementType.CREATE_VIEW);
        registerRelation(target, temporary, ifNotExists, view);
    }

    private void renameRegisteredView(TableRef source, TableRef target) {
        if (context == null || registeredRelations(true).containsKey(storedRelationKey(source, true))
                || registeredRelation(target) != null) {
            return;
        }
        Map<String, LineageResult> views = registeredRelations(false);
        String sourceKey = storedRelationKey(source, false);
        LineageResult previous = views.get(sourceKey);
        if (previous == null || previous.getStatementType() != StatementType.CREATE_VIEW) {
            return;
        }
        LineageResult view = copyResult(previous);
        view.setOutputTables(Collections.singletonList(target));
        view.setColumnLineage(LineageModelUtils.retargetColumnLineage(
                view.getColumnLineage(), target, Collections.emptyList()));
        views.put(currentRelationKey(target, views), view);
        views.remove(sourceKey);
    }

    private void renameRegisteredTable(TableRef source, TableRef target) {
        List<String> columns = registeredTableColumns(source);
        if (columns != null && registeredRelation(target) == null) {
            registeredRelations(false).remove(storedRelationKey(source, false));
            registerTableSchema(target, columns, false, false);
        }
    }

    private void dropSchemaColumns(TableRef table, List<String> removed) {
        List<String> columns = registeredTableColumns(table);
        if (columns == null) {
            return;
        }
        for (String name : removed) {
            if (!columns.remove(name)) {
                return;
            }
        }
        registerTableSchema(table, columns, false, false);
    }

    private LineageResult registeredRelation(TableRef table) {
        if (context == null) {
            return null;
        }
        LineageResult temporary = registeredRelations(true).get(storedRelationKey(table, true));
        return temporary != null ? temporary : registeredRelations(false).get(storedRelationKey(table, false));
    }

    private Map<String, LineageResult> registeredRelations(boolean temporary) {
        return temporary ? context.getSessionTemporaryRelations(SqlDialect.FLINK) : context.getTemporaryRelations();
    }

    private void dropRegisteredRelation(TableRef table, boolean temporary, boolean view) {
        if (context == null) {
            return;
        }
        String key = storedRelationKey(table, temporary);
        if (!temporary && registeredRelations(true).containsKey(storedRelationKey(table, true))) {
            return;
        }
        Map<String, LineageResult> relations = registeredRelations(temporary);
        LineageResult registered = relations.get(key);
        if (registered != null && (registered.getStatementType() == StatementType.CREATE_VIEW) == view) {
            relations.remove(key);
        }
    }

    private void addRegisteredRelationReference(TableRef table, FlinkParser.TableAliasContext aliasCtx,
                                                LineageResult relation) {
        Map<String, List<ColumnRef>> columns = temporaryRelationColumns(relation);
        Map<String, List<ColumnRef>> namedColumns = applyReferenceColumnAliases(columns, aliasCtx);
        String relationName = namedColumns == columns
                ? relationKey(table) : "$reference_" + derivedColumnLineage.size();
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(relationName));
        derivedReferences.add(relationName);
        derivedAliases.put(relationName, relationName);
        String alias = tableAlias(aliasCtx);
        String visibleName = alias == null ? table.getName() : alias;
        derivedAliases.put(visibleName.toLowerCase(Locale.ROOT), relationName);
        derivedAliases.put(localBindingKey(visibleName), relationName);
        if (alias == null) {
            for (String qualified : qualifiedTableBindings(table)) {
                derivedAliases.put(qualified, relationName);
                tableAliases.remove(qualified);
            }
        }
        derivedColumnLineage.put(relationName, namedColumns);
        incompleteDerivedSchemas.remove(relationName);
        for (TableRef source : relation.getInputTables()) {
            addInputTable(source, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relation);
        refreshColumnLineage();
    }

    private static Map<String, List<ColumnRef>> temporaryRelationColumns(LineageResult relation) {
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (ColumnLineage lineage : relation.getColumnLineage()) {
            columns.put(lineage.getTarget().getName(), lineage.getSources());
        }
        if (columns.isEmpty() && relation.getInputTables().size() == 1) {
            List<ColumnRef> wildcard = new ArrayList<>();
            wildcard.add(new ColumnRef(relation.getInputTables().get(0), "*"));
            columns.put("*", wildcard);
        }
        return columns;
    }

    private static LineageResult copyResult(LineageResult source) {
        LineageResult copy = new LineageResult();
        copy.setDialect(source.getDialect());
        copy.setDialectConfidence(source.getDialectConfidence());
        copy.setStatementType(source.getStatementType());
        copy.setInputTables(new ArrayList<>(source.getInputTables()));
        copy.setOutputTables(new ArrayList<>(source.getOutputTables()));
        copy.setColumnLineage(new ArrayList<>(source.getColumnLineage()));
        copy.setColumnUsages(new ArrayList<>(source.getColumnUsages()));
        copy.setDiagnostics(new ArrayList<>(source.getDiagnostics()));
        return copy;
    }

    private String cteReference(FlinkParser.MultipartIdentifierContext name) {
        if (name.identifier().size() != 1) {
            return null;
        }
        String key = localBindingKey(cleanIdentifier(name.identifier(0)));
        return cteNames.contains(key) ? key : null;
    }

    private List<ColumnLineage> readAssignments(FlinkParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (FlinkParser.AssignmentContext assignment : ctx.assignment()) {
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

    private List<ColumnLineage> readMergeInsertValues(FlinkParser.MergeNotMatchedActionContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx == null || ctx.expressionList() == null) {
            return lineages;
        }
        if (ctx.identifierList() == null) {
            if (containsSubquery(ctx.expressionList())) {
                return lineages;
            }
            List<ColumnRef> sources = resolveSources(sourceColumns(ctx.expressionList()));
            if (sources != null) {
                lineages.add(LineageModelUtils.columnLineage(target, "*",
                        LineageModelUtils.mergeColumnRefs(Collections.emptyList(), sources), ctx.expressionList().getText()));
            }
            return lineages;
        }
        List<String> targetColumns = identifierNames(ctx.identifierList());
        List<FlinkParser.ExpressionContext> expressions = ctx.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            FlinkParser.ExpressionContext expression = expressions.get(i);
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
                table = tableAliases.get(bindingLookupKey(col.qualifier));
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

    private List<ColumnRef> patternMeasureRefs(FlinkParser.ExpressionContext expression, TableRef table) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns(expression)) {
            refs.add(new ColumnRef(table, sourceColumn.name));
        }
        return refs;
    }

    private void collectSubqueryInputs(ParseTree tree) {
        collectSubqueryDependencies(tree, false);
    }

    private void collectSubqueryDependencies(ParseTree tree, boolean includeColumnUsages) {
        if (tree instanceof FlinkParser.ScalarSubqueryContext) {
            addSubqueryDependencies(((FlinkParser.ScalarSubqueryContext) tree).query(), includeColumnUsages);
            return;
        }
        if (tree instanceof FlinkParser.ExistsExprContext) {
            addSubqueryDependencies(((FlinkParser.ExistsExprContext) tree).query(), includeColumnUsages);
            return;
        }
        if (tree instanceof FlinkParser.PredicateContext) {
            FlinkParser.QueryContext query = ((FlinkParser.PredicateContext) tree).query();
            if (query != null) {
                addSubqueryDependencies(query, includeColumnUsages);
                return;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSubqueryDependencies(tree.getChild(i), includeColumnUsages);
        }
    }

    private void addSubqueryDependencies(FlinkParser.QueryContext query, boolean includeColumnUsages) {
        LineageResult subquery = lineageForQuery(query);
        inputTables.addAll(subquery.getInputTables());
        result.setInputTables(new ArrayList<>(inputTables));
        if (includeColumnUsages) {
            LineageModelUtils.mergeColumnUsages(result, subquery);
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof FlinkParser.ScalarSubqueryContext || tree instanceof FlinkParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof FlinkParser.PredicateContext) {
            FlinkParser.PredicateContext predicate = (FlinkParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(FlinkParser.QueryContext query) {
        if (projectionSubqueries == null) {
            return analyzeSubquery(query);
        }
        return projectionSubqueries.computeIfAbsent(query, this::analyzeSubquery);
    }

    private LineageResult analyzeSubquery(FlinkParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(queryResult);
        queryVisitor.correlatedQuery = true;
        queryVisitor.setContext(context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        queryVisitor.collectTopLevelQueryProjections(query);
        queryVisitor.finalizeResult();
        return queryResult;
    }

    private void collectTopLevelQueryProjections(FlinkParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        FlinkParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        collectSelectClauseProjections(specification.selectClause());
    }

    private void collectSelectClauseProjections(FlinkParser.SelectClauseContext ctx) {
        if (ctx == null) {
            return;
        }
        if (ctx.transformClause() != null) {
            projections.addAll(transformProjections(ctx.transformClause()));
            return;
        }
        if (ctx.selectItemList() == null) {
            return;
        }
        List<FlinkParser.SelectItemContext> items = ctx.selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            FlinkParser.SelectItemContext item = items.get(i);
            if (item instanceof FlinkParser.SelectExpressionContext) {
                FlinkParser.SelectExpressionContext selected = (FlinkParser.SelectExpressionContext) item;
                boolean positional = ctx == positionalInsertSelect && i < insertTargetColumns.size();
                Projection projection = projection(selected, i, positional);
                if (projection == null && ctx == positionalProjectionSelect) {
                    FlinkParser.ExpressionContext expression = selected.expression();
                    projection = new Projection(sourceColumns(expression), "EXPR$" + i, expression.getText(), i);
                }
                if (projection != null) {
                    projections.add(projection);
                }
            } else {
                Projection star = wildcardProjection(item, i);
                if (star != null) {
                    projections.add(star);
                }
            }
        }
    }

    private List<Projection> transformProjections(FlinkParser.TransformClauseContext ctx) {
        List<Projection> result = new ArrayList<>();
        List<SourceColumn> inputColumns = sourceColumns(ctx.expressionList());
        List<String> outputColumns = transformOutputNames(ctx.transformOutputList());
        for (String outputColumn : outputColumns) {
            result.add(new Projection(inputColumns, outputColumn, ctx.getText(), -1));
        }
        return result;
    }

    private static List<String> transformOutputNames(FlinkParser.TransformOutputListContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx == null) {
            names.add("transform");
            return names;
        }
        for (FlinkParser.TransformOutputContext output : ctx.transformOutput()) {
            names.add(cleanIdentifier(output.identifier(0)));
        }
        return names;
    }

    private static FlinkParser.QuerySpecificationContext topLevelQuerySpecification(FlinkParser.QueryContext query) {
        return topLevelQuerySpecification(query.queryTerm());
    }

    private static FlinkParser.QuerySpecificationContext topLevelQuerySpecification(FlinkParser.QueryTermContext term) {
        if (!(term instanceof FlinkParser.QueryTermDefaultContext)) {
            return null;
        }
        FlinkParser.QueryPrimaryContext primary = ((FlinkParser.QueryTermDefaultContext) term).queryPrimary();
        if (!(primary instanceof FlinkParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((FlinkParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(FlinkParser.SelectExpressionContext ctx, int ordinal, boolean positionalInsert) {
        String expression = ctx.expression().getText();
        List<SourceColumn> sourceColumns = sourceColumns(ctx.expression());
        String directColumn = sourceColumns.size() == 1 && isDirectColumnExpression(expression, sourceColumns.get(0))
                ? unqualifiedName(sourceColumns.get(0).name)
                : null;
        if (sourceColumns.isEmpty() && ctx.alias == null) {
            return null;
        }
        String targetColumn = ctx.alias == null
                ? inferredSingleSourceTarget(directColumn, sourceColumns, ctx.expression())
                : cleanIdentifier(ctx.alias);
        if (positionalInsert && !sourceColumns.isEmpty() && targetColumn == null) {
            targetColumn = expression;
        }
        if (targetColumn == null) {
            return null;
        }
        return new Projection(sourceColumns, targetColumn, expression, ordinal);
    }

    private String inferredSingleSourceTarget(
            String directColumn,
            List<SourceColumn> sourceColumns,
            FlinkParser.ExpressionContext expression) {
        if (directColumn != null) {
            return directColumn;
        }
        if (containsSubquery(expression) || isAggregateExpression(expression.getText())) {
            return null;
        }
        if (sourceColumns.size() == 1) {
            return unqualifiedName(sourceColumns.get(0).name);
        }
        return null;
    }

    private static boolean isAggregateExpression(String expression) {
        if (expression == null) {
            return false;
        }
        String normalized = expression.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("COUNT(")
                || normalized.startsWith("SUM(")
                || normalized.startsWith("AVG(")
                || normalized.startsWith("MIN(")
                || normalized.startsWith("MAX(")
                || normalized.startsWith("COLLECT(")
                || normalized.startsWith("LISTAGG(")
                || normalized.startsWith("ARRAY_AGG(")
                || normalized.startsWith("JSON_OBJECTAGG(")
                || normalized.startsWith("JSON_ARRAYAGG(");
    }

    private static Projection wildcardProjection(FlinkParser.SelectItemContext item, int ordinal) {
        if (item instanceof FlinkParser.SelectQualifiedStarContext) {
            FlinkParser.SelectQualifiedStarContext star = (FlinkParser.SelectQualifiedStarContext) item;
            List<String> parts = new ArrayList<>();
            for (FlinkParser.IdentifierContext id : star.qualifiedName().identifier()) {
                parts.add(cleanIdentifier(id));
            }
            String qualifier = String.join(".", parts);
            String qualified = parts.size() == 1 ? localBindingKey(parts.get(0)) : qualifiedBindingKey(parts);
            return new Projection(Collections.singletonList(new SourceColumn(qualifier, "*", qualified)),
                    "*", star.getText(), true, ordinal);
        }
        if (item instanceof FlinkParser.SelectStarContext) {
            return wildcardProjection(null, item.getText(), ordinal);
        }
        return null;
    }

    private static Projection wildcardProjection(String qualifier, String expression, int ordinal) {
        List<SourceColumn> sourceColumns = new ArrayList<>();
        sourceColumns.add(new SourceColumn(qualifier, "*"));
        return new Projection(sourceColumns, "*", expression, true, ordinal);
    }

    private static boolean isDirectColumnExpression(String expression, SourceColumn column) {
        String raw = column.qualifier != null ? column.qualifier + "." + column.name : column.name;
        String normalizedExpression = cleanIdentifier(expression);
        String normalizedRaw = cleanIdentifier(raw);
        String normalizedName = cleanIdentifier(column.name);
        return normalizedExpression.equals(normalizedRaw) || normalizedExpression.endsWith("." + normalizedName);
    }

    private static String unqualifiedName(String raw) {
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    private List<SourceColumn> sourceColumns(ParseTree tree) {
        return sourceColumns(tree, false);
    }

    private List<SourceColumn> sourceColumns(ParseTree tree, boolean inputScopeForWindows) {
        Set<SourceColumn> columns = new LinkedHashSet<>();
        if (tree != null) {
            collectSourceColumns(tree, columns, inputScopeForWindows);
        }
        return new ArrayList<>(columns);
    }

    private void addScalarSubquerySourceColumns(FlinkParser.QueryContext query, Set<SourceColumn> columns) {
        LineageResult subResult = lineageForQuery(query);
        inputTables.addAll(subResult.getInputTables());
        result.setInputTables(new ArrayList<>(inputTables));
        boolean hasSources = false;
        for (ColumnLineage lineage : subResult.getColumnLineage()) {
            for (ColumnRef source : lineage.getSources()) {
                if (source.getTable() != null) {
                    columns.add(SourceColumn.resolved(source));
                    hasSources = true;
                }
            }
        }
        if (hasSources) {
            return;
        }
        for (ColumnRef source : scalarSubqueryProjectionRefs(query)) {
            if (source.getTable() != null) {
                columns.add(SourceColumn.resolved(source));
            }
        }
    }

    private List<ColumnRef> scalarSubqueryProjectionRefs(FlinkParser.QueryContext query) {
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(new LineageResult());
        queryVisitor.correlatedQuery = true;
        queryVisitor.setContext(context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.incompleteDerivedSchemas.addAll(incompleteDerivedSchemas);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        FlinkParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        FlinkParser.SelectClauseContext selectClause = specification.selectClause();
        if (selectClause.transformClause() != null) {
            for (Projection projection : queryVisitor.transformProjections(selectClause.transformClause())) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(projection.sourceColumns);
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
            return refs;
        }
        if (selectClause.selectItemList() == null) {
            return refs;
        }
        for (FlinkParser.SelectItemContext item : selectClause.selectItemList().selectItem()) {
            if (item instanceof FlinkParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((FlinkParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns,
                                      boolean inputScopeForWindows) {
        if (tree instanceof FlinkParser.OverClauseContext) {
            FlinkParser.OverClauseContext over = (FlinkParser.OverClauseContext) tree;
            collectWindowSourceColumns(over, columns, inputScopeForWindows);
            return;
        }
        if (tree instanceof FlinkParser.ColumnReferenceContext) {
            FlinkParser.ColumnReferenceContext colRef = (FlinkParser.ColumnReferenceContext) tree;
            String column = cleanIdentifier(colRef.identifier());
            if (!isFlinkGeneratedExpression(column)) {
                columns.add(new SourceColumn(null, column));
            }
            return;
        }
        if (tree instanceof FlinkParser.DereferenceContext) {
            FlinkParser.DereferenceContext deref = (FlinkParser.DereferenceContext) tree;
            if (deref.primaryExpression() instanceof FlinkParser.SubscriptExpressionContext) {
                collectSourceColumns(deref.primaryExpression(), columns, inputScopeForWindows);
                return;
            }
            List<String> parts = collectDereferenceParts(deref);
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2);
                String name = parts.get(parts.size() - 1);
                String qualified = parts.size() == 2 ? localBindingKey(qualifier)
                        : qualifiedBindingKey(parts.subList(0, parts.size() - 1));
                columns.add(new SourceColumn(qualifier, name, qualified));
            } else if (parts.size() == 1) {
                columns.add(new SourceColumn(null, parts.get(0)));
            }
            return;
        }
        if (tree instanceof FlinkParser.SubscriptExpressionContext) {
            FlinkParser.SubscriptExpressionContext subscript = (FlinkParser.SubscriptExpressionContext) tree;
            collectSourceColumns(subscript.primaryExpression(), columns, inputScopeForWindows);
            collectSourceColumns(subscript.expression(), columns, inputScopeForWindows);
            return;
        }
        if (tree instanceof FlinkParser.ScalarSubqueryContext) {
            FlinkParser.ScalarSubqueryContext subquery = (FlinkParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof FlinkParser.ExistsExprContext) {
            FlinkParser.ExistsExprContext exists = (FlinkParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof FlinkParser.PredicateContext) {
            FlinkParser.PredicateContext predicate = (FlinkParser.PredicateContext) tree;
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
                        collectSourceColumns(child, columns, inputScopeForWindows);
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
            collectSourceColumns(tree.getChild(i), columns, inputScopeForWindows);
        }
    }

    private void collectWindowSourceColumns(FlinkParser.OverClauseContext over,
                                            Set<SourceColumn> columns, boolean inputScopeForWindows) {
        FlinkParser.WindowSpecContext window = over.windowSpec();
        if (window == null) {
            window = namedWindows.get(cleanIdentifier(over.identifier()).toLowerCase(Locale.ROOT));
        }
        Set<FlinkParser.WindowSpecContext> visited = new LinkedHashSet<>();
        while (window != null && visited.add(window)) {
            window = window.base == null
                    ? null : namedWindows.get(cleanIdentifier(window.base).toLowerCase(Locale.ROOT));
        }
        List<FlinkParser.WindowSpecContext> windows = new ArrayList<>(visited);
        Collections.reverse(windows);
        // Resolve inherited keys first and keep malformed nested windows from recursing.
        for (FlinkParser.WindowSpecContext spec : windows) {
            if (!activeWindows.add(spec)) {
                continue;
            }
            try {
                for (SourceColumn source : sourceColumns(spec)) {
                    columns.add(inputScopeForWindows ? source.inInputScope() : source);
                }
            } finally {
                activeWindows.remove(spec);
            }
        }
    }

    private List<String> collectDereferenceParts(FlinkParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof FlinkParser.DereferenceContext) {
            FlinkParser.DereferenceContext deref = (FlinkParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof FlinkParser.SubscriptExpressionContext) {
            FlinkParser.SubscriptExpressionContext subscript = (FlinkParser.SubscriptExpressionContext) tree;
            collectPrimaryParts(subscript.primaryExpression(), parts);
        } else if (tree instanceof FlinkParser.ColumnReferenceContext) {
            FlinkParser.ColumnReferenceContext colRef = (FlinkParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private TableRef renamedRelation(TableRef source, FlinkParser.MultipartIdentifierContext name) {
        return name.identifier().size() == 1
                ? new TableRef(source.getCatalog(), source.getSchema(), cleanIdentifier(name.identifier(0)))
                : tableRef(name);
    }

    private TableRef tableRef(FlinkParser.MultipartIdentifierContext ctx) {
        TableRef table = LineageModelUtils.tableRefFromParts(identifierParts(ctx));
        if (context == null || table.getCatalog() != null) {
            return table;
        }
        String schema = table.getSchema() == null ? context.getDefaultSchema() : table.getSchema();
        return new TableRef(context.getDefaultCatalog(), schema, table.getName());
    }

    private static List<String> identifierParts(FlinkParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (FlinkParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static List<String> qualifiedTableBindings(TableRef table) {
        if (table.getSchema() == null) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        parts.add(table.getSchema());
        parts.add(table.getName());
        List<String> bindings = new ArrayList<>();
        bindings.add(qualifiedBindingKey(parts));
        if (table.getCatalog() != null) {
            parts.add(0, table.getCatalog());
            bindings.add(qualifiedBindingKey(parts));
        }
        return bindings;
    }

    private static String localBindingKey(String name) {
        return "\0" + name;
    }

    private static String bindingLookupKey(String qualifier) {
        return qualifier.startsWith("\0") ? qualifier : qualifier.toLowerCase(Locale.ROOT);
    }

    private static String qualifiedBindingKey(List<String> parts) {
        // Dots inside quoted identifiers do not separate relation name components.
        return "\0" + String.join("\0", parts);
    }

    private String storedRelationKey(TableRef table, boolean temporary) {
        Map<String, LineageResult> relations = registeredRelations(temporary);
        String key = currentRelationKey(table, relations);
        if (!key.startsWith("\0") || relations.containsKey(key)) {
            return key;
        }
        String folded = structuralRelationParts(table).toLowerCase(Locale.ROOT);
        if (identifiesRegisteredTable(relations.get(folded), table)) {
            return folded;
        }
        // Older dotted keys can also identify a different case or path.
        String legacy = dottedRelationKey(table).toLowerCase(Locale.ROOT);
        return identifiesRegisteredTable(relations.get(legacy), table) ? legacy : key;
    }

    private static String currentRelationKey(TableRef table, Map<String, LineageResult> relations) {
        String key = relationKey(table);
        if (!key.startsWith("\0")) {
            String structural = structuralRelationKey(table);
            if (relations.containsKey(structural)) {
                return structural;
            }
            LineageResult stored = relations.get(key);
            if (stored != null && (!stored.getOutputTables().isEmpty()
                    || (stored.getStatementType() == StatementType.CREATE_TABLE && stored.getInputTables().size() == 1))
                    && !identifiesRegisteredTable(stored, table)) {
                return structural;
            }
        }
        return key;
    }

    private static boolean identifiesRegisteredTable(LineageResult relation, TableRef table) {
        return relation != null && (relation.getOutputTables().contains(table)
                || (relation.getStatementType() == StatementType.CREATE_TABLE
                    && relation.getInputTables().equals(Collections.singletonList(table))));
    }

    private static String relationKey(TableRef table) {
        String dotted = dottedRelationKey(table);
        String catalog = table.getCatalog() == null ? "" : table.getCatalog();
        String schema = table.getSchema() == null ? "" : table.getSchema();
        if (!dotted.equals(dotted.toLowerCase(Locale.ROOT)) || (!catalog.isEmpty() && schema.isEmpty())
                || catalog.contains(".") || schema.contains(".") || table.getName().contains(".")) {
            return structuralRelationKey(table);
        }
        return dotted;
    }

    private static String structuralRelationKey(TableRef table) {
        return "\0relation" + structuralRelationParts(table);
    }

    private static String structuralRelationParts(TableRef table) {
        String catalog = table.getCatalog() == null ? "" : table.getCatalog();
        String schema = table.getSchema() == null ? "" : table.getSchema();
        return "\0" + catalog + "\0" + schema + "\0" + table.getName();
    }

    private static String dottedRelationKey(TableRef table) {
        List<String> parts = new ArrayList<>();
        if (table.getCatalog() != null) {
            parts.add(table.getCatalog());
        }
        if (table.getSchema() != null) {
            parts.add(table.getSchema());
        }
        parts.add(table.getName());
        return String.join(".", parts);
    }

    private static String tableAlias(FlinkParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> tableAliasColumnAliases(FlinkParser.TableAliasContext ctx) {
        if (ctx == null || ctx.identifierList() == null) {
            return new ArrayList<>();
        }
        return identifierNames(ctx.identifierList());
    }

    private static List<String> cteColumnAliases(FlinkParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (FlinkParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(FlinkParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (FlinkParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(FlinkParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static String cleanIdentifier(String text) {
        String value = text.trim();
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1).replace("``", "`");
        }
        return value;
    }

    private static boolean isFlinkGeneratedExpression(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return "current_date".equals(normalized)
                || "current_time".equals(normalized)
                || "current_timestamp".equals(normalized)
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
        final boolean wildcard;
        final int ordinal;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
            this(sourceColumns, targetColumn, expression, -1);
        }

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, int ordinal) {
            this(sourceColumns, targetColumn, expression, false, ordinal);
        }

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, boolean wildcard, int ordinal) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
            this.wildcard = wildcard;
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
        final String qualifiedKey;
        final boolean inputOnly;

        SourceColumn(String qualifier, String name) {
            this(qualifier, name, null);
        }

        SourceColumn(String qualifier, String name, String qualifiedKey) {
            this(null, qualifier, name, qualifiedKey, false);
        }

        private SourceColumn(ColumnRef resolvedRef, String qualifier, String name, String qualifiedKey,
                             boolean inputOnly) {
            this.resolvedRef = resolvedRef;
            this.qualifier = qualifier;
            this.name = name;
            this.qualifiedKey = qualifiedKey;
            this.inputOnly = inputOnly;
        }

        static SourceColumn resolved(ColumnRef ref) {
            return new SourceColumn(ref, null, ref.getName(), null, false);
        }

        SourceColumn inInputScope() {
            return inputOnly || resolvedRef != null || qualifier != null
                    ? this : new SourceColumn(null, null, name, qualifiedKey, true);
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
            return inputOnly == that.inputOnly
                    && Objects.equals(resolvedRef, that.resolvedRef)
                    && Objects.equals(qualifier, that.qualifier)
                    && Objects.equals(name, that.name)
                    && Objects.equals(qualifiedKey, that.qualifiedKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolvedRef, qualifier, name, qualifiedKey, inputOnly);
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
