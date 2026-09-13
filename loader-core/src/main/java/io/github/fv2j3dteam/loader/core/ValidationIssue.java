package io.github.fv2j3dteam.loader.core;

public record ValidationIssue(String field, String message, String suggestion) {
    public ValidationIssue {
        field = field == null ? "" : field;
        message = message == null ? "" : message;
        suggestion = suggestion == null ? "" : suggestion;
    }
}
