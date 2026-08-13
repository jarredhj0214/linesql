package io.github.linesql.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ParseContext {
    private String defaultCatalog;
    private String defaultSchema;
    private Map<String, LineageResult> temporaryRelations = new LinkedHashMap<String, LineageResult>();

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
    }
}
