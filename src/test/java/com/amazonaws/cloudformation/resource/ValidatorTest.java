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
        final JSONObject object = new JSONObject().put("propertyB", Arrays.asList(1, 2, 3));

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasNoCause().hasMessageContaining("propertyA");
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isEqualTo("required");
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
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isEqualTo("additionalProperties");
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
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getKeyword()).isNull();
    }

    /**
     * All error messages thrown by
     * {@link com.amazonaws.cloudformation.resource.exceptions.ValidationException#SAFE_KEYWORDS}
     * should contain their original values, since they do not contain error
     * messages. All others should be scrubbed: Integer Keywords: multipleOf,
     * minimum, maximum, exclusiveMaximum, exclusiveMinimum String Keywords: pattern
     * Enum Const
     */
    @Test
    public void validateObject_invalidValue_shouldThrow_originalMessageShouldNotContainValue() {
        final JSONObject object = new JSONObject().put("StringProperty", "DoesNotSatisfyPatternAndTooLong")
            .put("StringProperty2", "tooShort").put("EnumProperty", "NotPartOfEnum").put("ConstProperty", "InCorrectConst")
            .put("ArrayProperty", Arrays.asList(1, 1, 2, 3, 4, 5, 6, 7, 8)) // too many items
            .put("ArrayProperty2", Arrays.asList(1)) // too few items and does not contain 7
            .put("IntProperty", 3) // too small and is not multiple of 5
            .put("IntProperty2", 300) // too large
            .put("NumberProperty", 3) // too small
            .put("NumberProperty2", 300) // too large
            .put("BooleanProperty", "true") // incorrect type
            .put("ObjectProperty", new JSONObject().put("SomeRandom", "SomeValue")) // too few properties
            .put("ObjectProperty2", new JSONObject() // too many properties
                .put("Key1", "val1").put("key2", "val2").put("key3", "val3"))
            .put("MapProperty", new JSONObject().put("def", "Value")); // not matching patternProperties

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasMessage("#: 21 schema violations found");
        assertThat(e.getSchemaPointer()).isEqualTo("#");
        assertThat(e.getCausingExceptions()).hasSize(15);
        assertThat(e.getKeyword()).isNull();

        // RandomProperty is required through dependencies
        final ValidationException dependenciesEx = getExceptionAtPointer(e, "#");
        assertThat(dependenciesEx).hasMessage("#: property [RandomProperty] is required");
        assertThat(dependenciesEx.getCausingExceptions()).isEmpty();
        assertThat(dependenciesEx.getKeyword()).isEqualTo("dependencies");

        // StringProperty is too long and does not match pattern
        final ValidationException stringEx = getExceptionAtPointer(e, "#/StringProperty");
        assertThat(stringEx).hasMessage("#/StringProperty: 2 schema violations found");
        assertThat(stringEx.getCausingExceptions()).hasSize(2);
        assertThat(stringEx.getKeyword()).isNull();

        final ValidationException stringExMaxLength = getExceptionByKeyword(stringEx, "maxLength", "#/StringProperty");
        assertThat(stringExMaxLength.getCausingExceptions()).isEmpty();
        assertThat(stringExMaxLength).hasMessage("#/StringProperty: expected maxLength: 20, actual: 31");

        final ValidationException stringExPattern = getExceptionByKeyword(stringEx, "pattern", "#/StringProperty");
        assertThat(stringExPattern.getCausingExceptions()).isEmpty();
        assertThat(stringExPattern).hasMessage("#/StringProperty: failed validation constraint for keyword [pattern]");

        // StringProperty2 is too short
        final ValidationException stringEx2 = getExceptionAtPointer(e, "#/StringProperty2");
        assertThat(stringEx2).hasMessage("#/StringProperty2: expected minLength: 10, actual: 8");
        assertThat(stringEx2.getCausingExceptions()).isEmpty();
        assertThat(stringEx2.getKeyword()).isEqualTo("minLength");

        // EnumProperty is not part of enum
        final ValidationException enumAllOfEx = getExceptionAtPointer(e, "#/EnumProperty");
        assertThat(enumAllOfEx.getCausingExceptions()).hasSize(1);
        assertThat(enumAllOfEx).hasMessage("#/EnumProperty: #: only 1 subschema matches out of 2");
        assertThat(enumAllOfEx.getKeyword()).isEqualTo("allOf");

        final ValidationException enumEx = enumAllOfEx.getCausingExceptions().get(0);
        assertThat(enumEx.getCausingExceptions()).isEmpty();
        assertThat(enumEx).hasMessage("#/EnumProperty: failed validation constraint for keyword [enum]");

        // ConstProperty does not match value
        final ValidationException constAllOfEx = getExceptionAtPointer(e, "#/ConstProperty");
        assertThat(constAllOfEx.getCausingExceptions()).hasSize(1);
        assertThat(constAllOfEx).hasMessage("#/ConstProperty: #: only 1 subschema matches out of 2");
        assertThat(constAllOfEx.getKeyword()).isEqualTo("allOf");

        final ValidationException constEx = constAllOfEx.getCausingExceptions().get(0);
        assertThat(constEx.getCausingExceptions()).isEmpty();
        assertThat(constEx).hasMessage("#/ConstProperty: failed validation constraint for keyword [const]");
        assertThat(constEx.getKeyword()).isEqualTo("const");

        // ArrayProperty has too many items and items are not unique
        final ValidationException arrEx = getExceptionAtPointer(e, "#/ArrayProperty");
        assertThat(arrEx).hasMessage("#/ArrayProperty: 2 schema violations found");
        assertThat(arrEx.getCausingExceptions()).hasSize(2);
        assertThat(arrEx.getKeyword()).isNull();

        final ValidationException arrExMaxItems = getExceptionByKeyword(arrEx, "maxItems", "#/ArrayProperty");
        assertThat(arrExMaxItems.getCausingExceptions()).isEmpty();
        assertThat(arrExMaxItems).hasMessage("#/ArrayProperty: expected maximum item count: 5, found: 9");

        final ValidationException arrExUniqueItems = getExceptionByKeyword(arrEx, "uniqueItems", "#/ArrayProperty");
        assertThat(arrExUniqueItems.getCausingExceptions()).isEmpty();
        assertThat(arrExUniqueItems).hasMessage("#/ArrayProperty: array items are not unique");

        // ArrayProperty2 has too few items and does not contain a minimum of 5
        final ValidationException arrEx2 = getExceptionAtPointer(e, "#/ArrayProperty2");
        assertThat(arrEx2).hasMessage("#/ArrayProperty2: 2 schema violations found");
        assertThat(arrEx2.getCausingExceptions()).hasSize(2);
        assertThat(arrEx2.getKeyword()).isNull();

        final ValidationException arrEx2MinItems = getExceptionByKeyword(arrEx2, "minItems", "#/ArrayProperty2");
        assertThat(arrEx2MinItems).hasMessage("#/ArrayProperty2: expected minimum item count: 2, found: 1");
        assertThat(arrEx2MinItems.getCausingExceptions()).isEmpty();

        final ValidationException arrEx2Contains = getExceptionByKeyword(arrEx2, "contains", "#/ArrayProperty2");
        assertThat(arrEx2Contains).hasMessage("#/ArrayProperty2: expected at least one array item to match 'contains' schema");
        assertThat(arrEx2Contains.getCausingExceptions()).isEmpty();

        // IntProperty is too small and is not multiple of 5
        final ValidationException intEx = getExceptionAtPointer(e, "#/IntProperty");
        assertThat(intEx).hasMessage("#/IntProperty: 2 schema violations found");
        assertThat(intEx.getCausingExceptions()).hasSize(2);

        final ValidationException intExMin = getExceptionByKeyword(intEx, "minimum", "#/IntProperty");
        assertThat(intExMin.getCausingExceptions()).isEmpty();
        assertThat(intExMin).hasMessage("#/IntProperty: failed validation constraint for keyword [minimum]");

        final ValidationException intExMultiple = getExceptionByKeyword(intEx, "multipleOf", "#/IntProperty");
        assertThat(intExMultiple.getCausingExceptions()).isEmpty();
        assertThat(intExMultiple).hasMessage("#/IntProperty: failed validation constraint for keyword [multipleOf]");

        // IntProperty2 is too large
        final ValidationException intEx2 = getExceptionAtPointer(e, "#/IntProperty2");
        assertThat(intEx2).hasMessage("#/IntProperty2: failed validation constraint for keyword [maximum]");
        assertThat(intEx2.getCausingExceptions()).isEmpty();
        assertThat(intEx2.getKeyword()).isEqualTo("maximum");

        // NumberProperty is too small
        final ValidationException numEx = getExceptionAtPointer(e, "#/NumberProperty");
        assertThat(numEx).hasMessage("#/NumberProperty: failed validation constraint for keyword [exclusiveMinimum]");
        assertThat(numEx.getCausingExceptions()).isEmpty();
        assertThat(numEx.getKeyword()).isEqualTo("exclusiveMinimum");

        // IntProperty2 is too large
        final ValidationException numEx2 = getExceptionAtPointer(e, "#/NumberProperty2");
        assertThat(numEx2).hasMessage("#/NumberProperty2: failed validation constraint for keyword [exclusiveMaximum]");
        assertThat(numEx2.getCausingExceptions()).isEmpty();
        assertThat(numEx2.getKeyword()).isEqualTo("exclusiveMaximum");

        // BooleanProperty is wrong type
        final ValidationException boolEx = getExceptionAtPointer(e, "#/BooleanProperty");
        assertThat(boolEx).hasMessage("#/BooleanProperty: expected type: Boolean, found: String");
        assertThat(boolEx.getCausingExceptions()).isEmpty();
        assertThat(boolEx.getKeyword()).isEqualTo("type");

        // ObjectProperty has too few properties
        final ValidationException objEx = getExceptionAtPointer(e, "#/ObjectProperty");
        assertThat(objEx).hasMessage("#/ObjectProperty: minimum size: [2], found: [1]");
        assertThat(objEx.getCausingExceptions()).isEmpty();
        assertThat(objEx.getKeyword()).isEqualTo("minProperties");

        // ObjectProperty2 has too many properties
        final ValidationException objEx2 = getExceptionAtPointer(e, "#/ObjectProperty2");
        assertThat(objEx2).hasMessage("#/ObjectProperty2: maximum size: [2], found: [3]");
        assertThat(objEx2.getCausingExceptions()).isEmpty();
        assertThat(objEx2.getKeyword()).isEqualTo("maxProperties");

        // MapProperty does not match patternProperties (fails for
        // additionalProperties), and fails oneOf and anyOf
        final ValidationException mapEx = getExceptionAtPointer(e, "#/MapProperty");
        assertThat(mapEx).hasMessage("#/MapProperty: #: only 0 subschema matches out of 3");
        assertThat(mapEx.getCausingExceptions()).hasSize(3);
        assertThat(mapEx.getKeyword()).isEqualTo("allOf");

        final ValidationException mapExPatternProp = getExceptionByKeyword(mapEx, "additionalProperties", "#/MapProperty");
        assertThat(mapExPatternProp).hasMessage("#/MapProperty: extraneous key [def] is not permitted");
        assertThat(mapExPatternProp.getCausingExceptions()).isEmpty();

        // TODO: looks like the allOf, oneOf, and anyOf error messages get a bit weird.
        // Issue #38
        final ValidationException mapExOneOf = getExceptionByKeyword(mapEx, "oneOf", "#/MapProperty");
        assertThat(mapExOneOf).hasMessage("#/MapProperty: #: 0 subschemas matched instead of one");
        assertThat(mapExOneOf.getCausingExceptions()).hasSize(1);

        final ValidationException mapExAnyOf = getExceptionByKeyword(mapEx, "anyOf", "#/MapProperty");
        assertThat(mapExAnyOf).hasMessage("#/MapProperty: #: no subschema matched out of the total 1 subschemas");
        assertThat(mapExAnyOf.getCausingExceptions()).hasSize(1);
    }

    private ValidationException getExceptionAtPointer(final ValidationException e, final String pointer) {
        return e.getCausingExceptions().stream().filter(ce -> pointer.equals(ce.getSchemaPointer())).findFirst()
            .orElseThrow(() -> new RuntimeException(String.format("No violations found for pointer: %s", pointer)));
    }

    private ValidationException getExceptionByKeyword(final ValidationException e, final String keyword, final String pointer) {
        return e.getCausingExceptions().stream().filter(ce -> keyword.equals(ce.getKeyword())).findFirst().orElseThrow(
            () -> new RuntimeException(String.format("No violations found for keyword [%s] at pointer [%s]", keyword, pointer)));
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
