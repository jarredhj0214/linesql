import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import io.github.linesql.core.model.TableRef;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class LineSqlBenchmark {
    private LineSqlBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            usage();
            return;
        }
        String command = args[0];
        if ("eval".equals(command)) {
            runEval(args);
        } else if ("no-column".equals(command)) {
            runNoColumn(args);
        } else if ("debug".equals(command)) {
            runDebug(args);
        } else {
            usage();
            throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void usage() {
        System.out.println("LineSQL benchmark tools");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  eval <input.tsv> <report.md> <failures.tsv>");
        System.out.println("  no-column <input.tsv> [samples.tsv]");
        System.out.println("  debug <sql-file>");
        System.out.println();
        System.out.println("Input TSV columns: taskRow<TAB>scriptIndex<TAB>sqlBase64");
    }

    private static void runEval(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("eval requires <input.tsv> <report.md> <failures.tsv>");
        }
        Path input = Paths.get(args[1]);
        Path report = Paths.get(args[2]);
        Path failures = Paths.get(args[3]);
        EvalStats stats = new EvalStats();
        List<FailureSample> samples = new ArrayList<FailureSample>();

        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(failures, StandardCharsets.UTF_8)) {
            writer.write("taskRow\tscriptIndex\tstatementIndex\terror\tsqlSnippet\n");
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                CorpusRow row = CorpusRow.parse(line);
                if (row != null) {
                    evaluateRow(row, stats, samples);
                }
            }
            for (FailureSample sample : samples) {
                writer.write(sample.toTsv());
                writer.newLine();
            }
        }

        String markdown = stats.toMarkdown(input, failures);
        Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        System.out.println(markdown);
        System.out.println("REPORT=" + report);
        System.out.println("FAILURES=" + failures);
    }

    private static void evaluateRow(CorpusRow row, EvalStats stats, List<FailureSample> samples) {
        stats.scripts++;
        boolean scriptError = false;
        boolean anyTable = false;
        boolean anyColumn = false;
        boolean anyOk = false;
        try {
            List<LineageResult> results = LineSql.parseScript(row.sql);
            if (results.isEmpty()) {
                scriptError = true;
                stats.increment(stats.errors, "EMPTY_RESULT");
                addFailure(samples, row, 0, "EMPTY_RESULT");
            }
            for (int i = 0; i < results.size(); i++) {
                LineageResult result = results.get(i);
                stats.statements++;
                boolean error = hasError(result);
                if (error) {
                    String key = diagKey(result);
                    stats.increment(stats.errors, key);
                    addFailure(samples, row, i + 1, key);
                } else {
                    stats.statementOk++;
                    anyOk = true;
                }
                if (!result.getDiagnostics().isEmpty()) {
                    stats.statementDiagnostics++;
                }
                SqlDialect dialect = result.getDialect() == null ? SqlDialect.UNKNOWN : result.getDialect();
                StatementType type = result.getStatementType() == null ? StatementType.UNKNOWN : result.getStatementType();
                stats.increment(stats.dialects, dialect.name());
                stats.increment(stats.statementTypes, type.name());
                boolean table = hasTables(result);
                boolean column = !result.getColumnLineage().isEmpty();
                if (table) {
                    stats.statementWithTable++;
                    anyTable = true;
                }
                if (column) {
                    stats.statementWithColumn++;
                    anyColumn = true;
                }
                if (error) {
                    scriptError = true;
                }
            }
        } catch (Throwable throwable) {
            stats.exceptions++;
            scriptError = true;
            String key = throwable.getClass().getSimpleName() + " | " + oneLine(throwable.getMessage(), 140);
            stats.increment(stats.errors, key);
            addFailure(samples, row, 0, key);
        }
        if (!scriptError) {
            stats.scriptOk++;
        } else if (anyOk || anyTable || anyColumn) {
            stats.scriptPartial++;
        }
        if (anyTable) {
            stats.scriptWithTable++;
        }
        if (anyColumn) {
            stats.scriptWithColumn++;
        }
    }

    private static void runNoColumn(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("no-column requires <input.tsv> [samples.tsv]");
        }
        Path input = Paths.get(args[1]);
        Path samplesPath = args.length > 2 ? Paths.get(args[2]) : null;
        Map<String, Long> byType = new TreeMap<String, Long>();
        Map<String, Long> byClass = new TreeMap<String, Long>();
        List<NoColumnSample> samples = new ArrayList<NoColumnSample>();
        long total = 0;

        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                CorpusRow row = CorpusRow.parse(line);
                if (row == null) {
                    continue;
                }
                List<LineageResult> results = LineSql.parseScript(row.sql);
                for (int i = 0; i < results.size(); i++) {
                    LineageResult result = results.get(i);
                    boolean table = hasTables(result);
                    if (!hasError(result) && table && result.getColumnLineage().isEmpty()) {
                        total++;
                        String statementType = String.valueOf(result.getStatementType());
                        String classification = classify(row.sql);
                        increment(byType, statementType);
                        increment(byClass, classification);
                        if (samples.size() < 200) {
                            samples.add(new NoColumnSample(row, i + 1, statementType, classification));
                        }
                    }
                }
            }
        }

        System.out.println("NO_COLUMN_WITH_TABLE=" + total);
        System.out.println("BY_TYPE=" + byType);
        System.out.println("BY_CLASS=" + byClass);
        if (samplesPath != null) {
            writeNoColumnSamples(samplesPath, samples);
            System.out.println("SAMPLES=" + samplesPath);
        } else {
            for (NoColumnSample sample : samples) {
                System.out.println(sample.toTsv());
            }
        }
    }

    private static void writeNoColumnSamples(Path samplesPath, List<NoColumnSample> samples) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(samplesPath, StandardCharsets.UTF_8)) {
            writer.write("taskRow\tscriptIndex\tstatementIndex\tstatementType\tclass\tsqlSnippet\n");
            for (NoColumnSample sample : samples) {
                writer.write(sample.toTsv());
                writer.newLine();
            }
        }
    }

    private static void runDebug(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("debug requires <sql-file>");
        }
        String sql = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
        List<LineageResult> results = LineSql.parseScript(sql);
        for (int i = 0; i < results.size(); i++) {
            LineageResult result = results.get(i);
            System.out.println("#" + (i + 1) + " dialect=" + result.getDialect()
                    + " type=" + result.getStatementType());
            System.out.println("tables in=" + result.getInputTables().size()
                    + " out=" + result.getOutputTables().size()
                    + " cols=" + result.getColumnLineage().size());
            for (Diagnostic diagnostic : result.getDiagnostics()) {
                System.out.println(diagnostic.getSeverity() + " "
                        + diagnostic.getCode() + ": " + diagnostic.getMessage());
            }
            for (ColumnLineage lineage : result.getColumnLineage()) {
                System.out.print(columnName(lineage.getTarget()) + " <- ");
                for (ColumnRef source : lineage.getSources()) {
                    System.out.print(columnName(source) + ",");
                }
                System.out.println();
            }
        }
    }

    private static boolean hasError(LineageResult result) {
        for (Diagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.getSeverity() == DiagnosticSeverity.ERROR) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTables(LineageResult result) {
        return !result.getInputTables().isEmpty() || !result.getOutputTables().isEmpty();
    }

    private static String diagKey(LineageResult result) {
        for (Diagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.getSeverity() == DiagnosticSeverity.ERROR) {
                String message = oneLine(diagnostic.getMessage(), 140);
                String code = diagnostic.getCode() == null ? "ERROR" : diagnostic.getCode();
                return code + " | " + message;
            }
        }
        return "NO_ERROR";
    }

    private static String classify(String sql) {
        String normalized = sql == null ? "" : sql.toLowerCase(Locale.ROOT);
        if (normalized.matches("(?s).*select\\s+\\*.*")) {
            return "SELECT_STAR";
        }
        if (normalized.matches("(?s).*select\\s+[^;]*(^|[^a-z_])count\\s*\\(\\s*\\*.*")) {
            return "COUNT_STAR_OR_AGG";
        }
        if (!normalized.contains(" from ")) {
            return "NO_FROM_OR_METADATA";
        }
        if (normalized.matches("(?s).*select\\s+('[^']*'|[0-9]+|null|true|false)(\\s+as\\s+[`a-z_].*)?\\s+from\\s+.*")) {
            return "CONSTANT_PROJECTION";
        }
        if (normalized.contains(" lateral view ") || normalized.contains(" explode(")) {
            return "LATERAL_OR_EXPLODE";
        }
        if (normalized.startsWith("with ")) {
            return "CTE";
        }
        if (normalized.contains(" union ")) {
            return "UNION";
        }
        if (normalized.matches("(?s).*select\\s+[^;]*(case\\s+when|if\\s*\\(|coalesce\\s*\\(|nvl\\s*\\(|regexp_|get_json_object|concat\\s*\\().*")) {
            return "EXPRESSION";
        }
        return "OTHER";
    }

    private static void addFailure(List<FailureSample> samples, CorpusRow row, int statementIndex, String key) {
        if (samples.size() < 200) {
            samples.add(new FailureSample(row, statementIndex, key));
        }
    }

    private static void increment(Map<String, Long> map, String key) {
        Long current = map.get(key);
        map.put(key, current == null ? 1L : current + 1L);
    }

    private static List<Map.Entry<String, Long>> top(Map<String, Long> map, int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(map.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
                return Long.compare(right.getValue(), left.getValue());
            }
        });
        if (entries.size() > limit) {
            return entries.subList(0, limit);
        }
        return entries;
    }

    private static String pct(long value, long total) {
        if (total == 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0 / total);
    }

    private static String oneLine(String value, int max) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private static String tsv(String value) {
        return (value == null ? "" : value).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String columnName(ColumnRef column) {
        if (column.getTable() == null) {
            return column.getName();
        }
        TableRef table = column.getTable();
        String prefix = table.getSchema() == null
                ? table.getName()
                : table.getSchema() + "." + table.getName();
        return prefix + "." + column.getName();
    }

    private static final class CorpusRow {
        final int taskRow;
        final int scriptIndex;
        final String sql;

        private CorpusRow(int taskRow, int scriptIndex, String sql) {
            this.taskRow = taskRow;
            this.scriptIndex = scriptIndex;
            this.sql = sql;
        }

        static CorpusRow parse(String line) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) {
                return null;
            }
            try {
                int taskRow = Integer.parseInt(parts[0]);
                int scriptIndex = Integer.parseInt(parts[1]);
                String sql = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
                return new CorpusRow(taskRow, scriptIndex, sql);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static final class EvalStats {
        long scripts;
        long scriptOk;
        long scriptPartial;
        long scriptWithTable;
        long scriptWithColumn;
        long statements;
        long statementOk;
        long statementWithTable;
        long statementWithColumn;
        long statementDiagnostics;
        long exceptions;
        final Map<String, Long> dialects = new TreeMap<String, Long>();
        final Map<String, Long> statementTypes = new TreeMap<String, Long>();
        final Map<String, Long> errors = new LinkedHashMap<String, Long>();

        void increment(Map<String, Long> map, String key) {
            LineSqlBenchmark.increment(map, key);
        }

        String toMarkdown(Path input, Path failures) {
            StringBuilder markdown = new StringBuilder();
            markdown.append("# LineSQL Corpus Evaluation\n\n");
            markdown.append("Input: `").append(input).append("`\n\n");
            markdown.append("## Summary\n\n");
            markdown.append("| Metric | Count | Rate |\n|---|---:|---:|\n");
            markdown.append("| SQL scripts | ").append(scripts).append(" | 100.00% |\n");
            markdown.append("| Script success(no ERROR) | ").append(scriptOk).append(" | ").append(pct(scriptOk, scripts)).append(" |\n");
            markdown.append("| Script partial(has some output but ERROR exists) | ").append(scriptPartial).append(" | ").append(pct(scriptPartial, scripts)).append(" |\n");
            markdown.append("| Script with table lineage | ").append(scriptWithTable).append(" | ").append(pct(scriptWithTable, scripts)).append(" |\n");
            markdown.append("| Script with column lineage | ").append(scriptWithColumn).append(" | ").append(pct(scriptWithColumn, scripts)).append(" |\n");
            markdown.append("| Statements returned | ").append(statements).append(" | 100.00% |\n");
            markdown.append("| Statement success(no ERROR) | ").append(statementOk).append(" | ").append(pct(statementOk, statements)).append(" |\n");
            markdown.append("| Statement with table lineage | ").append(statementWithTable).append(" | ").append(pct(statementWithTable, statements)).append(" |\n");
            markdown.append("| Statement with column lineage | ").append(statementWithColumn).append(" | ").append(pct(statementWithColumn, statements)).append(" |\n");
            markdown.append("| Statements with diagnostics | ").append(statementDiagnostics).append(" | ").append(pct(statementDiagnostics, statements)).append(" |\n");
            markdown.append("| Parser exceptions | ").append(exceptions).append(" | ").append(pct(exceptions, scripts)).append(" |\n");
            markdown.append("\n## Dialects\n\n| Dialect | Statements |\n|---|---:|\n");
            for (Map.Entry<String, Long> entry : top(dialects, 30)) {
                markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
            }
            markdown.append("\n## Statement Types\n\n| Type | Statements |\n|---|---:|\n");
            for (Map.Entry<String, Long> entry : top(statementTypes, 30)) {
                markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
            }
            markdown.append("\n## Top Errors\n\n| Error | Count |\n|---|---:|\n");
            for (Map.Entry<String, Long> entry : top(errors, 20)) {
                markdown.append("| ").append(entry.getKey().replace("|", "\\|")).append(" | ").append(entry.getValue()).append(" |\n");
            }
            markdown.append("\nFailure samples: `").append(failures).append("`\n");
            return markdown.toString();
        }
    }

    private static final class FailureSample {
        final CorpusRow row;
        final int statementIndex;
        final String error;

        private FailureSample(CorpusRow row, int statementIndex, String error) {
            this.row = row;
            this.statementIndex = statementIndex;
            this.error = error;
        }

        String toTsv() {
            return row.taskRow + "\t" + row.scriptIndex + "\t" + statementIndex + "\t"
                    + tsv(error) + "\t" + tsv(oneLine(row.sql, 500));
        }
    }

    private static final class NoColumnSample {
        final CorpusRow row;
        final int statementIndex;
        final String statementType;
        final String classification;

        private NoColumnSample(CorpusRow row, int statementIndex, String statementType, String classification) {
            this.row = row;
            this.statementIndex = statementIndex;
            this.statementType = statementType;
            this.classification = classification;
        }

        String toTsv() {
            return row.taskRow + "\t" + row.scriptIndex + "\t" + statementIndex + "\t"
                    + statementType + "\t" + classification + "\t" + tsv(oneLine(row.sql, 420));
        }
    }
}
