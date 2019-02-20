package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class Validator implements SchemaValidator {

    private static final String JSON_SCHEMA_ID = "https://json-schema.org/draft-07/schema";
    private static final String JSON_SCHEMA_PATH = "/schema/schema";
    private static final String METASCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private final InputStream definitionSchemaStream;
    private final JSONObject jsonSchemaObject;

    public Validator() {
        //local copy of the draft-07 schema used to avoid remote reference calls
        jsonSchemaObject = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(JSON_SCHEMA_PATH)));
        definitionSchemaStream = this.getClass().getResourceAsStream(METASCHEMA_PATH);
    }

    public void validateModel(final JSONObject modelObject,
                              final InputStream schemaStream) throws ValidationException {
        final JSONObject schemaObject = new JSONObject(new JSONTokener(schemaStream));
        try {
            final URI schemaURI = new URI(JSON_SCHEMA_ID);
            final SchemaLoader loader = SchemaLoader.builder()
                    .schemaJson(schemaObject)
                    //registers the local schema as with the draft-07 url
                    .registerSchemaByURI(schemaURI, jsonSchemaObject)
                    .draftV7Support()
                    .build();
            final Schema schema = loader.load().build();

            try {
                schema.validate(modelObject); // throws a ValidationException if this object is invalid
            } catch (org.everit.json.schema.ValidationException e) {
                throw new ValidationException(e);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URI format for json schema.");
        }
    }

    /**
     * Perform JSON Schema validation for the input resource definition against the resource provider definition schema
     * @param definition  JSON-encoded resource definition
     * @throws ValidationException  Thrown for any schema validation errors
     */
    public void validateResourceDefinition(final JSONObject definition) throws ValidationException {
        validateModel(definition, definitionSchemaStream);
    }

}
