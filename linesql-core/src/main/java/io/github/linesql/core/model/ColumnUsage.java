package io.github.linesql.core.model;

public class ColumnUsage {
    private ColumnUsageType type;
    private ColumnRef column;

    public ColumnUsage() {
    }

    public ColumnUsage(ColumnUsageType type, ColumnRef column) {
        this.type = type;
        this.column = column;
    }

    public ColumnUsageType getType() {
        return type;
    }

    public void setType(ColumnUsageType type) {
        this.type = type;
    }

    public ColumnRef getColumn() {
        return column;
    }

    public void setColumn(ColumnRef column) {
        this.column = column;
    }
}
