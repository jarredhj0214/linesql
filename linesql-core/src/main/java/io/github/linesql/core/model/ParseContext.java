package io.github.linesql.core.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseContext {
    private String defaultCatalog;
    private String defaultSchema;
    private Map<String, LineageResult> temporaryRelations = new LinkedHashMap<String, LineageResult>();
    private final Map<SqlDialect, Map<String, LineageResult>> sessionTemporaryRelations =
            new EnumMap<>(SqlDialect.class);
    private final Map<SqlDialect, Map<String, PreparedSql>> preparedStatements =
            new EnumMap<>(SqlDialect.class);
    private final Map<SqlDialect, Map<String, String>> sessionStringVariables = new EnumMap<>(SqlDialect.class);

    public String getDefaultCatalog() {
        return defaultCatalog;
    }

    public void setDefaultCatalog(String defaultCatalog) {
        this.defaultCatalog = defaultCatalog;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public Map<String, LineageResult> getTemporaryRelations() {
        return temporaryRelations;
    }

    public void setTemporaryRelations(Map<String, LineageResult> temporaryRelations) {
        this.temporaryRelations = temporaryRelations;
        sessionTemporaryRelations.clear();
    }

    /** Temporary definitions isolated from persistent metadata supplied by the caller. */
    public Map<String, LineageResult> getSessionTemporaryRelations(SqlDialect dialect) {
        return sessionTemporaryRelations.computeIfAbsent(dialect, ignored -> new LinkedHashMap<>());
    }

    public Map<String, PreparedSql> getPreparedStatements(SqlDialect dialect) {
        return preparedStatements.computeIfAbsent(dialect, ignored -> new LinkedHashMap<>());
    }

    /** Session string values tracked by the dialect parser. A null value represents an unresolved value. */
    public Map<String, String> getSessionStringVariables(SqlDialect dialect) {
        return sessionStringVariables.computeIfAbsent(dialect, ignored -> new LinkedHashMap<>());
    }

}
