package io.github.linesql.core.internal;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SimpleTokenLineageParser {
    private SimpleTokenLineageParser() {
    }

    public static LineageResult parse(List<Token> tokens, Config config) {
        LineageResult result = new LineageResult();
        result.setDialect(config.dialect);
        result.setDialectConfidence(1.0);
        try {
            Walker walker = new Walker(tokens, result, config);
            walker.parse();
            if (result.getColumnLineage().isEmpty()
                    && (result.getStatementType() == StatementType.SELECT
                    || result.getStatementType() == StatementType.INSERT
                    || result.getStatementType() == StatementType.CREATE_TABLE_AS_SELECT
                    || result.getStatementType() == StatementType.CREATE_VIEW)) {
                result.getDiagnostics().add(Diagnostic.warning(
                        config.diagnosticPrefix + "_COLUMN_LINEAGE_NOT_IMPLEMENTED",
                        config.displayName + " column lineage was not produced for this statement shape."));
            }
        } catch (RuntimeException e) {
            result.getDiagnostics().add(Diagnostic.error(config.diagnosticPrefix + "_PARSE_ERROR", e.getMessage()));
        }
        return result;
    }

    public static final class Config {
        private final SqlDialect dialect;
        private final String displayName;
        private final String diagnosticPrefix;
        private int select = -1;
        private int insert = -1;
        private int update = -1;
        private int delete = -1;
        private int create = -1;
        private int overwrite = -1;
        private int into = -1;
        private int external = -1;
        private int temporary = -1;
        private int table = -1;
        private int view = -1;
        private int ifToken = -1;
        private int not = -1;
        private int exists = -1;
        private int as = -1;
        private int set = -1;
        private int with = -1;
        private int from = -1;
        private int using = -1;
        private int join = -1;
        private int inner = -1;
        private int left = -1;
        private int right = -1;
        private int full = -1;
        private int cross = -1;
        private int outer = -1;
        private int on = -1;
        private int where = -1;
        private int group = -1;
        private int having = -1;
        private int order = -1;
        private int limit = -1;
        private int union = -1;
        private int partition = -1;
        private int stored = -1;
        private int row = -1;
        private int identifier = -1;
        private int backquotedIdentifier = -1;
        private int dot = -1;
        private int comma = -1;
        private int semi = -1;
        private int lparen = -1;
        private int rparen = -1;
        private int star = -1;
        private int eq = -1;
        private final Set<String> ignoredTableNames = new LinkedHashSet<>();
        private final Set<String> leadingProjectionKeywords = new LinkedHashSet<>();
        private final Set<Integer> leadingProjectionTokens = new LinkedHashSet<>();
        private final Set<Integer> extraClauseBoundaries = new LinkedHashSet<>();

        private Config(SqlDialect dialect, String displayName, String diagnosticPrefix) {
            this.dialect = dialect;
            this.displayName = displayName;
            this.diagnosticPrefix = diagnosticPrefix;
        }

        public static Config forDialect(SqlDialect dialect, String displayName, String diagnosticPrefix) {
            return new Config(dialect, displayName, diagnosticPrefix);
        }

        public Config select(int token) {
            this.select = token;
            return this;
        }

        public Config insert(int token) {
            this.insert = token;
            return this;
        }

        public Config update(int token) {
            this.update = token;
            return this;
        }

        public Config delete(int token) {
            this.delete = token;
            return this;
        }

        public Config create(int token) {
            this.create = token;
            return this;
        }

        public Config overwrite(int token) {
            this.overwrite = token;
            return this;
        }

        public Config into(int token) {
            this.into = token;
            return this;
        }

        public Config external(int token) {
            this.external = token;
            return this;
        }

        public Config temporary(int token) {
            this.temporary = token;
            return this;
        }

        public Config table(int token) {
            this.table = token;
            return this;
        }

        public Config view(int token) {
            this.view = token;
            return this;
        }

        public Config ifToken(int token) {
            this.ifToken = token;
            return this;
        }

        public Config not(int token) {
            this.not = token;
            return this;
        }

        public Config exists(int token) {
            this.exists = token;
            return this;
        }

        public Config as(int token) {
            this.as = token;
            return this;
        }

        public Config set(int token) {
            this.set = token;
            return this;
        }

        public Config with(int token) {
            this.with = token;
            return this;
        }

        public Config from(int token) {
            this.from = token;
            return this;
        }

        public Config using(int token) {
            this.using = token;
            return this;
        }

        public Config join(int token) {
            this.join = token;
            return this;
        }

        public Config inner(int token) {
            this.inner = token;
            return this;
        }

        public Config left(int token) {
            this.left = token;
            return this;
        }

        public Config right(int token) {
            this.right = token;
            return this;
        }

        public Config full(int token) {
            this.full = token;
            return this;
        }

        public Config cross(int token) {
            this.cross = token;
            return this;
        }

        public Config outer(int token) {
            this.outer = token;
            return this;
        }

        public Config on(int token) {
            this.on = token;
            return this;
        }

        public Config where(int token) {
            this.where = token;
            return this;
        }

        public Config group(int token) {
            this.group = token;
            return this;
        }

        public Config having(int token) {
            this.having = token;
            return this;
        }

        public Config order(int token) {
            this.order = token;
            return this;
        }

        public Config limit(int token) {
            this.limit = token;
            return this;
        }

        public Config union(int token) {
            this.union = token;
            return this;
        }

        public Config partition(int token) {
            this.partition = token;
            return this;
        }

        public Config stored(int token) {
            this.stored = token;
            return this;
        }

        public Config row(int token) {
            this.row = token;
            return this;
        }

        public Config identifier(int token) {
            this.identifier = token;
            return this;
        }

        public Config backquotedIdentifier(int token) {
            this.backquotedIdentifier = token;
            return this;
        }

        public Config dot(int token) {
            this.dot = token;
            return this;
        }

        public Config comma(int token) {
            this.comma = token;
            return this;
        }

        public Config semi(int token) {
            this.semi = token;
            return this;
        }

        public Config lparen(int token) {
            this.lparen = token;
            return this;
        }

        public Config rparen(int token) {
            this.rparen = token;
            return this;
        }

        public Config star(int token) {
            this.star = token;
            return this;
        }

        public Config eq(int token) {
            this.eq = token;
            return this;
        }

        public Config ignoredTableName(String tableName) {
            this.ignoredTableNames.add(tableName.toLowerCase(Locale.ROOT));
            return this;
        }

        public Config leadingProjectionKeyword(String keyword) {
            this.leadingProjectionKeywords.add(keyword.toLowerCase(Locale.ROOT));
            return this;
        }

        public Config leadingProjectionToken(int token) {
            this.leadingProjectionTokens.add(token);
            return this;
        }

        public Config extraClauseBoundary(int token) {
            this.extraClauseBoundaries.add(token);
            return this;
        }
    }

    private static class Walker {
        private final List<Token> tokens;
        private final LineageResult result;
        private final Config config;
        private final Map<String, TableRef> aliases = new LinkedHashMap<>();
        private final Map<String, SelectLineage> derivedRelations = new LinkedHashMap<>();
        private Map<String, SelectLineage> activeDerivedRelations = new LinkedHashMap<>();
        private final Set<TableRef> inputs = new LinkedHashSet<>();
        private final Set<TableRef> outputs = new LinkedHashSet<>();

        Walker(List<Token> tokens, LineageResult result, Config config) {
            this.result = result;
            this.config = config;
            this.tokens = trimSemicolon(tokens);
        }

        void parse() {
            if (tokens.isEmpty()) {
                result.getDiagnostics().add(Diagnostic.error("EMPTY_SQL", "SQL input is empty."));
                return;
            }
            if (is(0, config.select)) {
                parseSelectStatement(0, tokens.size(), null, new ArrayList<String>());
            } else if (is(0, config.with)) {
                parseWithSelect();
            } else if (is(0, config.insert)) {
                parseInsert();
            } else if (is(0, config.update)) {
                parseUpdate();
            } else if (is(0, config.delete)) {
                parseDelete();
            } else if (is(0, config.create)) {
                parseCreate();
            } else {
                result.setStatementType(StatementType.UNKNOWN);
                result.getDiagnostics().add(Diagnostic.warning(
                        config.diagnosticPrefix + "_STATEMENT_NOT_SUPPORTED",
                        config.displayName + " statement type is not supported by the current MVP parser."));
            }
            result.setInputTables(new ArrayList<>(inputs));
            result.setOutputTables(new ArrayList<>(outputs));
        }

        private void parseWithSelect() {
            int select = registerCtes(0, tokens.size());
            if (select < 0) {
                result.setStatementType(StatementType.SELECT);
                result.getDiagnostics().add(Diagnostic.warning(
                        config.diagnosticPrefix + "_CTE_NOT_SUPPORTED",
                        config.displayName + " CTE shape is not supported by the current parser."));
                return;
            }
            parseSelectStatement(select, tokens.size(), null, new ArrayList<String>());
        }

        private void parseSelectStatement(int start, int end, TableRef outputTable, List<String> targetColumns) {
            result.setStatementType(outputTable == null ? StatementType.SELECT : StatementType.INSERT);
            SelectLineage select = parseSelect(start, end);
            inputs.addAll(select.inputs);
            if (outputTable != null) {
                outputs.add(outputTable);
            }
            result.setColumnLineage(targetLineage(select.columnLineage, outputTable, targetColumns));
        }

        private void parseInsert() {
            int targetStart = firstPositive(
                    indexOfTopLevel(0, tokens.size(), config.into),
                    indexOfTopLevel(0, tokens.size(), config.overwrite),
                    -1,
                    -1);
            int select = indexOfTopLevel(0, tokens.size(), config.select);
            result.setStatementType(StatementType.INSERT);
            if (targetStart < 0 || select < 0) {
                return;
            }
            TableScan target = readTable(skipTableKeyword(targetStart + 1), select);
            if (target == null) {
                return;
            }
            List<String> targetColumns = readColumnList(target.nextIndex);
            SelectLineage selectLineage = parseQuery(target.nextIndex, tokens.size(), select);
            inputs.addAll(selectLineage.inputs);
            outputs.add(target.table);
            result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, targetColumns));
        }

        private void parseUpdate() {
            result.setStatementType(StatementType.UPDATE);
            int set = indexOfTopLevel(0, tokens.size(), config.set);
            if (set < 0) {
                return;
            }
            TableScan target = readTable(1, set);
            if (target == null) {
                return;
            }
            inputs.add(target.table);
            outputs.add(target.table);
            registerAlias(target);
            inputs.addAll(readTableSources(target.nextIndex, set));

            int from = indexOfTopLevel(set + 1, tokens.size(), config.from);
            if (from >= 0) {
                inputs.addAll(readTableSources(from + 1, tokens.size()));
            }

            int setEnd = firstPositive(from, indexOfTopLevel(set + 1, tokens.size(), config.where), -1, tokens.size());
            result.setColumnLineage(readUpdateAssignments(set + 1, setEnd, target.table));
        }

        private void parseDelete() {
            result.setStatementType(StatementType.DELETE);
            int firstFrom = indexOfTopLevel(0, tokens.size(), config.from);
            int using = indexOfTopLevel(0, tokens.size(), config.using);
            if (firstFrom < 0) {
                return;
            }

            if (is(1, config.from)) {
                int targetEnd = firstPositive(using, indexOfTopLevel(firstFrom + 1, tokens.size(), config.where), -1, tokens.size());
                TableScan target = readTable(firstFrom + 1, targetEnd);
                if (target != null) {
                    inputs.add(target.table);
                    outputs.add(target.table);
                    registerAlias(target);
                }
                if (using >= 0) {
                    inputs.addAll(readTableSources(using + 1, tokens.size()));
                }
                return;
            }

            String targetAlias = isIdentifier(1) ? clean(tokens.get(1).getText()).toLowerCase(Locale.ROOT) : null;
            List<TableRef> sources = readTableSources(firstFrom + 1, tokens.size());
            inputs.addAll(sources);
            if (targetAlias != null && aliases.containsKey(targetAlias)) {
                outputs.add(aliases.get(targetAlias));
            } else if (!sources.isEmpty()) {
                outputs.add(sources.get(0));
            }
        }

        private void parseCreate() {
            int objectTypeIndex = createObjectTypeIndex();
            if (objectTypeIndex < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int objectType = tokens.get(objectTypeIndex).getType();
            int objectStart = skipIfNotExists(objectTypeIndex + 1);
            int select = indexOfTopLevel(objectStart, tokens.size(), config.select);
            TableScan target = readTable(objectStart, select < 0 ? tokens.size() : select);
            if (target != null) {
                outputs.add(target.table);
            }
            if (select < 0 || target == null) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            result.setStatementType(objectType == config.view
                    ? StatementType.CREATE_VIEW
                    : StatementType.CREATE_TABLE_AS_SELECT);
            List<String> targetColumns = readColumnList(target.nextIndex);
            SelectLineage selectLineage = parseQuery(target.nextIndex, tokens.size(), select);
            inputs.addAll(selectLineage.inputs);
            result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, targetColumns));
        }

        private SelectLineage parseQuery(int start, int end, int fallbackSelect) {
            int with = indexOfTopLevel(start, end, config.with);
            if (with >= 0 && (fallbackSelect < 0 || with < fallbackSelect)) {
                int select = registerCtes(with, end);
                if (select >= 0) {
                    return parseSelect(select, end);
                }
            }
            return fallbackSelect >= 0 ? parseSelect(fallbackSelect, end) : new SelectLineage();
        }

        private SelectLineage parseSelect(int start, int end) {
            Map<String, TableRef> savedAliases = new LinkedHashMap<>(aliases);
            Map<String, SelectLineage> savedActiveDerivedRelations = activeDerivedRelations;
            activeDerivedRelations = new LinkedHashMap<>();
            int from = indexOfTopLevel(start, end, config.from);
            SelectLineage lineage = new SelectLineage();
            try {
                if (from >= 0) {
                    lineage.inputs.addAll(readTableSources(from + 1, end));
                }
                lineage.columnLineage.addAll(readProjections(start + 1, from < 0 ? end : from, lineage.inputs));
            } finally {
                aliases.clear();
                aliases.putAll(savedAliases);
                activeDerivedRelations = savedActiveDerivedRelations;
            }
            return lineage;
        }

        private List<TableRef> readTableSources(int start, int end) {
            List<TableRef> tables = new ArrayList<>();
            int i = start;
            while (i < end) {
                if (isClauseBoundary(i)) {
                    break;
                }
                if (isJoinToken(i) || is(i, config.comma)) {
                    i++;
                    continue;
                }
                if (is(i, config.lparen)) {
                    int close = matchingParen(i, end);
                    int nestedSelect = indexOfTopLevel(i + 1, close, config.select);
                    if (nestedSelect >= 0) {
                        SelectLineage nested = parseSelect(nestedSelect, close);
                        tables.addAll(nested.inputs);
                        String alias = readAlias(close + 1, end);
                        if (alias != null) {
                            activeDerivedRelations.put(lower(alias), nested);
                        }
                    }
                    i = nextAfterAlias(close + 1, end);
                    continue;
                }
                if (isIdentifier(i)) {
                    TableScan scan = readTable(i, end);
                    if (scan != null) {
                        SelectLineage derived = derivedRelations.get(lower(scan.table.getName()));
                        if (derived != null) {
                            tables.addAll(derived.inputs);
                            activeDerivedRelations.put(lower(scan.table.getName()), derived);
                            if (scan.alias != null) {
                                activeDerivedRelations.put(lower(scan.alias), derived);
                            }
                        } else if (!isIgnoredTable(scan.table)) {
                            tables.add(scan.table);
                            registerAlias(scan);
                        }
                        i = skipTableHint(scan.nextIndex, end);
                        continue;
                    }
                }
                i++;
            }
            return tables;
        }

        private int registerCtes(int start, int end) {
            int i = start + 1;
            while (i < end) {
                if (!isIdentifier(i)) {
                    return -1;
                }
                String name = clean(tokens.get(i).getText());
                i++;
                List<String> cteColumns = new ArrayList<>();
                if (is(i, config.lparen)) {
                    int close = matchingParen(i, end);
                    cteColumns = readIdentifierList(i + 1, close);
                    i = close + 1;
                }
                if (!is(i, config.as) || !is(i + 1, config.lparen)) {
                    return -1;
                }
                int close = matchingParen(i + 1, end);
                int cteSelect = indexOfTopLevel(i + 2, close, config.select);
                if (cteSelect >= 0) {
                    SelectLineage cteLineage = parseSelect(cteSelect, close);
                    derivedRelations.put(lower(name), remapTargetColumns(cteLineage, cteColumns));
                }
                i = close + 1;
                if (is(i, config.comma)) {
                    i++;
                    continue;
                }
                if (is(i, config.select)) {
                    return i;
                }
                return indexOfTopLevel(i, end, config.select);
            }
            return -1;
        }

        private SelectLineage remapTargetColumns(SelectLineage lineage, List<String> targetColumns) {
            if (targetColumns.isEmpty()) {
                return lineage;
            }
            SelectLineage mapped = new SelectLineage();
            mapped.inputs.addAll(lineage.inputs);
            for (int i = 0; i < lineage.columnLineage.size(); i++) {
                ColumnLineage original = lineage.columnLineage.get(i);
                String targetColumn = i < targetColumns.size() ? targetColumns.get(i) : original.getTarget().getName();
                ColumnLineage column = new ColumnLineage();
                column.setTarget(new ColumnRef(null, targetColumn));
                column.setSources(original.getSources());
                column.setExpression(original.getExpression());
                mapped.columnLineage.add(column);
            }
            return mapped;
        }

        private List<ColumnLineage> readProjections(int start, int end, List<TableRef> inputTables) {
            List<ColumnLineage> result = new ArrayList<>();
            for (Range range : splitTopLevel(start, end, config.comma)) {
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
            start = skipLeadingProjectionKeywords(start, end);
            if (start >= end || is(start, config.star)) {
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
            return sources == null ? null : new Projection(target, sources);
        }

        private int aliasIndex(int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (depth(start, i) == 0 && is(i, config.as) && i + 1 < end) {
                    return i + 1;
                }
            }
            if (end - start >= 2
                    && isIdentifier(end - 1)
                    && !isIdentifier(end - 2)
                    && !is(end - 2, config.dot)) {
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
                if (i + 1 < end && is(i + 1, config.lparen)) {
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
                TableRef table = null;
                if (column.qualifier != null) {
                    SelectLineage derived = activeDerivedRelations.get(lower(column.qualifier));
                    if (derived != null) {
                        List<ColumnRef> derivedSources = resolveDerivedColumn(derived, column.name);
                        if (derivedSources == null) {
                            return null;
                        }
                        refs.addAll(derivedSources);
                        continue;
                    }
                    table = aliases.get(lower(column.qualifier));
                } else if (inputTables.size() == 1) {
                    List<ColumnRef> derivedSources = resolveUnqualifiedDerivedColumn(column.name);
                    if (derivedSources != null) {
                        refs.addAll(derivedSources);
                        continue;
                    }
                    table = inputTables.get(0);
                }
                if (table == null) {
                    return null;
                }
                refs.add(new ColumnRef(table, column.name));
            }
            return refs;
        }

        private List<ColumnRef> resolveUnqualifiedDerivedColumn(String name) {
            Set<SelectLineage> active = new LinkedHashSet<>(activeDerivedRelations.values());
            if (active.size() != 1) {
                return null;
            }
            return resolveDerivedColumn(active.iterator().next(), name);
        }

        private List<ColumnRef> resolveDerivedColumn(SelectLineage derived, String name) {
            for (ColumnLineage lineage : derived.columnLineage) {
                if (lineage.getTarget() != null && lineage.getTarget().getName().equalsIgnoreCase(name)) {
                    return lineage.getSources();
                }
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
            for (Range range : splitTopLevel(start, end, config.comma)) {
                int equals = indexOfTopLevel(range.start, range.end, config.eq);
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
            if (i >= end || !isIdentifier(i)) {
                return null;
            }
            IdentifierRead tableName = readIdentifierParts(i, end);
            TableRef table = tableRef(tableName.parts);
            int next = tableName.nextIndex;
            String alias = null;
            if (next < end && is(next, config.as)) {
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
                if (i + 1 < end && is(i + 1, config.dot)) {
                    i += 2;
                } else {
                    i++;
                    break;
                }
            }
            return new IdentifierRead(parts, i);
        }

        private List<String> readIdentifierList(int start, int end) {
            List<String> identifiers = new ArrayList<>();
            for (Range range : splitTopLevel(start, end, config.comma)) {
                IdentifierRead read = readIdentifierParts(range.start, range.end);
                if (!read.parts.isEmpty()) {
                    identifiers.add(read.parts.get(read.parts.size() - 1));
                }
            }
            return identifiers;
        }

        private List<String> readColumnList(int start) {
            if (!is(start, config.lparen)) {
                return new ArrayList<>();
            }
            int close = matchingParen(start, tokens.size());
            return readIdentifierList(start + 1, close);
        }

        private void registerAlias(TableScan scan) {
            aliases.put(scan.table.getName().toLowerCase(Locale.ROOT), scan.table);
            if (scan.alias != null) {
                aliases.put(scan.alias.toLowerCase(Locale.ROOT), scan.table);
            }
        }

        private int createObjectTypeIndex() {
            int i = 1;
            if (is(i, config.temporary) || is(i, config.external)) {
                i++;
            }
            if (is(i, config.table) || is(i, config.view)) {
                return i;
            }
            return -1;
        }

        private int skipIfNotExists(int index) {
            if (is(index, config.ifToken)
                    && is(index + 1, config.not)
                    && is(index + 2, config.exists)) {
                return index + 3;
            }
            return index;
        }

        private int skipTableKeyword(int index) {
            return is(index, config.table) ? index + 1 : index;
        }

        private int skipTableHint(int index, int end) {
            if (is(index, config.with) && is(index + 1, config.lparen)) {
                int depth = 0;
                for (int i = index + 1; i < end; i++) {
                    if (is(i, config.lparen)) {
                        depth++;
                    } else if (is(i, config.rparen)) {
                        depth--;
                        if (depth == 0) {
                            return i + 1;
                        }
                    }
                }
            }
            return index;
        }

        private int skipLeadingProjectionKeywords(int start, int end) {
            int i = start;
            while (i < end) {
                String keyword = clean(tokens.get(i).getText()).toLowerCase(Locale.ROOT);
                if (!config.leadingProjectionTokens.contains(tokens.get(i).getType())
                        && !config.leadingProjectionKeywords.contains(keyword)) {
                    break;
                }
                i++;
                if ("top".equals(keyword)) {
                    i = skipTopLimit(i, end);
                }
            }
            return i;
        }

        private int skipTopLimit(int index, int end) {
            if (is(index, config.lparen)) {
                int depth = 0;
                for (int i = index; i < end; i++) {
                    if (is(i, config.lparen)) {
                        depth++;
                    } else if (is(i, config.rparen)) {
                        depth--;
                        if (depth == 0) {
                            return i + 1;
                        }
                    }
                }
                return end;
            }
            return index < end ? index + 1 : index;
        }

        private int matchingParen(int open, int end) {
            int depth = 0;
            for (int i = open; i < end; i++) {
                if (is(i, config.lparen)) {
                    depth++;
                } else if (is(i, config.rparen)) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return end;
        }

        private String readAlias(int start, int end) {
            int i = start;
            if (is(i, config.as)) {
                i++;
            }
            if (i < end && isIdentifier(i) && !isClauseBoundary(i) && !isJoinToken(i)) {
                return clean(tokens.get(i).getText());
            }
            return null;
        }

        private int nextAfterAlias(int start, int end) {
            int i = start;
            if (is(i, config.as)) {
                i++;
            }
            if (i < end && isIdentifier(i) && !isClauseBoundary(i) && !isJoinToken(i)) {
                return i + 1;
            }
            return start;
        }

        private boolean isClauseBoundary(int index) {
            int type = tokens.get(index).getType();
            return type == config.where
                    || type == config.group
                    || type == config.having
                    || type == config.order
                    || type == config.limit
                    || type == config.union
                    || type == config.on
                    || type == config.using
                    || type == config.partition
                    || type == config.stored
                    || type == config.row
                    || config.extraClauseBoundaries.contains(type);
        }

        private boolean isJoinToken(int index) {
            int type = tokens.get(index).getType();
            return type == config.join
                    || type == config.inner
                    || type == config.left
                    || type == config.right
                    || type == config.full
                    || type == config.cross
                    || type == config.outer;
        }

        private boolean isIdentifier(int index) {
            int type = tokens.get(index).getType();
            return type == config.identifier
                    || type == config.backquotedIdentifier;
        }

        private int indexOfTopLevel(int start, int end, int tokenType) {
            int depth = 0;
            for (int i = start; i < end; i++) {
                if (is(i, config.lparen)) {
                    depth++;
                } else if (is(i, config.rparen)) {
                    depth--;
                } else if (depth == 0 && is(i, tokenType)) {
                    return i;
                }
            }
            return -1;
        }

        private int firstPositive(int first, int second, int third, int fallback) {
            int result = fallback;
            if (first >= 0 && (result < 0 || first < result)) {
                result = first;
            }
            if (second >= 0 && (result < 0 || second < result)) {
                result = second;
            }
            if (third >= 0 && (result < 0 || third < result)) {
                result = third;
            }
            return result;
        }

        private List<Range> splitTopLevel(int start, int end, int separator) {
            List<Range> ranges = new ArrayList<>();
            int depth = 0;
            int current = start;
            for (int i = start; i < end; i++) {
                if (is(i, config.lparen)) {
                    depth++;
                } else if (is(i, config.rparen)) {
                    depth--;
                } else if (depth == 0 && is(i, separator)) {
                    ranges.add(new Range(current, i));
                    current = i + 1;
                }
            }
            ranges.add(new Range(current, end));
            return ranges;
        }

        private int depth(int start, int endInclusive) {
            int depth = 0;
            for (int i = start; i <= endInclusive; i++) {
                if (is(i, config.lparen)) {
                    depth++;
                } else if (is(i, config.rparen)) {
                    depth--;
                }
            }
            return depth;
        }

        private boolean is(int index, int tokenType) {
            return tokenType >= 0
                    && index >= 0
                    && index < tokens.size()
                    && tokens.get(index).getType() == tokenType;
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

        private String clean(String text) {
            String value = text.trim();
            if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
                return value.substring(1, value.length() - 1).replace("``", "`");
            }
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1).replace("\"\"", "\"");
            }
            if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
                return value.substring(1, value.length() - 1).replace("]]", "]");
            }
            return value;
        }

        private TableRef tableRef(List<String> parts) {
            if (parts.size() >= 3) {
                return new TableRef(parts.get(parts.size() - 3), parts.get(parts.size() - 2), parts.get(parts.size() - 1));
            }
            if (parts.size() == 2) {
                return new TableRef(null, parts.get(0), parts.get(1));
            }
            return new TableRef(null, null, parts.get(0));
        }

        private boolean isIgnoredTable(TableRef table) {
            String fullName = table.getCatalog() == null
                    ? (table.getSchema() == null ? table.getName() : table.getSchema() + "." + table.getName())
                    : table.getCatalog() + "." + table.getSchema() + "." + table.getName();
            return config.ignoredTableNames.contains(fullName.toLowerCase(Locale.ROOT))
                    || config.ignoredTableNames.contains(table.getName().toLowerCase(Locale.ROOT));
        }

        private String lower(String value) {
            return value.toLowerCase(Locale.ROOT);
        }

        private List<Token> trimSemicolon(List<Token> input) {
            List<Token> trimmed = new ArrayList<>(input);
            while (!trimmed.isEmpty() && isToken(trimmed.get(trimmed.size() - 1), config.semi)) {
                trimmed.remove(trimmed.size() - 1);
            }
            return trimmed;
        }

        private boolean isToken(Token token, int tokenType) {
            return tokenType >= 0 && token.getType() == tokenType;
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
