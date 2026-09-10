package io.github.linesql.dialect.oracle;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.oracle.antlr.OracleParser;
import io.github.linesql.dialect.oracle.antlr.OracleParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class OracleLineageVisitor extends OracleParserBaseVisitor<Void> {
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

    OracleLineageVisitor(LineageResult result) {
        this.result = result;
    }

    @Override
    public Void visitStatementDefault(OracleParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStmt(OracleParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStatement(OracleParser.InsertStatementContext ctx) {
        if (!ctx.multiTableInsertClause().isEmpty()) {
            visit(ctx.query());
            refreshColumnLineage();
            List<ColumnLineage> lineages = new ArrayList<>();
            for (OracleParser.MultiTableInsertClauseContext clause : ctx.multiTableInsertClause()) {
                TableRef target = tableRef(clause.multipartIdentifier());
                outputTables.add(target);
                lineages.addAll(readMultiTableInsertValues(clause, target));
                if (clause.condition != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(clause.condition));
                }
            }
            suppressColumnLineage = true;
            result.setColumnLineage(lineages);
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
            return null;
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (OracleParser.IdentifierContext id : ctx.columnList.identifier()) {
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
    public Void visitUpdateStmt(OracleParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(OracleParser.UpdateStatementContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = table;
        inputTables.add(table);
        outputTables.add(table);
        tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), table);
        }
        // Collect subquery inputs from assignments and where clause
        collectSubqueryInputs(ctx.assignmentList());
        if (ctx.whereClause() != null) {
            collectSubqueryInputs(ctx.whereClause());
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), table);
        result.setColumnLineage(assignments);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteStmt(OracleParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteStatement(OracleParser.DeleteStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        currentDmlTarget = target;
        inputTables.add(target);
        outputTables.add(target);
        tableAliases.put(target.getName().toLowerCase(Locale.ROOT), target);
        String alias = tableAlias(ctx.tableAlias());
        if (alias != null) {
            tableAliases.put(alias.toLowerCase(Locale.ROOT), target);
        }
        // Collect subquery inputs from where clause
        if (ctx.whereClause() != null) {
            collectSubqueryInputs(ctx.whereClause());
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitMergeStmt(OracleParser.MergeStmtContext ctx) {
        result.setStatementType(StatementType.MERGE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMergeStatement(OracleParser.MergeStatementContext ctx) {
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
        for (OracleParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.mergeMatchedAction() != null) {
                OracleParser.MergeMatchedActionContext action = clause.mergeMatchedAction();
                lineages.addAll(readAssignments(action.assignmentList(), target));
                if (action.updateWhere != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(action.updateWhere.expression()));
                }
                if (action.deleteWhere != null) {
                    addColumnUsages(ColumnUsageType.MERGE_WHEN, sourceColumns(action.deleteWhere.expression()));
                }
            }
            if (clause.mergeNotMatchedAction() != null) {
                OracleParser.MergeNotMatchedActionContext action = clause.mergeNotMatchedAction();
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

    @Override
    public Void visitCreateIndexStmt(OracleParser.CreateIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStatement(OracleParser.CreateIndexStatementContext ctx) {
        List<OracleParser.MultipartIdentifierContext> identifiers = ctx.multipartIdentifier();
        if (identifiers.size() > 1) {
            TableRef table = tableRef(identifiers.get(1));
            outputTables.add(table);
            currentDmlTarget = table;
            tableAliases.put(table.getName().toLowerCase(Locale.ROOT), table);
            addColumnUsages(ColumnUsageType.INDEX, sourceColumns(ctx.indexElementList()));
            for (OracleParser.OracleIndexOptionContext option : ctx.oracleIndexOption()) {
                if (option.oraclePartitionClause() != null) {
                    addIndexColumnUsages(table, option.oraclePartitionClause().identifierList());
                }
            }
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addIndexColumnUsages(TableRef table, OracleParser.IdentifierListContext identifierList) {
        if (identifierList == null) {
            return;
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (String column : identifierNames(identifierList)) {
            refs.add(new ColumnRef(table, column));
        }
        LineageModelUtils.addColumnUsages(result, ColumnUsageType.INDEX, refs);
    }

    @Override
    public Void visitAlterIndexStmt(OracleParser.AlterIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return null;
    }

    @Override
    public Void visitDropIndexStmt(OracleParser.DropIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return null;
    }

    @Override
    public Void visitCreateRoutineStmt(OracleParser.CreateRoutineStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        return null;
    }

    @Override
    public Void visitAnonymousBlockStmt(OracleParser.AnonymousBlockStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateTriggerStmt(OracleParser.CreateTriggerStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_TRIGGER);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTriggerStatement(OracleParser.CreateTriggerStatementContext ctx) {
        List<OracleParser.MultipartIdentifierContext> identifiers = ctx.multipartIdentifier();
        if (identifiers.size() > 1) {
            outputTables.add(tableRef(identifiers.get(1)));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateTableStmt(OracleParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTableStatement(OracleParser.CreateTableStatementContext ctx) {
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
            result.setStatementType(StatementType.CREATE_TABLE);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateViewStmt(OracleParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(OracleParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (OracleParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
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
    public Void visitDropTableStmt(OracleParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropTableStatement(OracleParser.DropTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(OracleParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(OracleParser.DropViewStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropRoutineStmt(OracleParser.DropRoutineStmtContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitDropTriggerStmt(OracleParser.DropTriggerStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TRIGGER);
        return null;
    }

    @Override
    public Void visitOracleSchemaObjectControlStmt(OracleParser.OracleSchemaObjectControlStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        List<OracleParser.MultipartIdentifierContext> identifiers = ctx.oracleSchemaObjectControlStatement().multipartIdentifier();
        if (ctx.oracleSchemaObjectControlStatement().SYNONYM() != null && !identifiers.isEmpty()) {
            outputTables.add(tableRef(identifiers.get(0)));
            if (ctx.oracleSchemaObjectControlStatement().CREATE() != null && identifiers.size() > 1) {
                inputTables.add(tableRef(identifiers.get(1)));
            }
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(OracleParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(OracleParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitLockTableStmt(OracleParser.LockTableStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLockTableStatement(OracleParser.LockTableStatementContext ctx) {
        for (OracleParser.MultipartIdentifierContext id : ctx.multipartIdentifier()) {
            inputTables.add(tableRef(id));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitGrantStmt(OracleParser.GrantStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitGrantStatement(OracleParser.GrantStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRevokeStmt(OracleParser.RevokeStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRevokeStatement(OracleParser.RevokeStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitTransactionStmt(OracleParser.TransactionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterSessionStmt(OracleParser.AlterSessionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterSystemStmt(OracleParser.AlterSystemStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAlterMaterializedViewStmt(OracleParser.AlterMaterializedViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        outputTables.add(tableRef(ctx.alterMaterializedViewStatement().multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(OracleParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(OracleParser.AlterTableRenameContext ctx) {
        List<OracleParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(OracleParser.AlterTableAddColumnContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(OracleParser.AlterTableOtherContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitShowStmt(OracleParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(OracleParser.ShowStatementContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof OracleParser.MultipartIdentifierContext) {
                inputTables.add(tableRef((OracleParser.MultipartIdentifierContext) child));
                break;
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitDescribeStmt(OracleParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        inputTables.add(tableRef(ctx.describeStatement().multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitCommentStmt(OracleParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(OracleParser.CommentStatementContext ctx) {
        OracleParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
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
    public Void visitAnalyzeTableStmt(OracleParser.AnalyzeTableStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeIndexStmt(OracleParser.AnalyzeIndexStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitExplainPlanStmt(OracleParser.ExplainPlanStmtContext ctx) {
        visit(ctx.explainPlanStatement());
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitExplainPlanStatement(OracleParser.ExplainPlanStatementContext ctx) {
        visit(ctx.statement());
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitAnalyzeTableStatement(OracleParser.AnalyzeTableStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitAnalyzeIndexStatement(OracleParser.AnalyzeIndexStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    // ============ Query traversal ============

    @Override
    public Void visitCtes(OracleParser.CtesContext ctx) {
        for (OracleParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(OracleParser.SetOperationContext ctx) {
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
    public Void visitTableName(OracleParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        // Filter DUAL pseudo table
        if (isDualTable(table)) {
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
    public Void visitTableFunctionRelation(OracleParser.TableFunctionRelationContext ctx) {
        TableRef function = tableRef(ctx.qualifiedName());
        addInputTable(function, ctx.tableAlias(), true);
        if (ctx.expressionList() != null) {
            for (OracleParser.ExpressionContext expression : ctx.expressionList().expression()) {
                addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(expression));
            }
        }
        if (ctx.jsonTableArgumentList() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.jsonTableArgumentList().expression()));
        }
        if (ctx.xmlTableArgumentList() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.xmlTableArgumentList().expression()));
        }
        return null;
    }

    @Override
    public Void visitAliasedQuery(OracleParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitLateralQuery(OracleParser.LateralQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$lateral" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), new ArrayList<>(), true);
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(OracleParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        registerPivotDerivedRelation(ctx.relationPrimary(), ctx.pivotClause());
        registerUnpivotDerivedRelation(ctx.relationPrimary(), ctx.pivotClause());
        if (ctx.matchRecognizeClause() != null) {
            visit(ctx.matchRecognizeClause());
            registerMatchRecognizeDerivedRelation(ctx.relationPrimary(), ctx.matchRecognizeClause());
        }
        for (OracleParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            registerPivotDerivedRelation(join.relationPrimary(), join.pivotClause());
            registerUnpivotDerivedRelation(join.relationPrimary(), join.pivotClause());
            if (join.matchRecognizeClause() != null) {
                visit(join.matchRecognizeClause());
                registerMatchRecognizeDerivedRelation(join.relationPrimary(), join.matchRecognizeClause());
            }
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    private void registerMatchRecognizeDerivedRelation(
            OracleParser.RelationPrimaryContext relationPrimary,
            OracleParser.MatchRecognizeClauseContext matchRecognize) {
        if (matchRecognize == null || visibleRelations.isEmpty()) {
            return;
        }
        VisibleRelation baseRelation = visibleRelations.get(visibleRelations.size() - 1);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        if (baseRelation.table != null) {
            List<ColumnRef> wildcard = new ArrayList<>();
            wildcard.add(new ColumnRef(baseRelation.table, "*"));
            columns.put("*", wildcard);
        }
        for (OracleParser.MatchRecognizeOptionContext option : matchRecognize.matchRecognizeOption()) {
            for (OracleParser.MatchMeasureContext measure : option.matchMeasure()) {
                List<ColumnRef> refs = columnRefs(sourceColumns(measure.expression()));
                if (refs != null && !refs.isEmpty()) {
                    columns.put(cleanIdentifier(measure.identifier()), refs);
                }
            }
        }
        if (columns.isEmpty()) {
            return;
        }
        String derivedName = "$match_recognize" + derivedColumnLineage.size();
        replaceVisibleRelationWithDerived(relationPrimary, columns, derivedName);
        String alias = tableAlias(matchRecognize.tableAlias());
        if (alias != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), derivedName);
        }
    }

    private void registerPivotDerivedRelation(
            OracleParser.RelationPrimaryContext relationPrimary,
            List<OracleParser.PivotClauseContext> pivotClauses) {
        if (pivotClauses == null || pivotClauses.isEmpty() || visibleRelations.isEmpty()) {
            return;
        }
        OracleParser.PivotClauseContext pivot = null;
        for (OracleParser.PivotClauseContext clause : pivotClauses) {
            if (clause.PIVOT() != null) {
                pivot = clause;
            }
        }
        if (pivot == null) {
            return;
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (OracleParser.PivotAggregationContext aggregate : pivot.pivotAggregation()) {
            List<ColumnRef> refs = columnRefs(sourceColumns(aggregate.expression()));
            if (refs == null || refs.isEmpty()) {
                continue;
            }
            String aggregateName = pivotAggregateName(aggregate);
            for (OracleParser.PivotInItemContext item : pivot.pivotInItem()) {
                String valueName = pivotValueName(item);
                if (valueName.isEmpty()) {
                    continue;
                }
                columns.put(valueName, refs);
                columns.put(valueName + "_" + aggregateName, refs);
                columns.put(aggregateName + "_" + valueName, refs);
            }
        }
        addColumnUsages(ColumnUsageType.GROUP_BY, pivotForSourceColumns(pivot.pivotForExpression()));
        if (columns.isEmpty()) {
            return;
        }
        replaceVisibleRelationWithDerived(relationPrimary, columns, "$pivot" + derivedColumnLineage.size());
    }

    private void registerUnpivotDerivedRelation(
            OracleParser.RelationPrimaryContext relationPrimary,
            List<OracleParser.PivotClauseContext> pivotClauses) {
        if (pivotClauses == null || pivotClauses.isEmpty() || visibleRelations.isEmpty()) {
            return;
        }
        OracleParser.PivotClauseContext unpivot = null;
        for (OracleParser.PivotClauseContext clause : pivotClauses) {
            if (clause.UNPIVOT() != null) {
                unpivot = clause;
            }
        }
        if (unpivot == null) {
            return;
        }
        VisibleRelation baseRelation = visibleRelations.get(visibleRelations.size() - 1);

        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        List<String> valueColumns = unpivotValueColumnNames(unpivot.unpivotValueColumns());
        List<List<String>> sourceGroups = unpivotSourceGroups(unpivot.unpivotInItem());
        for (int i = 0; i < valueColumns.size(); i++) {
            List<ColumnRef> refs = new ArrayList<>();
            for (List<String> sourceGroup : sourceGroups) {
                if (i < sourceGroup.size()) {
                    refs.addAll(columnRefsForVisibleRelation(baseRelation, sourceGroup.get(i)));
                }
            }
            columns.put(valueColumns.get(i), refs);
        }
        columns.put(cleanIdentifier(unpivot.identifier()), new ArrayList<ColumnRef>());

        replaceVisibleRelationWithDerived(relationPrimary, columns, "$unpivot" + derivedColumnLineage.size());
        refreshColumnLineage();
    }

    private void replaceVisibleRelationWithDerived(
            OracleParser.RelationPrimaryContext relationPrimary,
            Map<String, List<ColumnRef>> columns,
            String derivedName) {
        VisibleRelation baseRelation = visibleRelations.get(visibleRelations.size() - 1);
        if (baseRelation.derivedName != null) {
            derivedReferences.remove(baseRelation.derivedName);
            removeDerivedAliasValues(baseRelation.derivedName);
        }
        derivedColumnLineage.put(derivedName, columns);
        visibleRelations.set(visibleRelations.size() - 1, VisibleRelation.derived(derivedName));
        derivedReferences.add(derivedName);
        derivedAliases.put(derivedName, derivedName);
        String alias = relationPrimary == null ? null : relationAlias(relationPrimary);
        if (alias != null) {
            derivedAliases.put(alias.toLowerCase(Locale.ROOT), derivedName);
        }
        refreshColumnLineage();
    }

    private void removeDerivedAliasValues(String derivedName) {
        List<String> aliases = new ArrayList<>();
        for (Map.Entry<String, String> entry : derivedAliases.entrySet()) {
            if (derivedName.equals(entry.getValue())) {
                aliases.add(entry.getKey());
            }
        }
        for (String alias : aliases) {
            derivedAliases.remove(alias);
        }
    }

    private List<ColumnRef> columnRefsForVisibleRelation(VisibleRelation relation, String columnName) {
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

    private static String pivotAggregateName(OracleParser.PivotAggregationContext ctx) {
        if (ctx.identifier() != null) {
            return cleanIdentifier(ctx.identifier());
        }
        return cleanIdentifier(ctx.functionName().getText());
    }

    private static List<SourceColumn> pivotForSourceColumns(OracleParser.PivotForExpressionContext ctx) {
        List<SourceColumn> columns = new ArrayList<>();
        if (ctx.identifierList() != null) {
            for (String name : identifierNames(ctx.identifierList())) {
                columns.add(new SourceColumn(null, name));
            }
        } else if (ctx.identifier() != null) {
            columns.add(new SourceColumn(null, cleanIdentifier(ctx.identifier())));
        }
        return columns;
    }

    private static String pivotValueName(OracleParser.PivotInItemContext ctx) {
        if (ctx.identifier() != null) {
            return cleanIdentifier(ctx.identifier());
        }
        if (ctx.expressionList() != null) {
            List<String> parts = new ArrayList<>();
            for (OracleParser.ExpressionContext expression : ctx.expressionList().expression()) {
                String part = cleanPivotGeneratedColumnPart(expression.getText());
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            return String.join("_", parts);
        }
        return cleanPivotGeneratedColumnPart(ctx.expression().getText());
    }

    private static String cleanPivotGeneratedColumnPart(String text) {
        String literal = stringLiteralValue(text);
        String value = literal == null ? text : literal;
        return cleanIdentifier(value).replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String stringLiteralValue(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.length() < 2 || !value.startsWith("'") || !value.endsWith("'")) {
            return null;
        }
        return value.substring(1, value.length() - 1).replace("''", "'");
    }

    private static List<String> unpivotValueColumnNames(OracleParser.UnpivotValueColumnsContext ctx) {
        if (ctx.identifierList() != null) {
            return identifierNames(ctx.identifierList());
        }
        List<String> names = new ArrayList<>();
        names.add(cleanIdentifier(ctx.identifier()));
        return names;
    }

    private static List<List<String>> unpivotSourceGroups(List<OracleParser.UnpivotInItemContext> items) {
        List<List<String>> groups = new ArrayList<>();
        for (OracleParser.UnpivotInItemContext item : items) {
            if (item.identifierList() != null) {
                groups.add(identifierNames(item.identifierList()));
            } else if (!item.identifier().isEmpty()) {
                List<String> group = new ArrayList<>();
                group.add(cleanIdentifier(item.identifier(0)));
                groups.add(group);
            }
        }
        return groups;
    }

    private static String relationAlias(OracleParser.RelationPrimaryContext ctx) {
        OracleParser.TableAliasContext aliasCtx = null;
        if (ctx instanceof OracleParser.TableNameContext) {
            aliasCtx = ((OracleParser.TableNameContext) ctx).tableAlias();
        } else if (ctx instanceof OracleParser.AliasedQueryContext) {
            aliasCtx = ((OracleParser.AliasedQueryContext) ctx).tableAlias();
        } else if (ctx instanceof OracleParser.AliasedRelationContext) {
            aliasCtx = ((OracleParser.AliasedRelationContext) ctx).tableAlias();
        }
        return tableAlias(aliasCtx);
    }

    @Override
    public Void visitSelectClause(OracleParser.SelectClauseContext ctx) {
        for (OracleParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof OracleParser.SelectExpressionContext) {
                Projection projection = projection((OracleParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSelectIntoClause(OracleParser.SelectIntoClauseContext ctx) {
        for (OracleParser.IdentifierContext id : ctx.identifierList().identifier()) {
            insertTargetColumns.add(cleanIdentifier(id));
        }
        refreshColumnLineage();
        return null;
    }

    @Override
    public Void visitWhereClause(OracleParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoinCriteria(OracleParser.JoinCriteriaContext ctx) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitStartWithClause(OracleParser.StartWithClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitConnectByClause(OracleParser.ConnectByClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitGroupByClause(OracleParser.GroupByClauseContext ctx) {
        for (OracleParser.GroupByItemContext item : ctx.groupByItem()) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(item));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitHavingClause(OracleParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitModelClause(OracleParser.ModelClauseContext ctx) {
        visitChildren(ctx);
        registerModelDerivedRelation(ctx);
        return null;
    }

    @Override
    public Void visitMatchRecognizeOption(OracleParser.MatchRecognizeOptionContext ctx) {
        if (ctx.PARTITION() != null && ctx.expressionList() != null) {
            for (OracleParser.ExpressionContext expression : ctx.expressionList().expression()) {
                addColumnUsages(ColumnUsageType.WINDOW_PARTITION_BY, sourceColumns(expression));
            }
        }
        if (ctx.ORDER() != null) {
            for (OracleParser.SortItemContext sortItem : ctx.sortItem()) {
                addColumnUsages(ColumnUsageType.WINDOW_ORDER_BY, sourceColumns(sortItem.expression()));
            }
        }
        for (OracleParser.MatchMeasureContext measure : ctx.matchMeasure()) {
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(measure.expression()));
        }
        for (OracleParser.MatchDefinitionContext definition : ctx.matchDefinition()) {
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(definition.expression()));
        }
        return visitChildren(ctx);
    }

    private void registerModelDerivedRelation(OracleParser.ModelClauseContext ctx) {
        if (ctx == null || visibleRelations.isEmpty()) {
            return;
        }
        VisibleRelation baseRelation = visibleRelations.get(visibleRelations.size() - 1);
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        if (baseRelation.table != null) {
            List<ColumnRef> wildcard = new ArrayList<>();
            wildcard.add(new ColumnRef(baseRelation.table, "*"));
            columns.put("*", wildcard);
        }
        for (OracleParser.ModelOptionContext option : ctx.modelOption()) {
            for (OracleParser.ModelMeasureContext measure : option.modelMeasure()) {
                if (measure.identifier() == null) {
                    continue;
                }
                List<ColumnRef> refs = columnRefs(sourceColumns(measure.expression()));
                if (refs != null && !refs.isEmpty()) {
                    columns.put(cleanIdentifier(measure.identifier()), refs);
                }
            }
        }
        if (columns.isEmpty()) {
            return;
        }
        replaceVisibleRelationWithDerived(null, columns, "$model" + derivedColumnLineage.size());
    }

    @Override
    public Void visitModelOption(OracleParser.ModelOptionContext ctx) {
        if (ctx.expressionList() != null) {
            for (OracleParser.ExpressionContext expression : ctx.expressionList().expression()) {
                addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(expression));
            }
        }
        for (OracleParser.ModelMeasureContext measure : ctx.modelMeasure()) {
            addColumnUsages(ColumnUsageType.TABLE_MODEL, sourceColumns(measure.expression()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWindowSpec(OracleParser.WindowSpecContext ctx) {
        if (ctx.expressionList() != null) {
            for (OracleParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (OracleParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitKeepClause(OracleParser.KeepClauseContext ctx) {
        for (OracleParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWithinGroupClause(OracleParser.WithinGroupClauseContext ctx) {
        for (OracleParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWindowFrameBound(OracleParser.WindowFrameBoundContext ctx) {
        if (ctx.expression() != null) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(ctx.expression())));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(OracleParser.QueryOrganizationContext ctx) {
        for (OracleParser.SortItemContext sortItem : ctx.sortItem()) {
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

    private boolean isDualTable(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && "dual".equalsIgnoreCase(table.getName());
    }

    private void addInputTable(TableRef table, boolean visibleRelation) {
        addInputTable(table, null, visibleRelation);
    }

    private void addInputTable(TableRef table, OracleParser.TableAliasContext aliasCtx, boolean visibleRelation) {
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

    private void addDerivedReference(String rawName, OracleParser.TableAliasContext aliasCtx) {
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

    private void registerDerivedRelation(String name, OracleParser.QueryContext query, List<String> columnAliases) {
        registerDerivedRelation(name, query, columnAliases, false);
    }

    private void registerDerivedRelation(
            String name,
            OracleParser.QueryContext query,
            List<String> columnAliases,
            boolean inheritOuterScope) {
        LineageResult relationResult = new LineageResult();
        OracleLineageVisitor relationVisitor = new OracleLineageVisitor(relationResult);
        relationVisitor.cteNames.addAll(cteNames);
        relationVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        relationVisitor.derivedAliases.putAll(derivedAliases);
        if (inheritOuterScope) {
            relationVisitor.inputTables.addAll(inputTables);
            relationVisitor.tableAliases.putAll(tableAliases);
            relationVisitor.visibleRelations.addAll(visibleRelations);
            relationVisitor.visibleRelationCount = visibleRelationCount;
        }
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

    private LineageResult lineageForQueryTerm(OracleParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        OracleLineageVisitor queryVisitor = new OracleLineageVisitor(queryResult);
        queryVisitor.cteNames.addAll(cteNames);
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

    private void addUsingColumnUsages(OracleParser.IdentifierListContext ctx) {
        addUsingColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(OracleParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void addUsingColumnUsages(OracleParser.IdentifierListContext ctx, int relationStart) {
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

    private List<ColumnLineage> readAssignments(OracleParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (OracleParser.AssignmentContext assignment : ctx.assignment()) {
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

    private List<ColumnLineage> readMergeInsertValues(OracleParser.MergeNotMatchedActionContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx.identifierList() == null || ctx.expressionList() == null) {
            return lineages;
        }
        List<String> targetColumns = identifierNames(ctx.identifierList());
        List<OracleParser.ExpressionContext> expressions = ctx.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            OracleParser.ExpressionContext expression = expressions.get(i);
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

    private List<ColumnLineage> readMultiTableInsertValues(
            OracleParser.MultiTableInsertClauseContext ctx,
            TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (ctx.expressionList() == null) {
            return lineages;
        }
        List<OracleParser.ExpressionContext> expressions = ctx.expressionList().expression();
        List<String> targetColumns = ctx.targetColumnList() == null
                ? defaultTargetColumns(expressions.size())
                : identifierNames(ctx.targetColumnList());
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int i = 0; i < count; i++) {
            OracleParser.ExpressionContext expression = expressions.get(i);
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

    private static List<String> defaultTargetColumns(int count) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns.add("c" + (i + 1));
        }
        return columns;
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
        if (tree instanceof OracleParser.ScalarSubqueryContext) {
            OracleParser.ScalarSubqueryContext subquery = (OracleParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            LineageModelUtils.mergeColumnUsages(result, subResult);
            return;
        }
        if (tree instanceof OracleParser.ExistsExprContext) {
            OracleParser.ExistsExprContext exists = (OracleParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            LineageModelUtils.mergeColumnUsages(result, subResult);
            return;
        }
        if (tree instanceof OracleParser.PredicateContext) {
            OracleParser.PredicateContext predicate = (OracleParser.PredicateContext) tree;
            if (predicate.query() != null) {
                LineageResult subResult = lineageForQuery(predicate.query());
                inputTables.addAll(subResult.getInputTables());
                LineageModelUtils.mergeColumnUsages(result, subResult);
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSubqueryInputs(tree.getChild(i));
        }
    }

    private boolean containsSubquery(ParseTree tree) {
        if (tree instanceof OracleParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof OracleParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof OracleParser.PredicateContext) {
            OracleParser.PredicateContext predicate = (OracleParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(OracleParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        OracleLineageVisitor queryVisitor = new OracleLineageVisitor(queryResult);
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

    private void collectTopLevelQueryProjections(OracleParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        OracleParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        for (OracleParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof OracleParser.SelectExpressionContext) {
                Projection projection = projection((OracleParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            }
        }
    }

    private static OracleParser.QuerySpecificationContext topLevelQuerySpecification(OracleParser.QueryContext query) {
        if (!(query.queryTerm() instanceof OracleParser.QueryTermDefaultContext)) {
            return null;
        }
        OracleParser.QueryPrimaryContext primary =
                ((OracleParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof OracleParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((OracleParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(OracleParser.SelectExpressionContext ctx) {
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

    private void addScalarSubquerySourceColumns(OracleParser.QueryContext query, Set<SourceColumn> columns) {
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(OracleParser.QueryContext query) {
        OracleLineageVisitor queryVisitor = new OracleLineageVisitor(new LineageResult());
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        OracleParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (OracleParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof OracleParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((OracleParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof OracleParser.ColumnReferenceContext) {
            OracleParser.ColumnReferenceContext colRef = (OracleParser.ColumnReferenceContext) tree;
            String column = cleanIdentifier(colRef.identifier());
            if (!isOraclePseudocolumn(column)) {
                columns.add(new SourceColumn(null, column));
            }
            return;
        }
        if (tree instanceof OracleParser.DereferenceContext) {
            OracleParser.DereferenceContext deref = (OracleParser.DereferenceContext) tree;
            List<String> parts = collectDereferenceParts(deref);
            if (parts.size() >= 2) {
                String qualifier = parts.get(parts.size() - 2);
                String name = parts.get(parts.size() - 1);
                if (!isOraclePseudocolumn(name)) {
                    columns.add(new SourceColumn(qualifier, name));
                }
            } else if (parts.size() == 1) {
                String column = parts.get(0);
                if (!isOraclePseudocolumn(column)) {
                    columns.add(new SourceColumn(null, column));
                }
            }
            return;
        }
        if (tree instanceof OracleParser.ScalarSubqueryContext) {
            OracleParser.ScalarSubqueryContext subquery = (OracleParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof OracleParser.ExistsExprContext) {
            OracleParser.ExistsExprContext exists = (OracleParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof OracleParser.PredicateContext) {
            OracleParser.PredicateContext predicate = (OracleParser.PredicateContext) tree;
            if (predicate.query() != null) {
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

    private List<String> collectDereferenceParts(OracleParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof OracleParser.DereferenceContext) {
            OracleParser.DereferenceContext deref = (OracleParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof OracleParser.ColumnReferenceContext) {
            OracleParser.ColumnReferenceContext colRef = (OracleParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    private static boolean isOraclePseudocolumn(String column) {
        String normalized = column.toLowerCase(Locale.ROOT);
        return "rownum".equals(normalized)
                || "rowid".equals(normalized)
                || "ora_rowscn".equals(normalized)
                || "level".equals(normalized)
                || "connect_by_isleaf".equals(normalized)
                || "connect_by_iscycle".equals(normalized);
    }

    // ============ Utility ============

    private static TableRef tableRef(OracleParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static TableRef tableRef(OracleParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (OracleParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static List<String> identifierParts(OracleParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (OracleParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String tableAlias(OracleParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> cteColumnAliases(OracleParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (OracleParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(OracleParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (OracleParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static List<String> identifierNames(OracleParser.TargetColumnListContext ctx) {
        List<String> names = new ArrayList<>();
        for (OracleParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(OracleParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static String cleanIdentifier(String text) {
        String value = text.trim();
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
