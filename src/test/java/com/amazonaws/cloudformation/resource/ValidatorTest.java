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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.amazonaws.cloudformation.resource.exceptions.ValidationException;

import java.util.Arrays;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ValidatorTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String DESCRIPTION_KEY = "description";
    private static final String PRIMARY_IDENTIFIER_KEY = "primaryIdentifier";
    private static final String ADDITIONAL_PROPERTIES_KEY = "additionalProperties";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";
    private static final String EXAMPLE_DESCRIPTION = "Resource provider descriptions are important for customers to know what the resource is expected to do.";
    private static final String EXAMPLE_PRIMARY_IDENTIFIER = "/properties/propertyA";
    private static final Boolean ADDITIONAL_PROPERTIES_VALUE = false;

    private Validator validator;

    @BeforeEach
    public void setUp() {
        validator = new Validator();
    }

    @Test
    public void validateObject_validObject_shouldNotThrow() {
        final JSONObject object = new JSONObject().put("propertyA", "abc").put("propertyB", Arrays.asList(1, 2, 3));

        validator.validateObject(object, new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH))));
    }

    @Test
    public void validateObject_invalidObjectMissingRequiredProperties_shouldThrow() {
        final JSONObject object = new JSONObject().put("propertyB", Arrays.asList(1, 2, 3));

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessageContaining("propertyA");
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateObject_invalidObjectAdditionalProperties_shouldThrow() {
        final JSONObject object = new JSONObject().put("propertyA", "abc").put("propertyB", Arrays.asList(1, 2, 3))
            .put("propertyX", "notpartofschema");

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessageContaining("propertyX");
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateObject_invalidObjectMultiple_shouldThrow() {
        final JSONObject object = new JSONObject().put("propertyA", 123).put("propertyB", Arrays.asList(1, 2, 3))
            .put("propertyX", "notpartofschema").put("propertyY", "notpartofschema");

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e.getCausingExceptions()).hasSize(3);
        assertThat(e).hasMessage("#: 3 schema violations found");
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateDefinition_validMinimalDefinition_shouldNotThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()))
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_validExampleDefinition_shouldNotThrow() {
        final JSONObject definition = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_invalidDefinitionNoPropertiesKey_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER)).put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [" + PROPERTIES_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoDescriptionKey_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()))
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [" + DESCRIPTION_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoProperties_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PROPERTIES_KEY, new JSONObject())
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#/properties: minimum size: [1], found: [0]");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoPrimaryIdentifier_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()))
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [primaryIdentifier] not found");
    }

    @Test
    public void validateDefinition_invalidHandlerSection_shouldThrow() {

        final JSONObject definition = new JSONObject(new JSONTokener(this.getClass()
            .getResourceAsStream("/invalid-handlers.json")));

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#/handlers/read: #: only 1 subschema matches out of 2");
    }

    @Test
    public void validateDefinition_validHandlerSection_shouldNotThrow() {

        final JSONObject definition = new JSONObject(new JSONTokener(this.getClass()
            .getResourceAsStream("/valid-with-handlers.json")));

        validator.validateResourceDefinition(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com", "http://example.com", "git://example.com", "https://", })
    public void validateDefinition_nonMatchingDocumentationUrl_shouldThrow(final String documentationUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageContaining(documentationUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingDocumentationUrl_shouldNotThrow(final String documentationUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongDocumentationUrl_shouldThrow() {
        final String documentationUrl = "https://much-too-loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong.com/";
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageContaining(Integer.toString(documentationUrl.length()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com", "http://example.com", "git://example.com", "https://", })
    public void validateDefinition_nonMatchingSourceUrls_shouldThrow(final String sourceUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageContaining(sourceUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingSourceUrl_shouldNotThrow(final String sourceUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongSourceUrl_shouldThrow() {
        final String sourceUrl = "https://much-too-loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong.com/";
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl)
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageContaining(Integer.toString(sourceUrl.length()));
    }
}
