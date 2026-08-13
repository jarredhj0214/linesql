package io.github.linesql.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParseOptions {
    private boolean dialectDetectionEnabled = true;
    private LineageLevel lineageLevel = LineageLevel.COLUMN;
    private ErrorStrategy errorStrategy = ErrorStrategy.BEST_EFFORT;
    private List<SqlDialect> dialectHints = new ArrayList<SqlDialect>();

    public static Builder builder() {
        return new Builder();
    }

    public static ParseOptions defaults() {
        return builder().build();
    }

    public boolean isDialectDetectionEnabled() {
        return dialectDetectionEnabled;
    }

    public void setDialectDetectionEnabled(boolean dialectDetectionEnabled) {
        this.dialectDetectionEnabled = dialectDetectionEnabled;
    }

    public LineageLevel getLineageLevel() {
        return lineageLevel;
    }

    public void setLineageLevel(LineageLevel lineageLevel) {
        this.lineageLevel = lineageLevel;
    }

    public ErrorStrategy getErrorStrategy() {
        return errorStrategy;
    }

    public void setErrorStrategy(ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
    }

    public List<SqlDialect> getDialectHints() {
        return dialectHints;
    }

    public void setDialectHints(List<SqlDialect> dialectHints) {
        this.dialectHints = dialectHints;
    }

    public static class Builder {
        private final ParseOptions options = new ParseOptions();

        public Builder dialectDetectionEnabled(boolean dialectDetectionEnabled) {
            options.setDialectDetectionEnabled(dialectDetectionEnabled);
            return this;
        }

        public Builder lineageLevel(LineageLevel lineageLevel) {
            options.setLineageLevel(lineageLevel);
            return this;
        }

        public Builder errorStrategy(ErrorStrategy errorStrategy) {
            options.setErrorStrategy(errorStrategy);
            return this;
        }

        public Builder dialectHints(List<SqlDialect> dialectHints) {
            options.setDialectHints(new ArrayList<SqlDialect>(dialectHints));
            return this;
        }

        public ParseOptions build() {
            options.setDialectHints(Collections.unmodifiableList(new ArrayList<SqlDialect>(options.getDialectHints())));
            return options;
        }
    }
}
