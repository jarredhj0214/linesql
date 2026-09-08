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
    public static final String COMPATIBILITY_MODE_OPTION = "oceanbase.compatibilityMode";

    private final DialectParser mysqlModeParser = new MySqlDialectParser();
    private final DialectParser oracleModeParser = new OracleDialectParser();

    @Override
    public SqlDialect dialect() {
        return SqlDialect.OCEANBASE;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        String explicitMode = compatibilityMode(options);
        DialectParser delegate = "oracle".equals(explicitMode)
                ? oracleModeParser
                : "mysql".equals(explicitMode) ? mysqlModeParser : looksLikeOracleMode(sql) ? oracleModeParser : mysqlModeParser;
        LineageResult result = delegate.parse(sql, options, context);
        result.setDialect(SqlDialect.OCEANBASE);
        result.setDialectConfidence(1.0d);
        result.getDiagnostics().add(Diagnostic.warning(
                explicitMode == null ? "OCEANBASE_COMPATIBILITY_MODE_INFERRED" : "OCEANBASE_COMPATIBILITY_MODE_EXPLICIT",
                "OceanBase parser used "
                        + (delegate.dialect() == SqlDialect.ORACLE ? "Oracle" : "MySQL")
                        + " compatibility mode."));
        return result;
    }

    private static String compatibilityMode(ParseOptions options) {
        if (options == null || options.getDialectOptions() == null) {
            return null;
        }
        String mode = options.getDialectOptions().get(COMPATIBILITY_MODE_OPTION);
        if (mode == null) {
            return null;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if ("oracle".equals(normalized) || "mysql".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static boolean looksLikeOracleMode(String sql) {
        String normalized = sql == null ? "" : sql.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\bfrom\\s+dual\\b.*")
                || normalized.matches("(?s).*\\b(rownum|rowid|ora_rowscn|connect_by_isleaf|connect_by_iscycle)\\b.*")
                || normalized.matches("(?s).*\\bconnect\\s+by\\b.*")
                || normalized.matches("(?s).*\\bstart\\s+with\\b.*")
                || normalized.matches("(?s).*\\b(un)?pivot\\s*\\(.*")
                || normalized.matches("(?s).*\\bmatch_recognize\\s*\\(.*")
                || normalized.matches("(?s).*\\bmodel\\s+(return\\s+(updated|all)\\s+rows\\s+)?((ignore|keep)\\s+nav\\s+)?(partition|dimension|measures|rules)\\b.*")
                || normalized.matches("(?s).*\\bfrom\\b.+\\bsample\\s+(block\\s+)?\\(.*")
                || normalized.matches("(?s).*\\b(cross|outer)\\s+apply\\b.*")
                || normalized.matches("(?s).*\\blateral\\s*\\(.*")
                || normalized.matches("(?s).*\\b(from|join)\\s+table\\s*\\(.*")
                || normalized.matches("(?s).*\\bxmltable\\s*\\(.*\\bpassing\\b.*")
                || normalized.matches("(?s)^(?!\\s*explain\\s+partitions\\b).*\\bfrom\\b.+\\b(sub)?partition\\s*\\(.*")
                || normalized.matches("(?s)^\\s*comment\\s+on\\s+(table|column)\\b.*")
                || normalized.matches("(?s).*\\bmerge\\s+into\\b.*")
                || normalized.matches("(?s)^\\s*insert\\s+(all|first)\\b.*")
                || normalized.matches("(?s)^\\s*explain\\s+plan\\b.*")
                || normalized.matches("(?s)^\\s*lock\\s+table\\b.*")
                || normalized.matches("(?s)^\\s*analyze\\s+index\\b.*")
                || normalized.matches("(?s)^\\s*analyze\\s+table\\b.*\\b(compute|estimate|delete)\\s+statistics\\b.*")
                || normalized.matches("(?s)^\\s*analyze\\s+table\\b.*\\bvalidate\\s+structure\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(global|private)\\s+temporary\\s+table\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+table\\b.*\\b(number|varchar2)\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+table\\b.*\\b(nologging|logging|parallel|noparallel|compress|nocompress)\\b.*\\bas\\s+select\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+table\\b.*\\bpartition\\s+by\\s+(range|hash|list)\\b.*\\bas\\s+select\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+materialized\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+table\\b.*\\b(cascade\\s+constraints|purge)\\b.*")
                || normalized.matches("(?s)^\\s*truncate\\s+(table\\s+)?\\S+\\s+(drop|reuse)\\s+storage\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+table\\b.*\\badd\\b.*\\b(number|varchar2)\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+table\\b.*\\b(modify|drop|rename)\\b.*\\bcolumn\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(or\\s+replace\\s+)?(public\\s+)?(sequence|synonym)\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(or\\s+replace\\s+)?package\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(or\\s+replace\\s+)?package\\s+body\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+or\\s+replace\\s+trigger\\b.*")
                || normalized.matches("(?s)^\\s*(declare\\b.*\\bbegin\\b|begin\\b).*\\bend\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(or\\s+replace\\s+)?(no\\s+)?force\\s+view\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+bitmap\\s+index\\b.+\\bon\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(unique\\s+)?index\\s+[a-z_][a-z0-9_$]*\\.[a-z_][a-z0-9_$]*\\s+on\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+index\\s+[a-z_][a-z0-9_$]*\\.[a-z_][a-z0-9_$]*\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+index\\s+[a-z_][a-z0-9_$]*\\.[a-z_][a-z0-9_$]*\\b.*")
                || normalized.matches("(?s)^\\s*alter\\s+sequence\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+(public\\s+)?(sequence|synonym)\\b.*")
                || normalized.matches("(?s)^\\s*create\\s+(public\\s+)?database\\s+link\\b.*")
                || normalized.matches("(?s)^\\s*drop\\s+(public\\s+)?database\\s+link\\b.*")
                || normalized.matches("(?s).*\\bas\\s+of\\s+(timestamp|scn)\\b.*")
                || normalized.matches("(?s).*\\bfor\\s+update\\s+of\\b.*\\bskip\\s+locked\\b.*")
                || normalized.matches("(?s).*\\bfor\\s+update\\s+of\\b.*\\bwait\\s+\\d+\\b.*")
                || normalized.matches("(?s).*\\(\\s*\\+\\s*\\).*")
                || normalized.matches("(?s).*\\breturning\\b.+\\binto\\b.*")
                || normalized.matches("(?s).*\\bwith\\s+(read\\s+only|check\\s+option)\\b.*")
                || normalized.matches("(?s).*\\bbequeath\\s+(definer|current_user)\\b.*")
                || normalized.matches("(?s).*\\border\\s+by\\b.+\\bnulls\\s+(first|last)\\b.*")
                || normalized.matches("(?s).*\\bfetch\\s+(first|next)\\b.+\\b(rows?|percent)\\b.*")
                || normalized.matches("(?s).*\\b[a-z_][a-z0-9_$]*(\\.[a-z_][a-z0-9_$]*)?@[a-z_][a-z0-9_$]*\\b.*");
    }
}
