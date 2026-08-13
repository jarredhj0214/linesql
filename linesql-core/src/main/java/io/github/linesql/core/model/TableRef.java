package io.github.linesql.core.model;

import java.util.Objects;

public class TableRef {
    private String catalog;
    private String schema;
    private String name;

    public TableRef() {
    }

    public TableRef(String catalog, String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TableRef)) {
            return false;
        }
        TableRef tableRef = (TableRef) o;
        return Objects.equals(catalog, tableRef.catalog)
                && Objects.equals(schema, tableRef.schema)
                && Objects.equals(name, tableRef.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalog, schema, name);
    }

    @Override
    public String toString() {
        return "TableRef{"
                + "catalog='" + catalog + '\''
                + ", schema='" + schema + '\''
                + ", name='" + name + '\''
                + '}';
    }
}
