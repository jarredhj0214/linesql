package io.github.linesql.dialect.flink;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.flink.antlr.FlinkParser;
import io.github.linesql.dialect.flink.antlr.FlinkParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
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
        result.getColumnLineage().addAll(branchResult.getColumnLineage());
        LineageModelUtils.mergeColumnUsages(result, branchResult);
    }

    @Override
    public Void visitInsertStatement(FlinkParser.InsertStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (FlinkParser.IdentifierContext id : ctx.columnList.identifier()) {
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
    public Void visitUpdateStmt(FlinkParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(FlinkParser.UpdateStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);

        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        collectSubqueryInputs(ctx);

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
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeStatement(FlinkParser.MergeStatementContext ctx) {
        // Output table = MERGE INTO target
        TableRef target = tableRef(ctx.multipartIdentifier(0));
        outputTables.add(target);
        inputTables.add(target);
        String alias = tableAlias(ctx.tableAlias(0));
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), target);
        }
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);

        // Input table from USING clause - either table name or subquery
        if (ctx.multipartIdentifier().size() > 1) {
            // USING tableName
            TableRef source = tableRef(ctx.multipartIdentifier(1));
            inputTables.add(source);
            tableAliases.put(source.getName().toLowerCase(Locale.ROOT), source);
            String sourceAlias = tableAlias(ctx.tableAlias(1));
            if (sourceAlias != null) {
                tableAliases.put(sourceAlias.toLowerCase(Locale.ROOT), source);
            }
        }
        if (ctx.query() != null) {
            // USING (subquery) alias - collect input tables from the subquery
            LineageResult subResult = lineageForQuery(ctx.query());
            inputTables.addAll(subResult.getInputTables());
        }
        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        // Collect column lineage from WHEN MATCHED THEN UPDATE SET assignments
        List<ColumnLineage> assignments = new ArrayList<>();
        for (FlinkParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.expression() != null) {
                addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(clause.expression()));
            }
            if (clause.mergeMatchedAction() != null
                    && clause.mergeMatchedAction().assignmentList() != null) {
                assignments.addAll(readAssignments(clause.mergeMatchedAction().assignmentList(), target));
            }
            if (clause.mergeNotMatchedAction() != null) {
                assignments.addAll(readMergeInsertValues(clause.mergeNotMatchedAction(), target));
            }
        }
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
    public Void visitCreateTableStmt(FlinkParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(FlinkParser.CreateTableStatementContext ctx) {
        if (ctx.likeClause() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_LIKE);
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            inputTables.add(tableRef(ctx.likeClause().source));
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableModelSemantics(target, ctx.tableElementList(), ctx.distributionClause(), ctx.partitionedByClause());
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            collectColumnNameOnlyTargets(ctx.tableElementList());
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            registerTableSchema(target, ctx.tableElementList());
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateCatalogStmt(FlinkParser.CreateCatalogStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
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
            collectColumnNameOnlyTargets(ctx.tableElementList());
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            registerTableSchema(target, ctx.tableElementList());
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
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        registerTemporaryRelation(target);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropTableStmt(FlinkParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(FlinkParser.DropTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
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
        outputTables.add(tableRef(ctx.multipartIdentifier()));
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
        outputTables.add(tableRef(ctx.multipartIdentifier()));
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
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(FlinkParser.AlterTableAddColumnContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.tableElement());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableChangeColumn(FlinkParser.AlterTableChangeColumnContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableRenameColumn(FlinkParser.AlterTableRenameColumnContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
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
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableModifyColumns(FlinkParser.AlterTableModifyColumnsContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        addTableElementSemantics(target, ctx.alterTableElementList());
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableDropColumn(FlinkParser.AlterTableDropColumnContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
    }

    @Override
    public Void visitAlterTableDropColumns(FlinkParser.AlterTableDropColumnsContext ctx) {
        return addAlteredTable(ctx.multipartIdentifier());
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
        inputTables.add(tableRef(ctx.multipartIdentifier(0)));
        outputTables.add(tableRef(ctx.multipartIdentifier(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewAsQuery(FlinkParser.AlterViewAsQueryContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
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
        addTableElementSemantics(target, ctx.materializedTableAction().tableElement());
        if (ctx.materializedTableAction().query() != null) {
            visit(ctx.materializedTableAction().query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitUseStmt(FlinkParser.UseStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
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
                    inputTables.add(tableRef((FlinkParser.MultipartIdentifierContext) child));
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
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
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
        // Visit all insert statements in the set
        List<FlinkParser.InsertStatementContext> inserts = ctx.insertStatement();
        if (inserts != null && !inserts.isEmpty()) {
            for (FlinkParser.InsertStatementContext insert : inserts) {
                visitInsertStatement(insert);
            }
        }
        return null;
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(FlinkParser.CtesContext ctx) {
        for (FlinkParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(FlinkParser.SetOperationContext ctx) {
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
    public Void visitTableName(FlinkParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
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
    public Void visitTemporalTableName(FlinkParser.TemporalTableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        addInputTable(table, ctx.tableAlias(), true);
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
        derivedReferences.add(normalizedAlias);
        derivedAliases.put(normalizedAlias, normalizedAlias);
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(normalizedAlias));
        refreshColumnLineage();
        return null;
    }

    @Override
    public Void visitAliasedQuery(FlinkParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(FlinkParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (FlinkParser.LateralViewClauseContext lateralView : ctx.lateralViewClause()) {
            visit(lateralView);
        }
        for (FlinkParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.temporalClause() != null) {
                visit(join.temporalClause());
            }
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
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
        // TABLE(fn(TABLE source, DESCRIPTOR(col), expr)) - visit args to collect table refs
        visitChildren(ctx);
        TableRef source = firstTableFunctionInput(ctx.tableFunctionArgList());
        addWindowTableFunctionUsages(ctx.functionName(), ctx.tableFunctionArgList(), source);
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
        addWindowTableFunctionUsages(ctx.functionName(),
                ctx.tableFunctionArgList(),
                firstTableFunctionInput(ctx.tableFunctionArgList()));
        registerGeneratedRelation(ctx.tableAlias(), sourceColumns(ctx.tableFunctionArgList()));
        if (ctx.tableFunctionArgList() != null) {
            visit(ctx.tableFunctionArgList());
        }
        return null;
    }

    @Override
    public Void visitLateralTableFunction(FlinkParser.LateralTableFunctionContext ctx) {
        addWindowTableFunctionUsages(ctx.functionName(),
                ctx.tableFunctionArgList(),
                firstTableFunctionInput(ctx.tableFunctionArgList()));
        registerGeneratedRelation(ctx.tableAlias(), sourceColumns(ctx.tableFunctionArgList()));
        if (ctx.tableFunctionArgList() != null) {
            visit(ctx.tableFunctionArgList());
        }
        return null;
    }

    @Override
    public Void visitUnnestTableFunction(FlinkParser.UnnestTableFunctionContext ctx) {
        registerGeneratedRelation(ctx.tableAlias(), sourceColumns(ctx.expression()));
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
    public Void visitSelectClause(FlinkParser.SelectClauseContext ctx) {
        collectSelectClauseProjections(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitWhereClause(FlinkParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
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
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(element));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(FlinkParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitQualifyClause(FlinkParser.QualifyClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
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
        for (FlinkParser.SortItemContext sortItem : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
        return visitChildren(ctx);
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
        outputTables.add(tableRef(ctx));
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
        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), table);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        refreshColumnLineage();
    }

    private void addDerivedReference(String rawName, FlinkParser.TableAliasContext aliasCtx) {
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
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        derivedColumnLineage.put(normalizedAlias, columns);
        derivedReferences.add(normalizedAlias);
        derivedAliases.put(normalizedAlias, normalizedAlias);
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(normalizedAlias));
        refreshColumnLineage();
    }

    private static TableRef firstTableFunctionInput(FlinkParser.TableFunctionArgListContext ctx) {
        if (ctx == null) {
            return null;
        }
        for (FlinkParser.TableFunctionArgContext arg : ctx.tableFunctionArg()) {
            TableRef table = firstTableFunctionInput(arg);
            if (table != null) {
                return table;
            }
        }
        return null;
    }

    private static TableRef firstTableFunctionInput(FlinkParser.TableFunctionArgContext ctx) {
        if (ctx instanceof FlinkParser.TvfTableArgContext) {
            return tableRef(((FlinkParser.TvfTableArgContext) ctx).multipartIdentifier());
        }
        if (ctx instanceof FlinkParser.NamedTableFunctionArgContext) {
            return firstTableFunctionInput(((FlinkParser.NamedTableFunctionArgContext) ctx).tableFunctionArg());
        }
        return null;
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
            descriptors.add(cleanIdentifier(((FlinkParser.TvfDescriptorArgContext) ctx).identifier()));
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

    private LineageResult lineageForQueryTerm(FlinkParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(queryResult);
        queryVisitor.setContext(context);
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

    private void addWindowTableFunctionUsages(FlinkParser.FunctionNameContext functionName,
                                              FlinkParser.TableFunctionArgListContext args,
                                              TableRef source) {
        if (source == null || args == null || !isWindowTableFunction(functionName)) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String descriptor : tableFunctionDescriptors(args)) {
            refs.add(new ColumnRef(source, descriptor));
        }
        if (!refs.isEmpty()) {
            LineageModelUtils.addColumnUsages(result,
                    ColumnUsageType.WINDOW_ORDER_BY,
                    distinctColumnRefs(refs));
        }
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

    private void addUsingColumnUsages(FlinkParser.IdentifierListContext ctx) {
        addUsingColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(FlinkParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
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
        if (suppressColumnLineage || projections.isEmpty()) {
            return;
        }
        TableRef targetTable = outputTables.size() == 1 ? outputTables.iterator().next() : null;
        List<ColumnLineage> columnLineage = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = projections.get(i);
            if (projection.wildcard && expandWildcardProjectionLineage(targetTable, projection, columnLineage)) {
                continue;
            }
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null) {
                continue;
            }
            String targetColumn = targetColumn(projection, columnLineage.size());
            columnLineage.add(LineageModelUtils.columnLineage(targetTable, targetColumn, sources, projection.expression));
        }
        result.setColumnLineage(columnLineage);
    }

    private boolean expandWildcardProjectionLineage(
            TableRef targetTable,
            Projection projection,
            List<ColumnLineage> columnLineage) {
        if (projection.sourceColumns.size() != 1) {
            return false;
        }
        SourceColumn wildcard = projection.sourceColumns.get(0);
        if (!"*".equals(wildcard.name)) {
            return false;
        }
        String derivedName = null;
        if (wildcard.qualifier != null) {
            derivedName = derivedAliases.get(wildcard.qualifier.toLowerCase(Locale.ROOT));
        } else if (visibleRelations.size() == 1 && visibleRelations.get(0).derivedName != null) {
            derivedName = visibleRelations.get(0).derivedName;
        }
        if (derivedName == null) {
            return false;
        }
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null || columns.isEmpty() || (columns.size() == 1 && columns.containsKey("*"))) {
            return false;
        }
        for (Map.Entry<String, List<ColumnRef>> entry : columns.entrySet()) {
            if ("*".equals(entry.getKey())) {
                continue;
            }
            columnLineage.add(LineageModelUtils.columnLineage(
                    targetTable,
                    entry.getKey(),
                    entry.getValue(),
                    projection.expression));
        }
        return true;
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

    private void collectColumnNameOnlyTargets(FlinkParser.TableElementListContext ctx) {
        if (ctx == null) {
            return;
        }
        for (FlinkParser.TableElementContext element : ctx.tableElement()) {
            if (element instanceof FlinkParser.ColumnNameOnlyDefinitionContext) {
                FlinkParser.ColumnNameOnlyDefinitionContext column =
                        (FlinkParser.ColumnNameOnlyDefinitionContext) element;
                insertTargetColumns.add(cleanIdentifier(column.identifier()));
            }
        }
    }

    private String targetColumn(Projection projection, int index) {
        if (projection.wildcard) {
            return "*";
        }
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
                table = tableAliases.get(sourceColumn.qualifier.toLowerCase(Locale.ROOT));
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
            String key = qualifier.toLowerCase(Locale.ROOT);
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
        Map<String, ColumnRef> unique = new LinkedHashMap<>();
        for (ColumnRef ref : refs) {
            unique.put(columnKey(ref), ref);
        }
        return new ArrayList<>(unique.values());
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

    private void registerTemporaryRelation(TableRef table) {
        if (context == null) {
            return;
        }
        context.getTemporaryRelations().put(relationKey(table), copyResult(result));
    }

    private void registerTableSchema(TableRef table, FlinkParser.TableElementListContext tableElements) {
        if (context == null || tableElements == null) {
            return;
        }
        List<String> columnNames = declaredColumnNames(tableElements);
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
        context.getTemporaryRelations().put(relationKey(table), schema);
    }

    private static List<String> declaredColumnNames(FlinkParser.TableElementListContext ctx) {
        List<String> columns = new ArrayList<>();
        for (FlinkParser.TableElementContext element : ctx.tableElement()) {
            if (element instanceof FlinkParser.ColumnDefinitionContext) {
                columns.add(cleanIdentifier(((FlinkParser.ColumnDefinitionContext) element).identifier()));
            } else if (element instanceof FlinkParser.ColumnNameOnlyDefinitionContext) {
                columns.add(cleanIdentifier(((FlinkParser.ColumnNameOnlyDefinitionContext) element).identifier()));
            } else if (element instanceof FlinkParser.ComputedColumnDefinitionContext) {
                columns.add(cleanIdentifier(((FlinkParser.ComputedColumnDefinitionContext) element).identifier()));
            }
        }
        return columns;
    }

    private String temporaryRelationName(TableRef table) {
        if (context == null) {
            return null;
        }
        String key = relationKey(table);
        return context.getTemporaryRelations().containsKey(key) ? key : null;
    }

    private void addTemporaryRelationReference(String relationName, FlinkParser.TableAliasContext aliasCtx) {
        LineageResult relation = context.getTemporaryRelations().get(relationName);
        if (relation == null) {
            return;
        }
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(relationName));
        derivedReferences.add(relationName);
        derivedAliases.put(relationName, relationName);
        String alias = tableAlias(aliasCtx);
        if (alias != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), relationName);
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (ColumnLineage lineage : relation.getColumnLineage()) {
            columns.put(lineage.getTarget().getName(), lineage.getSources());
        }
        if (columns.isEmpty() && relation.getInputTables().size() == 1) {
            List<ColumnRef> wildcard = new ArrayList<>();
            wildcard.add(new ColumnRef(relation.getInputTables().get(0), "*"));
            columns.put("*", wildcard);
        }
        derivedColumnLineage.put(relationName, columns);
        for (TableRef table : relation.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relation);
        refreshColumnLineage();
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

    private boolean isCteReference(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && cteNames.contains(table.getName().toLowerCase(Locale.ROOT));
    }

    private List<ColumnLineage> readAssignments(FlinkParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (FlinkParser.AssignmentContext assignment : ctx.assignment()) {
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

    private List<ColumnLineage> readMergeInsertValues(FlinkParser.MergeNotMatchedActionContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx == null || ctx.identifierList() == null || ctx.expressionList() == null) {
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

    private List<ColumnRef> patternMeasureRefs(FlinkParser.ExpressionContext expression, TableRef table) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns(expression)) {
            refs.add(new ColumnRef(table, sourceColumn.name));
        }
        return refs;
    }

    private void collectSubqueryInputs(ParseTree tree) {
        if (tree instanceof FlinkParser.ScalarSubqueryContext) {
            FlinkParser.ScalarSubqueryContext subquery = (FlinkParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        if (tree instanceof FlinkParser.ExistsExprContext) {
            FlinkParser.ExistsExprContext exists = (FlinkParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        if (tree instanceof FlinkParser.PredicateContext) {
            FlinkParser.PredicateContext predicate = (FlinkParser.PredicateContext) tree;
            if (predicate.query() != null) {
                LineageResult subResult = lineageForQuery(predicate.query());
                inputTables.addAll(subResult.getInputTables());
                result.setInputTables(new ArrayList<>(inputTables));
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSubqueryInputs(tree.getChild(i));
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree instanceof FlinkParser.ScalarSubqueryContext) {
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
        LineageResult queryResult = new LineageResult();
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(queryResult);
        queryVisitor.setContext(context);
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
        for (FlinkParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof FlinkParser.SelectExpressionContext) {
                Projection projection = projection((FlinkParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            } else if (item instanceof FlinkParser.SelectQualifiedStarContext) {
                FlinkParser.SelectQualifiedStarContext star = (FlinkParser.SelectQualifiedStarContext) item;
                projections.add(wildcardProjection(qualifiedName(star.qualifiedName()), star.getText()));
            } else if (item instanceof FlinkParser.SelectStarContext) {
                projections.add(wildcardProjection(null, item.getText()));
            }
        }
    }

    private List<Projection> transformProjections(FlinkParser.TransformClauseContext ctx) {
        List<Projection> result = new ArrayList<>();
        List<SourceColumn> inputColumns = sourceColumns(ctx.expressionList());
        List<String> outputColumns = transformOutputNames(ctx.transformOutputList());
        for (String outputColumn : outputColumns) {
            result.add(new Projection(inputColumns, outputColumn, ctx.getText()));
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
        if (!(query.queryTerm() instanceof FlinkParser.QueryTermDefaultContext)) {
            return null;
        }
        FlinkParser.QueryPrimaryContext primary =
                ((FlinkParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof FlinkParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((FlinkParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(FlinkParser.SelectExpressionContext ctx) {
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
        String targetColumn = ctx.alias == null
                ? inferredSingleSourceTarget(directColumn, sourceColumns, ctx.expression())
                : cleanIdentifier(ctx.alias);
        if (targetColumn == null) {
            return null;
        }
        return new Projection(sourceColumns, targetColumn, expression);
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

    private static Projection wildcardProjection(String qualifier, String expression) {
        List<SourceColumn> sourceColumns = new ArrayList<>();
        sourceColumns.add(new SourceColumn(qualifier, "*"));
        return new Projection(sourceColumns, "*", expression, true);
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
        Set<SourceColumn> columns = new LinkedHashSet<>();
        if (tree != null) {
            collectSourceColumns(tree, columns);
        }
        return new ArrayList<>(columns);
    }

    private void addScalarSubquerySourceColumns(FlinkParser.QueryContext query, Set<SourceColumn> columns) {
        LineageResult subResult = lineageForQuery(query);
        for (TableRef table : subResult.getInputTables()) {
            addInputTable(table, false);
        }
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(FlinkParser.QueryContext query) {
        FlinkLineageVisitor queryVisitor = new FlinkLineageVisitor(new LineageResult());
        queryVisitor.setContext(context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
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

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof FlinkParser.ColumnReferenceContext) {
            FlinkParser.ColumnReferenceContext colRef = (FlinkParser.ColumnReferenceContext) tree;
            columns.add(new SourceColumn(null, cleanIdentifier(colRef.identifier())));
            return;
        }
        if (tree instanceof FlinkParser.DereferenceContext) {
            FlinkParser.DereferenceContext deref = (FlinkParser.DereferenceContext) tree;
            if (deref.primaryExpression() instanceof FlinkParser.SubscriptExpressionContext) {
                collectSourceColumns(deref.primaryExpression(), columns);
                return;
            }
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
        if (tree instanceof FlinkParser.SubscriptExpressionContext) {
            FlinkParser.SubscriptExpressionContext subscript = (FlinkParser.SubscriptExpressionContext) tree;
            collectSourceColumns(subscript.primaryExpression(), columns);
            collectSourceColumns(subscript.expression(), columns);
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

    private static TableRef tableRef(FlinkParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(FlinkParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (FlinkParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String qualifiedName(FlinkParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (FlinkParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return String.join(".", parts);
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
        return String.join(".", parts).toLowerCase(Locale.ROOT);
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

    private static class Projection {
        final List<SourceColumn> sourceColumns;
        final String targetColumn;
        final String expression;
        final boolean wildcard;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
            this(sourceColumns, targetColumn, expression, false);
        }

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, boolean wildcard) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
            this.wildcard = wildcard;
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
