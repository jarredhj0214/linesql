package io.github.linesql.core.model;

public class Diagnostic {
    private DiagnosticSeverity severity = DiagnosticSeverity.INFO;
    private String code;
    private String message;
    private SourceLocation location;

    public Diagnostic() {
    }

    public Diagnostic(DiagnosticSeverity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(DiagnosticSeverity.ERROR, code, message);
    }

    public static Diagnostic warning(String code, String message) {
        return new Diagnostic(DiagnosticSeverity.WARNING, code, message);
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(DiagnosticSeverity severity) {
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }
}
