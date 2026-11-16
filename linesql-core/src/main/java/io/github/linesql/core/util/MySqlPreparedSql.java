package io.github.linesql.core.util;

import io.github.linesql.core.model.DiagnosticSeverity;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.StatementType;

import java.util.LinkedHashMap;

public final class MySqlPreparedSql {
    private MySqlPreparedSql() {
    }

    public static String decodeLiteral(String text) {
        int start = 0;
        while (start < text.length() && text.charAt(start) != '\'' && text.charAt(start) != '"') {
            start++;
        }
        if (start == text.length()) {
            return null;
        }
        char quote = text.charAt(start);
        int end = text.length() - 1;
        StringBuilder sql = new StringBuilder(end - start);
        for (int index = start + 1; index < end; index++) {
            char current = text.charAt(index);
            if (current == quote && index + 1 < end && text.charAt(index + 1) == quote) {
                sql.append(quote);
                index++;
            } else if (current == '\\' && index + 1 < end) {
                char escaped = text.charAt(++index);
                switch (escaped) {
                    case '0': sql.append('\0'); break;
                    case 'b': sql.append('\b'); break;
                    case 'n': sql.append('\n'); break;
                    case 'r': sql.append('\r'); break;
                    case 't': sql.append('\t'); break;
                    case 'Z': sql.append((char) 26); break;
                    case '%':
                    case '_': sql.append('\\').append(escaped); break;
                    default: sql.append(escaped);
                }
            } else {
                sql.append(current);
            }
        }
        return sql.toString();
    }

    public static ParseContext snapshot(ParseContext context) {
        ParseContext prepared = new ParseContext();
        if (context != null) {
            prepared.setDefaultCatalog(context.getDefaultCatalog());
            prepared.setDefaultSchema(context.getDefaultSchema());
            prepared.setTemporaryRelations(new LinkedHashMap<>(context.getTemporaryRelations()));
        }
        return prepared;
    }

    public static void recordLineage(LineageResult target, LineageResult prepared) {
        if (prepared.getStatementType() == StatementType.UNKNOWN || prepared.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.ERROR)) {
            return;
        }
        target.setInputTables(prepared.getInputTables());
        target.setOutputTables(prepared.getOutputTables());
        target.setColumnLineage(prepared.getColumnLineage());
        target.setColumnUsages(prepared.getColumnUsages());
    }
}
