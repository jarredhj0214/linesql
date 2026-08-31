package io.github.linesql.dialect.starrocks;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.starrocks.antlr.StarRocksParser;
import io.github.linesql.dialect.starrocks.antlr.StarRocksParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class StarRocksLineageVisitor extends StarRocksParserBaseVisitor<Void> {
    private final LineageResult result;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Map<String, List<ColumnRef>> pivotColumnLineage = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final Deque<Set<String>> lambdaParameterScopes = new ArrayDeque<>();
    private final List<Projection> projections = new ArrayList<>();
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private TableRef currentDmlTarget;
    private int visibleRelationCount;
    private boolean suppressColumnLineage;

    StarRocksLineageVisitor(LineageResult result) {
        this.result = result;
    }

    @Override
    public Void visitStatementDefault(StarRocksParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAdminStmt(StarRocksParser.AdminStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterTaskStmt(StarRocksParser.AlterTaskStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAdminShowStmt(StarRocksParser.AdminShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAdminShowStatement(StarRocksParser.AdminShowStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitAdminRepairStmt(StarRocksParser.AdminRepairStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAdminRepairStatement(StarRocksParser.AdminRepairStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAdminCheckTabletStmt(StarRocksParser.AdminCheckTabletStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAdminSetPartitionVersionStmt(StarRocksParser.AdminSetPartitionVersionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAdminSetPartitionVersionStatement(StarRocksParser.AdminSetPartitionVersionStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitInsertStmt(StarRocksParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExplainStmt(StarRocksParser.ExplainStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitSubmitTaskStmt(StarRocksParser.SubmitTaskStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitSubmitTaskStatement(StarRocksParser.SubmitTaskStatementContext ctx) {
        if (ctx.insertStatement() != null) {
            result.setStatementType(StatementType.INSERT);
            visit(ctx.insertStatement());
        } else if (ctx.createTableStatement() != null) {
            visit(ctx.createTableStatement());
        } else if (ctx.query() != null) {
            result.setStatementType(StatementType.SELECT);
            visit(ctx.query());
        }
        return null;
    }

    @Override
    public Void visitDropTaskStmt(StarRocksParser.DropTaskStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitInsertStatement(StarRocksParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = ctx.targetTable == null ? null : tableRef(ctx.targetTable);
        if (target != null) {
            outputTables.add(target);
        }
        StarRocksParser.InsertColumnMappingContext columnMapping = ctx.insertColumnMapping();
        if (columnMapping != null && columnMapping.columnList != null) {
            for (StarRocksParser.IdentifierContext id : columnMapping.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        if (ctx.query() != null) {
            visit(ctx.query());
            refreshColumnLineage();
            if (target != null) {
                retargetColumnLineage(target);
            }
        } else {
            suppressColumnLineage = true;
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitExportTableStmt(StarRocksParser.ExportTableStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExportTableStatement(StarRocksParser.ExportTableStatementContext ctx) {
        inputTables.add(tableRef(ctx.source));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitCreateRoutineLoadStmt(StarRocksParser.CreateRoutineLoadStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateRoutineLoadStatement(StarRocksParser.CreateRoutineLoadStatementContext ctx) {
        TableRef target = tableRef(ctx.target);
        currentDmlTarget = target;
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.RoutineLoadClauseContext clause : ctx.routineLoadClause()) {
            if (clause.loadColumnList() != null) {
                lineages.addAll(readLoadDataColumns(clause.loadColumnList(), target));
            }
            if (clause.whereClause() != null) {
                addColumnUsages(ColumnUsageType.WHERE, sourceColumns(clause.whereClause().expression()));
            }
        }
        result.setColumnLineage(lineages);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterRoutineLoadStmt(StarRocksParser.AlterRoutineLoadStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitRoutineLoadControlStmt(StarRocksParser.RoutineLoadControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCancelLoadStmt(StarRocksParser.CancelLoadStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCancelExportStmt(StarRocksParser.CancelExportStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCancelRefreshMaterializedViewStmt(StarRocksParser.CancelRefreshMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCancelRefreshMaterializedViewStatement(
            StarRocksParser.CancelRefreshMaterializedViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCancelAlterTableStmt(StarRocksParser.CancelAlterTableStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCancelAlterTableStatement(StarRocksParser.CancelAlterTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreatePipeStmt(StarRocksParser.CreatePipeStmtContext ctx) {
        return visit(ctx.createPipeStatement());
    }

    @Override
    public Void visitCreatePipeStatement(StarRocksParser.CreatePipeStatementContext ctx) {
        visit(ctx.insertStatement());
        result.setStatementType(StatementType.LOAD_DATA);
        TableRef target = outputTables.size() == 1 ? outputTables.iterator().next() : null;
        result.setColumnLineage(externalLoadColumnLineage(
                result.getColumnLineage(),
                target,
                insertColumns(ctx.insertStatement())));
        projections.clear();
        suppressColumnLineage = true;
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private List<String> insertColumns(StarRocksParser.InsertStatementContext ctx) {
        StarRocksParser.InsertColumnMappingContext columnMapping = ctx.insertColumnMapping();
        if (columnMapping == null || columnMapping.columnList == null) {
            return new ArrayList<>();
        }
        return identifierNames(columnMapping.columnList);
    }

    private List<ColumnLineage> externalLoadColumnLineage(
            List<ColumnLineage> lineages,
            TableRef target,
            List<String> targetColumns) {
        List<ColumnLineage> sanitized = new ArrayList<>();
        for (ColumnLineage lineage : lineages) {
            if (lineage.getTarget() == null) {
                continue;
            }
            sanitized.add(LineageModelUtils.columnLineage(
                    lineage.getTarget().getTable(),
                    lineage.getTarget().getName(),
                    new ArrayList<ColumnRef>(),
                    lineage.getExpression()));
        }
        if (sanitized.isEmpty() && target != null) {
            for (String columnName : targetColumns) {
                sanitized.add(LineageModelUtils.columnLineage(
                        target,
                        columnName,
                        new ArrayList<ColumnRef>(),
                        null));
            }
        }
        return sanitized;
    }

    @Override
    public Void visitAlterPipeStmt(StarRocksParser.AlterPipeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropPipeStmt(StarRocksParser.DropPipeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateAnalyzeStmt(StarRocksParser.CreateAnalyzeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visit(ctx.createAnalyzeStatement());
    }

    @Override
    public Void visitCreateAnalyzeStatement(StarRocksParser.CreateAnalyzeStatementContext ctx) {
        if (ctx.multipartIdentifier() != null) {
            inputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
        }
        return null;
    }

    @Override
    public Void visitDropAnalyzeStmt(StarRocksParser.DropAnalyzeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropStatsStmt(StarRocksParser.DropStatsStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropStatsStatement(StarRocksParser.DropStatsStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitKillAnalyzeStmt(StarRocksParser.KillAnalyzeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAccountControlStmt(StarRocksParser.AccountControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitBackupStmt(StarRocksParser.BackupStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitBackupStatement(StarRocksParser.BackupStatementContext ctx) {
        collectBackupObjects(ctx.backupOnClause(), true);
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitRestoreStmt(StarRocksParser.RestoreStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRestoreStatement(StarRocksParser.RestoreStatementContext ctx) {
        collectBackupObjects(ctx.backupOnClause(), false);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRepositoryStmt(StarRocksParser.RepositoryStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitFileStmt(StarRocksParser.FileStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCatalogStmt(StarRocksParser.CatalogStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitStorageVolumeStmt(StarRocksParser.StorageVolumeStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    private void collectBackupObjects(StarRocksParser.BackupOnClauseContext ctx, boolean input) {
        if (ctx == null) {
            return;
        }
        for (StarRocksParser.BackupObjectContext object : ctx.backupObject()) {
            if (object.multipartIdentifier() == null || object.FUNCTION() != null || object.FUNCTIONS() != null) {
                continue;
            }
            TableRef table = tableRef(object.multipartIdentifier());
            if (input) {
                inputTables.add(table);
            } else {
                outputTables.add(table);
            }
        }
    }

    @Override
    public Void visitResourceStmt(StarRocksParser.ResourceStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitResourceGroupStmt(StarRocksParser.ResourceGroupStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateFunctionStmt(StarRocksParser.CreateFunctionStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        return null;
    }

    @Override
    public Void visitDropFunctionStmt(StarRocksParser.DropFunctionStmtContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitCreateDictionaryStmt(StarRocksParser.CreateDictionaryStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateDictionaryStatement(StarRocksParser.CreateDictionaryStatementContext ctx) {
        TableRef source = tableRef(ctx.source);
        TableRef target = tableRef(ctx.name);
        inputTables.add(source);
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.DictionaryColumnContext column : ctx.dictionaryColumn()) {
            String columnName = cleanIdentifier(column.identifier());
            lineages.add(LineageModelUtils.columnLineage(
                    target,
                    columnName,
                    Collections.singletonList(new ColumnRef(source, columnName)),
                    null));
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRefreshDictionaryStmt(StarRocksParser.RefreshDictionaryStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCancelRefreshDictionaryStmt(StarRocksParser.CancelRefreshDictionaryStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropDictionaryStmt(StarRocksParser.DropDictionaryStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitLoadLabelStmt(StarRocksParser.LoadLabelStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterLoadStmt(StarRocksParser.AlterLoadStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterSystemStmt(StarRocksParser.AlterSystemStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCancelDecommissionStmt(StarRocksParser.CancelDecommissionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitBackendBlacklistStmt(StarRocksParser.BackendBlacklistStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitKillStmt(StarRocksParser.KillStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitSyncStmt(StarRocksParser.SyncStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitLoadDataElement(StarRocksParser.LoadDataElementContext ctx) {
        TableRef target = tableRef(ctx.target);
        currentDmlTarget = target;
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.LoadDataOptionContext option : ctx.loadDataOption()) {
            if (isLoadColumnOption(option)) {
                lineages.addAll(readLoadDataColumns(option.loadColumnList(), target));
            }
            if (option.FROM() != null && option.identifierList() != null) {
                lineages.addAll(readLoadDataColumns(option.identifierList(), target));
            }
            if (option.whereClause() != null) {
                addColumnUsages(ColumnUsageType.WHERE, sourceColumns(option.whereClause().expression()));
            }
            if (option.assignmentList() != null) {
                collectSubqueryInputs(option.assignmentList());
                lineages.addAll(readAssignments(option.assignmentList(), target));
            }
        }
        result.setColumnLineage(lineages);
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private boolean isLoadColumnOption(StarRocksParser.LoadDataOptionContext option) {
        if (option.loadColumnList() == null) {
            return false;
        }
        int firstTokenType = option.getStart().getType();
        return firstTokenType == StarRocksParser.LPAREN;
    }

    private List<ColumnLineage> readLoadDataColumns(StarRocksParser.LoadColumnListContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.LoadColumnItemContext item : ctx.loadColumnItem()) {
            if (item.identifier() != null) {
                lineages.add(LineageModelUtils.columnLineage(
                        target,
                        cleanIdentifier(item.identifier()),
                        new ArrayList<ColumnRef>(),
                        null));
            } else if (item.assignment() != null) {
                ColumnLineage lineage = readAssignment(item.assignment(), target);
                if (lineage != null) {
                    lineages.add(lineage);
                }
            }
        }
        return lineages;
    }

    private List<ColumnLineage> readLoadDataColumns(StarRocksParser.IdentifierListContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            lineages.add(LineageModelUtils.columnLineage(
                    target,
                    columnName,
                    new ArrayList<ColumnRef>(),
                    null));
        }
        return lineages;
    }

    @Override
    public Void visitUpdateStmt(StarRocksParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(StarRocksParser.UpdateStatementContext ctx) {
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
            visitRelationList(ctx.relationList());
        }
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
    public Void visitDeleteStmt(StarRocksParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteStatement(StarRocksParser.DeleteStatementContext ctx) {
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
            visitRelationList(ctx.relationList());
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
    public Void visitCreateTableStmt(StarRocksParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStmt(StarRocksParser.CreateIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStatement(StarRocksParser.CreateIndexStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateDatabaseStmt(StarRocksParser.CreateDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_SCHEMA);
        return null;
    }

    @Override
    public Void visitCreateTableStatement(StarRocksParser.CreateTableStatementContext ctx) {
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
        if (ctx.ctasElements != null) {
            insertTargetColumns.addAll(ctasElementNames(ctx.ctasElements));
        }
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            result.setColumnLineage(readGeneratedColumnLineage(ctx.tableElementList(), target));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateViewStmt(StarRocksParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(StarRocksParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (StarRocksParser.ViewColumnDefinitionContext column : ctx.viewColumnList.viewColumnDefinition()) {
                insertTargetColumns.add(cleanIdentifier(column.identifier()));
            }
        }
        visit(ctx.query());
        refreshColumnLineage();
        retargetColumnLineage(target);
        addMaterializedViewOrderByUsages(ctx.materializedViewOption());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterViewStmt(StarRocksParser.AlterViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterViewStatement(StarRocksParser.AlterViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.alterViewColumnList() != null) {
            for (StarRocksParser.AlterViewColumnContext column : ctx.alterViewColumnList().alterViewColumn()) {
                insertTargetColumns.add(cleanIdentifier(column.identifier()));
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
    public Void visitDropTableStmt(StarRocksParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropIndexStmt(StarRocksParser.DropIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropIndexStatement(StarRocksParser.DropIndexStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropDatabaseStmt(StarRocksParser.DropDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.DROP_SCHEMA);
        return null;
    }

    @Override
    public Void visitDropTableStatement(StarRocksParser.DropTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(StarRocksParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(StarRocksParser.DropViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(StarRocksParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(StarRocksParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRefreshExternalTableStmt(StarRocksParser.RefreshExternalTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRefreshExternalTableStatement(StarRocksParser.RefreshExternalTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRefreshMaterializedViewStmt(StarRocksParser.RefreshMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRefreshMaterializedViewStatement(StarRocksParser.RefreshMaterializedViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewStmt(StarRocksParser.AlterMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterMaterializedViewStatement(StarRocksParser.AlterMaterializedViewStatementContext ctx) {
        TableRef view = tableRef(ctx.multipartIdentifier());
        if (ctx.alterMaterializedViewAction() != null
                && ctx.alterMaterializedViewAction().RENAME() != null
                && ctx.alterMaterializedViewAction().multipartIdentifier() != null) {
            result.setStatementType(StatementType.RENAME_TABLE);
            inputTables.add(view);
            outputTables.add(tableRef(ctx.alterMaterializedViewAction().multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
        } else if (ctx.alterMaterializedViewAction() != null
                && ctx.alterMaterializedViewAction().SWAP() != null
                && ctx.alterMaterializedViewAction().multipartIdentifier() != null) {
            outputTables.add(view);
            outputTables.add(tableRef(ctx.alterMaterializedViewAction().multipartIdentifier()));
        } else {
            outputTables.add(view);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterDatabaseStmt(StarRocksParser.AlterDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterTableStmt(StarRocksParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(StarRocksParser.AlterTableRenameContext ctx) {
        List<StarRocksParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableSwap(StarRocksParser.AlterTableSwapContext ctx) {
        List<StarRocksParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        outputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(StarRocksParser.AlterTableAddColumnContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(StarRocksParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAnalyzeTableStmt(StarRocksParser.AnalyzeTableStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeTableStatement(StarRocksParser.AnalyzeTableStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitShowStmt(StarRocksParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(StarRocksParser.ShowStatementContext ctx) {
        if (ctx.multipartIdentifier() != null) {
            inputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
        }
        return null;
    }

    @Override
    public Void visitDescribeStmt(StarRocksParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTransactionControlStmt(StarRocksParser.TransactionControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitPreparedStmt(StarRocksParser.PreparedStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitUseStmt(StarRocksParser.UseStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
        return null;
    }

    @Override
    public Void visitSetCatalogStmt(StarRocksParser.SetCatalogStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
        return null;
    }

    @Override
    public Void visitSetStmt(StarRocksParser.SetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDescribeStatement(StarRocksParser.DescribeStatementContext ctx) {
        if (ctx.multipartIdentifier() != null) {
            inputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
        }
        return null;
    }

    @Override
    public Void visitCommentStmt(StarRocksParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(StarRocksParser.CommentStatementContext ctx) {
        StarRocksParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
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
    public Void visitCtes(StarRocksParser.CtesContext ctx) {
        for (StarRocksParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(StarRocksParser.SetOperationContext ctx) {
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
    public Void visitTableName(StarRocksParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        if (isCteReference(table)) {
            addDerivedReference(table.getName(), ctx.tableAlias());
            return null;
        }
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitFilesTableFunction(StarRocksParser.FilesTableFunctionContext ctx) {
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived("$files" + visibleRelationCount));
        return null;
    }

    @Override
    public Void visitTableFunction(StarRocksParser.TableFunctionContext ctx) {
        if (ctx.expressionList() == null) {
            addOpaqueTableFunction(ctx.identifier(), ctx.tableAlias());
            return null;
        }
        List<ColumnRef> sources = columnRefs(sourceColumns(ctx.expressionList()), new LinkedHashSet<>());
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        String functionName = cleanIdentifier(ctx.identifier()).toLowerCase(Locale.ROOT);
        String alias = tableAlias(ctx.tableAlias());
        String derivedName = alias == null ? functionName : alias.toLowerCase(Locale.ROOT);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        columns.put("unnest", sources);
        columns.put(functionName, sources);
        for (String aliasColumn : tableAliasColumns(ctx.tableAlias())) {
            columns.put(aliasColumn.toLowerCase(Locale.ROOT), sources);
        }
        derivedColumnLineage.put(derivedName, columns);
        addDerivedReference(derivedName, ctx.tableAlias());
        return null;
    }

    private void addOpaqueTableFunction(StarRocksParser.IdentifierContext name, StarRocksParser.TableAliasContext aliasCtx) {
        String functionName = cleanIdentifier(name).toLowerCase(Locale.ROOT);
        String alias = tableAlias(aliasCtx);
        String derivedName = alias == null ? functionName : alias.toLowerCase(Locale.ROOT);
        derivedColumnLineage.put(derivedName, new LinkedHashMap<String, List<ColumnRef>>());
        addDerivedReference(derivedName, aliasCtx);
    }

    @Override
    public Void visitAliasedQuery(StarRocksParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(StarRocksParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (StarRocksParser.PivotClauseContext pivot : ctx.pivotClause()) {
            visit(pivot);
        }
        for (StarRocksParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    @Override
    public Void visitPivotClause(StarRocksParser.PivotClauseContext ctx) {
        registerPivotColumnLineage(ctx);
        addColumnUsages(ColumnUsageType.GROUP_BY, pivotColumnSourceColumns(ctx.pivotColumn()));
        return null;
    }

    @Override
    public Void visitSelectClause(StarRocksParser.SelectClauseContext ctx) {
        for (StarRocksParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof StarRocksParser.SelectExpressionContext) {
                Projection projection = projection((StarRocksParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWhereClause(StarRocksParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitJoinCriteria(StarRocksParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGroupByClause(StarRocksParser.GroupByClauseContext ctx) {
        addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(ctx));
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(StarRocksParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitQualifyClause(StarRocksParser.QualifyClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        collectWindowUsages(ctx.expression());
        collectSubqueryInputs(ctx.expression());
        return null;
    }

    @Override
    public Void visitWindowSpec(StarRocksParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (StarRocksParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (StarRocksParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(StarRocksParser.QueryOrganizationContext ctx) {
        for (StarRocksParser.SortItemContext sortItem : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitFunctionCallStar(StarRocksParser.FunctionCallStarContext ctx) {
        addFunctionFilterUsage(ctx.functionFilterClause());
        return visitChildren(ctx);
    }

    @Override
    public Void visitFunctionCall(StarRocksParser.FunctionCallContext ctx) {
        addFunctionFilterUsage(ctx.functionFilterClause());
        return visitChildren(ctx);
    }

    @Override
    public Void visitFunctionCallEmpty(StarRocksParser.FunctionCallEmptyContext ctx) {
        addFunctionFilterUsage(ctx.functionFilterClause());
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

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, StarRocksParser.TableAliasContext aliasCtx, boolean visibleRelation) {
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

    private void addDerivedReference(String rawName, StarRocksParser.TableAliasContext aliasCtx) {
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

    private void registerDerivedRelation(String name, StarRocksParser.QueryContext query, List<String> columnAliases) {
        LineageResult relationResult = new LineageResult();
        StarRocksLineageVisitor relationVisitor = new StarRocksLineageVisitor(relationResult);
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

    private LineageResult lineageForQueryTerm(StarRocksParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        StarRocksLineageVisitor queryVisitor = new StarRocksLineageVisitor(queryResult);
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

    private void addFunctionFilterUsage(StarRocksParser.FunctionFilterClauseContext ctx) {
        if (ctx != null) {
            pendingColumnUsages.add(new PendingColumnUsage(ColumnUsageType.WHERE, sourceColumns(ctx.expression())));
        }
    }

    private void addMaterializedViewOrderByUsages(List<StarRocksParser.MaterializedViewOptionContext> options) {
        for (StarRocksParser.MaterializedViewOptionContext option : options) {
            if (option.orderByDesc() == null) {
                continue;
            }
            List<ColumnRef> refs = new ArrayList<>();
            for (StarRocksParser.IdentifierContext identifier : option.orderByDesc().identifierList().identifier()) {
                String sortColumn = cleanIdentifier(identifier);
                for (ColumnLineage lineage : result.getColumnLineage()) {
                    ColumnRef target = lineage.getTarget();
                    if (target != null && target.getName() != null && target.getName().equalsIgnoreCase(sortColumn)) {
                        refs.addAll(lineage.getSources());
                    }
                }
            }
            if (refs.isEmpty()) {
                addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(option.orderByDesc()));
            } else {
                LineageModelUtils.addColumnUsages(result, ColumnUsageType.ORDER_BY, refs);
            }
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
            List<ColumnRef> resolved = columnRefs(singleton, new LinkedHashSet<String>());
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
                return columnRefs(projection.sourceColumns, new LinkedHashSet<String>());
            }
        }
        return null;
    }

    private void collectJoinColumnUsages(StarRocksParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(StarRocksParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void addUsingColumnUsages(StarRocksParser.IdentifierListContext ctx, int relationStart) {
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
        return columnRefs(projection.sourceColumns, new LinkedHashSet<>());
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

    private List<ColumnRef> columnRefs(List<SourceColumn> sourceColumns, Set<String> resolving) {
        TableRef defaultTable = singleVisiblePhysicalTable();
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
            if (sourceColumn.qualifier == null) {
                List<ColumnRef> pivotRefs = caseInsensitiveColumnRefs(pivotColumnLineage, sourceColumn.name);
                if (pivotRefs != null) {
                    refs.addAll(pivotRefs);
                    continue;
                }
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

    private TableRef singleVisiblePhysicalTable() {
        TableRef table = null;
        for (VisibleRelation relation : visibleRelations) {
            if (relation.table == null) {
                continue;
            }
            if (table != null && !sameTable(table, relation.table)) {
                return null;
            }
            table = relation.table;
        }
        return table;
    }

    private static boolean sameTable(TableRef left, TableRef right) {
        return java.util.Objects.equals(left.getCatalog(), right.getCatalog())
                && java.util.Objects.equals(left.getSchema(), right.getSchema())
                && java.util.Objects.equals(left.getName(), right.getName());
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
        return caseInsensitiveColumnRefs(columns, sourceColumn.name);
    }

    private static List<ColumnRef> caseInsensitiveColumnRefs(Map<String, List<ColumnRef>> columns, String columnName) {
        List<ColumnRef> refs = columns.get(columnName);
        if (refs != null) {
            return refs;
        }
        for (Map.Entry<String, List<ColumnRef>> entry : columns.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isCteReference(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && cteNames.contains(table.getName().toLowerCase(Locale.ROOT));
    }

    private List<ColumnLineage> readGeneratedColumnLineage(StarRocksParser.TableElementListContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx == null) {
            return lineages;
        }
        for (StarRocksParser.TableElementContext element : ctx.tableElement()) {
            if (element.generatedColumn() == null) {
                continue;
            }
            String columnName = cleanIdentifier(element.identifier());
            List<ColumnRef> sources = generatedColumnSources(element.generatedColumn().expression(), target);
            if (!sources.isEmpty()) {
                lineages.add(LineageModelUtils.columnLineage(target, columnName, sources, null));
            }
        }
        return lineages;
    }

    private List<ColumnRef> generatedColumnSources(StarRocksParser.ExpressionContext expression, TableRef target) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn source : sourceColumns(expression)) {
            if (source.resolvedRef != null) {
                refs.add(source.resolvedRef);
            } else {
                refs.add(new ColumnRef(target, source.name));
            }
        }
        return refs;
    }

    private List<ColumnLineage> readAssignments(StarRocksParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.AssignmentContext assignment : ctx.assignment()) {
            ColumnLineage lineage = readAssignment(assignment, defaultTarget);
            if (lineage != null) {
                lineages.add(lineage);
            }
        }
        return lineages;
    }

    private ColumnLineage readAssignment(StarRocksParser.AssignmentContext assignment, TableRef defaultTarget) {
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
            return null;
        }
        ColumnLineage lineage = new ColumnLineage();
        lineage.setTarget(new ColumnRef(table, columnName));
        lineage.setSources(sources);
        return lineage;
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
        if (tree instanceof StarRocksParser.ScalarSubqueryContext) {
            StarRocksParser.ScalarSubqueryContext subquery = (StarRocksParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        if (tree instanceof StarRocksParser.ExistsExprContext) {
            StarRocksParser.ExistsExprContext exists = (StarRocksParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        if (tree instanceof StarRocksParser.PredicateContext) {
            StarRocksParser.PredicateContext predicate = (StarRocksParser.PredicateContext) tree;
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

    private void collectWindowUsages(ParseTree tree) {
        if (tree instanceof StarRocksParser.WindowSpecContext) {
            visitWindowSpec((StarRocksParser.WindowSpecContext) tree);
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectWindowUsages(tree.getChild(i));
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree instanceof StarRocksParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof StarRocksParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof StarRocksParser.PredicateContext) {
            StarRocksParser.PredicateContext predicate = (StarRocksParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(StarRocksParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        StarRocksLineageVisitor queryVisitor = new StarRocksLineageVisitor(queryResult);
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

    private void collectTopLevelQueryProjections(StarRocksParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        StarRocksParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        for (StarRocksParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof StarRocksParser.SelectExpressionContext) {
                Projection projection = projection((StarRocksParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
    }

    private static StarRocksParser.QuerySpecificationContext topLevelQuerySpecification(StarRocksParser.QueryContext query) {
        if (!(query.queryTerm() instanceof StarRocksParser.QueryTermDefaultContext)) {
            return null;
        }
        StarRocksParser.QueryPrimaryContext primary =
                ((StarRocksParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof StarRocksParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((StarRocksParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(StarRocksParser.SelectExpressionContext ctx) {
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
        String normalizedExpression = normalizedIdentifierExpression(expression);
        String normalizedRaw = normalizedIdentifierExpression(raw);
        String normalizedName = normalizedIdentifierExpression(column.name);
        return normalizedExpression.equals(normalizedRaw) || normalizedExpression.endsWith("." + normalizedName);
    }

    private static String normalizedIdentifierExpression(String expression) {
        return String.join(".", splitIdentifier(expression));
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

    private static String unqualifiedName(String raw) {
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    private List<SourceColumn> sourceColumns(ParseTree tree) {
        Set<SourceColumn> columns = new LinkedHashSet<>();
        collectSourceColumns(tree, columns);
        return new ArrayList<>(columns);
    }

    private List<SourceColumn> pivotColumnSourceColumns(StarRocksParser.PivotColumnContext ctx) {
        List<SourceColumn> columns = new ArrayList<>();
        if (ctx == null) {
            return columns;
        }
        if (ctx.identifierList() != null) {
            for (StarRocksParser.IdentifierContext identifier : ctx.identifierList().identifier()) {
                columns.add(new SourceColumn(null, cleanIdentifier(identifier)));
            }
        } else if (ctx.identifier() != null) {
            columns.add(new SourceColumn(null, cleanIdentifier(ctx.identifier())));
        }
        return columns;
    }

    private void registerPivotColumnLineage(StarRocksParser.PivotClauseContext ctx) {
        List<String> valueNames = new ArrayList<>();
        for (StarRocksParser.PivotValueContext value : ctx.pivotValueList().pivotValue()) {
            valueNames.add(pivotValueName(value));
        }
        for (StarRocksParser.PivotAggregateContext aggregate : ctx.pivotAggregate()) {
            List<SourceColumn> sourceColumns = aggregate.expressionList() == null
                    ? Collections.<SourceColumn>emptyList()
                    : sourceColumns(aggregate.expressionList());
            List<ColumnRef> refs = columnRefs(sourceColumns, new LinkedHashSet<String>());
            if (refs == null || refs.isEmpty()) {
                continue;
            }
            String aggregateName = aggregate.identifier() == null
                    ? cleanIdentifier(aggregate.functionName().getText())
                    : cleanIdentifier(aggregate.identifier());
            for (String valueName : valueNames) {
                if (!valueName.isEmpty()) {
                    pivotColumnLineage.put((aggregateName + "_" + valueName).toLowerCase(Locale.ROOT), refs);
                }
            }
        }
    }

    private static String pivotValueName(StarRocksParser.PivotValueContext ctx) {
        if (ctx.expression() != null) {
            return cleanPivotGeneratedColumnPart(ctx.expression().getText());
        }
        if (ctx.expressionList() == null || ctx.expressionList().expression().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (StarRocksParser.ExpressionContext expression : ctx.expressionList().expression()) {
            String part = cleanPivotGeneratedColumnPart(expression.getText());
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return String.join("_", parts);
    }

    private static String cleanPivotGeneratedColumnPart(String text) {
        String literal = stringLiteralValue(text);
        String value = literal == null ? text : literal;
        return cleanIdentifier(value).replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private void addScalarSubquerySourceColumns(StarRocksParser.QueryContext query, Set<SourceColumn> columns) {
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(StarRocksParser.QueryContext query) {
        StarRocksLineageVisitor queryVisitor = new StarRocksLineageVisitor(new LineageResult());
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        StarRocksParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (StarRocksParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof StarRocksParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((StarRocksParser.SelectExpressionContext) item).expression()),
                        new LinkedHashSet<>());
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof StarRocksParser.TypedStringLiteralContext) {
            return;
        }
        if (tree instanceof StarRocksParser.DefaultLiteralContext) {
            return;
        }
        if (tree instanceof StarRocksParser.LambdaExpressionContext) {
            StarRocksParser.LambdaExpressionContext lambda = (StarRocksParser.LambdaExpressionContext) tree;
            Set<String> parameters = new LinkedHashSet<>();
            parameters.add(cleanIdentifier(lambda.identifier()).toLowerCase(Locale.ROOT));
            collectLambdaBody(lambda.expression(), parameters, columns);
            return;
        }
        if (tree instanceof StarRocksParser.LambdaExpressionListContext) {
            StarRocksParser.LambdaExpressionListContext lambda = (StarRocksParser.LambdaExpressionListContext) tree;
            Set<String> parameters = new LinkedHashSet<>();
            for (StarRocksParser.IdentifierContext identifier : lambda.identifierList().identifier()) {
                parameters.add(cleanIdentifier(identifier).toLowerCase(Locale.ROOT));
            }
            collectLambdaBody(lambda.expression(), parameters, columns);
            return;
        }
        if (tree instanceof StarRocksParser.FunctionCallContext) {
            StarRocksParser.FunctionCallContext function = (StarRocksParser.FunctionCallContext) tree;
            if (isFunction(function, "dict_mapping")) {
                collectDictMappingSourceColumns(function, columns);
                return;
            }
        }
        if (tree instanceof StarRocksParser.ColumnReferenceContext) {
            StarRocksParser.ColumnReferenceContext colRef = (StarRocksParser.ColumnReferenceContext) tree;
            if (colRef.identifier().getStart().getType() == StarRocksParser.DEFAULT) {
                return;
            }
            String columnName = cleanIdentifier(colRef.identifier());
            if (!isLambdaParameter(columnName)) {
                columns.add(new SourceColumn(null, columnName));
            }
            return;
        }
        if (tree instanceof StarRocksParser.DereferenceContext) {
            StarRocksParser.DereferenceContext deref = (StarRocksParser.DereferenceContext) tree;
            List<String> parts = collectDereferenceParts(deref);
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2);
                String name = parts.get(parts.size() - 1);
                columns.add(new SourceColumn(qualifier, name));
            } else if (parts.size() == 1) {
                collectSourceColumns(deref.primaryExpression(), columns);
            }
            return;
        }
        if (tree instanceof StarRocksParser.ScalarSubqueryContext) {
            StarRocksParser.ScalarSubqueryContext subquery = (StarRocksParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof StarRocksParser.ExistsExprContext) {
            StarRocksParser.ExistsExprContext exists = (StarRocksParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof StarRocksParser.PredicateContext) {
            StarRocksParser.PredicateContext predicate = (StarRocksParser.PredicateContext) tree;
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

    private void collectDictMappingSourceColumns(StarRocksParser.FunctionCallContext ctx, Set<SourceColumn> columns) {
        List<StarRocksParser.ExpressionContext> args = ctx.expressionList().expression();
        if (args.isEmpty()) {
            return;
        }
        TableRef dictionaryTable = tableRefFromLiteral(args.get(0).getText());
        if (dictionaryTable != null) {
            inputTables.add(dictionaryTable);
            result.setInputTables(new ArrayList<>(inputTables));
        }
        for (int i = 1; i < args.size(); i++) {
            StarRocksParser.ExpressionContext arg = args.get(i);
            String text = arg.getText();
            String literal = stringLiteralValue(text);
            if (dictionaryTable != null && i > 1 && literal != null && !literal.isEmpty()) {
                columns.add(SourceColumn.resolved(new ColumnRef(dictionaryTable, literal)));
            } else if (isScalarLiteralArgument(text)) {
                continue;
            } else {
                collectSourceColumns(arg, columns);
            }
        }
    }

    private static boolean isFunction(StarRocksParser.FunctionCallContext ctx, String name) {
        return ctx.functionName().getText().equalsIgnoreCase(name);
    }

    private static boolean isScalarLiteralArgument(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("null")) {
            return true;
        }
        return value.matches("[+-]?\\d+(\\.\\d+)?");
    }

    private void collectLambdaBody(ParseTree body, Set<String> parameters, Set<SourceColumn> columns) {
        lambdaParameterScopes.push(parameters);
        try {
            collectSourceColumns(body, columns);
        } finally {
            lambdaParameterScopes.pop();
        }
    }

    private boolean isLambdaParameter(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        for (Set<String> scope : lambdaParameterScopes) {
            if (scope.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private List<String> collectDereferenceParts(StarRocksParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof StarRocksParser.DereferenceContext) {
            StarRocksParser.DereferenceContext deref = (StarRocksParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof StarRocksParser.ColumnReferenceContext) {
            StarRocksParser.ColumnReferenceContext colRef = (StarRocksParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private static TableRef tableRef(StarRocksParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(StarRocksParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (StarRocksParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static TableRef tableRefFromLiteral(String text) {
        String literal = stringLiteralValue(text);
        if (literal == null || literal.trim().isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : literal.split("\\.")) {
            if (!part.trim().isEmpty()) {
                parts.add(cleanIdentifier(part.trim()));
            }
        }
        return parts.isEmpty() ? null : LineageModelUtils.tableRefFromParts(parts);
    }

    private static String stringLiteralValue(String text) {
        String value = text.trim();
        if (value.length() < 2) {
            return null;
        }
        char quote = value.charAt(0);
        if ((quote != '\'' && quote != '"') || value.charAt(value.length() - 1) != quote) {
            return null;
        }
        String body = value.substring(1, value.length() - 1);
        String doubled = String.valueOf(quote) + quote;
        return body.replace(doubled, String.valueOf(quote));
    }

    private static String tableAlias(StarRocksParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> tableAliasColumns(StarRocksParser.TableAliasContext ctx) {
        if (ctx == null || ctx.identifierList() == null) {
            return Collections.emptyList();
        }
        List<String> aliases = new ArrayList<>();
        for (StarRocksParser.IdentifierContext id : ctx.identifierList().identifier()) {
            aliases.add(cleanIdentifier(id));
        }
        return aliases;
    }

    private static List<String> cteColumnAliases(StarRocksParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (StarRocksParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(StarRocksParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (StarRocksParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static List<String> ctasElementNames(StarRocksParser.CtasElementListContext ctx) {
        List<String> names = new ArrayList<>();
        for (StarRocksParser.CtasElementContext element : ctx.ctasElement()) {
            if (element.identifier() != null) {
                names.add(cleanIdentifier(element.identifier()));
            }
        }
        return names;
    }

    private static String cleanIdentifier(StarRocksParser.IdentifierContext ctx) {
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
            return java.util.Objects.equals(resolvedRef, that.resolvedRef)
                    && java.util.Objects.equals(qualifier, that.qualifier)
                    && java.util.Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(resolvedRef, qualifier, name);
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
