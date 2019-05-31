package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import lombok.Builder;
import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Parser implements SchemaParser {

    private static final String JSON_SCHEMA_PATH = "/schema/schema";
    private static final String DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private final JSONObject definitionSchemaObject;
    private final JSONObject jsonSchemaObject;

    @Builder
    public Parser() {
        // local copy of the draft-07 schema used to avoid remote reference calls
        jsonSchemaObject = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(JSON_SCHEMA_PATH)));
        definitionSchemaObject = new JSONObject(
            new JSONTokener(this.getClass().getResourceAsStream(DEFINITION_SCHEMA_PATH))
        );
    }

    public ResourceTypeSchema getSchema(final InputStream resourceSchemaStream) {
        final JSONObject resourceSchemaObject = new JSONObject(new JSONTokener(resourceSchemaStream));

        // first validate incoming resource schema against definition schema
        Validator.builder().build()
            .validateObject(resourceSchemaObject, this.getClass().getResourceAsStream(DEFINITION_SCHEMA_PATH));

        // now extract identifiers from resource schema
        final SchemaLoader loader = SchemaLoader.builder()
            .schemaJson(resourceSchemaObject)
            // registers the local schema with the draft-07 url
            .draftV7Support()
            .build();

        final ObjectSchema.Builder builder = (ObjectSchema.Builder) loader.load();

        return new ResourceTypeSchema(builder);
    }

    public List<String> getCreateOnlyProperties(final InputStream resourceSchemaStream) throws ValidationException {
        final ResourceTypeSchema schema = getSchema(resourceSchemaStream);

        return schema.getCreateOnlyProperties().stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<String> getDeprecatedProperties(final InputStream resourceSchemaStream) throws ValidationException {
        final ResourceTypeSchema schema = getSchema(resourceSchemaStream);

        return schema.getDeprecatedProperties().stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<List<String>> getIdentifiers(final InputStream resourceSchemaStream) throws ValidationException {
        final ResourceTypeSchema schema = getSchema(resourceSchemaStream);

        final List<List<String>> identifiers = new ArrayList<>();
        schema.getIdentifiers().forEach(i ->
            identifiers.add(
                i.stream().map(Object::toString).collect(Collectors.toList())
            )
        );
        return identifiers;
    }

    public List<String> getReadOnlyProperties(final InputStream resourceSchemaStream) throws ValidationException {
        final ResourceTypeSchema schema = getSchema(resourceSchemaStream);

        return schema.getReadOnlyProperties().stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<String> getWriteOnlyProperties(final InputStream resourceSchemaStream) throws ValidationException {
        final ResourceTypeSchema schema = getSchema(resourceSchemaStream);

        return schema.getWriteOnlyProperties().stream().map(JSONPointer::toString).collect(Collectors.toList());
    }
}
