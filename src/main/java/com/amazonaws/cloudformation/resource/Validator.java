/*
* Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License").
* You may not use this file except in compliance with the License.
* A copy of the License is located at
*
*  http://aws.amazon.com/apache2.0
*
* or in the "license" file accompanying this file. This file is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied. See the License for the specific language governing
* permissions and limitations under the License.
*/
package com.amazonaws.cloudformation.resource;

import com.amazonaws.cloudformation.resource.exceptions.ValidationException;

import java.net.URI;
import java.net.URISyntaxException;

import lombok.Builder;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Validator implements SchemaValidator {

    private static final String JSON_SCHEMA_ID = "https://json-schema.org/draft-07/schema";
    private static final String JSON_SCHEMA_PATH = "/schema/schema";
    private static final String METASCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private final JSONObject definitionSchemaJsonObject;
    private final JSONObject jsonSchemaObject;

    @Builder
    public Validator() {
        // local copy of the draft-07 schema used to avoid remote reference calls
        jsonSchemaObject = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(JSON_SCHEMA_PATH)));
        definitionSchemaJsonObject = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(METASCHEMA_PATH)));
    }

    @Override
    public void validateObject(final JSONObject modelObject, final JSONObject definitionSchemaObject) throws ValidationException {
        try {
            final URI schemaURI = new URI(JSON_SCHEMA_ID);
            final SchemaLoader loader = SchemaLoader.builder().schemaJson(definitionSchemaObject)
                // registers the local schema with the draft-07 url
                .registerSchemaByURI(schemaURI, jsonSchemaObject).draftV7Support().build();
            final Schema schema = loader.load().build();

            try {
                schema.validate(modelObject); // throws a ValidationException if this object is invalid
            } catch (final org.everit.json.schema.ValidationException e) {
                throw new ValidationException(e);
            }
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Invalid URI format for JSON schema.");
        }
    }

    /**
     * Perform JSON Schema validation for the input resource definition against the
     * resource provider definition schema
     *
     * @param definition JSON-encoded resource definition
     * @throws ValidationException Thrown for any schema validation errors
     */
    public void validateResourceDefinition(final JSONObject definition) throws ValidationException {
        validateObject(definition, definitionSchemaJsonObject);
    }

}
