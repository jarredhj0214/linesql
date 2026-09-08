package io.github.linesql.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParseOptions {
    private boolean dialectDetectionEnabled = true;
    private LineageLevel lineageLevel = LineageLevel.COLUMN;
    private ErrorStrategy errorStrategy = ErrorStrategy.BEST_EFFORT;
    private List<SqlDialect> dialectHints = new ArrayList<SqlDialect>();
    private Map<String, String> dialectOptions = new LinkedHashMap<String, String>();

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

    public Map<String, String> getDialectOptions() {
        return dialectOptions;
    }

    public void setDialectOptions(Map<String, String> dialectOptions) {
        this.dialectOptions = dialectOptions;
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

        public Builder dialectOption(String key, String value) {
            options.getDialectOptions().put(key, value);
            return this;
        }

        public Builder dialectOptions(Map<String, String> dialectOptions) {
            options.setDialectOptions(new LinkedHashMap<String, String>(dialectOptions));
            return this;
        }

        public ParseOptions build() {
            options.setDialectHints(Collections.unmodifiableList(new ArrayList<SqlDialect>(options.getDialectHints())));
            options.setDialectOptions(Collections.unmodifiableMap(new LinkedHashMap<String, String>(options.getDialectOptions())));
            return options;
        }
    }
}
