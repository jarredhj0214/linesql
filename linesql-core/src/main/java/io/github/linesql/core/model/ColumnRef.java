package io.github.linesql.core.model;

public class ColumnRef {
    private TableRef table;
    private String name;

    public ColumnRef() {
    }

    public ColumnRef(TableRef table, String name) {
        this.table = table;
        this.name = name;
    }

    public TableRef getTable() {
        return table;
    }

    public void setTable(TableRef table) {
        this.table = table;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
