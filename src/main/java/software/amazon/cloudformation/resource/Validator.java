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

import lombok.Builder;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader.SchemaLoaderBuilder;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.json.JSONObject;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

public class Validator extends BaseValidator {

    protected static final String RESOURCE_DEFINITION_SCHEMA_PATH = "/schema/"
        + "provider.definition.schema.v1.json";
    protected static final String TYPE_CONFIGURATION_DEFINITION_SCHEMA_PATH = "/schema/"
        + "provider.configuration.definition.schema.v1.json";
    private static final URI RESOURCE_DEFINITION_SCHEMA_URI = newURI(
        "https://schema.cloudformation.us-east-1.amazonaws.com/provider.definition.schema.v1.json");
    private JSONObject typeConfigurationDefinitionJson;

    public Validator(SchemaClient downloader) {
        this(loadResourceAsJSON(RESOURCE_DEFINITION_SCHEMA_PATH), downloader);
        this.typeConfigurationDefinitionJson = loadResourceAsJSON(TYPE_CONFIGURATION_DEFINITION_SCHEMA_PATH);
    }

    private Validator(JSONObject definitionSchema,
                      SchemaClient downloader) {
        super(definitionSchema, downloader);
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
        registerMetaSchema(builder, typeConfigurationDefinitionJson);
        builder.schemaJson(definitionSchemaJsonObject);
        return builder.build().load().build();
    }

    /**
     * Performs JSON Schema validation for the input resource definition against the resource provider definition schema
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
     * @return - Schema instance for the given Resource Definition
     * @throws ValidationException if supplied <code>resourceDefinition</code> is invalid.
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
            registerMetaSchema(builder, resourceDefinition);
            registerMetaSchema(builder, typeConfigurationDefinitionJson);
            builder.schemaJson(resourceDefinition);
            // when resource definition is loaded as a schema, $refs are resolved and validated
            return builder.build().load().build();
        } catch (final org.everit.json.schema.ValidationException e) {
            throw ValidationException.newScrubbedException(e);
        } catch (final org.everit.json.schema.SchemaException e) {
            throw new ValidationException(e.getMessage(), e.getSchemaLocation(), e);
        }
    }
}
