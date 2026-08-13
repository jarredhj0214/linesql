package io.github.linesql.core.model;

public class ParseWarning {
    private String code;
    private String message;

    public ParseWarning() {
    }

    public ParseWarning(String code, String message) {
        this.code = code;
        this.message = message;
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
}
