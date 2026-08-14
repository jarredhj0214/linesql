package io.github.linesql.core.model;

public class DialectCandidate {
    private SqlDialect dialect = SqlDialect.UNKNOWN;
    private double confidence;
    private String reason;

    public DialectCandidate() {
    }

    public DialectCandidate(SqlDialect dialect, double confidence, String reason) {
        this.dialect = dialect;
        this.confidence = confidence;
        this.reason = reason;
    }

    public SqlDialect getDialect() {
        return dialect;
    }

    public void setDialect(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
