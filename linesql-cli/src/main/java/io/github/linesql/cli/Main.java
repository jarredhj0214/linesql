package io.github.linesql.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.in, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, InputStream input, PrintStream output, PrintStream error) throws IOException {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            error.println(e.getMessage());
            usage(error);
            return 2;
        }

        if (options.help) {
            usage(error);
            return 0;
        }

        String sql = options.sqlParts.isEmpty() ? readAll(input) : String.join(" ", options.sqlParts);
        if (sql.trim().isEmpty()) {
            usage(error);
            return 2;
        }

        List<LineageResult> results = options.dialect == null
                ? LineSql.parseScript(sql)
                : LineSql.parseScript(sql, options.dialect);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output, results);
        output.println();
        return 0;
    }

    private static class CliOptions {
        private final List<String> sqlParts = new ArrayList<>();
        private SqlDialect dialect;
        private boolean help;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.help = true;
                    continue;
                }
                if ("--dialect".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --dialect.");
                    }
                    options.dialect = parseDialect(args[++i]);
                    continue;
                }
                if (arg.startsWith("--dialect=")) {
                    options.dialect = parseDialect(arg.substring("--dialect=".length()));
                    continue;
                }
                if (arg.startsWith("-")) {
                    throw new IllegalArgumentException("Unknown option: " + arg);
                }
                options.sqlParts.add(arg);
            }
            return options;
        }

        private static SqlDialect parseDialect(String value) {
            try {
                return SqlDialect.valueOf(value.trim().replace('-', '_').toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported dialect: " + value);
            }
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void usage(PrintStream output) {
        output.println("Usage:");
        output.println("  linesql \"select id from db.table\"");
        output.println("  linesql --dialect HIVE \"select id from db.table\"");
        output.println("  cat script.sql | linesql");
        output.println();
        output.println("Dialects: SPARK, HIVE, FLINK, STARROCKS, MYSQL, ORACLE, SQLSERVER");
    }
}
