package io.github.linesql.dialect.spark;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.spark.antlr.SqlBaseParser;
import io.github.linesql.dialect.spark.antlr.SqlBaseParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SparkLineageVisitor extends SqlBaseParserBaseVisitor<Void> {
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
    private TableRef firstVisibleInputTable;
    private int visibleRelationCount;
    private int selectExpressionCount;
    private int skippedProjectionCount;
    private int pipeOutputProjectionStart = -1;
    private boolean suppressColumnLineage;
    private boolean suppressMissingColumnLineageDiagnostic;
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
        result.setStatementType(StatementType.INSERT);
        addOutput(ctx.identifierReference());
        addInsertTargetColumns(ctx.identifierList());
        return null;
    }

    @Override
    public Void visitInsertIntoReplaceUsing(SqlBaseParser.InsertIntoReplaceUsingContext ctx) {
        result.setStatementType(StatementType.INSERT);
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
    public Void visitCreatePipelineDataset(SqlBaseParser.CreatePipelineDatasetContext ctx) {
        if (ctx.query() != null) {
            result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
        } else {
            result.setStatementType(StatementType.UNKNOWN);
        }
        addOutput(ctx.createPipelineDatasetHeader().identifierReference());
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreatePipelineInsertIntoFlow(SqlBaseParser.CreatePipelineInsertIntoFlowContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateFlowAutoCdc(SqlBaseParser.CreateFlowAutoCdcContext ctx) {
        result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
        addOutput(ctx.autoCdcCommand().target);
        result.getDiagnostics().add(Diagnostic.warning(
                "CDC_LINEAGE_DEGRADED",
                "Spark AUTO CDC source and target tables were extracted; CDC column lineage is degraded."));
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateView(SqlBaseParser.CreateViewContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        addOutput(ctx.identifierReference());
        addViewTargetColumns(ctx.identifierCommentList());
        visitChildren(ctx);
        if (ctx.TEMPORARY() != null) {
            registerTemporaryRelation(ctx.identifierReference());
        }
        return null;
    }

    @Override
    public Void visitCreateMetricView(SqlBaseParser.CreateMetricViewContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        addOutput(ctx.identifierReference());
        suppressMissingColumnLineageDiagnostic = true;
        result.getDiagnostics().add(Diagnostic.warning(
                "CODE_LITERAL_NOT_EXPANDED",
                "Spark code literal view SQL is not expanded; table and column lineage are degraded."));
        return null;
    }

    @Override
    public Void visitCreateTempViewUsing(SqlBaseParser.CreateTempViewUsingContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        addOutput(ctx.tableIdentifier());
        return null;
    }

    @Override
    public Void visitAlterViewQuery(SqlBaseParser.AlterViewQueryContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        addOutput(ctx.identifierReference());
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropView(SqlBaseParser.DropViewContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        unregisterTemporaryRelation(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitDropTable(SqlBaseParser.DropTableContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        addOutput(ctx.identifierReference());
        unregisterTemporaryRelation(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitCacheTable(SqlBaseParser.CacheTableContext ctx) {
        result.setStatementType(StatementType.CACHE_TABLE);
        addOutput(ctx.identifierReference());
        visitChildren(ctx);
        if (ctx.query() != null) {
            registerTemporaryRelation(ctx.identifierReference());
        }
        return null;
    }

    @Override
    public Void visitUncacheTable(SqlBaseParser.UncacheTableContext ctx) {
        result.setStatementType(StatementType.UNCACHE_TABLE);
        addOutput(ctx.identifierReference());
        unregisterTemporaryRelation(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRenameTable(SqlBaseParser.RenameTableContext ctx) {
        result.setStatementType(StatementType.RENAME_TABLE);
        addInput(ctx.from);
        addOutput(ctx.to);
        unregisterTemporaryRelation(ctx.from);
        return null;
    }

    @Override
    public Void visitTruncateTable(SqlBaseParser.TruncateTableContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        addOutput(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAddTableColumns(SqlBaseParser.AddTableColumnsContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRenameTableColumn(SqlBaseParser.RenameTableColumnContext ctx) {
        markAlterTable(ctx.table);
        return null;
    }

    @Override
    public Void visitDropTableColumns(SqlBaseParser.DropTableColumnsContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitSetTableProperties(SqlBaseParser.SetTablePropertiesContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitUnsetTableProperties(SqlBaseParser.UnsetTablePropertiesContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAlterTableAlterColumn(SqlBaseParser.AlterTableAlterColumnContext ctx) {
        markAlterTable(ctx.table);
        return null;
    }

    @Override
    public Void visitHiveChangeColumn(SqlBaseParser.HiveChangeColumnContext ctx) {
        markAlterTable(ctx.table);
        return null;
    }

    @Override
    public Void visitHiveReplaceColumns(SqlBaseParser.HiveReplaceColumnsContext ctx) {
        markAlterTable(ctx.table);
        return null;
    }

    @Override
    public Void visitSetTableSerDe(SqlBaseParser.SetTableSerDeContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAddTablePartition(SqlBaseParser.AddTablePartitionContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRenameTablePartition(SqlBaseParser.RenameTablePartitionContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitDropTablePartitions(SqlBaseParser.DropTablePartitionsContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitSetTableLocation(SqlBaseParser.SetTableLocationContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRecoverPartitions(SqlBaseParser.RecoverPartitionsContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRepairTable(SqlBaseParser.RepairTableContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAlterClusterBy(SqlBaseParser.AlterClusterByContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAlterTableCollation(SqlBaseParser.AlterTableCollationContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAddTableConstraint(SqlBaseParser.AddTableConstraintContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitDropTableConstraint(SqlBaseParser.DropTableConstraintContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitAnalyze(SqlBaseParser.AnalyzeContext ctx) {
        markReadMetadata(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitDescribeRelation(SqlBaseParser.DescribeRelationContext ctx) {
        markReadMetadata(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitShowCreateTable(SqlBaseParser.ShowCreateTableContext ctx) {
        markReadMetadata(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitShowColumns(SqlBaseParser.ShowColumnsContext ctx) {
        markReadMetadata(ctx.table);
        return null;
    }

    @Override
    public Void visitShowPartitions(SqlBaseParser.ShowPartitionsContext ctx) {
        markReadMetadata(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitShowTblProperties(SqlBaseParser.ShowTblPropertiesContext ctx) {
        markReadMetadata(ctx.table);
        return null;
    }

    @Override
    public Void visitShowTables(SqlBaseParser.ShowTablesContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowTableExtended(SqlBaseParser.ShowTableExtendedContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowViews(SqlBaseParser.ShowViewsContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowCollations(SqlBaseParser.ShowCollationsContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitDescribeNamespace(SqlBaseParser.DescribeNamespaceContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitDescribeQuery(SqlBaseParser.DescribeQueryContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        suppressMissingColumnLineageDiagnostic = true;
        return visitChildren(ctx);
    }

    @Override
    public Void visitRefreshTable(SqlBaseParser.RefreshTableContext ctx) {
        markReadMetadata(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitRefreshResource(SqlBaseParser.RefreshResourceContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitClearCache(SqlBaseParser.ClearCacheContext ctx) {
        result.setStatementType(StatementType.UNCACHE_TABLE);
        suppressMissingColumnLineageDiagnostic = true;
        return null;
    }

    @Override
    public Void visitCreateIndex(SqlBaseParser.CreateIndexContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitDropIndex(SqlBaseParser.DropIndexContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitCommentTable(SqlBaseParser.CommentTableContext ctx) {
        markAlterTable(ctx.identifierReference());
        return null;
    }

    @Override
    public Void visitCommentNamespace(SqlBaseParser.CommentNamespaceContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitManageResource(SqlBaseParser.ManageResourceContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCommentColumn(SqlBaseParser.CommentColumnContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        if (ctx.identifierReference() != null) {
            addOutput(ctx.identifierReference());
        } else if (ctx.columnComment() != null) {
            addOutputTableForColumn(ctx.columnComment().column);
        }
        return null;
    }

    @Override
    public Void visitExplain(SqlBaseParser.ExplainContext ctx) {
        if (ctx.statement() != null) {
            return visit(ctx.statement());
        }
        result.setStatementType(StatementType.UNKNOWN);
        suppressMissingColumnLineageDiagnostic = true;
        return null;
    }

    @Override
    public Void visitVisitExecuteImmediate(SqlBaseParser.VisitExecuteImmediateContext ctx) {
        result.setStatementType(StatementType.UNKNOWN);
        suppressMissingColumnLineageDiagnostic = true;
        result.getDiagnostics().add(Diagnostic.warning(
                "DYNAMIC_SQL_NOT_EXPANDED",
                "Spark EXECUTE IMMEDIATE dynamic SQL is not expanded; table and column lineage are degraded."));
        return null;
    }

    @Override
    public Void visitUse(SqlBaseParser.UseContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitUseNamespace(SqlBaseParser.UseNamespaceContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetCatalog(SqlBaseParser.SetCatalogContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetTimeZone(SqlBaseParser.SetTimeZoneContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetPath(SqlBaseParser.SetPathContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetVariable(SqlBaseParser.SetVariableContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetQuotedConfiguration(SqlBaseParser.SetQuotedConfigurationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetConfiguration(SqlBaseParser.SetConfigurationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitResetQuotedConfiguration(SqlBaseParser.ResetQuotedConfigurationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitResetConfiguration(SqlBaseParser.ResetConfigurationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCreateNamespace(SqlBaseParser.CreateNamespaceContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetNamespaceProperties(SqlBaseParser.SetNamespacePropertiesContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitUnsetNamespaceProperties(SqlBaseParser.UnsetNamespacePropertiesContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetNamespaceCollation(SqlBaseParser.SetNamespaceCollationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitSetNamespaceLocation(SqlBaseParser.SetNamespaceLocationContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitDropNamespace(SqlBaseParser.DropNamespaceContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitShowNamespaces(SqlBaseParser.ShowNamespacesContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitAnalyzeTables(SqlBaseParser.AnalyzeTablesContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowCurrentNamespace(SqlBaseParser.ShowCurrentNamespaceContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowCatalogs(SqlBaseParser.ShowCatalogsContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitCreateFunction(SqlBaseParser.CreateFunctionContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCreateUserDefinedFunction(SqlBaseParser.CreateUserDefinedFunctionContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitDropFunction(SqlBaseParser.DropFunctionContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCall(SqlBaseParser.CallContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCreateVariable(SqlBaseParser.CreateVariableContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitDropVariable(SqlBaseParser.DropVariableContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitDeclareCursorStatement(SqlBaseParser.DeclareCursorStatementContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitOpenCursorStatement(SqlBaseParser.OpenCursorStatementContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitFetchCursorStatement(SqlBaseParser.FetchCursorStatementContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitCloseCursorStatement(SqlBaseParser.CloseCursorStatementContext ctx) {
        markNonLineageStatement();
        return null;
    }

    @Override
    public Void visitShowFunctions(SqlBaseParser.ShowFunctionsContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitShowProcedures(SqlBaseParser.ShowProceduresContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitDescribeFunction(SqlBaseParser.DescribeFunctionContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitDescribeProcedure(SqlBaseParser.DescribeProcedureContext ctx) {
        markReadMetadataWithoutTable();
        return null;
    }

    @Override
    public Void visitRefreshFunction(SqlBaseParser.RefreshFunctionContext ctx) {
        markReadMetadataWithoutTable();
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
        LineageModelUtils.mergeColumnUsages(result, left);
        LineageModelUtils.mergeColumnUsages(result, right);
        result.setColumnLineage(mergeSetColumnLineage(left, right));
        return null;
    }

    @Override
    public Void visitTable(SqlBaseParser.TableContext ctx) {
        addInput(ctx.identifierReference());
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
        addOutput(ctx.target, ctx.targetAlias);
        if (ctx.source != null) {
            addInput(ctx.source, ctx.sourceAlias);
        }
        addColumnUsages(ColumnUsageType.MERGE_ON, sourceColumns(ctx.mergeCondition));
        return visitChildren(ctx);
    }

    @Override
    public Void visitMatchedClause(SqlBaseParser.MatchedClauseContext ctx) {
        if (ctx.matchedCond != null) {
            addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(ctx.matchedCond));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitNotMatchedClause(SqlBaseParser.NotMatchedClauseContext ctx) {
        if (ctx.notMatchedCond != null) {
            addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(ctx.notMatchedCond));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitNotMatchedBySourceClause(SqlBaseParser.NotMatchedBySourceClauseContext ctx) {
        if (ctx.notMatchedBySourceCond != null) {
            addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(ctx.notMatchedBySourceCond));
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
        addInput(ctx.identifierReference(), ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitStreamTableName(SqlBaseParser.StreamTableNameContext ctx) {
        addInput(ctx.multipartIdentifier(), ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitUnnestTable(SqlBaseParser.UnnestTableContext ctx) {
        List<SourceColumn> sources = new ArrayList<>();
        for (SqlBaseParser.ExpressionContext expression : ctx.unnest().expression()) {
            sources.addAll(sourceColumns(expression));
        }
        addGeneratedColumns(tableAlias(ctx.unnest().tableAlias()),
                tableAliasColumnNames(ctx.unnest().tableAlias()), sources);
        return null;
    }

    @Override
    public Void visitJsonTableRelation(SqlBaseParser.JsonTableRelationContext ctx) {
        List<SourceColumn> sources = sourceColumns(ctx.jsonTable().jsonExpr);
        List<String> columnNames = new ArrayList<>();
        for (SqlBaseParser.JsonTableColumnContext column : ctx.jsonTable().jsonTableColumn()) {
            columnNames.add(cleanIdentifier(column.getChild(0).getText()));
        }
        addGeneratedColumns(tableAlias(ctx.jsonTable().tableAlias()), columnNames, sources);
        return null;
    }

    @Override
    public Void visitFunctionTableSubqueryArgument(SqlBaseParser.FunctionTableSubqueryArgumentContext ctx) {
        if (ctx.identifierReference() != null) {
            addInput(ctx.identifierReference());
            return null;
        }
        if (ctx.query() != null) {
            LineageResult queryResult = lineageForQuery(ctx.query());
            for (TableRef table : queryResult.getInputTables()) {
                addInputTable(table, false);
            }
            LineageModelUtils.mergeColumnUsages(result, queryResult);
        }
        return null;
    }

    @Override
    public Void visitTableValuedFunction(SqlBaseParser.TableValuedFunctionContext ctx) {
        SqlBaseParser.TableFunctionCallWithTrailingClausesContext callWithClauses =
                ctx.tableFunctionCallWithTrailingClauses();
        String functionName = callWithClauses.tableFunctionCall().funcName.getText();
        if ("range".equalsIgnoreCase(functionName)) {
            List<String> outputColumns = tableAliasColumnNames(callWithClauses.tableAlias());
            if (outputColumns.isEmpty()) {
                outputColumns.add("id");
            }
            String alias = tableAlias(callWithClauses.tableAlias());
            for (String outputColumn : outputColumns) {
                addGeneratedColumn(alias, outputColumn, new ArrayList<SourceColumn>());
            }
            refreshColumnLineage();
        }
        return visitChildren(ctx);
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
    public Void visitWhereClause(SqlBaseParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.booleanExpression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoinCriteria(SqlBaseParser.JoinCriteriaContext ctx) {
        if (ctx.booleanExpression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.booleanExpression()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitAggregationClause(SqlBaseParser.AggregationClauseContext ctx) {
        for (SqlBaseParser.NamedExpressionContext groupingExpression : ctx.groupingExpressions) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(groupingExpression.expression()));
        }
        for (SqlBaseParser.GroupByClauseContext groupByClause : ctx.groupingExpressionsWithGroupingAnalytics) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(groupByClause));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(SqlBaseParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.booleanExpression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(SqlBaseParser.QueryOrganizationContext ctx) {
        for (SqlBaseParser.SortItemContext sortItem : ctx.order) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitOperatorPipeRightSide(SqlBaseParser.OperatorPipeRightSideContext ctx) {
        if (ctx.extendList != null) {
            for (SqlBaseParser.NamedExpressionContext namedExpression : ctx.extendList.namedExpression()) {
                Projection projection = projection(namedExpression);
                if (projection != null) {
                    addGeneratedColumn(null, projection.targetColumn, projection.sourceColumns);
                }
            }
            refreshColumnLineage();
            return null;
        }
        if (ctx.AGGREGATE() != null && ctx.namedExpressionSeq() != null) {
            clearPipeOutputProjections();
            pipeOutputProjectionStart = projections.size();
            if (ctx.aggregationClause() != null) {
                for (SqlBaseParser.NamedExpressionContext groupingExpression
                        : ctx.aggregationClause().groupingExpressions) {
                    Projection projection = projection(groupingExpression);
                    if (projection != null) {
                        projections.add(projection);
                    }
                }
                for (SqlBaseParser.GroupByClauseContext groupByClause
                        : ctx.aggregationClause().groupingExpressionsWithGroupingAnalytics) {
                    if (groupByClause.expression() != null) {
                        Projection projection = projection(groupByClause.expression());
                        if (projection != null) {
                            projections.add(projection);
                        }
                    }
                }
            }
            for (SqlBaseParser.NamedExpressionContext namedExpression : ctx.namedExpressionSeq().namedExpression()) {
                Projection projection = projection(namedExpression);
                if (projection != null) {
                    addGeneratedColumn(null, projection.targetColumn, projection.sourceColumns);
                    projections.add(projection);
                }
            }
            refreshColumnLineage();
            return null;
        }
        if (ctx.selectClause() != null) {
            clearPipeOutputProjections();
        }
        if (ctx.operator != null && ctx.right != null) {
            refreshColumnLineage();
            LineageResult left = new LineageResult();
            left.setColumnLineage(new ArrayList<>(result.getColumnLineage()));
            LineageResult right = lineageForQueryPrimary(ctx.right);
            for (TableRef table : right.getInputTables()) {
                addInputTable(table, false);
            }
            LineageModelUtils.mergeColumnUsages(result, right);
            result.setColumnLineage(mergeSetColumnLineage(left, right));
            projections.clear();
            return null;
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
            String relationAlias = cleanIdentifier(ctx.tblName.getText());
            for (SqlBaseParser.IdentifierContext column : ctx.colName) {
                addGeneratedColumn(relationAlias, cleanIdentifier(column.getText()), sources);
            }
        }
        refreshColumnLineage();
        return visitChildren(ctx);
    }

    @Override
    public Void visitPivotClause(SqlBaseParser.PivotClauseContext ctx) {
        for (SqlBaseParser.NamedExpressionContext namedExpression : ctx.aggregates.namedExpression()) {
            Projection projection = projection(namedExpression);
            if (projection == null) {
                continue;
            }
            for (SqlBaseParser.PivotValueContext pivotValue : ctx.pivotValues) {
                String valueName = pivotValueName(pivotValue);
                if (valueName == null) {
                    continue;
                }
                addGeneratedColumn(null, valueName, projection.sourceColumns);
                addGeneratedColumn(null, valueName + "_" + projection.targetColumn, projection.sourceColumns);
            }
        }
        refreshColumnLineage();
        return visitChildren(ctx);
    }

    @Override
    public Void visitUnpivotSingleValueColumnClause(SqlBaseParser.UnpivotSingleValueColumnClauseContext ctx) {
        List<SourceColumn> sources = new ArrayList<>();
        for (SqlBaseParser.UnpivotColumnAndAliasContext column : ctx.unpivotColumns) {
            sources.add(new SourceColumn(null, cleanMultipartIdentifier(column.unpivotColumn().multipartIdentifier())));
        }
        addGeneratedColumn(null, cleanIdentifier(ctx.unpivotValueColumn().getText()), sources);
        addGeneratedColumn(null, cleanIdentifier(ctx.unpivotNameColumn().getText()), new ArrayList<SourceColumn>());
        refreshColumnLineage();
        return visitChildren(ctx);
    }

    @Override
    public Void visitUnpivotMultiValueColumnClause(SqlBaseParser.UnpivotMultiValueColumnClauseContext ctx) {
        for (int valueIndex = 0; valueIndex < ctx.unpivotValueColumns.size(); valueIndex++) {
            List<SourceColumn> sources = new ArrayList<>();
            for (SqlBaseParser.UnpivotColumnSetContext columnSet : ctx.unpivotColumnSets) {
                if (valueIndex < columnSet.unpivotColumns.size()) {
                    sources.add(new SourceColumn(
                            null,
                            cleanMultipartIdentifier(columnSet.unpivotColumns.get(valueIndex).multipartIdentifier())));
                }
            }
            addGeneratedColumn(null, cleanIdentifier(ctx.unpivotValueColumns.get(valueIndex).getText()), sources);
        }
        addGeneratedColumn(null, cleanIdentifier(ctx.unpivotNameColumn().getText()), new ArrayList<SourceColumn>());
        refreshColumnLineage();
        return visitChildren(ctx);
    }

    private void addGeneratedColumns(String relationAlias, List<String> columnNames, List<SourceColumn> sources) {
        if (columnNames.isEmpty() || sources.isEmpty()) {
            return;
        }
        for (String columnName : columnNames) {
            addGeneratedColumn(relationAlias, columnName, sources);
        }
        refreshColumnLineage();
    }

    private void addGeneratedColumn(String relationAlias, String columnName, List<SourceColumn> sources) {
        generatedColumns.put(columnName, sources);
        if (relationAlias != null) {
            generatedColumns.put(relationAlias + "." + columnName, sources);
        }
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

    boolean shouldWarnMissingColumnLineage() {
        return !suppressMissingColumnLineageDiagnostic;
    }

    private void markNonLineageStatement() {
        result.setStatementType(StatementType.UNKNOWN);
        suppressMissingColumnLineageDiagnostic = true;
    }

    private void markReadMetadataWithoutTable() {
        result.setStatementType(StatementType.READ_METADATA);
        suppressMissingColumnLineageDiagnostic = true;
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

    private void addInput(SqlBaseParser.MultipartIdentifierContext ctx, SqlBaseParser.TableAliasContext aliasContext) {
        addInputTable(tableRef(ctx.getText()), aliasContext, true);
    }

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, SqlBaseParser.TableAliasContext aliasContext, boolean visibleRelation) {
        if (visibleRelation) {
            visibleRelationCount++;
            if (firstVisibleInputTable == null) {
                firstVisibleInputTable = table;
            }
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
        if (columns.isEmpty() && relationResult.getInputTables().size() == 1) {
            columns.put("*", singletonColumnRef(relationResult.getInputTables().get(0), "*"));
        }
        derivedColumnLineage.put(relationName, columns);
        for (TableRef table : relationResult.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relationResult);
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

    private LineageResult lineageForQueryPrimary(SqlBaseParser.QueryPrimaryContext queryPrimary) {
        LineageResult queryResult = new LineageResult();
        SparkLineageVisitor queryVisitor = new SparkLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.setContext(context);
        queryVisitor.visit(queryPrimary);
        queryVisitor.refreshColumnLineage();
        return queryResult;
    }

    private LineageResult lineageForQuery(SqlBaseParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        SparkLineageVisitor queryVisitor = new SparkLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.setContext(context);
        queryVisitor.visit(query);
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
            lineage.setSources(LineageModelUtils.mergeColumnRefs(leftColumn.getSources(), rightColumn.getSources()));
            lineage.setExpression(leftColumn.getExpression());
            merged.add(lineage);
        }
        return merged;
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
        if (columns.isEmpty() && relation.getInputTables().size() == 1) {
            columns.put("*", singletonColumnRef(relation.getInputTables().get(0), "*"));
        }
        derivedColumnLineage.put(relationName, columns);
        for (TableRef table : relation.getInputTables()) {
            addInputTable(table, false);
        }
        LineageModelUtils.mergeColumnUsages(result, relation);
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
        copy.setColumnUsages(new ArrayList<>(source.getColumnUsages()));
        copy.setDiagnostics(new ArrayList<>(source.getDiagnostics()));
        return copy;
    }

    private void addOutput(SqlBaseParser.IdentifierReferenceContext ctx) {
        addOutput(ctx, null);
    }

    private void addOutput(SqlBaseParser.IdentifierReferenceContext ctx, SqlBaseParser.TableAliasContext aliasContext) {
        TableRef table = tableRef(ctx.getText());
        outputTables.add(table);
        tableAliases.put(table.getName().toLowerCase(java.util.Locale.ROOT), table);
        String alias = tableAlias(aliasContext);
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(java.util.Locale.ROOT), table);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        refreshColumnLineage();
    }

    private void addOutput(SqlBaseParser.TableIdentifierContext ctx) {
        outputTables.add(tableRef(ctx.getText()));
        result.setOutputTables(new ArrayList<>(outputTables));
        refreshColumnLineage();
    }

    private void addOutput(SqlBaseParser.MultipartIdentifierContext ctx) {
        outputTables.add(tableRef(ctx.getText()));
        result.setOutputTables(new ArrayList<>(outputTables));
        refreshColumnLineage();
    }

    private void addOutputTableForColumn(SqlBaseParser.MultipartIdentifierContext ctx) {
        List<String> parts = splitIdentifier(ctx.getText());
        if (parts.size() <= 1) {
            return;
        }
        outputTables.add(tableRef(String.join(".", parts.subList(0, parts.size() - 1))));
        result.setOutputTables(new ArrayList<>(outputTables));
        refreshColumnLineage();
    }

    private void markAlterTable(SqlBaseParser.IdentifierReferenceContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        addOutput(ctx);
    }

    private void markReadMetadata(SqlBaseParser.IdentifierReferenceContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        addInput(ctx);
    }

    private void refreshColumnLineage() {
        if (suppressColumnLineage || projections.isEmpty()) {
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

    private void clearPipeOutputProjections() {
        if (pipeOutputProjectionStart < 0) {
            return;
        }
        while (projections.size() > pipeOutputProjectionStart) {
            projections.remove(projections.size() - 1);
        }
        pipeOutputProjectionStart = -1;
        refreshColumnLineage();
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

    private void addViewTargetColumns(SqlBaseParser.IdentifierCommentListContext ctx) {
        if (ctx == null) {
            return;
        }
        for (SqlBaseParser.IdentifierCommentContext identifierComment : ctx.identifierComment()) {
            insertTargetColumns.add(cleanIdentifier(identifierComment.identifier().getText()));
        }
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

    private static List<String> tableAliasColumnNames(SqlBaseParser.TableAliasContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return identifierNames(ctx.identifierList());
    }

    private static String pivotValueName(SqlBaseParser.PivotValueContext ctx) {
        if (ctx.errorCapturingIdentifier() != null) {
            return cleanIdentifier(ctx.errorCapturingIdentifier().getText());
        }
        String text = ctx.expression().getText();
        if (text.length() >= 2
                && ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\"")))) {
            return text.substring(1, text.length() - 1);
        }
        return null;
    }

    private static String cleanMultipartIdentifier(SqlBaseParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (SqlBaseParser.ErrorCapturingIdentifierContext identifier : ctx.parts) {
            parts.add(cleanIdentifier(identifier.getText()));
        }
        return String.join(".", parts);
    }

    private List<ColumnRef> columnRefs(Projection projection) {
        return columnRefs(projection.sourceColumns, new LinkedHashSet<String>());
    }

    private void addColumnUsages(ColumnUsageType type, List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = partialColumnRefs(sourceColumns);
        LineageModelUtils.addColumnUsages(result, type, refs);
    }

    private List<ColumnRef> partialColumnRefs(List<SourceColumn> sourceColumns) {
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn sourceColumn : sourceColumns) {
            List<SourceColumn> singleton = new ArrayList<>();
            singleton.add(sourceColumn);
            List<ColumnRef> resolved = columnRefs(singleton, new LinkedHashSet<String>());
            if (resolved != null) {
                refs.addAll(resolved);
                addVisibleSingleTableClauseRef(refs, sourceColumn);
                continue;
            }
            List<ColumnRef> projectionResolved = columnRefsFromProjection(sourceColumn);
            if (projectionResolved != null) {
                refs.addAll(projectionResolved);
                continue;
            }
            addVisibleSingleTableClauseRef(refs, sourceColumn);
        }
        return refs;
    }

    private void addVisibleSingleTableClauseRef(List<ColumnRef> refs, SourceColumn sourceColumn) {
        if (sourceColumn.qualifier != null || firstVisibleInputTable == null || sourceColumn.name.contains(".")) {
            return;
        }
        ColumnRef fallback = new ColumnRef(firstVisibleInputTable, sourceColumn.name);
        if (!containsColumnRef(refs, fallback)) {
            refs.add(fallback);
        }
    }

    private static boolean containsColumnRef(List<ColumnRef> refs, ColumnRef candidate) {
        for (ColumnRef ref : refs) {
            if (columnKey(ref).equals(columnKey(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static List<ColumnRef> singletonColumnRef(TableRef table, String columnName) {
        List<ColumnRef> refs = new ArrayList<>();
        refs.add(new ColumnRef(table, columnName));
        return refs;
    }

    private List<ColumnRef> columnRefsFromProjection(SourceColumn sourceColumn) {
        if (sourceColumn.qualifier != null) {
            return null;
        }
        for (Projection projection : projections) {
            if (!projection.targetColumn.equalsIgnoreCase(sourceColumn.name)) {
                continue;
            }
            return columnRefs(projection.sourceColumns, new LinkedHashSet<String>());
        }
        return null;
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
        List<ColumnRef> refs = columns.get(sourceColumn.name);
        if (refs != null) {
            return refs;
        }
        List<ColumnRef> wildcard = columns.get("*");
        if (wildcard != null && wildcard.size() == 1 && wildcard.get(0).getTable() != null) {
            return singletonColumnRef(wildcard.get(0).getTable(), sourceColumn.name);
        }
        return null;
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
                ? unqualifiedColumnName(sourceColumns.get(0).name)
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

    private static Projection projection(SqlBaseParser.ExpressionContext ctx) {
        String expression = ctx.getText();
        List<SourceColumn> sourceColumns = sourceColumns(ctx);
        String directColumn = sourceColumns.size() == 1 && isDirectColumnExpression(expression, sourceColumns.get(0))
                ? unqualifiedColumnName(sourceColumns.get(0).name)
                : null;
        if (directColumn == null) {
            return null;
        }
        return new Projection(sourceColumns, directColumn, expression);
    }

    private static boolean isDirectColumnExpression(String expression, SourceColumn column) {
        return expression.equals(column.name) || expression.endsWith("." + column.name);
    }

    private static String unqualifiedColumnName(String raw) {
        List<String> parts = splitIdentifier(raw);
        return parts.isEmpty() ? raw : parts.get(parts.size() - 1);
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

    static class Projection {
        final List<SourceColumn> sourceColumns;
        final String targetColumn;
        final String expression;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
        }
    }

    static class SourceColumn {
        final String qualifier;
        final String name;

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
