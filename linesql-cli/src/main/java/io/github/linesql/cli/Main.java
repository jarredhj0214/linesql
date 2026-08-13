package io.github.linesql.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.LineageResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            usage();
            return;
        }

        String sql = args.length == 0 ? readAll(System.in) : String.join(" ", args);
        if (sql.trim().isEmpty()) {
            usage();
            System.exit(2);
            return;
        }

        List<LineageResult> results = LineSql.parseScript(sql);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(System.out, results);
        System.out.println();
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

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("  linesql \"select id from db.table\"");
        System.err.println("  cat script.sql | linesql");
    }
}
