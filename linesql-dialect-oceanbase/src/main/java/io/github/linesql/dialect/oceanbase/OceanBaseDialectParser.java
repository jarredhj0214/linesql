package io.github.linesql.dialect.oceanbase;

import io.github.linesql.core.model.Diagnostic;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.mysql.MySqlDialectParser;
import io.github.linesql.dialect.oracle.OracleDialectParser;

import java.util.Locale;

public class OceanBaseDialectParser implements DialectParser {
    private final DialectParser mysqlModeParser = new MySqlDialectParser();
    private final DialectParser oracleModeParser = new OracleDialectParser();

    @Override
    public SqlDialect dialect() {
        return SqlDialect.OCEANBASE;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        DialectParser delegate = looksLikeOracleMode(sql) ? oracleModeParser : mysqlModeParser;
        LineageResult result = delegate.parse(sql, options, context);
        result.setDialect(SqlDialect.OCEANBASE);
        result.setDialectConfidence(1.0d);
        result.getDiagnostics().add(Diagnostic.warning(
                "OCEANBASE_COMPATIBILITY_MODE_INFERRED",
                delegate.dialect() == SqlDialect.ORACLE
                        ? "OceanBase parser used Oracle compatibility mode."
                        : "OceanBase parser used MySQL compatibility mode."));
        return result;
    }

    private static boolean looksLikeOracleMode(String sql) {
        String normalized = sql == null ? "" : sql.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || normalized.matches("(?s).*\\bconnect\\s+by\\b.*")
                || normalized.matches("(?s).*\\bstart\\s+with\\b.*")
                || normalized.matches("(?s).*\\bmerge\\s+into\\b.*");
    }
}
