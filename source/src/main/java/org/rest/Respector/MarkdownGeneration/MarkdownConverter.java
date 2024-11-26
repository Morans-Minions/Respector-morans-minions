package org.rest.Respector.MarkdownGeneration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class MarkdownConverter {

    private Map<String, JsonObject> componentsMap = new HashMap<>();

    public String convert(String filePath) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = new FileReader(filePath)) {
            JsonObject openApiJson = gson.fromJson(reader, JsonObject.class);
            StringBuilder markdown = new StringBuilder();

            // Store components for resolving $ref
            JsonObject components = openApiJson.getAsJsonObject("components");
            for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
                componentsMap.put("#/components/" + entry.getKey() + "/", entry.getValue().getAsJsonObject());
            }

            // Extract basic info
            JsonObject info = openApiJson.getAsJsonObject("info");
            String title = info.get("title").getAsString();
            String version = info.get("version").getAsString();
            String description = info.get("description").getAsString();

            markdown.append("# ").append(title).append("\n");
            markdown.append("**Version:** ").append(version).append("\n\n");
            markdown.append(description).append("\n\n");

            // Process paths
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            for (Map.Entry<String, JsonElement> pathEntry : paths.entrySet()) {
                markdown.append("## ").append(pathEntry.getKey()).append("\n");
                JsonObject pathItem = pathEntry.getValue().getAsJsonObject();

                for (Map.Entry<String, JsonElement> methodEntry : pathItem.entrySet()) {
                    markdown.append("### Method:").append("\n**" + methodEntry.getKey().toUpperCase()).append("**\n\n");
                    processOperation(markdown, methodEntry.getValue().getAsJsonObject());
                }
            }

            return markdown.toString();
        }
    }

    // Generates a table based on parameters
    private String generateMarkdownTable(JsonArray parameters) {
        StringBuilder table = new StringBuilder();

        int[] columnWidths = new int[4];

        columnWidths[0] = "name".length();
        columnWidths[1] = "in".length();
        columnWidths[2] = "required".length();
        columnWidths[3] = "schema".length();

        // Gets correct width for table to ensure length is correct
        for (JsonElement param : parameters) {
            JsonObject parameter = param.getAsJsonObject();
            columnWidths[0] = Math.max(columnWidths[0], parameter.get("name").getAsString().length());
            columnWidths[1] = Math.max(columnWidths[1], parameter.get("in").getAsString().length());
            columnWidths[2] = Math.max(columnWidths[2],
                    String.valueOf(parameter.get("required").getAsBoolean()).length());
            columnWidths[3] = Math.max(columnWidths[3],
                    getParameterSchemaDescription(parameter.getAsJsonObject("schema")).length());
        }

        // Creates table based on width calculated above
        table.append(String.format("| %-" + columnWidths[0] + "s | %-" + columnWidths[1] + "s | %-" + columnWidths[2]
                + "s | %-" + columnWidths[3] + "s |\n", "Name", "In", "Required", "Schema"));

        table.append("|");

        for (int width : columnWidths) {

            for (int i = 0; i < width + 2; i++) {
                table.append("-");
            }
            table.append("|");
        }

        table.append("\n");

        for (JsonElement param : parameters) {
            JsonObject parameter = param.getAsJsonObject();
            table.append(String.format(
                    "| %-" + columnWidths[0] + "s | %-" + columnWidths[1] + "s | %-" + columnWidths[2]
                            + "s | %-" + columnWidths[3] + "s |\n",
                    parameter.get("name").getAsString(), parameter.get("in").getAsString(),
                    parameter.get("required").getAsBoolean(),
                    getParameterSchemaDescription(parameter.getAsJsonObject("schema"))));

        }

        return table.toString();
    }

    private void processOperation(StringBuilder markdown, JsonObject operation) {
        // Summary
        if (operation.has("summary")) {
            markdown.append("### Summary:\n");
            if (operation.get("summary").getAsString() != "") {
                markdown.append(operation.get("summary").getAsString()).append("\n\n");
            } else {
                markdown.append("No Summary\n");
            }
        }

        // Description
        if (operation.has("description")) {
            markdown.append("### Description:\n");
            if (operation.get("description").getAsString() != "") {
                markdown.append(operation.get("description").getAsString()).append("\n\n");
            } else {
                markdown.append("No Description\n");
            }
        }

        // Operation ID
        if (operation.has("operationId")) {
            markdown.append("### Operation ID\n");
            markdown.append("`").append(operation.get("operationId").getAsString()).append("`\n\n");
        }

        // Parameters
        if (operation.has("parameters")) {
            markdown.append("### Parameters\n");
            JsonArray parameters = operation.getAsJsonArray("parameters");

            if (parameters.size() > 0) {
                markdown.append(generateMarkdownTable(parameters));
            } else {
                markdown.append("No Parameters Specified\n\n");
            }
        }

        // Request Body
        if (operation.has("requestBody")) {
            markdown.append("### Request Body\n");
            JsonObject requestBody = operation.getAsJsonObject("requestBody");
            JsonObject content = requestBody.getAsJsonObject("content");
            JsonObject applicationJson = content.getAsJsonObject("application/json");
            JsonObject schema = applicationJson.getAsJsonObject("schema");
            markdown.append("```json\n");
            generateSchemaMarkdown(markdown, schema);
            markdown.append("\n```\n\n");
        }

        // Responses
        if (operation.has("responses")) {
            markdown.append("### Responses\n");
            JsonObject responses = operation.getAsJsonObject("responses");
            for (Map.Entry<String, JsonElement> responseEntry : responses.entrySet()) {
                JsonObject responseObj = responseEntry.getValue().getAsJsonObject();
                markdown.append("* **").append(responseEntry.getKey()).append(" ")
                        .append(responseObj.get("description").getAsString()).append("**\n");
                if (responseObj.has("content")) {
                    JsonObject content = responseObj.getAsJsonObject("content");
                    JsonObject applicationJson = content.getAsJsonObject("application/json");
                    if (applicationJson != null) {
                        JsonObject schema = applicationJson.getAsJsonObject("schema");
                        markdown.append("```json\n");
                        generateSchemaMarkdown(markdown, schema);
                        markdown.append("\n```\n");
                    }
                }
            }
            markdown.append("\n");
        }
    }

    private String getParameterSchemaDescription(JsonObject schema) {
        StringBuilder description = new StringBuilder();
        description.append(schema.get("type").getAsString());
        if (schema.has("format")) {
            description.append(" (").append(schema.get("format").getAsString()).append(")");
        }
        if (schema.has("default")) {
            description.append(" (default: ").append(schema.get("default").toString()).append(")");
        }
        if (schema.has("maximum")) {
            description.append(" (maximum: ").append(schema.get("maximum").getAsInt()).append(")");
        }
        if (schema.has("minimum")) {
            description.append(" (minimum: ").append(schema.get("minimum").getAsInt()).append(")");
        }
        return description.toString();
    }

    private void generateSchemaMarkdown(StringBuilder markdown, JsonObject schema) {
        if (schema.has("$ref")) {
            schema = resolveReference(schema).getAsJsonObject();
        }
        if (schema.has("properties")) {
            JsonObject properties = schema.getAsJsonObject("properties");
            for (Map.Entry<String, JsonElement> propertyEntry : properties.entrySet()) {
                JsonObject propertyObj = propertyEntry.getValue().getAsJsonObject();
                markdown.append("\"").append(propertyEntry.getKey()).append("\": ");
                if (propertyObj.has("$ref")) {
                    markdown.append(resolveReference(propertyObj).toString());
                } else {
                    markdown.append("\"").append(propertyObj.get("type").getAsString()).append("\"");
                }
                markdown.append(",\n");
            }
        } else if (schema.has("type") && schema.get("type").getAsString().equals("array") && schema.has("items")) {
            markdown.append("[\n");
            generateSchemaMarkdown(markdown, schema.getAsJsonObject("items"));
            markdown.append("\n]\n");
        } else {
            markdown.append("{ // Items in the array are of type '").append(schema.get("type").getAsString())
                    .append("' }\n");
        }
    }

    private JsonElement resolveReference(JsonObject object) {
        if (object.has("$ref")) {
            String ref = object.get("$ref").getAsString();
            return componentsMap.get(ref);
        }
        return object;
    }
}