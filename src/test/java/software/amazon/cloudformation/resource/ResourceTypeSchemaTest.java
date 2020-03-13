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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static software.amazon.cloudformation.resource.ValidatorTest.loadJSON;

import java.util.List;

import org.everit.json.schema.PublicJSONPointer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

public class ResourceTypeSchemaTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String EMPTY_SCHEMA_PATH = "/empty-schema.json";
    private static final String SCHEMA_WITH_ONEOF = "/valid-with-oneof-schema.json";
    private static final String MINIMAL_SCHEMA_PATH = "/minimal-schema.json";
    private static final String NO_ADDITIONAL_PROPERTIES_SCHEMA_PATH = "/no-additional-properties-schema.json";
    private static final String WRITEONLY_MODEL_PATH = "/write-only-model.json";

    @Test
    public void getProperties() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        assertThat(schema.getDescription()).isEqualTo("A test schema for unit tests.");
        assertThat(schema.getSourceUrl()).isEqualTo("https://mycorp.com/my-repo.git");
        assertThat(schema.getTypeName()).isEqualTo("AWS::Test::TestModel");
        assertThat(schema.getUnprocessedProperties()).isEmpty();
    }

    @Test
    public void getCreateOnlyProperties() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getCreateOnlyPropertiesAsStrings();

        assertThat(result).containsExactly("/properties/propertyA", "/properties/propertyD");
    }

    @Test
    public void getDeprecatedProperties() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getDeprecatedPropertiesAsStrings();
        assertThat(result).containsExactly("/properties/propertyC");
    }

    @Test
    public void getPrimaryIdentifier() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getPrimaryIdentifierAsStrings();
        assertThat(result).containsExactly("/properties/propertyA");
    }

    @Test
    public void getAdditionalIdentifiers() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<List<String>> result = schema.getAdditionalIdentifiersAsStrings();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly("/properties/propertyB");
    }

    @Test
    public void getReadOnlyProperties() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getReadOnlyPropertiesAsStrings();
        assertThat(result).containsExactly("/properties/propertyB");
    }

    @Test
    public void getWriteOnlyProperties() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getWriteOnlyPropertiesAsStrings();
        assertThat(result).containsExactly("/properties/propertyC", "/properties/propertyE/nestedProperty");
    }

    @Test
    public void getReplacementStrategy() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getReplacementStrategy();
        assertThat(result).containsExactly("create", "delete");
        assertThat(result).doesNotContainSequence("delete", "create");
    }

    @Test
    public void invalidSchema_shouldThrow() {
        JSONObject o = loadJSON(EMPTY_SCHEMA_PATH);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> ResourceTypeSchema.load(o)).withNoCause()
            .withMessage("#/properties: minimum size: [1], found: [0]");
    }

    @Test
    public void invalidSchema_noAdditionalProperties_shouldThrow() {
        JSONObject o = loadJSON(NO_ADDITIONAL_PROPERTIES_SCHEMA_PATH);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> ResourceTypeSchema.load(o)).withNoCause()
            .withMessage("#: required key [additionalProperties] not found");
    }

    @Test
    public void minimalSchema_hasNoSemantics() {
        JSONObject o = loadJSON(MINIMAL_SCHEMA_PATH);
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        assertThat(schema.getDescription()).isEqualTo("A test schema for unit tests.");
        assertThat(schema.getSourceUrl()).isNull();
        assertThat(schema.getDocumentationUrl()).isNull();
        assertThat(schema.getTypeName()).isEqualTo("AWS::Test::TestModel");
        assertThat(schema.getUnprocessedProperties()).isEmpty();
        assertThat(schema.getCreateOnlyPropertiesAsStrings()).isEmpty();
        assertThat(schema.getDeprecatedPropertiesAsStrings()).isEmpty();
        assertThat(schema.getPrimaryIdentifierAsStrings()).containsExactly("/properties/PropertyA");
        assertThat(schema.getAdditionalIdentifiersAsStrings()).isEmpty();
        assertThat(schema.getReadOnlyPropertiesAsStrings()).isEmpty();
        assertThat(schema.getWriteOnlyPropertiesAsStrings()).isEmpty();
        assertThat(schema.getReplacementStrategy()).isEmpty();
    }

    @Test
    public void removeWriteOnlyProperties_hasWriteOnlyProperties_shouldRemove() {
        JSONObject o = loadJSON(TEST_SCHEMA_PATH);
        ResourceTypeSchema schema = ResourceTypeSchema.load(o);
        JSONObject resourceModel = loadJSON(WRITEONLY_MODEL_PATH);

        schema.removeWriteOnlyProperties(resourceModel);

        // check that model doesn't contain the writeOnly properties

        assertThat(schema.getWriteOnlyPropertiesAsStrings().stream()
            .anyMatch(writeOnlyProperty -> new PublicJSONPointer(writeOnlyProperty.replaceFirst("^/properties", ""))
                .isInObject(resourceModel))).isFalse();

        // ensure that other non writeOnlyProperty is not removed
        assertThat(resourceModel.has("propertyB")).isTrue();
    }

    @Test
    public void validSchema_withOneOf_shouldSucceed() {
        JSONObject resource = loadJSON("/valid-with-oneof-schema.json");
        final ResourceTypeSchema schema = ResourceTypeSchema.load(resource);
    }

    /**
     * validate a valid model against a schema containing conditionals, like "oneOf"
     */
    @Test
    public void schemaWithOneOf_validateCorrectModel_shouldSucceed() {
        JSONObject resourceDefinition = loadJSON(SCHEMA_WITH_ONEOF);
        ResourceTypeSchema schema = ResourceTypeSchema.load(resourceDefinition);

        // SCHEMA_WITH_ONEOF requires either propertyA or propertyB
        // both models below should validate successfully
        final JSONObject modelWithPropA = getEmptyModel().put("propertyA", "property a, not b");
        final JSONObject modelWithPropB = getEmptyModel().put("propertyB", "property b, not a");

        schema.validate(modelWithPropA);
        schema.validate(modelWithPropB);
    }

    /**
     * validate an invalid model against a schema containing conditionals ("oneOf")
     */
    @Test
    public void schemaWithOneOf_validateIncorrectModel_shouldThrow() {
        JSONObject resourceDefinition = loadJSON(SCHEMA_WITH_ONEOF);
        ResourceTypeSchema schema = ResourceTypeSchema.load(resourceDefinition);

        final JSONObject modelWithNeitherAnorB = getEmptyModel();

        assertThatExceptionOfType(org.everit.json.schema.ValidationException.class).isThrownBy(
            () -> schema.validate(modelWithNeitherAnorB));
    }

    static JSONObject getEmptyModel() {
        return new JSONObject().put("id", "required.identifier");
    }

}
