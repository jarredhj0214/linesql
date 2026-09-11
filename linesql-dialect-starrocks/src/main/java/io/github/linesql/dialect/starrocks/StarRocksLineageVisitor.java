package io.github.linesql.dialect.starrocks;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
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
    private int externalLoadCount;
    private boolean suppressColumnLineage;
    private ParseContext context;

    StarRocksLineageVisitor(LineageResult result) {
        this.result = result;
    }

    void setContext(ParseContext context) {
        this.context = context;
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
        } else if (ctx.cacheSelectStatement() != null) {
            result.setStatementType(StatementType.SELECT);
            visit(ctx.cacheSelectStatement().query());
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
        TableRef source = tableRef(ctx.source);
        inputTables.add(source);
        if (ctx.identifierList() != null) {
            result.setColumnLineage(exportColumnLineage(source, identifierNames(ctx.identifierList())));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private List<ColumnLineage> exportColumnLineage(TableRef source, List<String> columns) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (String column : columns) {
            lineages.add(LineageModelUtils.columnLineage(
                    null,
                    column,
                    Collections.singletonList(new ColumnRef(source, column)),
                    null));
        }
        return lineages;
    }

    @Override
    public Void visitCreateRoutineLoadStmt(StarRocksParser.CreateRoutineLoadStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateRoutineLoadStatement(StarRocksParser.CreateRoutineLoadStatementContext ctx) {
        TableRef target = routineLoadTargetTable(ctx.job, ctx.target);
        currentDmlTarget = target;
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        String sourcePrefix = "$routine_load" + (++externalLoadCount);
        for (StarRocksParser.RoutineLoadClauseContext clause : ctx.routineLoadClause()) {
            if (clause.loadColumnList() != null) {
                lineages.addAll(readLoadDataColumns(clause.loadColumnList(), target, sourcePrefix));
            }
            if (clause.whereClause() != null) {
                addExternalColumnUsages(ColumnUsageType.WHERE,
                        sourceColumns(clause.whereClause().expression()),
                        sourcePrefix);
            }
        }
        List<ColumnLineage> allLineages = new ArrayList<>(result.getColumnLineage());
        allLineages.addAll(lineages);
        result.setColumnLineage(allLineages);
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
        return visitChildren(ctx);
    }

    @Override
    public Void visitCancelExportStmt(StarRocksParser.CancelExportStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCancelLoadStatement(StarRocksParser.CancelLoadStatementContext ctx) {
        LineageModelUtils.addColumnUsages(result,
                ColumnUsageType.READ_METADATA,
                metadataColumnRefs(null, sourceColumns(ctx.expression())));
        return null;
    }

    @Override
    public Void visitCancelExportStatement(StarRocksParser.CancelExportStatementContext ctx) {
        LineageModelUtils.addColumnUsages(result,
                ColumnUsageType.READ_METADATA,
                metadataColumnRefs(null, sourceColumns(ctx.expression())));
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
        String sourcePrefix = "$pipe" + (++externalLoadCount);
        result.setColumnLineage(externalLoadColumnLineage(
                result.getColumnLineage(),
                target,
                insertColumns(ctx.insertStatement()),
                sourcePrefix));
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
            List<String> targetColumns,
            String sourcePrefix) {
        List<ColumnLineage> sanitized = new ArrayList<>();
        for (ColumnLineage lineage : lineages) {
            if (lineage.getTarget() == null) {
                continue;
            }
            List<ColumnRef> sources = new ArrayList<>();
            for (ColumnRef source : lineage.getSources()) {
                sources.add(new ColumnRef(null, sourcePrefix + "." + simpleExternalColumnName(source)));
            }
            sanitized.add(LineageModelUtils.columnLineage(
                    lineage.getTarget().getTable(),
                    lineage.getTarget().getName(),
                    sources,
                    lineage.getExpression()));
        }
        if (sanitized.isEmpty() && target != null) {
            for (String columnName : targetColumns) {
                List<ColumnRef> sources = Collections.singletonList(new ColumnRef(null, sourcePrefix + "." + columnName));
                sanitized.add(LineageModelUtils.columnLineage(
                        target,
                        columnName,
                        sources,
                        null));
            }
        }
        return sanitized;
    }

    private static String simpleExternalColumnName(ColumnRef source) {
        if (source == null || source.getName() == null) {
            return "*";
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1) : name;
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
            TableRef table = tableRef(ctx.multipartIdentifier());
            inputTables.add(table);
            result.setInputTables(new ArrayList<>(inputTables));
            addMetadataColumnUsages(table, ctx.identifierList());
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
        return visitChildren(ctx);
    }

    @Override
    public Void visitAccountControlStatement(StarRocksParser.AccountControlStatementContext ctx) {
        if (ctx.multipartIdentifier() != null && (ctx.GRANT() != null || ctx.REVOKE() != null)) {
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitBackupStmt(StarRocksParser.BackupStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitBackupStatement(StarRocksParser.BackupStatementContext ctx) {
        collectBackupObjects(ctx.backupOnClause(), true, backupDatabase(ctx), false);
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitRecoverStmt(StarRocksParser.RecoverStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitRecoverStatement(StarRocksParser.RecoverStatementContext ctx) {
        if (ctx.DATABASE() != null) {
            result.setStatementType(StatementType.CREATE_SCHEMA);
            return null;
        }
        result.setStatementType(StatementType.ALTER_TABLE);
        if (ctx.multipartIdentifier() != null) {
            outputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitRestoreStmt(StarRocksParser.RestoreStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRestoreStatement(StarRocksParser.RestoreStatementContext ctx) {
        collectBackupObjects(ctx.backupOnClause(), false, restoreTargetDatabase(ctx), true);
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
        collectBackupObjects(ctx, input, null, false);
    }

    private void collectBackupObjects(StarRocksParser.BackupOnClauseContext ctx,
                                      boolean input,
                                      String schemaOverride,
                                      boolean useObjectAlias) {
        if (ctx == null) {
            return;
        }
        for (StarRocksParser.BackupObjectContext object : ctx.backupObject()) {
            if (object.multipartIdentifier() == null || object.FUNCTION() != null || object.FUNCTIONS() != null) {
                continue;
            }
            TableRef table = backupObjectTable(object, schemaOverride, useObjectAlias);
            if (input) {
                inputTables.add(table);
            } else {
                outputTables.add(table);
            }
        }
    }

    private TableRef backupObjectTable(StarRocksParser.BackupObjectContext object,
                                       String schemaOverride,
                                       boolean useObjectAlias) {
        TableRef table = tableRef(object.multipartIdentifier());
        List<String> parts = identifierParts(object.multipartIdentifier());
        String alias = backupObjectAlias(object);
        String name = useObjectAlias && alias != null ? alias : table.getName();
        if (schemaOverride != null && !schemaOverride.isEmpty()) {
            String catalog = parts.size() >= 3 ? parts.get(parts.size() - 3)
                    : (context == null ? null : context.getDefaultCatalog());
            return new TableRef(catalog, schemaOverride, name);
        }
        if (useObjectAlias && alias != null) {
            return new TableRef(table.getCatalog(), table.getSchema(), alias);
        }
        return table;
    }

    private static String backupObjectAlias(StarRocksParser.BackupObjectContext object) {
        if (object.backupObjectAlias() == null) {
            return null;
        }
        return cleanIdentifier(object.backupObjectAlias().identifier());
    }

    private static String restoreTargetDatabase(StarRocksParser.RestoreStatementContext ctx) {
        if (ctx.restoreDatabaseClause() == null) {
            if (ctx.restoreLegacyDatabaseClause() != null) {
                return cleanIdentifier(ctx.restoreLegacyDatabaseClause().identifier());
            }
            return snapshotDatabase(ctx.snapshot);
        }
        StarRocksParser.IdentifierContext target = ctx.restoreDatabaseClause().target;
        if (target != null) {
            return cleanIdentifier(target);
        }
        return cleanIdentifier(ctx.restoreDatabaseClause().source);
    }

    private static String backupDatabase(StarRocksParser.BackupStatementContext ctx) {
        if (ctx.database != null) {
            return cleanIdentifier(ctx.database);
        }
        return snapshotDatabase(ctx.snapshot);
    }

    private static String snapshotDatabase(StarRocksParser.MultipartIdentifierContext snapshot) {
        List<String> parts = identifierParts(snapshot);
        if (parts.size() < 2) {
            return null;
        }
        return parts.get(parts.size() - 2);
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
    public Void visitWarehouseStmt(StarRocksParser.WarehouseStmtContext ctx) {
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
        List<ColumnLineage> allLineages = new ArrayList<>(result.getColumnLineage());
        allLineages.addAll(lineages);
        result.setColumnLineage(allLineages);
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
    public Void visitSqlBlacklistStmt(StarRocksParser.SqlBlacklistStmtContext ctx) {
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
        TableRef source = ctx.source == null ? null : tableRef(ctx.source);
        currentDmlTarget = target;
        if (source != null) {
            inputTables.add(source);
        }
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        String sourcePrefix = "$load" + (++externalLoadCount);
        for (StarRocksParser.LoadDataOptionContext option : ctx.loadDataOption()) {
            if (isLoadColumnOption(option)) {
                if (source == null) {
                    lineages.addAll(readLoadDataColumns(option.loadColumnList(), target, sourcePrefix));
                } else {
                    lineages.addAll(readLoadTableColumns(option.loadColumnList(), target, source));
                }
            }
            if (source == null && option.FROM() != null && option.identifierList() != null) {
                lineages.addAll(readLoadDataColumns(option.identifierList(), target, sourcePrefix));
            }
            if (option.whereClause() != null) {
                if (source == null) {
                    addExternalColumnUsages(ColumnUsageType.WHERE,
                            sourceColumns(option.whereClause().expression()),
                            sourcePrefix);
                } else {
                    addLoadTableColumnUsages(ColumnUsageType.WHERE,
                            sourceColumns(option.whereClause().expression()),
                            source);
                }
            }
            if (option.assignmentList() != null) {
                collectSubqueryInputs(option.assignmentList());
                if (source == null) {
                    lineages.addAll(readExternalAssignments(option.assignmentList(), target, sourcePrefix));
                } else {
                    lineages.addAll(readLoadTableAssignments(option.assignmentList(), target, source));
                }
            }
        }
        List<ColumnLineage> allLineages = new ArrayList<>(result.getColumnLineage());
        allLineages.addAll(lineages);
        result.setColumnLineage(allLineages);
        result.setInputTables(new ArrayList<>(inputTables));
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

    private List<ColumnLineage> readLoadDataColumns(StarRocksParser.LoadColumnListContext ctx,
                                                    TableRef target,
                                                    String sourcePrefix) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.LoadColumnItemContext item : ctx.loadColumnItem()) {
            if (item.identifier() != null) {
                String columnName = cleanIdentifier(item.identifier());
                lineages.add(LineageModelUtils.columnLineage(
                        target,
                        columnName,
                        Collections.singletonList(new ColumnRef(null, sourcePrefix + "." + columnName)),
                        null));
            } else if (item.assignment() != null) {
                ColumnLineage lineage = readExternalAssignment(item.assignment(), target, sourcePrefix);
                if (lineage != null) {
                    lineages.add(lineage);
                }
            }
        }
        return lineages;
    }

    private List<ColumnLineage> readLoadTableColumns(StarRocksParser.LoadColumnListContext ctx,
                                                     TableRef target,
                                                     TableRef source) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.LoadColumnItemContext item : ctx.loadColumnItem()) {
            if (item.identifier() != null) {
                String columnName = cleanIdentifier(item.identifier());
                lineages.add(LineageModelUtils.columnLineage(
                        target,
                        columnName,
                        Collections.singletonList(new ColumnRef(source, columnName)),
                        null));
            } else if (item.assignment() != null) {
                ColumnLineage lineage = readLoadTableAssignment(item.assignment(), target, source);
                if (lineage != null) {
                    lineages.add(lineage);
                }
            }
        }
        return lineages;
    }

    private List<ColumnLineage> readLoadDataColumns(StarRocksParser.IdentifierListContext ctx,
                                                    TableRef target,
                                                    String sourcePrefix) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            lineages.add(LineageModelUtils.columnLineage(
                    target,
                    columnName,
                    Collections.singletonList(new ColumnRef(null, sourcePrefix + "." + columnName)),
                    null));
        }
        return lineages;
    }

    private List<ColumnLineage> readExternalAssignments(StarRocksParser.AssignmentListContext ctx,
                                                        TableRef target,
                                                        String sourcePrefix) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.AssignmentContext assignment : ctx.assignment()) {
            ColumnLineage lineage = readExternalAssignment(assignment, target, sourcePrefix);
            if (lineage != null) {
                lineages.add(lineage);
            }
        }
        return lineages;
    }

    private List<ColumnLineage> readLoadTableAssignments(StarRocksParser.AssignmentListContext ctx,
                                                         TableRef target,
                                                         TableRef source) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.AssignmentContext assignment : ctx.assignment()) {
            ColumnLineage lineage = readLoadTableAssignment(assignment, target, source);
            if (lineage != null) {
                lineages.add(lineage);
            }
        }
        return lineages;
    }

    private ColumnLineage readExternalAssignment(StarRocksParser.AssignmentContext assignment,
                                                 TableRef target,
                                                 String sourcePrefix) {
        List<String> parts = identifierParts(assignment.multipartIdentifier());
        String columnName = parts.get(parts.size() - 1);
        ColumnLineage lineage = new ColumnLineage();
        lineage.setTarget(new ColumnRef(target, columnName));
        lineage.setSources(externalColumnRefs(sourceColumns(assignment.expression()), sourcePrefix));
        lineage.setExpression(assignment.expression().getText());
        return lineage;
    }

    private ColumnLineage readLoadTableAssignment(StarRocksParser.AssignmentContext assignment,
                                                  TableRef target,
                                                  TableRef source) {
        List<String> parts = identifierParts(assignment.multipartIdentifier());
        String columnName = parts.get(parts.size() - 1);
        List<ColumnRef> sources = loadTableColumnRefs(sourceColumns(assignment.expression()), source);
        ColumnLineage lineage = new ColumnLineage();
        lineage.setTarget(new ColumnRef(target, columnName));
        lineage.setSources(sources);
        lineage.setExpression(assignment.expression().getText());
        return lineage;
    }

    private void addLoadTableColumnUsages(ColumnUsageType type,
                                          List<SourceColumn> sourceColumns,
                                          TableRef source) {
        LineageModelUtils.addColumnUsages(result, type, loadTableColumnRefs(sourceColumns, source));
    }

    private List<ColumnRef> loadTableColumnRefs(List<SourceColumn> sourceColumns, TableRef source) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            if (sourceColumn.resolvedRef != null) {
                refs.add(sourceColumn.resolvedRef);
            } else if (sourceColumn.qualifier == null) {
                refs.add(new ColumnRef(source, sourceColumn.name));
            } else if (sourceColumn.qualifier.equalsIgnoreCase(source.getName())) {
                refs.add(new ColumnRef(source, sourceColumn.name));
            } else {
                refs.add(new ColumnRef(source, sourceColumn.qualifier + "." + sourceColumn.name));
            }
        }
        return refs;
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
    public Void visitMergeStmt(StarRocksParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStatement(StarRocksParser.MergeStatementContext ctx) {
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
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumns(ctx.tableAlias(1)));
            addDerivedReference(relationName, ctx.tableAlias(1));
        }

        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.expression()));

        List<ColumnLineage> lineages = new ArrayList<>();
        for (StarRocksParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.mergeCondition() != null) {
                addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(clause.mergeCondition().expression()));
                collectSubqueryInputs(clause.mergeCondition().expression());
            }
            if (clause.mergeMatchedAction() != null && clause.mergeMatchedAction().assignmentList() != null) {
                collectSubqueryInputs(clause.mergeMatchedAction().assignmentList());
                lineages.addAll(readAssignments(clause.mergeMatchedAction().assignmentList(), target));
            }
            if (clause.mergeNotMatchedAction() != null && clause.mergeNotMatchedAction().expressionList() != null) {
                collectSubqueryInputs(clause.mergeNotMatchedAction().expressionList());
                lineages.addAll(readMergeInsertValues(clause.mergeNotMatchedAction(), target));
            }
        }
        result.setColumnLineage(lineages);
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
        TableRef table = tableRef(ctx.multipartIdentifier());
        outputTables.add(table);
        result.setOutputTables(new ArrayList<>(outputTables));
        addIndexColumnUsages(table, ctx.identifierList());
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
        addTableModelColumnUsages(target, ctx.keyDesc(), ctx.distributionDesc(), ctx.orderByDesc());
        addPartitionColumnUsages(target, ctx.partitionDesc());
        addRollupColumnUsages(target, ctx.rollupClause());
        if (ctx.propertiesClause() != null) {
            addBloomFilterColumnUsages(target, ctx.propertiesClause().propertyList());
        }
        if (ctx.ctasElements != null) {
            insertTargetColumns.addAll(ctasElementNames(ctx.ctasElements));
        }
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
            registerTemporaryRelation(target);
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
            result.setColumnLineage(readGeneratedColumnLineage(ctx.tableElementList(), target));
            addTableElementIndexColumnUsages(target, ctx.tableElementList());
            registerTableSchema(target, ctx.tableElementList());
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
        registerTemporaryRelation(target);
        if (ctx.MATERIALIZED() != null) {
            addMaterializedViewModelColumnUsages(target, ctx.materializedViewOption());
        }
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
        if (ctx.alterMaterializedViewAction() != null
                && ctx.alterMaterializedViewAction().SET() != null) {
            addBloomFilterColumnUsages(view, firstPropertyList(ctx.alterMaterializedViewAction()));
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
    public Void visitAlterTableOther(StarRocksParser.AlterTableOtherContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        outputTables.add(table);
        result.setOutputTables(new ArrayList<>(outputTables));
        for (StarRocksParser.AlterTableActionContext action : ctx.alterTableAction()) {
            collectAlterTableActionLineage(table, action);
        }
        return null;
    }

    private void collectAlterTableActionLineage(TableRef table, StarRocksParser.AlterTableActionContext action) {
        if (action == null) {
            return;
        }
        if (action.ADD() != null
                && action.INDEX() != null
                && !action.identifierList().isEmpty()) {
            addIndexColumnUsages(table, action.identifierList().get(0));
        }
        if (action.ADD() != null
                && action.ROLLUP() != null) {
            addRollupColumnUsages(table, action.rollupDefinition());
        }
        if (action.ADD() != null
                && action.generatedColumn() != null
                && !action.identifier().isEmpty()) {
            appendColumnLineage(readGeneratedColumnLineage(
                    table,
                    action.identifier().get(0),
                    action.generatedColumn()));
        }
        if (action.tableElementList() != null) {
            appendColumnLineage(readGeneratedColumnLineage(action.tableElementList(), table));
            addTableElementIndexColumnUsages(table, action.tableElementList());
        }
        if (action.MODIFY() != null
                && action.generatedColumn() != null
                && !action.identifier().isEmpty()) {
            appendColumnLineage(readGeneratedColumnLineage(
                    table,
                    action.identifier().get(0),
                    action.generatedColumn()));
        }
        if (action.ORDER() != null
                && !action.identifierList().isEmpty()) {
            addColumnUsages(table, action.identifierList().get(0), ColumnUsageType.TABLE_MODEL);
        }
        if (action.DISTRIBUTED() != null
                && action.HASH() != null
                && !action.identifierList().isEmpty()) {
            List<StarRocksParser.IdentifierListContext> lists = action.identifierList();
            addColumnUsages(table, lists.get(lists.size() - 1), ColumnUsageType.TABLE_MODEL);
        }
        if (action.distributionDesc() != null) {
            addDistributionColumnUsages(table, action.distributionDesc());
        }
        if (action.DROP() != null
                && action.PARTITIONS() != null
                && action.WHERE() != null
                && !action.expression().isEmpty()) {
            List<ColumnRef> refs = tableModelRefs(
                    table,
                    sourceColumns(action.expression().get(action.expression().size() - 1)));
            LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
        }
        if (action.SET() != null) {
            addBloomFilterColumnUsages(table, firstPropertyList(action));
        }
    }

    private void appendColumnLineage(List<ColumnLineage> lineages) {
        if (lineages == null || lineages.isEmpty()) {
            return;
        }
        List<ColumnLineage> allLineages = new ArrayList<>(result.getColumnLineage());
        allLineages.addAll(lineages);
        result.setColumnLineage(allLineages);
    }

    @Override
    public Void visitAnalyzeTableStmt(StarRocksParser.AnalyzeTableStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeTableStatement(StarRocksParser.AnalyzeTableStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        inputTables.add(table);
        result.setInputTables(new ArrayList<>(inputTables));
        addMetadataColumnUsages(table, ctx.identifierList());
        if (ctx.analyzeHistogramClause() != null) {
            addMetadataColumnUsages(table, ctx.analyzeHistogramClause().identifierList());
        }
        return null;
    }

    @Override
    public Void visitShowStmt(StarRocksParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(StarRocksParser.ShowStatementContext ctx) {
        TableRef metadataTable = null;
        if (ctx.PROC() != null && !ctx.string().isEmpty()) {
            metadataTable = showProcMetadataRef(ctx.string(0).getText());
            if (metadataTable != null) {
                inputTables.add(metadataTable);
                result.setInputTables(new ArrayList<>(inputTables));
            }
        } else if (ctx.multipartIdentifier() != null) {
            metadataTable = showMetadataTable(ctx);
            inputTables.add(metadataTable);
            result.setInputTables(new ArrayList<>(inputTables));
        }
        addShowMetadataColumnUsages(metadataTable, ctx);
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
        String temporaryRelationName = temporaryRelationName(table);
        if (temporaryRelationName != null) {
            addTemporaryRelationReference(temporaryRelationName, ctx.tableAlias());
            return null;
        }
        if (isCteReference(table)) {
            addDerivedReference(table.getName(), ctx.tableAlias());
            return null;
        }
        addInputTable(table, ctx.tableAlias(), true);
        return null;
    }

    @Override
    public Void visitFilesTableFunction(StarRocksParser.FilesTableFunctionContext ctx) {
        String filesName = "$files" + (visibleRelationCount + 1);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (String aliasColumn : tableAliasColumns(ctx.tableAlias())) {
            columns.put(aliasColumn.toLowerCase(Locale.ROOT),
                    Collections.singletonList(new ColumnRef(null, filesName + "." + aliasColumn)));
        }
        if (!columns.isEmpty()) {
            String alias = tableAlias(ctx.tableAlias());
            String derivedName = alias == null ? filesName : alias.toLowerCase(Locale.ROOT);
            derivedColumnLineage.put(derivedName, columns);
            addDerivedReference(derivedName, ctx.tableAlias());
            return null;
        }
        visibleRelationCount++;
        visibleRelations.add(VisibleRelation.derived(filesName));
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
            addOpaqueTableFunction(ctx.identifier(), ctx.tableAlias());
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
        String functionRelationName = "$" + functionName + (visibleRelationCount + 1);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (String aliasColumn : tableAliasColumns(aliasCtx)) {
            columns.put(aliasColumn.toLowerCase(Locale.ROOT),
                    Collections.singletonList(new ColumnRef(null, functionRelationName + "." + aliasColumn)));
        }
        derivedColumnLineage.put(derivedName, columns);
        addDerivedReference(derivedName, aliasCtx);
    }

    @Override
    public Void visitAliasedQuery(StarRocksParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumns(ctx.tableAlias()));
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitLateralQuery(StarRocksParser.LateralQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$lateral" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumns(ctx.tableAlias()));
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitAliasedRelation(StarRocksParser.AliasedRelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relation());
        String alias = tableAlias(ctx.tableAlias());
        if (alias == null) {
            return null;
        }
        List<VisibleRelation> addedRelations = visibleRelations.subList(relationStart, visibleRelations.size());
        if (addedRelations.size() != 1) {
            return null;
        }
        VisibleRelation relation = addedRelations.get(0);
        if (relation.table != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), relation.table);
        } else if (relation.derivedName != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), relation.derivedName);
        }
        refreshColumnLineage();
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
        List<StarRocksParser.SelectItemContext> items = ctx.selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            StarRocksParser.SelectItemContext item = items.get(i);
            if (item instanceof StarRocksParser.SelectExpressionContext) {
                Projection projection = projection((StarRocksParser.SelectExpressionContext) item, i);
                if (projection != null) {
                    projections.add(projection);
                }
            } else if (item instanceof StarRocksParser.SelectQualifiedStarContext) {
                StarRocksParser.SelectQualifiedStarContext star = (StarRocksParser.SelectQualifiedStarContext) item;
                projections.add(wildcardProjection(
                        qualifiedName(star.qualifiedName()),
                        star.getText(),
                        excludedColumnNames(star.excludeClause()),
                        i));
            } else if (item instanceof StarRocksParser.SelectStarContext) {
                StarRocksParser.SelectStarContext star = (StarRocksParser.SelectStarContext) item;
                projections.add(wildcardProjection(null, item.getText(), excludedColumnNames(star.excludeClause()), i));
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

    private void addExternalColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns, String sourcePrefix) {
        LineageModelUtils.addColumnUsages(result, type, externalColumnRefs(sourceColumns, sourcePrefix));
    }

    private List<ColumnRef> externalColumnRefs(List<SourceColumn> sourceColumns, String sourcePrefix) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn source : sourceColumns) {
            refs.add(new ColumnRef(null, sourcePrefix + "." + source.name));
        }
        return refs;
    }

    private void addMetadataColumnUsages(TableRef table, StarRocksParser.IdentifierListContext ctx) {
        addColumnUsages(table, ctx, ColumnUsageType.READ_METADATA);
    }

    private void addShowMetadataColumnUsages(TableRef table, StarRocksParser.ShowStatementContext ctx) {
        List<ColumnRef> refs = new ArrayList<>();
        if (ctx.whereClause() != null) {
            refs.addAll(metadataColumnRefs(table, sourceColumns(ctx.whereClause().expression())));
        }
        if (ctx.expression() != null) {
            refs.addAll(metadataColumnRefs(table, sourceColumns(ctx.expression())));
        }
        if (ctx.queryOrganization() != null) {
            for (StarRocksParser.SortItemContext sortItem : ctx.queryOrganization().sortItem()) {
                refs.addAll(metadataColumnRefs(table, sourceColumns(sortItem.expression())));
            }
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.READ_METADATA, refs);
    }

    private static List<ColumnRef> metadataColumnRefs(TableRef table, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn source : sourceColumns) {
            String columnName = source.qualifier == null ? source.name : source.qualifier + "." + source.name;
            refs.add(new ColumnRef(table, columnName));
        }
        return refs;
    }

    private void addIndexColumnUsages(TableRef table, StarRocksParser.IdentifierListContext ctx) {
        addColumnUsages(table, ctx, ColumnUsageType.INDEX);
    }

    private void addBloomFilterColumnUsages(TableRef table, StarRocksParser.PropertyListContext ctx) {
        if (table == null || ctx == null) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (StarRocksParser.PropertyContext property : ctx.property()) {
            String key = cleanPropertyValue(property.getChild(0).getText());
            if (!"bloom_filter_columns".equalsIgnoreCase(key)) {
                continue;
            }
            String value = cleanPropertyValue(property.getChild(2).getText());
            for (String columnName : value.split(",")) {
                String cleaned = cleanIdentifier(columnName.trim());
                if (!cleaned.isEmpty()) {
                    refs.add(new ColumnRef(table, cleaned));
                }
            }
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.INDEX, refs);
    }

    private StarRocksParser.PropertyListContext firstPropertyList(ParseTree tree) {
        if (tree instanceof StarRocksParser.PropertyListContext) {
            return (StarRocksParser.PropertyListContext) tree;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            StarRocksParser.PropertyListContext found = firstPropertyList(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void addTableModelColumnUsages(TableRef table,
                                           StarRocksParser.KeyDescContext keyDesc,
                                           StarRocksParser.DistributionDescContext distributionDesc,
                                           StarRocksParser.OrderByDescContext orderByDesc) {
        if (keyDesc != null) {
            addColumnUsages(table, keyDesc.identifierList(), ColumnUsageType.TABLE_MODEL);
        }
        if (distributionDesc != null && distributionDesc.identifierList() != null) {
            addDistributionColumnUsages(table, distributionDesc);
        }
        if (orderByDesc != null) {
            addColumnUsages(table, orderByDesc.identifierList(), ColumnUsageType.TABLE_MODEL);
        }
    }

    private void addDistributionColumnUsages(TableRef table, StarRocksParser.DistributionDescContext ctx) {
        if (ctx != null && ctx.identifierList() != null) {
            addColumnUsages(table, ctx.identifierList(), ColumnUsageType.TABLE_MODEL);
        }
    }

    private void addPartitionColumnUsages(TableRef table, StarRocksParser.PartitionDescContext ctx) {
        if (table == null || ctx == null) {
            return;
        }
        if (ctx.identifierList() != null) {
            addColumnUsages(table, ctx.identifierList(), ColumnUsageType.TABLE_MODEL);
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        if (ctx.expressionList() != null) {
            for (StarRocksParser.ExpressionContext expression : ctx.expressionList().expression()) {
                refs.addAll(tableModelRefs(table, sourceColumns(expression)));
            }
        } else if (ctx.expression() != null) {
            refs.addAll(tableModelRefs(table, sourceColumns(ctx.expression())));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.TABLE_MODEL, refs);
    }

    private void addColumnUsages(TableRef table, StarRocksParser.IdentifierListContext ctx, ColumnUsageType type) {
        if (table == null || ctx == null) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String columnName : identifierNames(ctx)) {
            refs.add(new ColumnRef(table, columnName));
        }
        LineageModelUtils.addColumnUsages(result, type, refs);
    }

    private static List<ColumnRef> tableModelRefs(TableRef table, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn source : sourceColumns) {
            refs.add(new ColumnRef(table, source.name));
        }
        return refs;
    }

    private void addTableElementIndexColumnUsages(TableRef table, StarRocksParser.TableElementListContext ctx) {
        if (table == null || ctx == null) {
            return;
        }
        for (StarRocksParser.TableElementContext element : ctx.tableElement()) {
            if (element.indexDefinition() != null) {
                addIndexColumnUsages(table, element.indexDefinition().identifierList());
            }
        }
    }

    private void addRollupColumnUsages(TableRef table, StarRocksParser.RollupClauseContext ctx) {
        if (ctx == null) {
            return;
        }
        addRollupColumnUsages(table, ctx.rollupDefinition());
    }

    private void addRollupColumnUsages(TableRef table, List<StarRocksParser.RollupDefinitionContext> definitions) {
        if (table == null || definitions == null) {
            return;
        }
        for (StarRocksParser.RollupDefinitionContext definition : definitions) {
            addColumnUsages(table, definition.identifierList(), ColumnUsageType.TABLE_MODEL);
        }
    }

    private void addMaterializedViewModelColumnUsages(TableRef target,
                                                      List<StarRocksParser.MaterializedViewOptionContext> options) {
        if (target == null || options == null) {
            return;
        }
        for (StarRocksParser.MaterializedViewOptionContext option : options) {
            addPartitionColumnUsages(target, option.partitionDesc());
            addDistributionColumnUsages(target, option.distributionDesc());
            if (option.orderByDesc() != null) {
                addColumnUsages(target, option.orderByDesc().identifierList(), ColumnUsageType.TABLE_MODEL);
            }
            if (option.propertiesClause() != null) {
                addBloomFilterColumnUsages(target, option.propertiesClause().propertyList());
            }
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
        refreshColumnLineage(outputTables.size() == 1 ? outputTables.iterator().next() : null);
    }

    private void refreshColumnLineage(TableRef targetTable) {
        if (suppressColumnLineage || projections.isEmpty()) {
            return;
        }
        List<ColumnLineage> columnLineage = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = projections.get(i);
            if (projection.wildcard && expandWildcardProjectionLineage(targetTable, projection, columnLineage)) {
                continue;
            }
            if (projection.wildcard) {
                addPivotGeneratedColumnLineage(targetTable, projection, columnLineage);
            }
            if (projection.wildcard && !projection.excludedColumns.isEmpty()) {
                continue;
            }
            List<ColumnRef> sources = columnRefs(projection);
            if (sources == null) {
                continue;
            }
            String targetColumn = targetColumn(projection, projection.ordinal);
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
            if (projection.excludedColumns.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
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

    private void addPivotGeneratedColumnLineage(
            TableRef targetTable,
            Projection projection,
            List<ColumnLineage> columnLineage) {
        if (pivotColumnLineage.isEmpty() || projection.sourceColumns.size() != 1) {
            return;
        }
        SourceColumn wildcard = projection.sourceColumns.get(0);
        if (!"*".equals(wildcard.name) || wildcard.qualifier != null) {
            return;
        }
        for (Map.Entry<String, List<ColumnRef>> entry : pivotColumnLineage.entrySet()) {
            if (projection.excludedColumns.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            columnLineage.add(LineageModelUtils.columnLineage(
                    targetTable,
                    entry.getKey(),
                    entry.getValue(),
                    projection.expression));
        }
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
            if (sourceColumn.qualifier == null) {
                List<ColumnRef> pivotRefs = caseInsensitiveColumnRefs(pivotColumnLineage, sourceColumn.name);
                if (pivotRefs != null) {
                    refs.addAll(pivotRefs);
                    continue;
                }
            }
            TableRef table = defaultTable;
            if (sourceColumn.qualifier != null) {
                String qualifierKey = sourceColumn.qualifier.toLowerCase(Locale.ROOT);
                table = tableAliases.get(qualifierKey);
                if (table == null && derivedAliases.containsKey(qualifierKey)) {
                    return null;
                }
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
        List<ColumnRef> refs = caseInsensitiveColumnRefs(columns, sourceColumn.name);
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

    private void registerTableSchema(TableRef table, StarRocksParser.TableElementListContext tableElements) {
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

    private static List<String> declaredColumnNames(StarRocksParser.TableElementListContext ctx) {
        List<String> columns = new ArrayList<>();
        for (StarRocksParser.TableElementContext element : ctx.tableElement()) {
            if (element.identifier() != null) {
                columns.add(cleanIdentifier(element.identifier()));
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

    private void addTemporaryRelationReference(String relationName, StarRocksParser.TableAliasContext aliasCtx) {
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
        List<String> aliasColumns = tableAliasColumns(aliasCtx);
        List<ColumnLineage> lineages = relation.getColumnLineage();
        for (int i = 0; i < lineages.size(); i++) {
            ColumnLineage lineage = lineages.get(i);
            String columnName = i < aliasColumns.size() ? aliasColumns.get(i) : lineage.getTarget().getName();
            columns.put(columnName, lineage.getSources());
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

    private List<ColumnLineage> readGeneratedColumnLineage(TableRef target,
                                                           StarRocksParser.IdentifierContext identifier,
                                                           StarRocksParser.GeneratedColumnContext generatedColumn) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (target == null || identifier == null || generatedColumn == null) {
            return lineages;
        }
        List<ColumnRef> sources = generatedColumnSources(generatedColumn.expression(), target);
        if (!sources.isEmpty()) {
            lineages.add(LineageModelUtils.columnLineage(target, cleanIdentifier(identifier), sources, null));
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

    private List<ColumnLineage> readMergeInsertValues(StarRocksParser.MergeNotMatchedActionContext ctx,
                                                      TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx.identifierList() == null || ctx.expressionList() == null) {
            return lineages;
        }
        List<String> targetColumns = identifierNames(ctx.identifierList());
        List<StarRocksParser.ExpressionContext> expressions = ctx.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            StarRocksParser.ExpressionContext expression = expressions.get(i);
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
        if (tree == null) {
            return;
        }
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
        List<StarRocksParser.SelectItemContext> items = specification.selectClause().selectItemList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            StarRocksParser.SelectItemContext item = items.get(i);
            if (item instanceof StarRocksParser.SelectExpressionContext) {
                Projection projection = projection((StarRocksParser.SelectExpressionContext) item, i);
                if (projection != null) {
                    projections.add(projection);
                }
            } else if (item instanceof StarRocksParser.SelectQualifiedStarContext) {
                StarRocksParser.SelectQualifiedStarContext star = (StarRocksParser.SelectQualifiedStarContext) item;
                projections.add(wildcardProjection(
                        qualifiedName(star.qualifiedName()),
                        star.getText(),
                        excludedColumnNames(star.excludeClause()),
                        i));
            } else if (item instanceof StarRocksParser.SelectStarContext) {
                StarRocksParser.SelectStarContext star = (StarRocksParser.SelectStarContext) item;
                projections.add(wildcardProjection(null, item.getText(), excludedColumnNames(star.excludeClause()), i));
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

    private Projection projection(StarRocksParser.SelectExpressionContext ctx, int ordinal) {
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
        return new Projection(sourceColumns, targetColumn, expression, ordinal);
    }

    private String inferredSingleSourceTarget(
            String directColumn,
            List<SourceColumn> sourceColumns,
            StarRocksParser.ExpressionContext expression) {
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
                || normalized.startsWith("GROUP_CONCAT(")
                || normalized.startsWith("ARRAY_AGG(")
                || normalized.startsWith("BITMAP_UNION(")
                || normalized.startsWith("HLL_UNION(");
    }

    private static Projection wildcardProjection(String qualifier, String expression, Set<String> excludedColumns, int ordinal) {
        List<SourceColumn> sourceColumns = new ArrayList<>();
        sourceColumns.add(new SourceColumn(qualifier, "*"));
        return new Projection(sourceColumns, "*", expression, true, excludedColumns, ordinal);
    }

    private static Set<String> excludedColumnNames(StarRocksParser.ExcludeClauseContext ctx) {
        Set<String> excluded = new LinkedHashSet<>();
        if (ctx == null || ctx.identifierList() == null) {
            return excluded;
        }
        for (StarRocksParser.IdentifierContext identifier : ctx.identifierList().identifier()) {
            excluded.add(cleanIdentifier(identifier).toLowerCase(Locale.ROOT));
        }
        return excluded;
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
        if (tree == null) {
            return;
        }
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

    private TableRef tableRef(StarRocksParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        if (parts.size() == 1 && context != null
                && (context.getDefaultSchema() != null || context.getDefaultCatalog() != null)) {
            return new TableRef(context.getDefaultCatalog(), context.getDefaultSchema(), parts.get(0));
        }
        if (parts.size() == 2 && context != null && context.getDefaultCatalog() != null) {
            return new TableRef(context.getDefaultCatalog(), parts.get(0), parts.get(1));
        }
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private TableRef routineLoadTargetTable(StarRocksParser.MultipartIdentifierContext job,
                                            StarRocksParser.MultipartIdentifierContext target) {
        List<String> targetParts = identifierParts(target);
        if (targetParts.size() != 1) {
            return tableRef(target);
        }
        List<String> jobParts = identifierParts(job);
        if (jobParts.size() >= 2) {
            String catalog = jobParts.size() >= 3 ? jobParts.get(jobParts.size() - 3)
                    : (context == null ? null : context.getDefaultCatalog());
            return new TableRef(catalog, jobParts.get(jobParts.size() - 2), targetParts.get(0));
        }
        return tableRef(target);
    }

    private TableRef showMetadataTable(StarRocksParser.ShowStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        if (ctx.INDEX() == null
                && ctx.INDEXES() == null
                && ctx.KEY() == null
                && ctx.KEYS() == null
                && ctx.COLUMNS() == null) {
            return table;
        }
        List<String> parts = identifierParts(ctx.multipartIdentifier());
        if (parts.size() != 1 || ctx.identifier() == null) {
            return table;
        }
        String schema = cleanIdentifier(ctx.identifier());
        String catalog = context == null ? null : context.getDefaultCatalog();
        return new TableRef(catalog, schema, parts.get(0));
    }

    private TableRef showProcMetadataRef(String text) {
        String path = stringLiteralValue(text);
        if (path == null) {
            return null;
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/dbs/")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : normalized.substring("/dbs/".length()).split("/")) {
            if (!part.trim().isEmpty()) {
                parts.add(cleanIdentifier(part.trim()));
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String catalog = context == null ? null : context.getDefaultCatalog();
        if (parts.size() == 1) {
            return new TableRef(catalog, null, parts.get(0));
        }
        return new TableRef(catalog, parts.get(0), parts.get(1));
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

    private static List<String> identifierParts(StarRocksParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (StarRocksParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String qualifiedName(StarRocksParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (StarRocksParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return String.join(".", parts);
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

    private static String cleanPropertyValue(String text) {
        String literal = stringLiteralValue(text);
        return literal == null ? cleanIdentifier(text) : literal;
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
        final boolean wildcard;
        final Set<String> excludedColumns;
        final int ordinal;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
            this(sourceColumns, targetColumn, expression, -1);
        }

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, int ordinal) {
            this(sourceColumns, targetColumn, expression, false, Collections.emptySet(), ordinal);
        }

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, boolean wildcard) {
            this(sourceColumns, targetColumn, expression, wildcard, Collections.emptySet(), -1);
        }

        Projection(
                List<SourceColumn> sourceColumns,
                String targetColumn,
                String expression,
                boolean wildcard,
                Set<String> excludedColumns,
                int ordinal) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
            this.wildcard = wildcard;
            this.excludedColumns = excludedColumns;
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
