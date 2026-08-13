package io.github.linesql.core.model;

public class SourceLocation {
    private Integer line;
    private Integer column;
    private Integer offset;

    public SourceLocation() {
    }

    public SourceLocation(Integer line, Integer column, Integer offset) {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public Integer getColumn() {
        return column;
    }

    public void setColumn(Integer column) {
        this.column = column;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}
