package io.github.fv2j3dteam.loader.core;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public record ValidationResult(boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        valid = errors.isEmpty();
    }

    public static ValidationResult validResult() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult invalidResult(List<ValidationIssue> issues) {
        return new ValidationResult(false, issues == null ? List.of() : List.copyOf(issues), List.of());
    }

    public List<ValidationIssue> allIssues() {
        return Collections.unmodifiableList(Stream.concat(errors.stream(), warnings.stream()).toList());
    }
}
