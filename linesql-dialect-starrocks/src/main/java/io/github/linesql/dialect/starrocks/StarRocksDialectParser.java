package io.github.linesql.dialect.starrocks;

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
import io.github.linesql.dialect.starrocks.antlr.StarRocksLineageLexer;
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

public class StarRocksDialectParser implements DialectParser {
    @Override
    public SqlDialect dialect() {
        return SqlDialect.STARROCKS;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        LineageResult result = new LineageResult();
        result.setDialect(SqlDialect.STARROCKS);
        result.setDialectConfidence(1.0);
        try {
            StarRocksLineageVisitor visitor = new StarRocksLineageVisitor(tokens(sql), result);
            visitor.parse();
            if (result.getColumnLineage().isEmpty()
                    && (result.getStatementType() == StatementType.SELECT
                    || result.getStatementType() == StatementType.INSERT
                    || result.getStatementType() == StatementType.CREATE_TABLE_AS_SELECT
                    || result.getStatementType() == StatementType.CREATE_VIEW)) {
                result.getDiagnostics().add(Diagnostic.warning(
                        "STARROCKS_COLUMN_LINEAGE_NOT_IMPLEMENTED",
                        "StarRocks column lineage was not produced for this statement shape."));
            }
        } catch (RuntimeException e) {
            result.getDiagnostics().add(Diagnostic.error("STARROCKS_PARSE_ERROR", e.getMessage()));
        }
        return result;
    }

    private static List<Token> tokens(String sql) {
        StarRocksLineageLexer lexer = new StarRocksLineageLexer(CharStreams.fromString(sql));
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

    private static class StarRocksLineageVisitor {
        private final List<Token> tokens;
        private final LineageResult result;
        private final Map<String, TableRef> aliases = new LinkedHashMap<>();
        private final Set<TableRef> inputs = new LinkedHashSet<>();
        private final Set<TableRef> outputs = new LinkedHashSet<>();

        StarRocksLineageVisitor(List<Token> tokens, LineageResult result) {
            this.tokens = trimSemicolon(tokens);
            this.result = result;
        }

        void parse() {
            if (tokens.isEmpty()) {
                result.getDiagnostics().add(Diagnostic.error("EMPTY_SQL", "SQL input is empty."));
                return;
            }
            if (is(0, StarRocksLineageLexer.SELECT)) {
                parseSelectStatement(0, tokens.size(), null, new ArrayList<String>());
            } else if (is(0, StarRocksLineageLexer.INSERT)) {
                parseInsert();
            } else if (is(0, StarRocksLineageLexer.CREATE)) {
                parseCreate();
            } else {
                result.setStatementType(StatementType.UNKNOWN);
                result.getDiagnostics().add(Diagnostic.warning(
                        "STARROCKS_STATEMENT_NOT_SUPPORTED",
                        "StarRocks statement type is not supported by the current MVP parser."));
            }
            result.setInputTables(new ArrayList<>(inputs));
            result.setOutputTables(new ArrayList<>(outputs));
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
                    indexOfTopLevel(0, tokens.size(), StarRocksLineageLexer.INTO),
                    indexOfTopLevel(0, tokens.size(), StarRocksLineageLexer.OVERWRITE),
                    -1,
                    -1);
            int select = indexOfTopLevel(0, tokens.size(), StarRocksLineageLexer.SELECT);
            result.setStatementType(StatementType.INSERT);
            if (targetStart < 0 || select < 0) {
                return;
            }
            TableScan target = readTable(skipTableKeyword(targetStart + 1), select);
            if (target == null) {
                return;
            }
            SelectLineage selectLineage = parseSelect(select, tokens.size());
            inputs.addAll(selectLineage.inputs);
            outputs.add(target.table);
            result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, new ArrayList<String>()));
        }

        private void parseCreate() {
            int objectTypeIndex = createObjectTypeIndex();
            if (objectTypeIndex < 0) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            int objectType = tokens.get(objectTypeIndex).getType();
            int objectStart = skipIfNotExists(objectTypeIndex + 1);
            int select = indexOfTopLevel(objectStart, tokens.size(), StarRocksLineageLexer.SELECT);
            TableScan target = readTable(objectStart, select < 0 ? tokens.size() : select);
            if (target != null) {
                outputs.add(target.table);
            }
            if (select < 0 || target == null) {
                result.setStatementType(StatementType.UNKNOWN);
                return;
            }
            result.setStatementType(objectType == StarRocksLineageLexer.VIEW
                    ? StatementType.CREATE_VIEW
                    : StatementType.CREATE_TABLE_AS_SELECT);
            SelectLineage selectLineage = parseSelect(select, tokens.size());
            inputs.addAll(selectLineage.inputs);
            result.setColumnLineage(targetLineage(selectLineage.columnLineage, target.table, new ArrayList<String>()));
        }

        private SelectLineage parseSelect(int start, int end) {
            int from = indexOfTopLevel(start, end, StarRocksLineageLexer.FROM);
            SelectLineage lineage = new SelectLineage();
            if (from >= 0) {
                lineage.inputs.addAll(readTableSources(from + 1, end));
            }
            lineage.columnLineage.addAll(readProjections(start + 1, from < 0 ? end : from, lineage.inputs));
            return lineage;
        }

        private List<TableRef> readTableSources(int start, int end) {
            List<TableRef> tables = new ArrayList<>();
            int i = start;
            while (i < end) {
                if (isClauseBoundary(i)) {
                    break;
                }
                if (isJoinToken(i) || is(i, StarRocksLineageLexer.COMMA)) {
                    i++;
                    continue;
                }
                if (isIdentifier(i)) {
                    TableScan scan = readTable(i, end);
                    if (scan != null) {
                        tables.add(scan.table);
                        registerAlias(scan);
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
            for (Range range : splitTopLevel(start, end, StarRocksLineageLexer.COMMA)) {
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
            if (start >= end || is(start, StarRocksLineageLexer.STAR)) {
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
                if (depth(start, i) == 0 && is(i, StarRocksLineageLexer.AS) && i + 1 < end) {
                    return i + 1;
                }
            }
            if (end - start >= 2
                    && isIdentifier(end - 1)
                    && !isIdentifier(end - 2)
                    && !is(end - 2, StarRocksLineageLexer.DOT)) {
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
                if (i + 1 < end && is(i + 1, StarRocksLineageLexer.LPAREN)) {
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

        private TableScan readTable(int start, int end) {
            int i = start;
            if (i >= end || !isIdentifier(i)) {
                return null;
            }
            IdentifierRead tableName = readIdentifierParts(i, end);
            TableRef table = tableRef(tableName.parts);
            int next = tableName.nextIndex;
            String alias = null;
            if (next < end && is(next, StarRocksLineageLexer.AS)) {
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
                if (i + 1 < end && is(i + 1, StarRocksLineageLexer.DOT)) {
                    i += 2;
                } else {
                    i++;
                    break;
                }
            }
            return new IdentifierRead(parts, i);
        }

        private void registerAlias(TableScan scan) {
            aliases.put(scan.table.getName().toLowerCase(Locale.ROOT), scan.table);
            if (scan.alias != null) {
                aliases.put(scan.alias.toLowerCase(Locale.ROOT), scan.table);
            }
        }

        private int createObjectTypeIndex() {
            int i = 1;
            if (is(i, StarRocksLineageLexer.TEMPORARY) || is(i, StarRocksLineageLexer.EXTERNAL)) {
                i++;
            }
            if (is(i, StarRocksLineageLexer.TABLE) || is(i, StarRocksLineageLexer.VIEW)) {
                return i;
            }
            return -1;
        }

        private int skipIfNotExists(int index) {
            if (is(index, StarRocksLineageLexer.IF)
                    && is(index + 1, StarRocksLineageLexer.NOT)
                    && is(index + 2, StarRocksLineageLexer.EXISTS)) {
                return index + 3;
            }
            return index;
        }

        private int skipTableKeyword(int index) {
            return is(index, StarRocksLineageLexer.TABLE) ? index + 1 : index;
        }

        private boolean isClauseBoundary(int index) {
            int type = tokens.get(index).getType();
            return type == StarRocksLineageLexer.WHERE
                    || type == StarRocksLineageLexer.GROUP
                    || type == StarRocksLineageLexer.HAVING
                    || type == StarRocksLineageLexer.ORDER
                    || type == StarRocksLineageLexer.LIMIT
                    || type == StarRocksLineageLexer.UNION
                    || type == StarRocksLineageLexer.ON
                    || type == StarRocksLineageLexer.PARTITION
                    || type == StarRocksLineageLexer.STORED
                    || type == StarRocksLineageLexer.ROW;
        }

        private boolean isJoinToken(int index) {
            int type = tokens.get(index).getType();
            return type == StarRocksLineageLexer.JOIN
                    || type == StarRocksLineageLexer.INNER
                    || type == StarRocksLineageLexer.LEFT
                    || type == StarRocksLineageLexer.RIGHT
                    || type == StarRocksLineageLexer.FULL
                    || type == StarRocksLineageLexer.CROSS
                    || type == StarRocksLineageLexer.OUTER;
        }

        private boolean isIdentifier(int index) {
            int type = tokens.get(index).getType();
            return type == StarRocksLineageLexer.IDENTIFIER
                    || type == StarRocksLineageLexer.BACKQUOTED_IDENTIFIER;
        }

        private int indexOfTopLevel(int start, int end, int tokenType) {
            int depth = 0;
            for (int i = start; i < end; i++) {
                if (is(i, StarRocksLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, StarRocksLineageLexer.RPAREN)) {
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
                if (is(i, StarRocksLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, StarRocksLineageLexer.RPAREN)) {
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
                if (is(i, StarRocksLineageLexer.LPAREN)) {
                    depth++;
                } else if (is(i, StarRocksLineageLexer.RPAREN)) {
                    depth--;
                }
            }
            return depth;
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
            while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1).getType() == StarRocksLineageLexer.SEMI) {
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
