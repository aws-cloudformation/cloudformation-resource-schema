package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;

import org.json.JSONObject;

import java.io.InputStream;

public interface SchemaValidator {

    /**
     * Perform JSON Schema validation for the input model against the specified schema
     * @param modelObject   JSON-encoded resource model
     * @param schemaStream  The JSON schema to validate the model against
     * @throws ValidationException  Thrown for any schema validation errors
     */
    void validateObject(final JSONObject modelObject,
                        final InputStream schemaStream) throws ValidationException;

}
