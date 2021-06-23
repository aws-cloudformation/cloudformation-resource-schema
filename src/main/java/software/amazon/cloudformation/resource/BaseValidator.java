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

import lombok.SneakyThrows;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SchemaLoader.SchemaLoaderBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

class BaseValidator implements SchemaValidator {
    protected static final String ID_KEY = "$id";
    protected static final URI JSON_SCHEMA_URI_HTTP = newURI("http://json-schema.org/draft-07/schema");
    protected static final String JSON_SCHEMA_PATH = "/schema/schema";
    protected static final String BASE_DEFINITION_SCHEMA_PATH = "/schema/base.definition.schema.v1.json";

    /**
     * resource definition schema ("resource schema schema"). All resource schemas
     * are validated against this one and JSON schema draft v7 below.
     */
    protected final JSONObject definitionSchemaJsonObject;

    /**
     * locally cached draft-07 JSON schema. All resource schemas are validated
     * against it
     */
    private final JSONObject jsonSchemaObject;

    /**
     * locally cached draft-07 JSON schema. All resource schemas are validated
     * against it
     */
    private final JSONObject baseDefinitionSchemaObject;
    /**
     * this is what SchemaLoader uses to download remote $refs. Not necessarily an
     * HTTP client, see the docs for details. We override the default SchemaClient
     * client in unit tests to be able to control how remote refs are resolved.
     */
    private final SchemaClient downloader;

    BaseValidator(JSONObject definitionSchema,
                  SchemaClient downloader) {
        this.jsonSchemaObject = loadResourceAsJSON(JSON_SCHEMA_PATH);
        this.definitionSchemaJsonObject = definitionSchema;
        this.downloader = downloader;
        this.baseDefinitionSchemaObject = loadResourceAsJSON(BASE_DEFINITION_SCHEMA_PATH);
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
            final String id;
            if (schema.has(ID_KEY)) {
                id = schema.getString(ID_KEY);

                if (id.isEmpty()) {
                    throw new ValidationException("Invalid $id value", "$id", "[empty string]");
                }
                final URI uri = new URI(id);
                loaderBuilder.registerSchemaByURI(uri, schema);
            }
        } catch (URISyntaxException | JSONException e) {
            // $id is not a string or URI is invalid
            throw new ValidationException("Invalid $id value", "$id", e);
        }
    }

    /**
     * Convenience method - creates a SchemaLoaderBuilder with cached JSON draft-07 meta-schema
     *
     * @param schemaObject
     * @return
     */
    SchemaLoaderBuilder getSchemaLoader(JSONObject schemaObject) {
        return getSchemaLoader().schemaJson(schemaObject);
    }

    /** get schema-builder preloaded with JSON draft V7 meta-schema */
    SchemaLoaderBuilder getSchemaLoader() {
        final SchemaLoaderBuilder builder = SchemaLoader
            .builder()
            .draftV7Support()
            .schemaClient(downloader);
        // // registers the local schema with the draft-07 url
        // // draftV7 schema is registered twice because - once for HTTP and once for HTTPS URIs
        builder.registerSchemaByURI(JSON_SCHEMA_URI_HTTP, jsonSchemaObject);
        // registers the local base definition schema to resolve the refs
        registerMetaSchema(builder, baseDefinitionSchemaObject);
        return builder;
    }

    static JSONObject loadResourceAsJSON(String path) {
        return new JSONObject(new JSONTokener(BaseValidator.class.getResourceAsStream(path)));
    }

    /** wrapper around new URI that throws an unchecked exception */
    @SneakyThrows(URISyntaxException.class)
    static URI newURI(final String uri) {
        return new URI(uri);
    }
}
