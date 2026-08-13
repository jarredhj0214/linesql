package io.github.linesql.core.model;

import java.util.ArrayList;
import java.util.List;

public class LineageResult {
    private String version = "0.1";
    private SqlDialect dialect = SqlDialect.UNKNOWN;
    private double dialectConfidence;
    private StatementType statementType = StatementType.UNKNOWN;
    private List<TableRef> inputTables = new ArrayList<TableRef>();
    private List<TableRef> outputTables = new ArrayList<TableRef>();
    private List<ColumnLineage> columnLineage = new ArrayList<ColumnLineage>();
    private List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

    public static LineageResult error(SqlDialect dialect, String code, String message) {
        LineageResult result = new LineageResult();
        result.setDialect(dialect);
        result.getDiagnostics().add(Diagnostic.error(code, message));
        return result;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public SqlDialect getDialect() {
        return dialect;
    }

    public void setDialect(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public double getDialectConfidence() {
        return dialectConfidence;
    }

    public void setDialectConfidence(double dialectConfidence) {
        this.dialectConfidence = dialectConfidence;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
    }

    public List<TableRef> getInputTables() {
        return inputTables;
    }

    public void setInputTables(List<TableRef> inputTables) {
        this.inputTables = inputTables;
    }

    public List<TableRef> getOutputTables() {
        return outputTables;
    }

    public void setOutputTables(List<TableRef> outputTables) {
        this.outputTables = outputTables;
    }

    public List<ColumnLineage> getColumnLineage() {
        return columnLineage;
    }

    public void setColumnLineage(List<ColumnLineage> columnLineage) {
        this.columnLineage = columnLineage;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }
}
