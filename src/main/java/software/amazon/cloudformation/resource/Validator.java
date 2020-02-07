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

    private static final URI JSON_SCHEMA_URI_HTTPS = newURI("https://json-schema.org/draft-07/schema");
    private static final URI JSON_SCHEMA_URI_HTTP = newURI("http://json-schema.org/draft-07/schema");
    private static final URI RESOURCE_DEFINITION_SCHEMA_URI = newURI(
        "https://schema.cloudformation.us-east-1.amazonaws.com/provider.definition.schema.v1.json");
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

    public Validator(SchemaClient downloader) {
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

    /**
     * builds a Schema instance that can be used to validate Resource Definition Schema as a JSON object
     */
    private Schema makeResourceDefinitionSchema() {
        SchemaLoaderBuilder builder = getSchemaLoader();
        builder.schemaJson(definitionSchemaJsonObject);
        return builder.build().load().build();
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
     * Performs JSON Schema validation for the input resource definition against the
     * resource provider definition schema
     *
     * @param resourceDefinition JSON-encoded resource definition
     * @throws ValidationException Thrown for any schema validation errors
     */
    void validateResourceDefinition(final JSONObject resourceDefinition) {
        // loading resource definition always performs validation
        loadResourceDefinitionSchema(resourceDefinition);
    }

    /**
     * create a Schema instance that can be used to validate CloudFormation resources.
     *
     * @param resourceDefinition - CloudFormation Resource Provider Schema (Resource Definition)
     * @throws ValidationException if supplied <code>resourceDefinition</code> is invalid.
     * @return - Schema instance for the given Resource Definition
     */
    public Schema loadResourceDefinitionSchema(final JSONObject resourceDefinition) {

        // inject/replace $schema URI to ensure that provider definition schema is used
        resourceDefinition.put("$schema", RESOURCE_DEFINITION_SCHEMA_URI.toString());

        try {
            // step 1: validate resourceDefinition as a JSON object
            // this validator cannot validate schema-specific attributes. For example if definition
            // contains "propertyA": { "$ref":"./some-non-existent-location.json#definitions/PropertyX"}
            // validateObject will succeed, because all it cares about is that "$ref" is a URI
            // In order to validate that $ref points at an existing location in an existing document
            // we have to "load" the schema
            Schema resourceDefValidator = makeResourceDefinitionSchema();
            resourceDefValidator.validate(resourceDefinition);

            // step 2: load resource definition as a Schema that can be used to validate resource models;
            // definitionSchemaJsonObject becomes a meta-schema
            SchemaLoaderBuilder builder = getSchemaLoader();
            registerMetaSchema(builder, jsonSchemaObject);
            builder.schemaJson(resourceDefinition);
            // when resource definition is loaded as a schema, $refs are resolved and validated
            return builder.build().load().build();
        } catch (final org.everit.json.schema.ValidationException e) {
            throw ValidationException.newScrubbedException(e);
        } catch (final org.everit.json.schema.SchemaException e) {
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
        return getSchemaLoader().schemaJson(schemaObject);
    }

    /** get schema-builder preloaded with JSON draft V7 meta-schema */
    private SchemaLoaderBuilder getSchemaLoader() {
        final SchemaLoaderBuilder builder = SchemaLoader
            .builder()
            .draftV7Support()
            .schemaClient(downloader);

        // registers the local schema with the draft-07 url
        // draftV7 schema is registered twice because - once for HTTP and once for HTTPS URIs
        builder.registerSchemaByURI(JSON_SCHEMA_URI_HTTP, jsonSchemaObject);
        builder.registerSchemaByURI(JSON_SCHEMA_URI_HTTPS, jsonSchemaObject);

        return builder;
    }

    private static JSONObject loadResourceAsJSON(String path) {
        return new JSONObject(new JSONTokener(Validator.class.getResourceAsStream(path)));
    }

    /** wrapper around new URI that throws an unchecked exception */
    static URI newURI(final String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(uri);
        }
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
}
