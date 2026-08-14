package io.github.linesql.dialect.mysql;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.mysql.antlr.MySqlLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MySqlDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.MYSQL);
        result.setDialectConfidence(1.0);
        try {
            MySqlLineageVisitor visitor = new MySqlLineageVisitor(tokens(sql), result);
            visitor.parse();
            if (visitor.shouldWarnMissingColumnLineage()
                    && result.getColumnLineage().isEmpty()
                    && (result.getStatementType() == StatementType.SELECT
                    || result.getStatementType() == StatementType.INSERT
                    || result.getStatementType() == StatementType.CREATE_TABLE_AS_SELECT
                    || result.getStatementType() == StatementType.CREATE_VIEW)) {
                result.getDiagnostics().add(Diagnostic.warning(
                        "MYSQL_COLUMN_LINEAGE_NOT_IMPLEMENTED",
                        "MySQL column lineage was not produced for this statement shape."));
            }
        } catch (RuntimeException e) {
            result.getDiagnostics().add(Diagnostic.error("MYSQL_PARSE_ERROR", e.getMessage()));
        }
        return result;
    }

    private static List<Token> tokens(String sql) {
        MySqlLineageLexer lexer = new MySqlLineageLexer(CharStreams.fromString(sql));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<Token> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static class MySqlLineageVisitor {
        private final List<Token> tokens;
        private final LineageResult result;
        private final Map<String, TableRef> aliases = new LinkedHashMap<>();
        private final Map<String, Map<String, List<ColumnRef>>> derivedColumnLineage = new LinkedHashMap<>();
        private final Map<String, List<TableRef>> derivedInputs = new LinkedHashMap<>();
        private final Set<TableRef> inputs = new LinkedHashSet<>();
        private final Set<TableRef> outputs = new LinkedHashSet<>();
        private boolean suppressMissingColumnLineageDiagnostic;

        MySqlLineageVisitor(List<Token> tokens, LineageResult result) {
            this.tokens = trimSemicolon(tokens);
            this.result = result;
        }

        void parse() {
            if (tokens.isEmpty()) {
                result.getDiagnostics().add(Diagnostic.error("EMPTY_SQL", "SQL input is empty."));
                return;
            }
            if (is(0, MySqlLineageLexer.SELECT)) {
                parseSelectStatement(0, tokens.size(), null, new ArrayList<String>());
            } else if (is(0, MySqlLineageLexer.WITH)) {
                parseWithSelect();
            } else if (is(0, MySqlLineageLexer.INSERT)) {
                parseInsert();
            } else if (is(0, MySqlLineageLexer.REPLACE)) {
                parseReplace();
            } else if (is(0, MySqlLineageLexer.CREATE)) {
                parseCreate();
            } else if (is(0, MySqlLineageLexer.UPDATE)) {
                parseUpdate();
            } else if (is(0, MySqlLineageLexer.DELETE)) {
                parseDelete();
            } else if (is(0, MySqlLineageLexer.DROP)) {
                parseDrop();
            } else if (is(0, MySqlLineageLexer.TRUNCATE)) {
                parseTruncate();
            } else if (is(0, MySqlLineageLexer.ALTER)) {
                parseAlter();
            } else if (is(0, MySqlLineageLexer.SHOW) || is(0, MySqlLineageLexer.DESCRIBE)) {
                parseMetadataRead();
            } else if (is(0, MySqlLineageLexer.COMMENT)) {
                parseComment();
            } else {
                result.setStatementType(StatementType.UNKNOWN);
                result.getDiagnostics().add(Diagnostic.warning(
                        "MYSQL_STATEMENT_NOT_SUPPORTED",
                        "MySQL statement type is not supported by the current MVP parser."));
            }
            result.setInputTables(new ArrayList<>(inputs));
            result.setOutputTables(new ArrayList<>(outputs));
        }

        boolean shouldWarnMissingColumnLineage() {
            return !suppressMissingColumnLineageDiagnostic;
        }

        private void parseSelectStatement(int start, int end, TableRef outputTable, List<String> targetColumns) {
            result.setStatementType(StatementType.SELECT);
            SelectLineage select = parseSelect(start, end);
            addInputs(select.inputs);
            if (outputTable != null) {
                outputs.add(outputTable);
                result.setStatementType(targetColumns.isEmpty()
                        ? result.getStatementType()
                        : StatementType.INSERT);
            }
            result.setColumnLineage(targetLineage(select.columnLineage, outputTable, targetColumns));
        }

        private void parseWithSelect() {
            int mainSelect = registerCtes(0, tokens.size());
            if (mainSelect < 0) {
                result.setStatementType(StatementType.SELECT);
                return;
            }
            parseSelectStatement(mainSelect, tokens.size(), null, new ArrayList<String>());
        }

        private void parseInsert() {
            parseWriteInto(StatementType.INSERT);
        }

        private void parseReplace() {
            parseWriteInto(StatementType.INSERT);
        }

        private void parseWriteInto(StatementType statementType) {
            int into = indexOfTopLevel(0, tokens.size(), MySqlLineageLexer.INTO);
            int select = indexOfTopLevel(0, tokens.size(), MySqlLineageLexer.SELECT);
            if (into < 0) {
                result.setStatementType(statementType);
                return;
            }
            int values = indexOfTopLevel(into + 1, tokens.size(), MySqlLineageLexer.VALUES);
            int duplicate = indexOfTopLevel(into + 1, tokens.size(), MySqlLineageLexer.DUPLICATE);
            int targetEnd = firstPositive(select, values, duplicate, tokens.size());
            TableScan target = readTable(into + 1, targetEnd);
            if (target == null) {
                result.setStatementType(statementType);
                return;
            }
            outputs.add(target.table);
            List<String> targetColumns = readColumnList(target.nextIndex);
            result.setStatementType(statementType);
            if (select < 0) {
                suppressMissingColumnLineageDiagnostic = true;
                return;
            }
            SelectLineage selectLineage = parseSelect(select, tokens.size());
            addInputs(selectLineage.inputs);
            result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, targetColumns));
        }

        private void parseCreate() {
            int objectTypeIndex = createObjectTypeIndex();
            if (objectTypeIndex < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int objectType = tokens.get(objectTypeIndex).getType();
            int objectIndex = skipIfNotExists(objectTypeIndex + 1);
            int select = indexOfTopLevel(objectIndex, tokens.size(), MySqlLineageLexer.SELECT);
            TableScan target = readTable(objectIndex, select < 0 ? tokens.size() : select);
            if (target != null) {
                outputs.add(target.table);
            }
            int like = indexOfTopLevel(objectIndex, tokens.size(), MySqlLineageLexer.LIKE);
            if (select < 0 && like >= 0 && objectType == MySqlLineageLexer.TABLE && target != null) {
                TableScan source = readTable(like + 1, tokens.size());
                if (source != null) {
                    result.setStatementType(StatementType.CREATE_TABLE_LIKE);
                    inputs.add(source.table);
                    return;
                }
            }
            if (objectType == MySqlLineageLexer.VIEW) {
                result.setStatementType(StatementType.CREATE_VIEW);
            } else {
                result.setStatementType(StatementType.CREATE_TABLE_AS_SELECT);
            }
            if (select >= 0 && target != null) {
                SelectLineage selectLineage = parseSelect(select, tokens.size());
                addInputs(selectLineage.inputs);
                result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, new ArrayList<String>()));
            }
        }

        private int createObjectTypeIndex() {
            int i = 1;
            if (is(i, MySqlLineageLexer.OR) && is(i + 1, MySqlLineageLexer.REPLACE)) {
                i += 2;
            }
            if (is(i, MySqlLineageLexer.TEMPORARY)) {
                i++;
            }
            if (is(i, MySqlLineageLexer.TABLE) || is(i, MySqlLineageLexer.VIEW)) {
                return i;
            }
            return -1;
        }

        private int skipIfNotExists(int index) {
            if (is(index, MySqlLineageLexer.IF)
                    && is(index + 1, MySqlLineageLexer.NOT)
                    && is(index + 2, MySqlLineageLexer.EXISTS)) {
                return index + 3;
            }
            return index;
        }

        private int skipIfExists(int index) {
            if (is(index, MySqlLineageLexer.IF) && is(index + 1, MySqlLineageLexer.EXISTS)) {
                return index + 2;
            }
            return index;
        }

        private void parseUpdate() {
            result.setStatementType(StatementType.UPDATE);
            TableScan target = readTable(1, tokens.size());
            if (target == null) {
                return;
            }
            outputs.add(target.table);
            inputs.add(target.table);
            registerAlias(target);
            for (int i = target.nextIndex; i < tokens.size(); i++) {
                if (isJoinToken(i)) {
                    TableScan joined = readTable(i + 1, tokens.size());
                    if (joined != null) {
                        inputs.add(joined.table);
                        registerAlias(joined);
                        i = joined.nextIndex - 1;
                    }
                }
            }
            int set = indexOfTopLevel(0, tokens.size(), MySqlLineageLexer.SET);
            if (set >= 0) {
                int setEnd = firstTopLevelClauseBoundary(set + 1, tokens.size());
                result.setColumnLineage(readUpdateAssignments(set + 1, setEnd, target.table));
            }
        }

        private void parseDelete() {
            result.setStatementType(StatementType.DELETE);
            int from = indexOfTopLevel(0, tokens.size(), MySqlLineageLexer.FROM);
            if (from < 0) {
                return;
            }
            TableScan target = readTable(from + 1, tokens.size());
            if (target != null) {
                outputs.add(target.table);
                inputs.add(target.table);
                registerAlias(target);
            }
            int using = indexOfTopLevel(0, tokens.size(), MySqlLineageLexer.USING);
            if (using >= 0) {
                inputs.addAll(readTableSources(using + 1, tokens.size()));
            } else if (from >= 0) {
                inputs.addAll(readTableSources(from + 1, tokens.size()));
            }
        }

        private void parseDrop() {
            result.setStatementType(StatementType.DROP_TABLE);
            int table = indexOfTopLevel(1, tokens.size(), MySqlLineageLexer.TABLE);
            if (table < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int targetStart = skipIfExists(table + 1);
            TableScan target = readTable(targetStart, tokens.size());
            if (target != null) {
                outputs.add(target.table);
            }
        }

        private void parseTruncate() {
            result.setStatementType(StatementType.TRUNCATE_TABLE);
            int table = indexOfTopLevel(1, tokens.size(), MySqlLineageLexer.TABLE);
            int targetStart = table >= 0 ? table + 1 : 1;
            TableScan target = readTable(targetStart, tokens.size());
            if (target != null) {
                outputs.add(target.table);
            }
        }

        private void parseAlter() {
            int table = indexOfTopLevel(1, tokens.size(), MySqlLineageLexer.TABLE);
            if (table < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            TableScan target = readTable(table + 1, tokens.size());
            if (target == null) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int rename = indexOfTopLevel(target.nextIndex, tokens.size(), MySqlLineageLexer.RENAME);
            int to = rename >= 0 ? indexOfTopLevel(rename + 1, tokens.size(), MySqlLineageLexer.TO) : -1;
            if (to >= 0) {
                TableScan renamed = readTable(to + 1, tokens.size());
                if (renamed != null) {
                    result.setStatementType(StatementType.RENAME_TABLE);
                    inputs.add(target.table);
                    outputs.add(renamed.table);
                    return;
                }
            }
            result.setStatementType(StatementType.ALTER_TABLE);
            outputs.add(target.table);
        }

        private void parseMetadataRead() {
            result.setStatementType(StatementType.READ_METADATA);
            int table = indexOfTopLevel(1, tokens.size(), MySqlLineageLexer.TABLE);
            int targetStart = table >= 0 ? table + 1 : 1;
            TableScan target = readTable(targetStart, tokens.size());
            if (target != null) {
                inputs.add(target.table);
            }
        }

        private void parseComment() {
            int on = indexOfTopLevel(1, tokens.size(), MySqlLineageLexer.ON);
            if (on < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int objectIndex = on + 1;
            if (is(objectIndex, MySqlLineageLexer.COLUMN)) {
                result.setStatementType(StatementType.ALTER_TABLE);
                IdentifierRead column = readIdentifierParts(objectIndex + 1, tokens.size());
                if (column.parts.size() > 1) {
                    outputs.add(tableRef(column.parts.subList(0, column.parts.size() - 1)));
                }
                return;
            }
            if (is(objectIndex, MySqlLineageLexer.TABLE)) {
                result.setStatementType(StatementType.ALTER_TABLE);
                TableScan target = readTable(objectIndex + 1, tokens.size());
                if (target != null) {
                    outputs.add(target.table);
                }
                return;
            }
            result.setStatementType(StatementType.UNKNOWN);
        }

        private SelectLineage parseSelect(int start, int end) {
            int union = indexOfTopLevel(start + 1, end, MySqlLineageLexer.UNION);
            if (union >= 0) {
                SelectLineage left = parseSelect(start, union);
                int rightStart = union + 1;
                if (is(rightStart, MySqlLineageLexer.ALL) || is(rightStart, MySqlLineageLexer.DISTINCT)) {
                    rightStart++;
                }
                SelectLineage right = parseSelect(rightStart, end);
                SelectLineage merged = new SelectLineage();
                merged.inputs.addAll(left.inputs);
                merged.inputs.addAll(right.inputs);
                merged.columnLineage.addAll(mergeSetColumnLineage(left.columnLineage, right.columnLineage));
                return merged;
            }
            int from = indexOfTopLevel(start, end, MySqlLineageLexer.FROM);
            int projectionStart = start + 1;
            int projectionEnd = from < 0 ? end : from;
            SelectLineage lineage = new SelectLineage();
            if (from >= 0) {
                lineage.inputs.addAll(readTableSources(from + 1, end));
            }
            lineage.columnLineage.addAll(readProjections(projectionStart, projectionEnd, lineage.inputs));
            return lineage;
        }

        private List<ColumnLineage> mergeSetColumnLineage(List<ColumnLineage> left, List<ColumnLineage> right) {
            int size = Math.min(left.size(), right.size());
            List<ColumnLineage> merged = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ColumnLineage leftColumn = left.get(i);
                ColumnLineage rightColumn = right.get(i);
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(leftColumn.getTarget());
                lineage.setSources(mergeColumnRefs(leftColumn.getSources(), rightColumn.getSources()));
                lineage.setExpression(leftColumn.getExpression());
                merged.add(lineage);
            }
            return merged;
        }

        private List<ColumnRef> mergeColumnRefs(List<ColumnRef> left, List<ColumnRef> right) {
            Map<String, ColumnRef> refs = new LinkedHashMap<>();
            for (ColumnRef ref : left) {
                refs.put(columnKey(ref), ref);
            }
            for (ColumnRef ref : right) {
                refs.put(columnKey(ref), ref);
            }
            return new ArrayList<>(refs.values());
        }

        private String columnKey(ColumnRef ref) {
            TableRef table = ref.getTable();
            String tableKey = table == null
                    ? ""
                    : String.valueOf(table.getCatalog()) + "." + table.getSchema() + "." + table.getName();
            return tableKey + "." + ref.getName();
        }

        private List<TableRef> readTableSources(int start, int end) {
            List<TableRef> tables = new ArrayList<>();
            int i = start;
            while (i < end) {
                if (isClauseBoundary(i)) {
                    break;
                }
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    int close = matchingParen(i, end);
                    int nestedSelect = indexOfTopLevel(i + 1, close, MySqlLineageLexer.SELECT);
                    if (nestedSelect >= 0) {
                        SelectLineage nested = parseSelect(nestedSelect, close);
                        tables.addAll(nested.inputs);
                        String alias = readAlias(close + 1, end);
                        if (alias != null) {
                            registerDerived(alias, nested);
                        }
                    }
                    i = nextAfterAlias(close + 1, end);
                    continue;
                }
                if (isJoinToken(i) || is(i, MySqlLineageLexer.COMMA)) {
                    i++;
                    continue;
                }
                if (isTableStart(i)) {
                    TableScan scan = readTable(i, end);
                    if (scan != null) {
                        String relationName = scan.table.getName().toLowerCase(Locale.ROOT);
                        if (derivedColumnLineage.containsKey(relationName)) {
                            tables.addAll(derivedInputs.get(relationName));
                        } else {
                            tables.add(scan.table);
                            registerAlias(scan);
                        }
                        i = scan.nextIndex;
                        continue;
                    }
                }
                i++;
            }
            return tables;
        }

        private List<ColumnLineage> readProjections(int start, int end, List<TableRef> inputTables) {
            List<ColumnLineage> result = new ArrayList<>();
            for (Range range : splitTopLevel(start, end, MySqlLineageLexer.COMMA)) {
                Projection projection = readProjection(range.start, range.end, inputTables);
                if (projection == null) {
                    continue;
                }
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(new ColumnRef(null, projection.targetColumn));
                lineage.setSources(projection.sources);
                lineage.setExpression(text(range.start, range.end));
                result.add(lineage);
            }
            return result;
        }

        private Projection readProjection(int start, int end, List<TableRef> inputTables) {
            if (start >= end || is(start, MySqlLineageLexer.STAR)) {
                return null;
            }
            int aliasIndex = aliasIndex(start, end);
            int expressionEnd = aliasIndex < 0 ? end : aliasIndex;
            List<SourceColumn> sourceColumns = sourceColumns(start, expressionEnd);
            String target = aliasIndex < 0 ? directTarget(sourceColumns, start, expressionEnd) : clean(text(aliasIndex, end));
            if (target == null) {
                return null;
            }
            List<ColumnRef> sources = resolveSources(sourceColumns, inputTables);
            if (sources == null) {
                return null;
            }
            return new Projection(target, sources);
        }

        private int aliasIndex(int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (depth(start, i) != 0) {
                    continue;
                }
                if (is(i, MySqlLineageLexer.AS) && i + 1 < end) {
                    return i + 1;
                }
            }
            if (end - start >= 2
                    && isIdentifier(end - 1)
                    && !isIdentifier(end - 2)
                    && !is(end - 2, MySqlLineageLexer.DOT)) {
                return end - 1;
            }
            return -1;
        }

        private String directTarget(List<SourceColumn> columns, int start, int end) {
            if (columns.size() != 1) {
                return null;
            }
            String expression = text(start, end);
            SourceColumn column = columns.get(0);
            if (expression.equals(column.raw) || expression.endsWith("." + column.name)) {
                return column.name;
            }
            return null;
        }

        private List<SourceColumn> sourceColumns(int start, int end) {
            Set<SourceColumn> columns = new LinkedHashSet<>();
            int i = start;
            while (i < end) {
                if (!isIdentifier(i)) {
                    i++;
                    continue;
                }
                if (i + 1 < end && is(i + 1, MySqlLineageLexer.LPAREN)) {
                    i++;
                    continue;
                }
                IdentifierRead read = readIdentifierParts(i, end);
                if (read.parts.size() == 1 && isKeywordLike(read.parts.get(0))) {
                    i = read.nextIndex;
                    continue;
                }
                if (!read.parts.isEmpty()) {
                    String qualifier = read.parts.size() >= 2 ? read.parts.get(read.parts.size() - 2) : null;
                    String name = read.parts.get(read.parts.size() - 1);
                    columns.add(new SourceColumn(qualifier, name, String.join(".", read.parts)));
                }
                i = read.nextIndex;
            }
            return new ArrayList<>(columns);
        }

        private List<ColumnRef> resolveSources(List<SourceColumn> columns, List<TableRef> inputTables) {
            List<ColumnRef> refs = new ArrayList<>();
            for (SourceColumn column : columns) {
                List<ColumnRef> derivedRefs = resolveDerivedSources(column);
                if (derivedRefs != null) {
                    refs.addAll(derivedRefs);
                    continue;
                }
                TableRef table = null;
                if (column.qualifier != null) {
                    table = aliases.get(column.qualifier.toLowerCase(Locale.ROOT));
                } else if (inputTables.size() == 1) {
                    table = inputTables.get(0);
                }
                if (table == null) {
                    return null;
                }
                refs.add(new ColumnRef(table, column.name));
            }
            return refs;
        }

        private List<ColumnRef> resolveDerivedSources(SourceColumn column) {
            if (column.qualifier != null) {
                Map<String, List<ColumnRef>> columns = derivedColumnLineage.get(column.qualifier.toLowerCase(Locale.ROOT));
                return columns == null ? null : columns.get(column.name);
            }
            if (derivedColumnLineage.size() == 1) {
                Map<String, List<ColumnRef>> columns = derivedColumnLineage.values().iterator().next();
                return columns.get(column.name);
            }
            return null;
        }

        private List<ColumnLineage> targetLineage(List<ColumnLineage> source, TableRef targetTable, List<String> targetColumns) {
            if (targetTable == null) {
                return source;
            }
            List<ColumnLineage> mapped = new ArrayList<>();
            for (int i = 0; i < source.size(); i++) {
                ColumnLineage original = source.get(i);
                String targetColumn = i < targetColumns.size() ? targetColumns.get(i) : original.getTarget().getName();
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(new ColumnRef(targetTable, targetColumn));
                lineage.setSources(original.getSources());
                lineage.setExpression(original.getExpression());
                mapped.add(lineage);
            }
            return mapped;
        }

        private List<ColumnLineage> readUpdateAssignments(int start, int end, TableRef defaultTarget) {
            List<ColumnLineage> result = new ArrayList<>();
            for (Range range : splitTopLevel(start, end, MySqlLineageLexer.COMMA)) {
                int equals = indexOfTopLevel(range.start, range.end, MySqlLineageLexer.EQ);
                if (equals <= range.start || equals >= range.end - 1) {
                    continue;
                }
                ColumnRef target = readAssignmentTarget(range.start, equals, defaultTarget);
                if (target == null) {
                    continue;
                }
                List<ColumnRef> sources = resolveSources(sourceColumns(equals + 1, range.end), new ArrayList<>(inputs));
                if (sources == null) {
                    continue;
                }
                ColumnLineage lineage = new ColumnLineage();
                lineage.setTarget(target);
                lineage.setSources(sources);
                lineage.setExpression(text(range.start, range.end));
                result.add(lineage);
            }
            return result;
        }

        private ColumnRef readAssignmentTarget(int start, int end, TableRef defaultTarget) {
            IdentifierRead targetRead = readIdentifierParts(start, end);
            if (targetRead.parts.isEmpty() || targetRead.nextIndex != end) {
                return null;
            }
            String column = targetRead.parts.get(targetRead.parts.size() - 1);
            TableRef table = defaultTarget;
            if (targetRead.parts.size() >= 2) {
                String qualifier = targetRead.parts.get(targetRead.parts.size() - 2).toLowerCase(Locale.ROOT);
                table = aliases.get(qualifier);
            }
            if (table == null) {
                return null;
            }
            outputs.add(table);
            return new ColumnRef(table, column);
        }

        private TableScan readTable(int start, int end) {
            int i = start;
            if (i < end && is(i, MySqlLineageLexer.TABLE)) {
                i++;
            }
            if (i >= end || !isIdentifier(i)) {
                return null;
            }
            IdentifierRead tableName = readIdentifierParts(i, end);
            TableRef table = tableRef(tableName.parts);
            int next = tableName.nextIndex;
            String alias = null;
            if (next < end && is(next, MySqlLineageLexer.AS)) {
                next++;
            }
            if (next < end && isIdentifier(next) && !isClauseBoundary(next) && !isJoinToken(next)) {
                alias = clean(tokens.get(next).getText());
                next++;
            }
            return new TableScan(table, alias, next);
        }

        private IdentifierRead readIdentifierParts(int start, int end) {
            List<String> parts = new ArrayList<>();
            int i = start;
            while (i < end && isIdentifier(i)) {
                parts.add(clean(tokens.get(i).getText()));
                if (i + 1 < end && is(i + 1, MySqlLineageLexer.DOT)) {
                    i += 2;
                } else {
                    i++;
                    break;
                }
            }
            return new IdentifierRead(parts, i);
        }

        private List<String> readColumnList(int start) {
            List<String> columns = new ArrayList<>();
            if (start >= tokens.size() || !is(start, MySqlLineageLexer.LPAREN)) {
                return columns;
            }
            int end = matchingParen(start, tokens.size());
            for (Range range : splitTopLevel(start + 1, end, MySqlLineageLexer.COMMA)) {
                if (range.start < range.end) {
                    columns.add(clean(text(range.start, range.end)));
                }
            }
            return columns;
        }

        private void addInputs(List<TableRef> tables) {
            inputs.addAll(tables);
        }

        private void registerDerived(String relationName, SelectLineage lineage) {
            Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
            for (ColumnLineage columnLineage : lineage.columnLineage) {
                columns.put(columnLineage.getTarget().getName(), columnLineage.getSources());
            }
            String key = relationName.toLowerCase(Locale.ROOT);
            derivedColumnLineage.put(key, columns);
            derivedInputs.put(key, lineage.inputs);
        }

        private void registerAlias(TableScan scan) {
            aliases.put(scan.table.getName().toLowerCase(Locale.ROOT), scan.table);
            if (scan.alias != null) {
                aliases.put(scan.alias.toLowerCase(Locale.ROOT), scan.table);
            }
        }

        private boolean isTableStart(int index) {
            return isIdentifier(index);
        }

        private boolean isIdentifier(int index) {
            int type = tokens.get(index).getType();
            return type == MySqlLineageLexer.IDENTIFIER
                    || type == MySqlLineageLexer.BACKQUOTED_IDENTIFIER;
        }

        private boolean isClauseBoundary(int index) {
            int type = tokens.get(index).getType();
            return type == MySqlLineageLexer.WHERE
                    || type == MySqlLineageLexer.GROUP
                    || type == MySqlLineageLexer.HAVING
                    || type == MySqlLineageLexer.ORDER
                    || type == MySqlLineageLexer.LIMIT
                    || type == MySqlLineageLexer.UNION
                    || type == MySqlLineageLexer.SET
                    || type == MySqlLineageLexer.ON
                    || type == MySqlLineageLexer.VALUES
                    || type == MySqlLineageLexer.DUPLICATE;
        }

        private boolean isJoinToken(int index) {
            int type = tokens.get(index).getType();
            return type == MySqlLineageLexer.JOIN
                    || type == MySqlLineageLexer.INNER
                    || type == MySqlLineageLexer.LEFT
                    || type == MySqlLineageLexer.RIGHT
                    || type == MySqlLineageLexer.FULL
                    || type == MySqlLineageLexer.CROSS
                    || type == MySqlLineageLexer.OUTER;
        }

        private int indexOfTopLevel(int start, int end, int tokenType) {
            int depth = 0;
            for (int i = start; i < end; i++) {
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, MySqlLineageLexer.RPAREN)) {
                    depth--;
                } else if (depth == 0 && is(i, tokenType)) {
                    return i;
                }
            }
            return -1;
        }

        private int firstTopLevelClauseBoundary(int start, int end) {
            int depth = 0;
            for (int i = start; i < end; i++) {
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, MySqlLineageLexer.RPAREN)) {
                    depth--;
                } else if (depth == 0 && isClauseBoundary(i)) {
                    return i;
                }
            }
            return end;
        }

        private int registerCtes(int start, int end) {
            int i = start + 1;
            while (i < end) {
                if (!isIdentifier(i)) {
                    return -1;
                }
                String name = clean(tokens.get(i).getText());
                i++;
                if (i < end && is(i, MySqlLineageLexer.LPAREN)) {
                    i = matchingParen(i, end) + 1;
                }
                if (i >= end || !is(i, MySqlLineageLexer.AS) || i + 1 >= end || !is(i + 1, MySqlLineageLexer.LPAREN)) {
                    return -1;
                }
                int close = matchingParen(i + 1, end);
                int cteSelect = indexOfTopLevel(i + 2, close, MySqlLineageLexer.SELECT);
                if (cteSelect >= 0) {
                    registerDerived(name, parseSelect(cteSelect, close));
                }
                i = close + 1;
                if (i < end && is(i, MySqlLineageLexer.COMMA)) {
                    i++;
                    continue;
                }
                if (i < end && is(i, MySqlLineageLexer.SELECT)) {
                    return i;
                }
                return indexOfTopLevel(i, end, MySqlLineageLexer.SELECT);
            }
            return -1;
        }

        private String readAlias(int start, int end) {
            int i = start;
            if (i < end && is(i, MySqlLineageLexer.AS)) {
                i++;
            }
            if (i < end && isIdentifier(i)) {
                return clean(tokens.get(i).getText());
            }
            return null;
        }

        private int nextAfterAlias(int start, int end) {
            int i = start;
            if (i < end && is(i, MySqlLineageLexer.AS)) {
                i++;
            }
            if (i < end && isIdentifier(i)) {
                return i + 1;
            }
            return start;
        }

        private int firstPositive(int first, int second, int third, int fallback) {
            int result = fallback;
            if (first >= 0 && first < result) {
                result = first;
            }
            if (second >= 0 && second < result) {
                result = second;
            }
            if (third >= 0 && third < result) {
                result = third;
            }
            return result;
        }

        private List<Range> splitTopLevel(int start, int end, int separator) {
            List<Range> ranges = new ArrayList<>();
            int depth = 0;
            int current = start;
            for (int i = start; i < end; i++) {
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, MySqlLineageLexer.RPAREN)) {
                    depth--;
                } else if (depth == 0 && is(i, separator)) {
                    ranges.add(new Range(current, i));
                    current = i + 1;
                }
            }
            ranges.add(new Range(current, end));
            return ranges;
        }

        private int matchingParen(int start, int end) {
            int depth = 0;
            for (int i = start; i < end; i++) {
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, MySqlLineageLexer.RPAREN)) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return end - 1;
        }

        private int depth(int start, int endInclusive) {
            int depth = 0;
            for (int i = start; i <= endInclusive; i++) {
                if (is(i, MySqlLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, MySqlLineageLexer.RPAREN)) {
                    depth--;
                }
            }
            return depth;
        }

        private int skipIf(int index, int first, int second) {
            if (is(index, first)) {
                return index + 1;
            }
            if (is(index, second)) {
                return index + 1;
            }
            return index;
        }

        private boolean is(int index, int tokenType) {
            return index >= 0 && index < tokens.size() && tokens.get(index).getType() == tokenType;
        }

        private String text(int start, int end) {
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (builder.length() > 0 && needsSpace(tokens.get(i - 1), tokens.get(i))) {
                    builder.append(' ');
                }
                builder.append(tokens.get(i).getText());
            }
            return builder.toString();
        }

        private boolean needsSpace(Token left, Token right) {
            return isIdentifierText(left.getText()) && isIdentifierText(right.getText());
        }

        private boolean isIdentifierText(String text) {
            return !text.isEmpty() && Character.isLetterOrDigit(text.charAt(0));
        }

        private boolean isKeywordLike(String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            return "as".equals(normalized)
                    || "distinct".equals(normalized)
                    || "all".equals(normalized);
        }

        private static String clean(String text) {
            String value = text.trim();
            if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
                return value.substring(1, value.length() - 1).replace("``", "`");
            }
            return value;
        }

        private static TableRef tableRef(List<String> parts) {
            if (parts.size() >= 3) {
                return new TableRef(parts.get(parts.size() - 3), parts.get(parts.size() - 2), parts.get(parts.size() - 1));
            }
            if (parts.size() == 2) {
                return new TableRef(null, parts.get(0), parts.get(1));
            }
            return new TableRef(null, null, parts.get(0));
        }

        private static List<Token> trimSemicolon(List<Token> input) {
            List<Token> trimmed = new ArrayList<>(input);
            while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1).getType() == MySqlLineageLexer.SEMI) {
                trimmed.remove(trimmed.size() - 1);
            }
            return trimmed;
        }
    }

    private static class SelectLineage {
        private final List<TableRef> inputs = new ArrayList<>();
        private final List<ColumnLineage> columnLineage = new ArrayList<>();
    }

    private static class TableScan {
        private final TableRef table;
        private final String alias;
        private final int nextIndex;

        TableScan(TableRef table, String alias, int nextIndex) {
            this.table = table;
            this.alias = alias;
            this.nextIndex = nextIndex;
        }
    }

    private static class IdentifierRead {
        private final List<String> parts;
        private final int nextIndex;

        IdentifierRead(List<String> parts, int nextIndex) {
            this.parts = parts;
            this.nextIndex = nextIndex;
        }
    }

    private static class Range {
        private final int start;
        private final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class SourceColumn {
        private final String qualifier;
        private final String name;
        private final String raw;

        SourceColumn(String qualifier, String name, String raw) {
            this.qualifier = qualifier;
            this.name = name;
            this.raw = raw;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SourceColumn)) {
                return false;
            }
            SourceColumn that = (SourceColumn) o;
            return raw.equals(that.raw);
        }

        @Override
        public int hashCode() {
            return raw.hashCode();
        }
    }

    private static class Projection {
        private final String targetColumn;
        private final List<ColumnRef> sources;

        Projection(String targetColumn, List<ColumnRef> sources) {
            this.targetColumn = targetColumn;
            this.sources = sources;
        }
    }
}
