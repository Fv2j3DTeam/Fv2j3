package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.DependencyType;
import io.github.fv2j3dteam.api.ModDependency;
import io.github.fv2j3dteam.api.ModDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ModMetadataValidator {
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+-]*$");
    private static final Pattern VERSION_CONSTRAINT_PATTERN = Pattern.compile("^(>=|<=|>|<|=|\\^|~)?[A-Za-z0-9][A-Za-z0-9._+-]*$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModMetadataValidator() {
    }

    public static ValidationResult validate(JsonNode root) {
        List<ValidationIssue> errors = new ArrayList<>();
        if (root == null || root.isNull()) {
            return ValidationResult.invalidResult(List.of(new ValidationIssue("root", "Metadata JSON is missing or empty.", "Provide a valid META-INF/fv2j3.mod.json file.")));
        }

        if (root.has("requiredMinecraftVersion")) {
            errors.add(new ValidationIssue(
                    "requiredMinecraftVersion",
                    "Fv2j3 does not support per-mod Minecraft version metadata; Minecraft 1.12.2 is fixed.",
                    "Remove requiredMinecraftVersion from the metadata."
            ));
        }

        String id = readText(root, "id");
        if (id == null || id.isBlank()) {
            errors.add(new ValidationIssue("id", "Mod id is required.", "Set a short identifier such as 'example_mod'."));
        } else if (!MOD_ID_PATTERN.matcher(id).matches()) {
            errors.add(new ValidationIssue("id", "Invalid mod id '" + id + "'.", "Use lowercase letters, digits, and underscores only."));
        }

        String name = readText(root, "name");
        if (name == null || name.isBlank()) {
            errors.add(new ValidationIssue("name", "Mod name is required.", "Set a human readable name."));
        }

        String version = readText(root, "version");
        if (version == null || version.isBlank()) {
            errors.add(new ValidationIssue("version", "Mod version is required.", "Set a valid version such as '1.0.0'."));
        } else if (!VERSION_PATTERN.matcher(version).matches()) {
            errors.add(new ValidationIssue("version", "Invalid version '" + version + "'.", "Use a simple semantic-style version string."));
        }

        String entrypoint = readText(root, "entrypoint");
        if (entrypoint == null || entrypoint.isBlank()) {
            errors.add(new ValidationIssue("entrypoint", "Mod entrypoint is required.", "Provide a Java class name such as 'com.example.ExampleMod'."));
        } else if (!JAVA_IDENTIFIER.matcher(entrypoint).matches()) {
            errors.add(new ValidationIssue("entrypoint", "Invalid entrypoint '" + entrypoint + "'.", "Use a valid Java class name, not a path."));
        }

        String loaderRequired = readText(root, "requiredLoaderVersion");
        if (loaderRequired != null && !loaderRequired.isBlank() && !VERSION_CONSTRAINT_PATTERN.matcher(loaderRequired).matches()) {
            errors.add(new ValidationIssue("requiredLoaderVersion", "Invalid requiredLoaderVersion '" + loaderRequired + "'.", "Use an expression like '>=1.0.0'."));
        }

        validateDependencies(root, "dependencies", errors);
        validateDependencies(root, "optionalDependencies", errors);

        return new ValidationResult(errors.isEmpty(), errors, List.of());
    }

    public static ValidationResult validate(ModDescriptor descriptor) {
        List<ValidationIssue> errors = new ArrayList<>();
        if (descriptor == null) {
            return ValidationResult.invalidResult(List.of(new ValidationIssue("descriptor", "Mod descriptor is null.", "Create a valid ModDescriptor.")));
        }

        if (descriptor.id() == null || descriptor.id().isBlank()) {
            errors.add(new ValidationIssue("id", "Mod id is required.", "Set a valid mod identifier."));
        } else if (!MOD_ID_PATTERN.matcher(descriptor.id()).matches()) {
            errors.add(new ValidationIssue("id", "Invalid mod id '" + descriptor.id() + "'.", "Use lowercase letters, digits, and underscores only."));
        }

        if (descriptor.name() == null || descriptor.name().isBlank()) {
            errors.add(new ValidationIssue("name", "Mod name is required.", "Set a human readable name."));
        }

        if (descriptor.version() == null || descriptor.version().isBlank()) {
            errors.add(new ValidationIssue("version", "Mod version is required.", "Set a version such as '1.0.0'."));
        } else if (!VERSION_PATTERN.matcher(descriptor.version()).matches()) {
            errors.add(new ValidationIssue("version", "Invalid version '" + descriptor.version() + "'.", "Use a simple semantic-style version string."));
        }

        if (descriptor.entrypoint() == null || descriptor.entrypoint().isBlank()) {
            errors.add(new ValidationIssue("entrypoint", "Mod entrypoint is required.", "Set a valid Java class name."));
        } else if (!JAVA_IDENTIFIER.matcher(descriptor.entrypoint()).matches()) {
            errors.add(new ValidationIssue("entrypoint", "Invalid entrypoint '" + descriptor.entrypoint() + "'.", "Use a valid Java class name."));
        }

        if (descriptor.dependencies() != null) {
            validateDependencyList(descriptor.dependencies(), "dependencies", errors);
        }
        if (descriptor.optionalDependencies() != null) {
            validateDependencyList(descriptor.optionalDependencies(), "optionalDependencies", errors);
        }

        if (descriptor.requiredLoaderVersion() != null && !descriptor.requiredLoaderVersion().isBlank()) {
            if (!VERSION_CONSTRAINT_PATTERN.matcher(descriptor.requiredLoaderVersion()).matches()) {
                errors.add(new ValidationIssue("requiredLoaderVersion", "Invalid requiredLoaderVersion '" + descriptor.requiredLoaderVersion() + "'.", "Use something like '>=1.0.0'."));
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, List.of());
    }

    private static void validateDependencies(JsonNode root, String fieldName, List<ValidationIssue> errors) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            errors.add(new ValidationIssue(fieldName, "Field must be an array.", "Provide a JSON array of dependency objects."));
            return;
        }

        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull() || !item.isObject()) {
                errors.add(new ValidationIssue(fieldName, "Dependency entries must be objects.", "Use {\"id\":\"mod_id\",\"versionRange\":\"^1.0.0\",\"type\":\"required\"}."));
                continue;
            }
            String depId = readText(item, "id");
            if (depId == null || depId.isBlank()) {
                errors.add(new ValidationIssue(fieldName, "Dependency id is required.", "Set a dependency id."));
                continue;
            }
            if (!MOD_ID_PATTERN.matcher(depId).matches()) {
                errors.add(new ValidationIssue(fieldName, "Invalid dependency id '" + depId + "'.", "Use lowercase letters, digits, and underscores only."));
            }
            if (!seen.add(depId.toLowerCase(Locale.ROOT))) {
                errors.add(new ValidationIssue(fieldName, "Duplicate dependency id '" + depId + "'.", "Remove duplicates from the dependency list."));
            }
            String type = readText(item, "type");
            if (type != null && !type.isBlank()) {
                try {
                    DependencyType.valueOf(type.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    errors.add(new ValidationIssue(fieldName, "Unsupported dependency type '" + type + "'.", "Use 'required' or 'optional'."));
                }
            }
        }
    }

    private static void validateDependencyList(List<ModDependency> list, String fieldName, List<ValidationIssue> errors) {
        Set<String> seen = new HashSet<>();
        for (ModDependency dependency : list) {
            if (dependency == null) {
                errors.add(new ValidationIssue(fieldName, "Dependency entry is null.", "Remove null dependency entries."));
                continue;
            }
            if (!MOD_ID_PATTERN.matcher(dependency.id()).matches()) {
                errors.add(new ValidationIssue(fieldName, "Invalid dependency id '" + dependency.id() + "'.", "Use lowercase letters, digits, and underscores only."));
            }
            if (!seen.add(dependency.id().toLowerCase(Locale.ROOT))) {
                errors.add(new ValidationIssue(fieldName, "Duplicate dependency id '" + dependency.id() + "'.", "Remove duplicates."));
            }
            if (dependency.type() == null) {
                errors.add(new ValidationIssue(fieldName, "Dependency type is required.", "Use REQUIRED or OPTIONAL."));
            }
        }
    }

    private static String readText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }
}
