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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ValidatorTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TEST_VALUE_SCHEMA_PATH = "/scrubbed-values-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String DESCRIPTION_KEY = "description";
    private static final String PRIMARY_IDENTIFIER_KEY = "primaryIdentifier";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";
    private static final String EXAMPLE_DESCRIPTION = "Resource provider descriptions are important for customers to know what the resource is expected to do.";
    private static final String EXAMPLE_PRIMARY_IDENTIFIER = "/properties/propertyA";

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
        final String propVal = "abc";
        final JSONObject object = new JSONObject().put("propertyA", propVal);

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessageContaining("propertyB").hasMessageNotContaining(propVal);
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isEqualTo("required");
    }

    @Test
    public void validateObject_invalidObjectAdditionalProperties_shouldThrow() {
        final String propValue = "notpartofschema";
        final JSONObject object = new JSONObject().put("propertyA", "abc").put("propertyB", Arrays.asList(1, 2, 3))
            .put("propertyX", propValue);

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessageContaining("propertyX");
        assertThat(e).hasMessageNotContaining(propValue);
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isEqualTo("additionalProperties");
    }

    @Test
    public void validateObject_invalidType_messageShouldNotContainValue() {
        final JSONObject object = new JSONObject().put("BooleanProperty", "true");

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e).hasMessageNotContaining("true");
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getKeyword()).isEqualTo("type");
    }

    @ParameterizedTest
    @CsvSource({"WaaaaaaaayTooLong,maxLength", "TooShort,minLength", "NoPatternMatch,pattern"})
    public void validateObject_invalidStringValue_messageShouldNotContainValue(final String value, final String keyword) {
        final JSONObject object = new JSONObject().put("StringProperty", value);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getSchemaPointer()).isEqualTo("#/StringProperty");
        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getMessage()).doesNotContain(value);
        assertThat(e.getCausingExceptions().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"enum", "const"})
    public void validateObject_invalidEnumValue_messageShouldNotContainValue(final String keyword) {
        final String propName = keyword + "Property";
        final String propVal = "NotPartOfEnum";
        final JSONObject object = new JSONObject().put(propName, propVal);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        final String pointer = "#/" + propName;
        assertThat(e.getSchemaPointer()).isEqualTo(pointer);
        assertThat(e.getKeyword()).isEqualTo("allOf");
        assertThat(e).hasMessageNotContaining(propVal);
        assertThat(e.getCausingExceptions()).hasSize(1);

        final ValidationException enumEx = e.getCausingExceptions().get(0);
        assertThat(enumEx.getSchemaPointer()).isEqualTo(pointer);
        assertThat(enumEx).hasMessageNotContaining(propVal);
        assertThat(enumEx.getKeyword()).isEqualTo(keyword);
        assertThat(enumEx.getCausingExceptions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"test-test,uniqueItems", "test,minItems", "test-test2-test3,maxItems", "Y-X,contains"})
    public void validateObject_invalidArrayValue_messageShouldNotContainValue(final String listAsString, final String keyword) {
        final List<String> values = Arrays.asList(listAsString.split("-"));
        final JSONObject object = new JSONObject().put("ArrayProperty", values);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/ArrayProperty");
        assertThat(e.getCausingExceptions()).isEmpty();

        values.forEach(v -> assertThat(e).hasMessageNotContaining(v));
    }

    @ParameterizedTest
    @CsvSource({"5,IntProperty,minimum", "300,IntProperty,maximum", "23,IntProperty,multipleOf", "5,NumberProperty,exclusiveMinimum", "300,NumberProperty,exclusiveMaximum"})
    public void validateObject_invalidNumValue_messageShouldNotContainValue(final String numAsString, final String propName, final String keyword) {
        final JSONObject object = new JSONObject().put(propName, Integer.valueOf(numAsString));

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/" + propName);
        assertThat(e).hasMessageNotContaining(numAsString);
        assertThat(e.getCausingExceptions().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"test,minProperties", "test-test1-test2,maxProperties","test-dep,dependencies"})
    public void validateObject_invalidSubObject_messageShouldNotContainValue(final String keysAsString, final String keyword) {
        final String val = "testValue";
        final JSONObject subSchema = new JSONObject();

        final List<String> keys = Arrays.asList(keysAsString.split("-"));
        keys.forEach(k -> subSchema.put(k, val));

        final JSONObject object = new JSONObject().put("ObjectProperty", subSchema);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/ObjectProperty");
        assertThat(e).hasMessageNotContaining(val);
        assertThat(e.getCausingExceptions().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"test,minProperties", "test-test1-test2,maxProperties","test-dep,dependencies"})
    public void validateObject_invalidPatternProperties_messageShouldNotContainValue(final String keysAsString, final String keyword) {
        final String val = "Value";
        final JSONObject object = new JSONObject().put("MapProperty", new JSONObject().put("def", "val"));

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getSchemaPointer()).isEqualTo("#/MapProperty");
        assertThat(e.getKeyword()).isEqualTo("additionalProperties");
        assertThat(e).hasMessageNotContaining(val);
        assertThat(e.getCausingExceptions()).isEmpty();

    }

    @ParameterizedTest
    @ValueSource(strings = {"anyOf", "allOf", "oneOf"})
    public void validateObject_invalidCombiner_messageShouldNotContainValue(final String keyword) {
        final String propName = keyword + "Property";
        final String propVal = "NotAnInteger";
        final JSONObject object = new JSONObject().put(propName, propVal);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
                ValidationException.class);

        final String pointer = "#/" + propName;
        assertThat(e.getSchemaPointer()).isEqualTo(pointer);
        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e).hasMessageNotContaining(propVal);
        assertThat(e.getCausingExceptions()).hasSize(1);

        final ValidationException enumEx = e.getCausingExceptions().get(0);
        assertThat(enumEx.getSchemaPointer()).isEqualTo(pointer);
        assertThat(enumEx).hasMessageNotContaining(propVal);
        assertThat(enumEx.getKeyword()).isEqualTo("type");
        assertThat(enumEx.getCausingExceptions()).isEmpty();
    }

    @Test
    public void validateObject_invalidObjectMultiple_messageShouldNotContainValue() {
        final String propValue = "notpartofschema";
        final JSONObject object = new JSONObject().put("propertyA", 123).put("propertyB", Arrays.asList(1, 2, 3))
                .put("propertyX", propValue).put("propertyY", propValue);

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(object,
                        new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
                ValidationException.class);

        assertThat(e.getCausingExceptions()).hasSize(3);
        assertThat(e).hasMessage("#: 3 schema violations found");
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isNull();

        e.getCausingExceptions().forEach(ce -> {
            assertThat(ce).hasMessageNotContaining(propValue);
            assertThat(ce.getCausingExceptions()).isEmpty();
        });
    }

    @Test
    public void validateDefinition_validMinimalDefinition_shouldNotThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));
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
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER)).put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [" + PROPERTIES_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoDescriptionKey_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()))
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER));

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [" + DESCRIPTION_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoProperties_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PROPERTIES_KEY, new JSONObject())
            .put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER));

        ValidationException e = catchThrowableOfType(() -> validator.validateResourceDefinition(definition),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessage("#/properties: minimum size: [1], found: [0]");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoPrimaryIdentifier_shouldThrow() {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));

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
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageNotContaining(documentationUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingDocumentationUrl_shouldNotThrow(final String documentationUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl);
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongDocumentationUrl_shouldThrow() {
        final String documentationUrl = "https://much-too-loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong.com/";
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("documentationUrl", documentationUrl);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageContaining(Integer.toString(documentationUrl.length()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com", "http://example.com", "git://example.com", "https://", })
    public void validateDefinition_nonMatchingSourceUrls_shouldThrow(final String sourceUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageNotContaining((sourceUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingSourceUrl_shouldNotThrow(final String sourceUrl) {
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl);
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongSourceUrl_shouldThrow() {
        final String sourceUrl = "https://much-too-loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong.com/";
        final JSONObject definition = new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
            .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION).put(PRIMARY_IDENTIFIER_KEY, Arrays.asList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject())).put("sourceUrl", sourceUrl);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageContaining(Integer.toString(sourceUrl.length()));
    }
}
