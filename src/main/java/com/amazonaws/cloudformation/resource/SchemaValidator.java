package com.amazonaws.cloudformation.resource;

import com.amazonaws.cloudformation.resource.exceptions.ValidationException;
import org.json.JSONObject;

import java.io.InputStream;

public interface SchemaValidator {

    String DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";

    /**
     * Perform JSON Schema validation for the input model against the specified schema
     * @param modelObject   JSON-encoded resource model
     * @param schemaStream  The JSON schema to validate the model against
     * @throws ValidationException  Thrown for any schema validation errors
     */
    void validateObject(JSONObject modelObject,
                        InputStream schemaStream) throws ValidationException;

}
