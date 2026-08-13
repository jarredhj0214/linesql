package io.github.linesql.core.model;

import java.util.ArrayList;
import java.util.List;

public class ColumnLineage {
    private ColumnRef target;
    private List<ColumnRef> sources = new ArrayList<ColumnRef>();
    private String expression;

    public ColumnRef getTarget() {
        return target;
    }

    public void setTarget(ColumnRef target) {
        this.target = target;
    }

    public List<ColumnRef> getSources() {
        return sources;
    }

    public void setSources(List<ColumnRef> sources) {
        this.sources = sources;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
