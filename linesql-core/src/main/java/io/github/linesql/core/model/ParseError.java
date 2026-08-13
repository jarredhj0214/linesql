package io.github.linesql.core.model;

public class ParseError {
    private String code;
    private String message;
    private Integer position;

    public ParseError() {
    }

    public ParseError(String code, String message) {
        this(code, message, null);
    }

    public ParseError(String code, String message, Integer position) {
        this.code = code;
        this.message = message;
        this.position = position;
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

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}
