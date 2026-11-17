package io.github.linesql.core.model;

/** SQL text and the namespace in effect when the statement was prepared. */
public final class PreparedSql {
    private final String sql;
    private final String catalog;
    private final String schema;

    public PreparedSql(String sql, String catalog, String schema) {
        this.sql = sql;
        this.catalog = catalog;
        this.schema = schema;
    }

    public String getSql() {
        return sql;
    }

    public String getCatalog() {
        return catalog;
    }

    public String getSchema() {
        return schema;
    }
}
