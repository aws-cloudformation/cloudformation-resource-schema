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
package software.amazon.cloudformation.resource;

import java.net.URI;
import java.net.URISyntaxException;

import lombok.Builder;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SchemaLoader.SchemaLoaderBuilder;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

public class Validator implements SchemaValidator {

    private static final String ID_KEY = "$id";
    private static final String JSON_SCHEMA_PATH = "/schema/schema";
    private static final String RESOURCE_DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    /**
     * resource definition schema ("resource schema schema"). All resource schemas
     * are validated against this one and JSON schema draft v7 below.
     */
    private final JSONObject definitionSchemaJsonObject;
    /**
     * locally cached draft-07 JSON schema. All resource schemas are validated
     * against it
     */
    private final JSONObject jsonSchemaObject;
    /**
     * this is what SchemaLoader uses to download remote $refs. Not necessarily an
     * HTTP client, see the docs for details. We override the default SchemaClient
     * client in unit tests to be able to control how remote refs are resolved.
     */
    private final SchemaClient downloader;

    Validator(SchemaClient downloader) {
        this(loadResourceAsJSON(JSON_SCHEMA_PATH), loadResourceAsJSON(RESOURCE_DEFINITION_SCHEMA_PATH), downloader);
    }

    private Validator(JSONObject jsonSchema,
                      JSONObject definitionSchema,
                      SchemaClient downloader) {
        this.jsonSchemaObject = jsonSchema;
        this.definitionSchemaJsonObject = definitionSchema;
        this.downloader = downloader;
    }

    @Builder
    public Validator() {
        this(new DefaultSchemaClient());
    }

    @Override
    public void validateObject(final JSONObject modelObject, final JSONObject definitionSchemaObject) throws ValidationException {
        final SchemaLoaderBuilder loader = getSchemaLoader(definitionSchemaObject);

        try {
            final Schema schema = loader.build().load().build();
            schema.validate(modelObject); // throws a ValidationException if this object is invalid
        } catch (final org.everit.json.schema.ValidationException e) {
            throw ValidationException.newScrubbedException(e);
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
        // validateObject cannot validate schema-specific attributes. For example if definition
        // contains "propertyA": { "$ref":"./some-non-existent-location.json#definitions/PropertyX"}
        // validateObject will succeed, because all it cares about is that "$ref" is a URI
        // In order to validate that $ref points at an existing location in an existing document
        // we have to "load" the schema
        loadResourceDefinitionSchema(definition);
    }

    public Schema loadResourceDefinitionSchema(final JSONObject resourceDefinition) {
        validateObject(resourceDefinition, definitionSchemaJsonObject);
        return getResourceSchemaBuilder(resourceDefinition).build();
    }

    /**
     * returns Schema.Builder with pre-loaded JSON draft-07 meta-schema and resource definition meta-schema
     * (resource.definition.schema.v1.json). Resulting Schema.Builder can be used to build a schema that
     * can be used to validate parts of CloudFormation template.
     *
     * @param resourceDefinition - actual resource definition (not resource definition schema)
     * @return
     */
    public Schema.Builder<?> getResourceSchemaBuilder(final JSONObject resourceDefinition) {
        final SchemaLoaderBuilder loaderBuilder = getSchemaLoader(resourceDefinition);
        registerMetaSchema(loaderBuilder, definitionSchemaJsonObject);

        final SchemaLoader loader = loaderBuilder.build();
        try {
            return loader.load();
        } catch (org.everit.json.schema.SchemaException e) {
            throw new ValidationException(e.getMessage(), e.getSchemaLocation(), e);
        }
    }

    /**
     * Convenience method - creates a SchemaLoaderBuilder with cached JSON draft-07 meta-schema
     *
     * @param schemaObject
     * @return
     */
    private SchemaLoaderBuilder getSchemaLoader(JSONObject schemaObject) {
        final SchemaLoaderBuilder builder = SchemaLoader
            .builder()
            .schemaJson(schemaObject)
            .draftV7Support()
            .schemaClient(downloader);
        // registers the local schema with the draft-07 url
        registerMetaSchema(builder, jsonSchemaObject);
        return builder;
    }

    /**
     * Register a meta-schema with the SchemaLoaderBuilder. The meta-schema $id is used to generate schema URI
     * This has the effect of caching the meta-schema. When SchemaLoaderBuilder is used to build the Schema object,
     * the cached version will be used. No calls to remote URLs will be made.
     * Validator caches JSON schema (/resources/schema) and Resource Definition Schema
     * (/resources/provider.definition.schema.v1.json)
     *
     * @param loaderBuilder
     * @param schema meta-schema JSONObject to be cached. Must have a valid $id property
     */
    void registerMetaSchema(final SchemaLoaderBuilder loaderBuilder, JSONObject schema) {
        try {
            String id = schema.getString(ID_KEY);
            if (id.isEmpty()) {
                throw new ValidationException("Invalid $id value", "$id", "[empty string]");
            }
            final URI uri = new URI(id);
            loaderBuilder.registerSchemaByURI(uri, schema);
        } catch (URISyntaxException e) {
            throw new ValidationException("Invalid $id value", "$id", e);
        } catch (JSONException e) {
            // $id is missing or not a string
            throw new ValidationException("Invalid $id value", "$id", e);
        }
    }

    private static JSONObject loadResourceAsJSON(String path) {
        return new JSONObject(new JSONTokener(Validator.class.getResourceAsStream(path)));
    }
}
