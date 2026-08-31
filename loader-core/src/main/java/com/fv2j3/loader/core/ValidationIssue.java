package com.fv2j3.loader.core;

public record ValidationIssue(String field, String message, String suggestion) {
    public ValidationIssue {
        field = field == null ? "" : field;
        message = message == null ? "" : message;
        suggestion = suggestion == null ? "" : suggestion;
    }
}
