package io.github.linesql.core.model;

import java.util.ArrayList;
import java.util.List;

public class LineageResult {
    private SqlDialect dialect = SqlDialect.UNKNOWN;
    private StatementType statementType = StatementType.UNKNOWN;
    private List<TableRef> sourceTables = new ArrayList<TableRef>();
    private List<TableRef> targetTables = new ArrayList<TableRef>();
    private List<ColumnLineage> columnLineage = new ArrayList<ColumnLineage>();
    private List<ParseWarning> warnings = new ArrayList<ParseWarning>();
    private List<ParseError> errors = new ArrayList<ParseError>();

    public static LineageResult error(SqlDialect dialect, String code, String message) {
        LineageResult result = new LineageResult();
        result.setDialect(dialect);
        result.getErrors().add(new ParseError(code, message));
        return result;
    }

    public SqlDialect getDialect() {
        return dialect;
    }

    public void setDialect(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
    }

    public List<TableRef> getSourceTables() {
        return sourceTables;
    }

    public void setSourceTables(List<TableRef> sourceTables) {
        this.sourceTables = sourceTables;
    }

    public List<TableRef> getTargetTables() {
        return targetTables;
    }

    public void setTargetTables(List<TableRef> targetTables) {
        this.targetTables = targetTables;
    }

    public List<ColumnLineage> getColumnLineage() {
        return columnLineage;
    }

    public void setColumnLineage(List<ColumnLineage> columnLineage) {
        this.columnLineage = columnLineage;
    }

    public List<ParseWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ParseWarning> warnings) {
        this.warnings = warnings;
    }

    public List<ParseError> getErrors() {
        return errors;
    }

    public void setErrors(List<ParseError> errors) {
        this.errors = errors;
    }
}
