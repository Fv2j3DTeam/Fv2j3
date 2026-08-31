package com.fv2j3.loader.core;

import com.fv2j3.api.ModDependency;
import com.fv2j3.api.ModDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModMetadataParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String METADATA_PATH = "META-INF/fv2j3.mod.json";

    private ModMetadataParser() {
    }

    public static ModDescriptor parse(InputStream inputStream) throws ModMetadataParseException {
        if (inputStream == null) {
            throw new ModMetadataParseException("Metadata stream is null.");
        }

        try {
            JsonNode root = MAPPER.readTree(inputStream);
            return parse(root);
        } catch (IOException ex) {
            throw new ModMetadataParseException("Failed to parse metadata JSON.", ex);
        }
    }

    public static ModDescriptor parse(String jsonText) throws ModMetadataParseException {
        if (jsonText == null || jsonText.isBlank()) {
            throw new ModMetadataParseException("Metadata JSON text is blank.");
        }

        try {
            JsonNode root = MAPPER.readTree(jsonText);
            return parse(root);
        } catch (JsonProcessingException ex) {
            throw new ModMetadataParseException("Failed to parse metadata JSON text.", ex);
        }
    }

    public static ModDescriptor parse(JsonNode root) throws ModMetadataParseException {
        if (root == null || root.isNull()) {
            throw new ModMetadataParseException("Metadata JSON is missing.");
        }

        ValidationResult validationResult = ModMetadataValidator.validate(root);
        if (!validationResult.valid()) {
            String joined = validationResult.errors().stream()
                    .map(issue -> issue.field() + ": " + issue.message())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("Metadata validation failed");
            throw new ModMetadataParseException("Invalid mod metadata: " + joined);
        }

        String id = requiredText(root, "id");
        String name = requiredText(root, "name");
        String version = requiredText(root, "version");
        String description = textOrDefault(root, "description", "");
        List<String> authors = listOfStrings(root, "authors");
        String license = textOrDefault(root, "license", "UNSPECIFIED");
        List<ModDependency> dependencies = parseDependencies(root, "dependencies", false);
        List<ModDependency> optionalDependencies = parseDependencies(root, "optionalDependencies", true);
        String requiredLoaderVersion = textOrDefault(root, "requiredLoaderVersion", ">=0.1.0");
        String entrypoint = requiredText(root, "entrypoint");

        return new ModDescriptor(
                id,
                version,
                name,
                description,
                authors,
                license,
                dependencies,
                optionalDependencies,
                requiredLoaderVersion,
                entrypoint
        );
    }

    public static String metadataPath() {
        return METADATA_PATH;
    }

    private static List<ModDependency> parseDependencies(JsonNode root, String fieldName, boolean optional) throws ModMetadataParseException {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ModMetadataParseException("Field '" + fieldName + "' must be an array.");
        }

        List<ModDependency> dependencies = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new ModMetadataParseException("Dependency entries in '" + fieldName + "' must be JSON objects.");
            }
            String depId = textOrDefault(item, "id", "");
            String versionRange = textOrDefault(item, "versionRange", "*");
            String typeRaw = textOrDefault(item, "type", optional ? "optional" : "required");
            var dependencyType = switch (typeRaw.trim().toLowerCase(Locale.ROOT)) {
                case "optional" -> com.fv2j3.api.DependencyType.OPTIONAL;
                default -> com.fv2j3.api.DependencyType.REQUIRED;
            };
            dependencies.add(new ModDependency(depId, versionRange, dependencyType));
        }
        return List.copyOf(dependencies);
    }

    private static String requiredText(JsonNode root, String fieldName) throws ModMetadataParseException {
        String value = textOrDefault(root, fieldName, null);
        if (value == null || value.isBlank()) {
            throw new ModMetadataParseException("Field '" + fieldName + "' is required.");
        }
        return value;
    }

    private static String textOrDefault(JsonNode root, String fieldName, String defaultValue) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.toString();
    }

    private static List<String> listOfStrings(JsonNode root, String fieldName) throws ModMetadataParseException {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ModMetadataParseException("Field '" + fieldName + "' must be an array of strings.");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return List.copyOf(values);
    }
}
