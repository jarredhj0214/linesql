package io.github.linesql.dialect.mysql;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsageType;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.util.LineageModelUtils;
import io.github.linesql.dialect.mysql.antlr.MySqlParser;
import io.github.linesql.dialect.mysql.antlr.MySqlParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class MySqlLineageVisitor extends MySqlParserBaseVisitor<Void> {
    private final LineageResult result;
    private final Set<TableRef> inputTables = new LinkedHashSet<>();
    private final Set<TableRef> outputTables = new LinkedHashSet<>();
    private final Map<String, TableRef> tableAliases = new LinkedHashMap<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
    private final Map<String, String> derivedAliases = new LinkedHashMap<>();
    private final Set<String> derivedReferences = new LinkedHashSet<>();
    private final Set<String> insertRowAliases = new LinkedHashSet<>();
    private final Map<String, Map<String, String>> insertRowAliasColumns = new LinkedHashMap<>();
    private final Map<String, MySqlParser.WindowSpecContext> namedWindows = new LinkedHashMap<>();
    private final List<Projection> projections = new ArrayList<>();
    private final List<String> insertTargetColumns = new ArrayList<>();
    private final List<VisibleRelation> visibleRelations = new ArrayList<>();
    private final List<PendingColumnUsage> pendingColumnUsages = new ArrayList<>();
    private TableRef currentDmlTarget;
    private int visibleRelationCount;
    private boolean suppressColumnLineage;
    private ParseContext context;

    MySqlLineageVisitor(LineageResult result) {
        this.result = result;
    }

    void setContext(ParseContext context) {
        this.context = context;
    }

    @Override
    public Void visitStatementDefault(MySqlParser.StatementDefaultContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableStmt(MySqlParser.TableStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableStatement(MySqlParser.TableStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return visit(ctx.queryOrganization());
    }

    @Override
    public Void visitValuesStmt(MySqlParser.ValuesStmtContext ctx) {
        result.setStatementType(StatementType.SELECT);
        return null;
    }

    @Override
    public Void visitHandlerStmt(MySqlParser.HandlerStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitHandlerOpenStatement(MySqlParser.HandlerOpenStatementContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitHandlerCloseStatement(MySqlParser.HandlerCloseStatementContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitHandlerReadStatement(MySqlParser.HandlerReadStatementContext ctx) {
        result.setStatementType(StatementType.SELECT);
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        if (ctx.handlerReadTail().whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.handlerReadTail().whereClause().expression()));
        }
        return null;
    }

    @Override
    public Void visitInsertStmt(MySqlParser.InsertStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStatement(MySqlParser.InsertStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (MySqlParser.IdentifierContext id : ctx.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        } else if (ctx.assignmentList() != null) {
            for (MySqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
                List<String> parts = identifierParts(assignment.multipartIdentifier());
                if (!parts.isEmpty()) {
                    insertTargetColumns.add(parts.get(parts.size() - 1));
                }
            }
        }
        registerInsertRowAlias(ctx.insertRowAlias());
        if (ctx.query() != null) {
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
            if (ctx.onDuplicateKeyUpdate() != null) {
                TableRef previousDmlTarget = currentDmlTarget;
                currentDmlTarget = target;
                List<ColumnLineage> lineages = new ArrayList<>(result.getColumnLineage());
                lineages.addAll(readDuplicateKeyAssignments(
                        ctx.onDuplicateKeyUpdate().assignmentList(),
                        target,
                        insertedSourcesByColumn()));
                result.setColumnLineage(lineages);
                currentDmlTarget = previousDmlTarget;
                projections.clear();
            }
        } else if (ctx.tableStatement() != null) {
            visit(ctx.tableStatement());
            result.setStatementType(StatementType.INSERT);
        } else if (ctx.assignmentList() != null) {
            collectSubqueryInputs(ctx.assignmentList());
            List<ColumnLineage> lineages = readAssignments(ctx.assignmentList(), target);
            if (ctx.onDuplicateKeyUpdate() != null) {
                TableRef previousDmlTarget = currentDmlTarget;
                currentDmlTarget = target;
                lineages.addAll(readDuplicateKeyAssignments(
                        ctx.onDuplicateKeyUpdate().assignmentList(),
                        target,
                        insertedSourcesByColumn(lineages)));
                currentDmlTarget = previousDmlTarget;
            }
            result.setColumnLineage(lineages);
        } else if (ctx.VALUES() != null || ctx.VALUE() != null) {
            List<ColumnLineage> lineages = readInsertedValueColumns(ctx.valuesClause(), target);
            if (ctx.onDuplicateKeyUpdate() != null) {
                TableRef previousDmlTarget = currentDmlTarget;
                currentDmlTarget = target;
                lineages.addAll(readDuplicateKeyAssignments(
                        ctx.onDuplicateKeyUpdate().assignmentList(),
                        target,
                        insertedSourcesByColumn(lineages)));
                currentDmlTarget = previousDmlTarget;
            }
            result.setColumnLineage(lineages);
        } else if (ctx.onDuplicateKeyUpdate() != null) {
            TableRef previousDmlTarget = currentDmlTarget;
            currentDmlTarget = target;
            result.setColumnLineage(readDuplicateKeyAssignments(
                    ctx.onDuplicateKeyUpdate().assignmentList(),
                    target,
                    new LinkedHashMap<String, List<ColumnRef>>()));
            currentDmlTarget = previousDmlTarget;
        } else {
            suppressColumnLineage = true;
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void registerInsertRowAlias(MySqlParser.InsertRowAliasContext ctx) {
        if (ctx == null) {
            return;
        }
        String alias = cleanIdentifier(ctx.identifier()).toLowerCase(Locale.ROOT);
        insertRowAliases.add(alias);
        Map<String, String> aliasColumns = new LinkedHashMap<>();
        if (ctx.identifierList() != null) {
            List<MySqlParser.IdentifierContext> identifiers = ctx.identifierList().identifier();
            for (int i = 0; i < identifiers.size() && i < insertTargetColumns.size(); i++) {
                aliasColumns.put(cleanIdentifier(identifiers.get(i)).toLowerCase(Locale.ROOT), insertTargetColumns.get(i));
            }
        } else {
            for (String targetColumn : insertTargetColumns) {
                aliasColumns.put(targetColumn.toLowerCase(Locale.ROOT), targetColumn);
            }
        }
        insertRowAliasColumns.put(alias, aliasColumns);
    }

    @Override
    public Void visitReplaceStmt(MySqlParser.ReplaceStmtContext ctx) {
        result.setStatementType(StatementType.INSERT);
        return visitChildren(ctx);
    }

    @Override
    public Void visitReplaceStatement(MySqlParser.ReplaceStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.columnList != null) {
            for (MySqlParser.IdentifierContext id : ctx.columnList.identifier()) {
                insertTargetColumns.add(cleanIdentifier(id));
            }
        }
        if (ctx.query() != null) {
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
        } else if (ctx.assignmentList() != null) {
            collectSubqueryInputs(ctx.assignmentList());
            result.setColumnLineage(readAssignments(ctx.assignmentList(), target));
        } else if (ctx.VALUES() != null || ctx.VALUE() != null) {
            result.setColumnLineage(readInsertedValueColumns(ctx.valuesClause(), target));
        } else {
            suppressColumnLineage = true;
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitLoadDataStmt(MySqlParser.LoadDataStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadXmlStmt(MySqlParser.LoadXmlStmtContext ctx) {
        result.setStatementType(StatementType.LOAD_DATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLoadDataStatement(MySqlParser.LoadDataStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        for (MySqlParser.LoadDataOptionContext option : ctx.loadDataOption()) {
            if (option.loadColumnList() != null) {
                lineages.addAll(readLoadDataColumns(option.loadColumnList(), target));
            }
            if (option.assignmentList() != null) {
                collectSubqueryInputs(option.assignmentList());
                lineages.addAll(readAssignments(option.assignmentList(), target));
            }
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitLoadXmlStatement(MySqlParser.LoadXmlStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        List<ColumnLineage> lineages = new ArrayList<>();
        for (MySqlParser.LoadXmlOptionContext option : ctx.loadXmlOption()) {
            if (option.loadColumnList() != null) {
                lineages.addAll(readLoadDataColumns(option.loadColumnList(), target));
            }
            if (option.assignmentList() != null) {
                collectSubqueryInputs(option.assignmentList());
                lineages.addAll(readAssignments(option.assignmentList(), target));
            }
        }
        result.setColumnLineage(lineages);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private List<ColumnLineage> readLoadDataColumns(MySqlParser.LoadColumnListContext ctx, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (MySqlParser.LoadColumnRefContext columnRef : ctx.loadColumnRef()) {
            if (columnRef.identifier() == null) {
                continue;
            }
            String columnName = cleanIdentifier(columnRef.identifier());
            lineages.add(LineageModelUtils.columnLineage(
                    target,
                    columnName,
                    new ArrayList<ColumnRef>(),
                    null));
        }
        return lineages;
    }

    private List<ColumnLineage> readInsertedValueColumns(List<MySqlParser.ValuesClauseContext> rows, TableRef target) {
        List<ColumnLineage> lineages = new ArrayList<>();
        if (insertTargetColumns.isEmpty()) {
            return lineages;
        }
        List<List<ColumnRef>> sourcesByPosition = new ArrayList<>();
        for (int i = 0; i < insertTargetColumns.size(); i++) {
            sourcesByPosition.add(new ArrayList<ColumnRef>());
        }
        for (MySqlParser.ValuesClauseContext row : rows) {
            if (row.expressionList() == null) {
                continue;
            }
            List<MySqlParser.ExpressionContext> expressions = row.expressionList().expression();
            for (int i = 0; i < expressions.size() && i < sourcesByPosition.size(); i++) {
                collectSubqueryInputs(expressions.get(i));
                List<ColumnRef> sources = resolveSources(sourceColumns(expressions.get(i)));
                if (sources != null) {
                    sourcesByPosition.get(i).addAll(sources);
                }
            }
        }
        for (int i = 0; i < insertTargetColumns.size(); i++) {
            lineages.add(LineageModelUtils.columnLineage(
                    target,
                    insertTargetColumns.get(i),
                    deduplicateColumns(sourcesByPosition.get(i)),
                    null));
        }
        return lineages;
    }

    @Override
    public Void visitUpdateStmt(MySqlParser.UpdateStmtContext ctx) {
        result.setStatementType(StatementType.UPDATE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(MySqlParser.UpdateStatementContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        visitRelationListForInputs(ctx.relationList());
        TableRef defaultTarget = firstTableInRelationList(ctx.relationList());
        if (defaultTarget != null) {
            outputTables.add(defaultTarget);
        }
        addAssignmentTargetTables(ctx.assignmentList());
        currentDmlTarget = firstOutputTable();
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectDmlOrganization(ctx.dmlOrganization());
        collectSubqueryInputs(ctx.assignmentList());
        List<ColumnLineage> assignments = readAssignments(ctx.assignmentList(), firstOutputTable());
        result.setColumnLineage(assignments);
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private TableRef firstTableInRelationList(MySqlParser.RelationListContext ctx) {
        if (ctx == null || ctx.relation().isEmpty()) {
            return null;
        }
        MySqlParser.RelationPrimaryContext primary = ctx.relation(0).relationPrimary();
        if (!(primary instanceof MySqlParser.TableNameContext)) {
            return null;
        }
        TableRef table = tableRef(((MySqlParser.TableNameContext) primary).multipartIdentifier());
        return isCteReference(table) ? null : table;
    }

    private void addAssignmentTargetTables(MySqlParser.AssignmentListContext ctx) {
        for (MySqlParser.AssignmentContext assignment : ctx.assignment()) {
            List<String> parts = identifierParts(assignment.multipartIdentifier());
            if (parts.size() < 2) {
                continue;
            }
            String qualifier = parts.get(parts.size() - 2).toLowerCase(Locale.ROOT);
            TableRef resolved = tableAliases.get(qualifier);
            if (resolved != null) {
                outputTables.add(resolved);
            }
        }
    }

    private void visitRelationForUpdate(MySqlParser.RelationContext ctx) {
        visitRelationPrimaryForDml(ctx.relationPrimary(), true);
        for (MySqlParser.JoinRelationContext join : ctx.joinRelation()) {
            visitRelationPrimaryForDml(join.relationPrimary(), false);
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria());
            }
        }
    }

    @Override
    public Void visitDeleteStmt(MySqlParser.DeleteStmtContext ctx) {
        result.setStatementType(StatementType.DELETE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeleteFrom(MySqlParser.DeleteFromContext ctx) {
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
        collectDmlOrganization(ctx.dmlOrganization());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteAlias(MySqlParser.DeleteAliasContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        visitRelationListForInputs(ctx.relationList());
        addDeleteTargets(ctx.deleteTargetList());
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectDmlOrganization(ctx.dmlOrganization());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDeleteAliasUsing(MySqlParser.DeleteAliasUsingContext ctx) {
        if (ctx.ctes() != null) {
            visit(ctx.ctes());
        }
        visitRelationListForInputs(ctx.relationList());
        addDeleteTargets(ctx.deleteTargetList());
        if (ctx.whereClause() != null) {
            addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.whereClause().expression()));
            collectSubqueryInputs(ctx.whereClause());
        }
        collectDmlOrganization(ctx.dmlOrganization());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addDeleteTargets(MySqlParser.DeleteTargetListContext ctx) {
        for (MySqlParser.DeleteTargetContext deleteTargetContext : ctx.deleteTarget()) {
            List<String> parts = identifierParts(deleteTargetContext.multipartIdentifier());
            String deleteAlias = parts.get(parts.size() - 1).toLowerCase(Locale.ROOT);
            TableRef target = tableAliases.get(deleteAlias);
            if (target != null) {
                outputTables.add(target);
                if (currentDmlTarget == null) {
                    currentDmlTarget = target;
                }
            } else if (parts.size() > 1) {
                outputTables.add(tableRef(deleteTargetContext.multipartIdentifier()));
            }
        }
    }

    private void visitRelationListForInputs(MySqlParser.RelationListContext ctx) {
        for (MySqlParser.RelationContext relation : ctx.relation()) {
            visitRelationPrimaryForDml(relation.relationPrimary(), false);
            for (MySqlParser.JoinRelationContext join : relation.joinRelation()) {
                visitRelationPrimaryForDml(join.relationPrimary(), false);
                if (join.joinCriteria() != null) {
                    collectJoinColumnUsages(join.joinCriteria());
                }
            }
        }
    }

    private void visitRelationPrimaryForDml(MySqlParser.RelationPrimaryContext primary, boolean output) {
        if (primary instanceof MySqlParser.TableNameContext) {
            MySqlParser.TableNameContext tableName = (MySqlParser.TableNameContext) primary;
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
        if (primary instanceof MySqlParser.AliasedQueryContext) {
            MySqlParser.AliasedQueryContext query = (MySqlParser.AliasedQueryContext) primary;
            String alias = tableAlias(query.tableAlias());
            String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
            registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), query.query(), tableAliasColumnAliases(query.tableAlias()));
            addDerivedReference(relationName, query.tableAlias());
        }
    }

    @Override
    public Void visitCreateTableStmt(MySqlParser.CreateTableStmtContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStmt(MySqlParser.CreateIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateIndexStatement(MySqlParser.CreateIndexStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitCreateDatabaseStmt(MySqlParser.CreateDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_SCHEMA);
        return null;
    }

    @Override
    public Void visitDropDatabaseStmt(MySqlParser.DropDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.DROP_SCHEMA);
        return null;
    }

    @Override
    public Void visitAlterDatabaseStmt(MySqlParser.AlterDatabaseStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateTablespaceStmt(MySqlParser.CreateTablespaceStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDropTablespaceStmt(MySqlParser.DropTablespaceStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateTableStatement(MySqlParser.CreateTableStatementContext ctx) {
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
            addCreateTableTargetColumns(ctx.tableElementList());
            visit(ctx.query());
            refreshColumnLineage();
            retargetColumnLineage(target);
            if (ctx.TEMPORARY() != null) {
                registerTemporaryRelation(target);
            }
        } else {
            result.setStatementType(StatementType.CREATE_TABLE);
        }
        addCreateTableReferencedTables(ctx.tableElementList());
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addCreateTableReferencedTables(MySqlParser.TableElementListContext tableElementList) {
        if (tableElementList == null) {
            return;
        }
        for (MySqlParser.TableElementContext element : tableElementList.tableElement()) {
            if (element.tableConstraint() != null && element.tableConstraint().referenceDefinition() != null) {
                inputTables.add(tableRef(element.tableConstraint().referenceDefinition().multipartIdentifier()));
            }
            for (MySqlParser.ColumnConstraintContext columnConstraint : element.columnConstraint()) {
                if (columnConstraint.referenceDefinition() != null) {
                    inputTables.add(tableRef(columnConstraint.referenceDefinition().multipartIdentifier()));
                }
            }
        }
    }

    private void addCreateTableTargetColumns(MySqlParser.TableElementListContext tableElementList) {
        if (tableElementList == null) {
            return;
        }
        for (MySqlParser.TableElementContext element : tableElementList.tableElement()) {
            if (element.getChildCount() >= 2
                    && element.getChild(0) instanceof MySqlParser.IdentifierContext
                    && element.getChild(1) instanceof MySqlParser.DataTypeContext) {
                insertTargetColumns.add(cleanIdentifier((MySqlParser.IdentifierContext) element.getChild(0)));
            }
        }
    }

    @Override
    public Void visitCreateViewStmt(MySqlParser.CreateViewStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateViewStatement(MySqlParser.CreateViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (MySqlParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
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
    public Void visitDropTableStmt(MySqlParser.DropTableStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropIndexStmt(MySqlParser.DropIndexStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropIndexStatement(MySqlParser.DropIndexStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropTableStatement(MySqlParser.DropTableStatementContext ctx) {
        for (MySqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            TableRef table = tableRef(identifier);
            outputTables.add(table);
            unregisterTemporaryRelation(table);
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropViewStmt(MySqlParser.DropViewStmtContext ctx) {
        result.setStatementType(StatementType.DROP_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDropViewStatement(MySqlParser.DropViewStatementContext ctx) {
        for (MySqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            outputTables.add(tableRef(identifier));
        }
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitDropRoutineStmt(MySqlParser.DropRoutineStmtContext ctx) {
        result.setStatementType(StatementType.DROP_ROUTINE);
        return null;
    }

    @Override
    public Void visitDropTriggerStmt(MySqlParser.DropTriggerStmtContext ctx) {
        result.setStatementType(StatementType.DROP_TRIGGER);
        return null;
    }

    @Override
    public Void visitDropEventStmt(MySqlParser.DropEventStmtContext ctx) {
        result.setStatementType(StatementType.DROP_EVENT);
        return null;
    }

    @Override
    public Void visitResourceGroupStmt(MySqlParser.ResourceGroupStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitServerStmt(MySqlParser.ServerStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitPluginStmt(MySqlParser.PluginStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitTruncateTableStmt(MySqlParser.TruncateTableStmtContext ctx) {
        result.setStatementType(StatementType.TRUNCATE_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTruncateTableStatement(MySqlParser.TruncateTableStatementContext ctx) {
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitRenameTableStmt(MySqlParser.RenameTableStmtContext ctx) {
        result.setStatementType(StatementType.RENAME_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitRenameTableStatement(MySqlParser.RenameTableStatementContext ctx) {
        for (MySqlParser.RenameTablePairContext pair : ctx.renameTablePair()) {
            inputTables.add(tableRef(pair.source));
            outputTables.add(tableRef(pair.target));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableStmt(MySqlParser.AlterTableStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterTableRename(MySqlParser.AlterTableRenameContext ctx) {
        List<MySqlParser.MultipartIdentifierContext> ids = ctx.multipartIdentifier();
        result.setStatementType(StatementType.RENAME_TABLE);
        inputTables.add(tableRef(ids.get(0)));
        outputTables.add(tableRef(ids.get(1)));
        result.setInputTables(new ArrayList<>(inputTables));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableAddColumn(MySqlParser.AlterTableAddColumnContext ctx) {
        addReferencedTables(ctx);
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    @Override
    public Void visitAlterTableOther(MySqlParser.AlterTableOtherContext ctx) {
        if (ctx.alterTableActionList() != null) {
            for (MySqlParser.AlterTableActionContext action : ctx.alterTableActionList().alterTableAction()) {
                addAlterTableReferencedTable(action);
                if (action.EXCHANGE() != null && action.multipartIdentifier() != null) {
                    TableRef exchangeTable = tableRef(action.multipartIdentifier());
                    inputTables.add(exchangeTable);
                    outputTables.add(exchangeTable);
                    result.setInputTables(new ArrayList<>(inputTables));
                }
            }
        }
        outputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setOutputTables(new ArrayList<>(outputTables));
        return null;
    }

    private void addAlterTableReferencedTable(MySqlParser.AlterTableActionContext action) {
        if (action.referenceDefinition() != null) {
            inputTables.add(tableRef(action.referenceDefinition().multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        addReferencedTables(action);
    }

    private void addReferencedTables(ParseTree tree) {
        if (tree instanceof MySqlParser.ReferenceDefinitionContext) {
            MySqlParser.ReferenceDefinitionContext reference = (MySqlParser.ReferenceDefinitionContext) tree;
            inputTables.add(tableRef(reference.multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
            return;
        }
        if (tree instanceof MySqlParser.TableConstraintContext) {
            MySqlParser.TableConstraintContext constraint = (MySqlParser.TableConstraintContext) tree;
            if (constraint.referenceDefinition() != null) {
                inputTables.add(tableRef(constraint.referenceDefinition().multipartIdentifier()));
                result.setInputTables(new ArrayList<>(inputTables));
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            addReferencedTables(tree.getChild(i));
        }
    }

    @Override
    public Void visitAlterViewStmt(MySqlParser.AlterViewStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_VIEW);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAlterViewStatement(MySqlParser.AlterViewStatementContext ctx) {
        TableRef target = tableRef(ctx.multipartIdentifier());
        outputTables.add(target);
        if (ctx.viewColumnList != null) {
            for (MySqlParser.IdentifierContext id : ctx.viewColumnList.identifier()) {
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
    public Void visitAlterRoutineStmt(MySqlParser.AlterRoutineStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_ROUTINE);
        return null;
    }

    @Override
    public Void visitAlterEventStmt(MySqlParser.AlterEventStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_EVENT);
        return null;
    }

    @Override
    public Void visitAnalyzeTableStmt(MySqlParser.AnalyzeTableStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitAnalyzeTableStatement(MySqlParser.AnalyzeTableStatementContext ctx) {
        for (MySqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            inputTables.add(tableRef(identifier));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitTableMaintenanceStmt(MySqlParser.TableMaintenanceStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableMaintenanceStatement(MySqlParser.TableMaintenanceStatementContext ctx) {
        for (MySqlParser.MultipartIdentifierContext identifier : ctx.multipartIdentifierList().multipartIdentifier()) {
            inputTables.add(tableRef(identifier));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitExplainStmt(MySqlParser.ExplainStmtContext ctx) {
        return visit(ctx.explainStatement());
    }

    @Override
    public Void visitExplainStatement(MySqlParser.ExplainStatementContext ctx) {
        if (ctx.statement() != null) {
            return visit(ctx.statement());
        }
        if (ctx.query() != null) {
            result.setStatementType(StatementType.SELECT);
            return visit(ctx.query());
        }
        if (ctx.insertStatement() != null) {
            return visit(ctx.insertStatement());
        }
        if (ctx.replaceStatement() != null) {
            return visit(ctx.replaceStatement());
        }
        if (ctx.updateStatement() != null) {
            return visit(ctx.updateStatement());
        }
        if (ctx.deleteStatement() != null) {
            return visit(ctx.deleteStatement());
        }
        if (ctx.multipartIdentifier() != null) {
            result.setStatementType(StatementType.READ_METADATA);
            inputTables.add(tableRef(ctx.multipartIdentifier()));
            result.setInputTables(new ArrayList<>(inputTables));
            return null;
        }
        result.setStatementType(StatementType.READ_METADATA);
        return null;
    }

    @Override
    public Void visitUseStmt(MySqlParser.UseStmtContext ctx) {
        result.setStatementType(StatementType.USE_SCHEMA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitUseStatement(MySqlParser.UseStatementContext ctx) {
        return null;
    }

    @Override
    public Void visitLockTablesStmt(MySqlParser.LockTablesStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitLockTablesStatement(MySqlParser.LockTablesStatementContext ctx) {
        for (MySqlParser.LockTableContext lockTable : ctx.lockTable()) {
            inputTables.add(tableRef(lockTable.multipartIdentifier()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitUnlockTablesStmt(MySqlParser.UnlockTablesStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitSetStmt(MySqlParser.SetStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return visit(ctx.setStatement());
    }

    @Override
    public Void visitSetStatement(MySqlParser.SetStatementContext ctx) {
        for (MySqlParser.SetElementContext element : ctx.setElement()) {
            collectSubqueryInputs(element);
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitTransactionStmt(MySqlParser.TransactionStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDoStmt(MySqlParser.DoStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        collectSubqueryInputs(ctx.doStatement());
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitCallStmt(MySqlParser.CallStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        collectSubqueryInputs(ctx.callStatement());
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitPrepareStmt(MySqlParser.PrepareStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitExecuteStmt(MySqlParser.ExecuteStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitDeallocatePrepareStmt(MySqlParser.DeallocatePrepareStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitCreateRoutineStmt(MySqlParser.CreateRoutineStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_ROUTINE);
        return null;
    }

    @Override
    public Void visitCreateTriggerStmt(MySqlParser.CreateTriggerStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_TRIGGER);
        return visit(ctx.createTriggerStatement());
    }

    @Override
    public Void visitCreateTriggerStatement(MySqlParser.CreateTriggerStatementContext ctx) {
        List<MySqlParser.MultipartIdentifierContext> identifiers = ctx.multipartIdentifier();
        if (identifiers.size() > 1) {
            outputTables.add(tableRef(identifiers.get(1)));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitCreateEventStmt(MySqlParser.CreateEventStmtContext ctx) {
        result.setStatementType(StatementType.CREATE_EVENT);
        return visit(ctx.createEventStatement());
    }

    @Override
    public Void visitCreateEventStatement(MySqlParser.CreateEventStatementContext ctx) {
        if (ctx.eventBodyStatement() != null) {
            visit(ctx.eventBodyStatement());
            result.setStatementType(StatementType.CREATE_EVENT);
            result.setInputTables(new ArrayList<>(inputTables));
            result.setOutputTables(new ArrayList<>(outputTables));
        }
        return null;
    }

    @Override
    public Void visitAccountStmt(MySqlParser.AccountStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitAdminStmt(MySqlParser.AdminStmtContext ctx) {
        result.setStatementType(StatementType.CONTROL);
        return null;
    }

    @Override
    public Void visitShowStmt(MySqlParser.ShowStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitShowStatement(MySqlParser.ShowStatementContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof MySqlParser.ShowCreateStatementContext
                    || child instanceof MySqlParser.ShowColumnsStatementContext
                    || child instanceof MySqlParser.ShowIndexStatementContext
                    || child instanceof MySqlParser.ShowTableStatusStatementContext) {
                return visit(child);
            }
            if (child instanceof MySqlParser.MultipartIdentifierContext) {
                inputTables.add(tableRef((MySqlParser.MultipartIdentifierContext) child));
                break;
            }
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitShowCreateStatement(MySqlParser.ShowCreateStatementContext ctx) {
        if (ctx.multipartIdentifier() != null && isShowCreateTableLike(ctx)) {
            inputTables.add(tableRef(ctx.multipartIdentifier()));
        }
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    private boolean isShowCreateTableLike(MySqlParser.ShowCreateStatementContext ctx) {
        if (ctx.getChildCount() < 3) {
            return false;
        }
        String objectType = ctx.getChild(2).getText();
        return "table".equalsIgnoreCase(objectType) || "view".equalsIgnoreCase(objectType);
    }

    @Override
    public Void visitShowColumnsStatement(MySqlParser.ShowColumnsStatementContext ctx) {
        inputTables.add(showTableRef(ctx.multipartIdentifier(), ctx.showFromSchema()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitShowIndexStatement(MySqlParser.ShowIndexStatementContext ctx) {
        inputTables.add(showTableRef(ctx.multipartIdentifier(), ctx.showFromSchema()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitShowTableStatusStatement(MySqlParser.ShowTableStatusStatementContext ctx) {
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitDescribeStmt(MySqlParser.DescribeStmtContext ctx) {
        result.setStatementType(StatementType.READ_METADATA);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDescribeStatement(MySqlParser.DescribeStatementContext ctx) {
        inputTables.add(tableRef(ctx.multipartIdentifier()));
        result.setInputTables(new ArrayList<>(inputTables));
        return null;
    }

    @Override
    public Void visitCommentStmt(MySqlParser.CommentStmtContext ctx) {
        result.setStatementType(StatementType.ALTER_TABLE);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCommentStatement(MySqlParser.CommentStatementContext ctx) {
        MySqlParser.MultipartIdentifierContext id = ctx.multipartIdentifier();
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
    public Void visitCtes(MySqlParser.CtesContext ctx) {
        for (MySqlParser.NamedQueryContext namedQuery : ctx.namedQuery()) {
            String cteName = cleanIdentifier(namedQuery.name).toLowerCase(Locale.ROOT);
            cteNames.add(cteName);
            registerDerivedRelation(cteName, namedQuery.query(), cteColumnAliases(namedQuery));
        }
        return null;
    }

    @Override
    public Void visitSetOperation(MySqlParser.SetOperationContext ctx) {
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
    public Void visitQuerySpecification(MySqlParser.QuerySpecificationContext ctx) {
        if (ctx.fromClause() != null) {
            visit(ctx.fromClause());
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
        if (ctx.windowClause() != null) {
            visit(ctx.windowClause());
        }
        return null;
    }

    @Override
    public Void visitTableName(MySqlParser.TableNameContext ctx) {
        TableRef table = tableRef(ctx.multipartIdentifier());
        if (isDualPseudoTable(table)) {
            return null;
        }
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
    public Void visitAliasedQuery(MySqlParser.AliasedQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$subquery" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(relationName.toLowerCase(Locale.ROOT), ctx.query(), tableAliasColumnAliases(ctx.tableAlias()));
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitLateralQuery(MySqlParser.LateralQueryContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$lateral" + derivedColumnLineage.size() : alias;
        registerDerivedRelation(
                relationName.toLowerCase(Locale.ROOT),
                ctx.query(),
                tableAliasColumnAliases(ctx.tableAlias()),
                true);
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitJsonTableRelation(MySqlParser.JsonTableRelationContext ctx) {
        String alias = tableAlias(ctx.tableAlias());
        String relationName = alias == null ? "$json_table" + derivedColumnLineage.size() : alias;
        registerJsonTableRelation(relationName.toLowerCase(Locale.ROOT), ctx.jsonTable());
        addDerivedReference(relationName, ctx.tableAlias());
        return null;
    }

    @Override
    public Void visitRelation(MySqlParser.RelationContext ctx) {
        int relationStart = visibleRelations.size();
        visit(ctx.relationPrimary());
        for (MySqlParser.JoinRelationContext join : ctx.joinRelation()) {
            visit(join.relationPrimary());
            if (join.joinCriteria() != null) {
                collectJoinColumnUsages(join.joinCriteria(), relationStart);
            }
        }
        return null;
    }

    @Override
    public Void visitSelectClause(MySqlParser.SelectClauseContext ctx) {
        for (MySqlParser.SelectItemContext item : ctx.selectItemList().selectItem()) {
            if (item instanceof MySqlParser.SelectExpressionContext) {
                Projection projection = projection((MySqlParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            } else if (item instanceof MySqlParser.SelectStarContext) {
                projections.add(starProjection(null, item.getText()));
            } else if (item instanceof MySqlParser.SelectQualifiedStarContext) {
                MySqlParser.SelectQualifiedStarContext star = (MySqlParser.SelectQualifiedStarContext) item;
                projections.add(starProjection(qualifiedNameText(star.qualifiedName()), item.getText()));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitWhereClause(MySqlParser.WhereClauseContext ctx) {
        addColumnUsages(ColumnUsageType.WHERE, sourceColumns(ctx.expression()));
        return null;
    }

    @Override
    public Void visitJoinCriteria(MySqlParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx);
        return null;
    }

    @Override
    public Void visitGroupByClause(MySqlParser.GroupByClauseContext ctx) {
        for (MySqlParser.GroupByItemContext groupByItem : ctx.groupByItem()) {
            addColumnUsages(ColumnUsageType.GROUP_BY, sourceColumns(groupByItem.expression()));
        }
        return null;
    }

    @Override
    public Void visitHavingClause(MySqlParser.HavingClauseContext ctx) {
        addColumnUsages(ColumnUsageType.HAVING, sourceColumns(ctx.expression()));
        return null;
    }

    @Override
    public Void visitWindowSpec(MySqlParser.WindowSpecContext ctx) {
        addWindowSpecUsages(ctx, new LinkedHashSet<String>());
        return visitChildren(ctx);
    }

    private void addWindowSpecUsages(MySqlParser.WindowSpecContext ctx, Set<String> seenWindows) {
        String inheritedWindow = inheritedWindowName(ctx);
        if (inheritedWindow != null && seenWindows.add(inheritedWindow)) {
            MySqlParser.WindowSpecContext inheritedSpec = namedWindows.get(inheritedWindow);
            if (inheritedSpec != null) {
                addWindowSpecUsages(inheritedSpec, seenWindows);
            }
        }
        if (ctx.expressionList() != null) {
            for (MySqlParser.ExpressionContext expression : ctx.expressionList().expression()) {
                pendingColumnUsages.add(new PendingColumnUsage(
                        ColumnUsageType.WINDOW_PARTITION_BY,
                        sourceColumns(expression)));
            }
        }
        for (MySqlParser.SortItemContext sortItem : ctx.sortItem()) {
            pendingColumnUsages.add(new PendingColumnUsage(
                    ColumnUsageType.WINDOW_ORDER_BY,
                    sourceColumns(sortItem.expression())));
        }
    }

    @Override
    public Void visitNamedWindow(MySqlParser.NamedWindowContext ctx) {
        namedWindows.put(cleanIdentifier(ctx.identifier()).toLowerCase(Locale.ROOT), ctx.windowSpec());
        for (Projection projection : projections) {
            addNamedWindowSourceColumns(projection.expressionTree, projection.sourceColumns);
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQueryOrganization(MySqlParser.QueryOrganizationContext ctx) {
        for (MySqlParser.SortItemContext sortItem : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
        return null;
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

    private void addInputTable(TableRef table, MySqlParser.TableAliasContext aliasCtx, boolean visibleRelation) {
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

    private void addDerivedReference(String rawName, MySqlParser.TableAliasContext aliasCtx) {
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

    private void registerDerivedRelation(String name, MySqlParser.QueryContext query, List<String> columnAliases) {
        registerDerivedRelation(name, query, columnAliases, false);
    }

    private void registerDerivedRelation(
            String name,
            MySqlParser.QueryContext query,
            List<String> columnAliases,
            boolean includeOuterScope) {
        LineageResult relationResult = new LineageResult();
        MySqlLineageVisitor relationVisitor = new MySqlLineageVisitor(relationResult);
        relationVisitor.setContext(context);
        relationVisitor.cteNames.addAll(cteNames);
        if (includeOuterScope) {
            relationVisitor.tableAliases.putAll(tableAliases);
            relationVisitor.derivedReferences.addAll(derivedReferences);
        }
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

    private void registerJsonTableRelation(String name, MySqlParser.JsonTableContext jsonTable) {
        List<ColumnRef> jsonSourceRefs = columnRefs(sourceColumns(jsonTable.expression()));
        if (jsonSourceRefs == null) {
            jsonSourceRefs = new ArrayList<>();
        }
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        for (MySqlParser.JsonTableColumnContext column : jsonTable.jsonTableColumn()) {
            collectJsonTableColumns(column, jsonSourceRefs, columns);
        }
        derivedColumnLineage.put(name, columns);
    }

    private void collectJsonTableColumns(
            MySqlParser.JsonTableColumnContext column,
            List<ColumnRef> jsonSourceRefs,
            Map<String, List<ColumnRef>> columns) {
        if (column.identifier() != null) {
            List<ColumnRef> sources = column.ORDINALITY() == null
                    ? new ArrayList<>(jsonSourceRefs)
                    : new ArrayList<>();
            columns.put(cleanIdentifier(column.identifier()), sources);
            return;
        }
        for (MySqlParser.JsonTableColumnContext nestedColumn : column.jsonTableColumn()) {
            collectJsonTableColumns(nestedColumn, jsonSourceRefs, columns);
        }
    }

    private void registerTemporaryRelation(TableRef table) {
        if (context == null) {
            return;
        }
        context.getTemporaryRelations().put(relationKey(table), copyResult(result));
    }

    private void unregisterTemporaryRelation(TableRef table) {
        if (context == null) {
            return;
        }
        context.getTemporaryRelations().remove(relationKey(table));
    }

    private String temporaryRelationName(TableRef table) {
        if (context == null) {
            return null;
        }
        String key = relationKey(table);
        return context.getTemporaryRelations().containsKey(key) ? key : null;
    }

    private void addTemporaryRelationReference(String relationName, MySqlParser.TableAliasContext aliasCtx) {
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

    private LineageResult lineageForQueryTerm(MySqlParser.QueryTermContext queryTerm) {
        LineageResult queryResult = new LineageResult();
        MySqlLineageVisitor queryVisitor = new MySqlLineageVisitor(queryResult);
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

    private void collectJoinColumnUsages(MySqlParser.JoinCriteriaContext ctx) {
        collectJoinColumnUsages(ctx, 0);
    }

    private void collectJoinColumnUsages(MySqlParser.JoinCriteriaContext ctx, int relationStart) {
        if (ctx.expression() != null) {
            addColumnUsages(ColumnUsageType.JOIN_ON, sourceColumns(ctx.expression()));
        } else {
            addUsingColumnUsages(ctx.identifierList(), relationStart);
        }
    }

    private void collectDmlOrganization(MySqlParser.DmlOrganizationContext ctx) {
        if (ctx == null) {
            return;
        }
        for (MySqlParser.SortItemContext sortItem : ctx.sortItem()) {
            addColumnUsages(ColumnUsageType.ORDER_BY, sourceColumns(sortItem.expression()));
        }
    }

    private void addUsingColumnUsages(MySqlParser.IdentifierListContext ctx, int relationStart) {
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
        TableRef defaultTable = defaultVisibleTable();
        List<ColumnRef> refs = new ArrayList<>();
        for (SourceColumn rawSourceColumn : sourceColumns) {
            if (rawSourceColumn.resolvedRef != null) {
                refs.add(rawSourceColumn.resolvedRef);
                continue;
            }
            SourceColumn sourceColumn = scopedSourceColumn(rawSourceColumn);
            if ("*".equals(sourceColumn.name)) {
                refs.addAll(wildcardColumnRefs(sourceColumn));
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

    private TableRef defaultVisibleTable() {
        TableRef table = null;
        for (VisibleRelation relation : visibleRelations) {
            if (relation.table == null) {
                continue;
            }
            if (table != null) {
                return null;
            }
            table = relation.table;
        }
        if (table != null) {
            return table;
        }
        return visibleRelationCount <= 1 && inputTables.size() == 1
                ? inputTables.iterator().next()
                : null;
    }

    private List<ColumnRef> wildcardColumnRefs(SourceColumn sourceColumn) {
        List<ColumnRef> refs = new ArrayList<>();
        if (sourceColumn.qualifier != null) {
            String qualifier = sourceColumn.qualifier.toLowerCase(Locale.ROOT);
            TableRef table = tableAliases.get(qualifier);
            if (table != null) {
                refs.add(new ColumnRef(table, "*"));
                return refs;
            }
            String derivedName = derivedAliases.get(qualifier);
            if (derivedName != null) {
                refs.addAll(derivedWildcardColumnRefs(derivedName));
            }
            return deduplicateColumns(refs);
        }
        for (VisibleRelation relation : visibleRelations) {
            if (relation.table != null) {
                refs.add(new ColumnRef(relation.table, "*"));
            } else if (relation.derivedName != null) {
                refs.addAll(derivedWildcardColumnRefs(relation.derivedName));
            }
        }
        if (refs.isEmpty() && inputTables.size() == 1) {
            refs.add(new ColumnRef(inputTables.iterator().next(), "*"));
        }
        return deduplicateColumns(refs);
    }

    private List<ColumnRef> derivedWildcardColumnRefs(String derivedName) {
        Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(derivedName);
        if (columns == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (List<ColumnRef> columnRefs : columns.values()) {
            refs.addAll(columnRefs);
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

    private static boolean isDualPseudoTable(TableRef table) {
        return table.getCatalog() == null
                && table.getSchema() == null
                && "dual".equalsIgnoreCase(table.getName());
    }

    private List<ColumnLineage> readAssignments(MySqlParser.AssignmentListContext ctx, TableRef defaultTarget) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (MySqlParser.AssignmentContext assignment : ctx.assignment()) {
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
        return insertedSourcesByColumn(result.getColumnLineage());
    }

    private Map<String, List<ColumnRef>> insertedSourcesByColumn(List<ColumnLineage> lineages) {
        Map<String, List<ColumnRef>> sourcesByColumn = new LinkedHashMap<>();
        for (ColumnLineage lineage : lineages) {
            if (lineage.getTarget() != null) {
                sourcesByColumn.put(lineage.getTarget().getName().toLowerCase(Locale.ROOT), lineage.getSources());
            }
        }
        return sourcesByColumn;
    }

    private List<ColumnLineage> readDuplicateKeyAssignments(MySqlParser.AssignmentListContext ctx,
                                                            TableRef defaultTarget,
                                                            Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        List<ColumnLineage> lineages = new ArrayList<>();
        for (MySqlParser.AssignmentContext assignment : ctx.assignment()) {
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

    private List<ColumnRef> duplicateKeySources(MySqlParser.ExpressionContext expression,
                                                Map<String, List<ColumnRef>> insertedSourcesByColumn) {
        List<ColumnRef> sources = new ArrayList<>();
        List<ColumnRef> existingRowSources = resolveSources(sourceColumnsSkippingInsertedRowReferences(expression));
        if (existingRowSources != null) {
            sources.addAll(existingRowSources);
        }
        for (String valuesColumn : valuesFunctionColumns(expression)) {
            List<ColumnRef> insertedSources = insertedSourcesByColumn.get(valuesColumn.toLowerCase(Locale.ROOT));
            if (insertedSources != null) {
                sources.addAll(insertedSources);
            }
        }
        for (String valuesColumn : insertRowAliasColumns(expression)) {
            List<ColumnRef> insertedSources = insertedSourcesByColumn.get(valuesColumn.toLowerCase(Locale.ROOT));
            if (insertedSources != null) {
                sources.addAll(insertedSources);
            }
        }
        return deduplicateColumns(sources);
    }

    private List<String> insertRowAliasColumns(ParseTree tree) {
        List<String> columns = new ArrayList<>();
        collectInsertRowAliasColumns(tree, columns);
        return columns;
    }

    private void collectInsertRowAliasColumns(ParseTree tree, List<String> columns) {
        if (tree instanceof MySqlParser.DereferenceContext) {
            List<String> parts = collectDereferenceParts((MySqlParser.DereferenceContext) tree);
            if (parts.size() >= 2) {
                String alias = parts.get(parts.size() - 2).toLowerCase(Locale.ROOT);
                Map<String, String> aliasColumns = insertRowAliasColumns.get(alias);
                if (aliasColumns != null) {
                    String aliasColumn = parts.get(parts.size() - 1).toLowerCase(Locale.ROOT);
                    String targetColumn = aliasColumns.get(aliasColumn);
                    if (targetColumn != null) {
                        columns.add(targetColumn);
                    }
                    return;
                }
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectInsertRowAliasColumns(tree.getChild(i), columns);
        }
    }

    private List<SourceColumn> sourceColumnsSkippingInsertedRowReferences(ParseTree tree) {
        Set<SourceColumn> columns = new LinkedHashSet<>();
        collectSourceColumnsSkippingInsertedRowReferences(tree, columns);
        return new ArrayList<>(columns);
    }

    private void collectSourceColumnsSkippingInsertedRowReferences(ParseTree tree, Set<SourceColumn> columns) {
        if (isValuesFunctionCall(tree) || isDefaultFunctionCall(tree) || isInsertRowAliasReference(tree)) {
            return;
        }
        if (tree instanceof MySqlParser.ColumnReferenceContext
                || tree instanceof MySqlParser.DereferenceContext
                || tree instanceof MySqlParser.ScalarSubqueryContext
                || tree instanceof MySqlParser.ExistsExprContext
                || tree instanceof MySqlParser.PredicateContext) {
            collectSourceColumns(tree, columns);
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectSourceColumnsSkippingInsertedRowReferences(tree.getChild(i), columns);
        }
    }

    private boolean isInsertRowAliasReference(ParseTree tree) {
        if (!(tree instanceof MySqlParser.DereferenceContext)) {
            return false;
        }
        List<String> parts = collectDereferenceParts((MySqlParser.DereferenceContext) tree);
        return parts.size() >= 2
                && insertRowAliases.contains(parts.get(parts.size() - 2).toLowerCase(Locale.ROOT));
    }

    private List<String> valuesFunctionColumns(ParseTree tree) {
        List<String> columns = new ArrayList<>();
        collectValuesFunctionColumns(tree, columns);
        return columns;
    }

    private void collectValuesFunctionColumns(ParseTree tree, List<String> columns) {
        if (isValuesFunctionCall(tree)) {
            MySqlParser.FunctionCallContext functionCall = (MySqlParser.FunctionCallContext) tree;
            String column = directColumnName(functionCall.expressionList().expression(0));
            if (column != null) {
                columns.add(column);
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectValuesFunctionColumns(tree.getChild(i), columns);
        }
    }

    private boolean isValuesFunctionCall(ParseTree tree) {
        if (!(tree instanceof MySqlParser.FunctionCallContext)) {
            return false;
        }
        MySqlParser.FunctionCallContext functionCall = (MySqlParser.FunctionCallContext) tree;
        return functionCall.functionName() != null
                && "values".equalsIgnoreCase(functionCall.functionName().getText())
                && functionCall.expressionList() != null
                && functionCall.expressionList().expression().size() == 1;
    }

    private boolean isDefaultFunctionCall(ParseTree tree) {
        return tree instanceof MySqlParser.FunctionCallContext
                && ((MySqlParser.FunctionCallContext) tree).functionName() != null
                && "default".equalsIgnoreCase(((MySqlParser.FunctionCallContext) tree).functionName().getText());
    }

    private String directColumnName(MySqlParser.ExpressionContext expression) {
        List<SourceColumn> columns = sourceColumns(expression);
        if (columns.size() != 1) {
            return null;
        }
        SourceColumn sourceColumn = columns.get(0);
        if (sourceColumn.resolvedRef != null) {
            return sourceColumn.resolvedRef.getName();
        }
        return unqualifiedName(sourceColumn.name);
    }

    private List<ColumnRef> deduplicateColumns(List<ColumnRef> columns) {
        List<ColumnRef> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColumnRef column : columns) {
            String key = columnKey(column);
            if (seen.add(key)) {
                deduplicated.add(column);
            }
        }
        return deduplicated;
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
        if (tree instanceof MySqlParser.ScalarSubqueryContext) {
            MySqlParser.ScalarSubqueryContext subquery = (MySqlParser.ScalarSubqueryContext) tree;
            LineageResult subResult = lineageForQuery(subquery.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof MySqlParser.ExistsExprContext) {
            MySqlParser.ExistsExprContext exists = (MySqlParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            inputTables.addAll(subResult.getInputTables());
            return;
        }
        if (tree instanceof MySqlParser.PredicateContext) {
            MySqlParser.PredicateContext predicate = (MySqlParser.PredicateContext) tree;
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
        if (tree instanceof MySqlParser.ScalarSubqueryContext) {
            return true;
        }
        if (tree instanceof MySqlParser.ExistsExprContext) {
            return true;
        }
        if (tree instanceof MySqlParser.PredicateContext) {
            MySqlParser.PredicateContext predicate = (MySqlParser.PredicateContext) tree;
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

    private LineageResult lineageForQuery(MySqlParser.QueryContext query) {
        LineageResult queryResult = new LineageResult();
        MySqlLineageVisitor queryVisitor = new MySqlLineageVisitor(queryResult);
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

    private void collectTopLevelQueryProjections(MySqlParser.QueryContext query) {
        if (!projections.isEmpty()) {
            return;
        }
        MySqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return;
        }
        for (MySqlParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof MySqlParser.SelectExpressionContext) {
                Projection projection = projection((MySqlParser.SelectExpressionContext) item);
                if (projection != null) {
                    projections.add(projection);
                }
            } else if (item instanceof MySqlParser.SelectStarContext) {
                projections.add(starProjection(null, item.getText()));
            } else if (item instanceof MySqlParser.SelectQualifiedStarContext) {
                MySqlParser.SelectQualifiedStarContext star = (MySqlParser.SelectQualifiedStarContext) item;
                projections.add(starProjection(qualifiedNameText(star.qualifiedName()), item.getText()));
            }
        }
    }

    private static MySqlParser.QuerySpecificationContext topLevelQuerySpecification(MySqlParser.QueryContext query) {
        if (!(query.queryTerm() instanceof MySqlParser.QueryTermDefaultContext)) {
            return null;
        }
        MySqlParser.QueryPrimaryContext primary =
                ((MySqlParser.QueryTermDefaultContext) query.queryTerm()).queryPrimary();
        if (!(primary instanceof MySqlParser.QueryPrimaryDefaultContext)) {
            return null;
        }
        return ((MySqlParser.QueryPrimaryDefaultContext) primary).querySpecification();
    }

    private Projection projection(MySqlParser.SelectExpressionContext ctx) {
        String expression = ctx.expression().getText();
        List<SourceColumn> sourceColumns = sourceColumns(ctx.expression());
        addNamedWindowSourceColumns(ctx.expression(), sourceColumns);
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
        return new Projection(sourceColumns, targetColumn, expression, ctx.expression());
    }

    private Projection starProjection(String qualifier, String expression) {
        List<SourceColumn> sourceColumns = new ArrayList<>();
        sourceColumns.add(new SourceColumn(qualifier, "*"));
        return new Projection(sourceColumns, "*", expression, null);
    }

    private void addNamedWindowSourceColumns(ParseTree tree, List<SourceColumn> sourceColumns) {
        Set<SourceColumn> columns = new LinkedHashSet<>(sourceColumns);
        collectNamedWindowSourceColumns(tree, columns);
        sourceColumns.clear();
        sourceColumns.addAll(columns);
    }

    private void collectNamedWindowSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (tree instanceof MySqlParser.WindowRefContext) {
            MySqlParser.WindowRefContext windowRef = (MySqlParser.WindowRefContext) tree;
            if (windowRef.identifier() != null) {
                MySqlParser.WindowSpecContext windowSpec =
                        namedWindows.get(cleanIdentifier(windowRef.identifier()).toLowerCase(Locale.ROOT));
                if (windowSpec != null) {
                    columns.addAll(sourceColumns(windowSpec));
                }
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectNamedWindowSourceColumns(tree.getChild(i), columns);
        }
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

    private void addScalarSubquerySourceColumns(MySqlParser.QueryContext query, Set<SourceColumn> columns) {
        LineageResult subResult = lineageForQuery(query);
        mergeInputTables(subResult);
        LineageModelUtils.mergeColumnUsages(result, subResult);
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

    private List<ColumnRef> scalarSubqueryProjectionRefs(MySqlParser.QueryContext query) {
        MySqlLineageVisitor queryVisitor = new MySqlLineageVisitor(new LineageResult());
        queryVisitor.setContext(context);
        queryVisitor.cteNames.addAll(cteNames);
        queryVisitor.tableAliases.putAll(tableAliases);
        queryVisitor.derivedColumnLineage.putAll(derivedColumnLineage);
        queryVisitor.derivedAliases.putAll(derivedAliases);
        queryVisitor.derivedReferences.addAll(derivedReferences);
        queryVisitor.visit(query);
        MySqlParser.QuerySpecificationContext specification = topLevelQuerySpecification(query);
        if (specification == null || specification.selectClause() == null) {
            return new ArrayList<>();
        }
        List<ColumnRef> refs = new ArrayList<>();
        for (MySqlParser.SelectItemContext item : specification.selectClause().selectItemList().selectItem()) {
            if (item instanceof MySqlParser.SelectExpressionContext) {
                List<ColumnRef> itemRefs = queryVisitor.columnRefs(
                        queryVisitor.sourceColumns(((MySqlParser.SelectExpressionContext) item).expression()));
                if (itemRefs != null) {
                    refs.addAll(itemRefs);
                }
            }
        }
        return refs;
    }

    private void collectSourceColumns(ParseTree tree, Set<SourceColumn> columns) {
        if (isDefaultFunctionCall(tree)) {
            return;
        }
        if (tree instanceof MySqlParser.WindowSpecContext) {
            MySqlParser.WindowSpecContext windowSpec = (MySqlParser.WindowSpecContext) tree;
            String inheritedWindow = inheritedWindowName(windowSpec);
            if (inheritedWindow != null) {
                MySqlParser.WindowSpecContext inheritedSpec = namedWindows.get(inheritedWindow);
                if (inheritedSpec != null && inheritedSpec != windowSpec) {
                    collectSourceColumns(inheritedSpec, columns);
                }
            }
        }
        if (tree instanceof MySqlParser.ColumnReferenceContext) {
            MySqlParser.ColumnReferenceContext colRef = (MySqlParser.ColumnReferenceContext) tree;
            if (isUnquotedNonColumnLiteral(colRef.identifier())) {
                return;
            }
            columns.add(new SourceColumn(null, cleanIdentifier(colRef.identifier())));
            return;
        }
        if (tree instanceof MySqlParser.DereferenceContext) {
            MySqlParser.DereferenceContext deref = (MySqlParser.DereferenceContext) tree;
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
        if (tree instanceof MySqlParser.ScalarSubqueryContext) {
            MySqlParser.ScalarSubqueryContext subquery = (MySqlParser.ScalarSubqueryContext) tree;
            addScalarSubquerySourceColumns(subquery.query(), columns);
            return;
        }
        if (tree instanceof MySqlParser.ExistsExprContext) {
            MySqlParser.ExistsExprContext exists = (MySqlParser.ExistsExprContext) tree;
            LineageResult subResult = lineageForQuery(exists.query());
            mergeInputTables(subResult);
            for (io.github.linesql.core.model.ColumnUsage usage : subResult.getColumnUsages()) {
                if (usage.getColumn() != null && usage.getColumn().getTable() != null) {
                    columns.add(SourceColumn.resolved(usage.getColumn()));
                }
            }
            return;
        }
        if (tree instanceof MySqlParser.PredicateContext) {
            MySqlParser.PredicateContext predicate = (MySqlParser.PredicateContext) tree;
            if (predicate.query() != null) {
                LineageResult subResult = lineageForQuery(predicate.query());
                mergeInputTables(subResult);
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

    private static String inheritedWindowName(MySqlParser.WindowSpecContext ctx) {
        if (ctx == null || ctx.windowInheritance() == null) {
            return null;
        }
        return cleanIdentifier(ctx.windowInheritance().getText()).toLowerCase(Locale.ROOT);
    }

    private void mergeInputTables(LineageResult subResult) {
        for (TableRef table : subResult.getInputTables()) {
            addInputTable(table, false);
        }
    }

    private List<String> collectDereferenceParts(MySqlParser.DereferenceContext ctx) {
        List<String> parts = new ArrayList<>();
        ParseTree base = ctx.primaryExpression();
        collectPrimaryParts(base, parts);
        parts.add(cleanIdentifier(ctx.identifier()));
        return parts;
    }

    private void collectPrimaryParts(ParseTree tree, List<String> parts) {
        if (tree instanceof MySqlParser.DereferenceContext) {
            MySqlParser.DereferenceContext deref = (MySqlParser.DereferenceContext) tree;
            collectPrimaryParts(deref.primaryExpression(), parts);
            parts.add(cleanIdentifier(deref.identifier()));
        } else if (tree instanceof MySqlParser.ColumnReferenceContext) {
            MySqlParser.ColumnReferenceContext colRef = (MySqlParser.ColumnReferenceContext) tree;
            parts.add(cleanIdentifier(colRef.identifier()));
        }
    }

    // ============ Utility ============

    private static TableRef tableRef(MySqlParser.MultipartIdentifierContext ctx) {
        List<String> parts = identifierParts(ctx);
        return LineageModelUtils.tableRefFromParts(parts);
    }

    private static TableRef showTableRef(
            MySqlParser.MultipartIdentifierContext table,
            MySqlParser.ShowFromSchemaContext schema) {
        List<String> parts = identifierParts(table);
        if (parts.size() == 1 && schema != null) {
            List<String> qualified = new ArrayList<>();
            qualified.add(cleanIdentifier(schema.identifier()));
            qualified.add(parts.get(0));
            return LineageModelUtils.tableRefFromParts(qualified);
        }
        return LineageModelUtils.tableRefFromParts(parts);
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

    private static List<String> identifierParts(MySqlParser.MultipartIdentifierContext ctx) {
        List<String> parts = new ArrayList<>();
        for (MySqlParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return parts;
    }

    private static String qualifiedNameText(MySqlParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (MySqlParser.IdentifierContext id : ctx.identifier()) {
            parts.add(cleanIdentifier(id));
        }
        return String.join(".", parts);
    }

    private static String tableAlias(MySqlParser.TableAliasContext ctx) {
        if (ctx == null || ctx.strictIdentifier() == null) {
            return null;
        }
        return cleanIdentifier(ctx.strictIdentifier().getText());
    }

    private static List<String> tableAliasColumnAliases(MySqlParser.TableAliasContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx != null && ctx.columnAliases != null) {
            for (MySqlParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> cteColumnAliases(MySqlParser.NamedQueryContext ctx) {
        List<String> aliases = new ArrayList<>();
        if (ctx.columnAliases != null) {
            for (MySqlParser.IdentifierContext id : ctx.columnAliases.identifier()) {
                aliases.add(cleanIdentifier(id));
            }
        }
        return aliases;
    }

    private static List<String> identifierNames(MySqlParser.IdentifierListContext ctx) {
        List<String> names = new ArrayList<>();
        for (MySqlParser.IdentifierContext id : ctx.identifier()) {
            names.add(cleanIdentifier(id));
        }
        return names;
    }

    private static String cleanIdentifier(MySqlParser.IdentifierContext ctx) {
        return cleanIdentifier(ctx.getText());
    }

    private static boolean isUnquotedNonColumnLiteral(MySqlParser.IdentifierContext ctx) {
        String text = ctx.getText();
        if (text.startsWith("`")) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "false".equals(normalized)
                || "null".equals(normalized)
                || "unknown".equals(normalized)
                || "default".equals(normalized)
                || "current_date".equals(normalized)
                || "current_time".equals(normalized)
                || "current_timestamp".equals(normalized)
                || "current_user".equals(normalized)
                || "utc_date".equals(normalized)
                || "utc_time".equals(normalized)
                || "utc_timestamp".equals(normalized)
                || "localtime".equals(normalized)
                || "localtimestamp".equals(normalized);
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
        final ParseTree expressionTree;

        Projection(List<SourceColumn> sourceColumns, String targetColumn, String expression, ParseTree expressionTree) {
            this.sourceColumns = sourceColumns;
            this.targetColumn = targetColumn;
            this.expression = expression;
            this.expressionTree = expressionTree;
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
