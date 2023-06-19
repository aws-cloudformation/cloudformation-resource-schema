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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static software.amazon.cloudformation.resource.ValidatorTest.loadJSON;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

public class BaseValidatorTest {

    private static final String RESOURCE_DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TEST_MINIMAL_SCHEMA_PATH = "/minimal-schema.json";
    private static final String TEST_RESOURCE_SCHEMA_WITH_OVERRIDE_PATH = "/test-resource-schema-with-list-override.json";
    private static final String TEST_VALUE_SCHEMA_PATH = "/scrubbed-values-schema.json";

    private BaseValidator baseValidator;

    @BeforeEach
    public void setUp() {
        baseValidator = new BaseValidator(new JSONObject(), new DefaultSchemaClient());
    }

    @Test
    public void testNewURI_happyCase() {
        final String uriString = "http://json-schema.org/draft-07/schema";
        assertThat(BaseValidator.newURI(uriString).toString()).isEqualTo(uriString);
    }

    @Test
    public void testNewURI_IncorrectSyntax_ShouldThrow() {
        final String uriString = "json-schema.org/draft-07?schema/q/h?s=^IXIC";
        assertThatExceptionOfType(URISyntaxException.class).isThrownBy(() -> BaseValidator.newURI(uriString));
    }

    @Test
    public void validateObject_validObject_shouldPassHandlerSchemaValidation() {
        final JSONObject object = new JSONObject().put("Person", new JSONObject().put("Name", "Jon")).put("Human",
            new JSONObject().put("LastName", "Snow"));

        baseValidator.validateObjectByListHandlerSchema(object,
            new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_RESOURCE_SCHEMA_WITH_OVERRIDE_PATH))));
    }

    @Test
    public void validateObject_validObject_shouldNotPassHandlerSchemaValidation() {
        final JSONObject object = new JSONObject().put("Person", new JSONObject().put("Name", "Jon")).put("Human",
            new JSONObject().put("LastName", "Snow").put("LastName2", "Stark"));
        final org.everit.json.schema.ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObjectByListHandlerSchema(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_RESOURCE_SCHEMA_WITH_OVERRIDE_PATH)))),
            org.everit.json.schema.ValidationException.class);
        assertThat(e.getMessage()).isEqualTo("#/Human: extraneous key [LastName2] is not permitted");
    }

    @Test
    public void validateObject_validObject_shouldPassHandlerSchemaValidationEmptySchema() {
        final JSONObject object = new JSONObject().put("Person", new JSONObject().put("Name", "Jon"));
        baseValidator.validateObjectByListHandlerSchema(object,
            new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_MINIMAL_SCHEMA_PATH))));
    }

    /**
     * trivial coverage test: cannot cache a schema if it has an invalid $id
     */
    @ParameterizedTest
    @ValueSource(strings = { ":invalid/uri", "" })
    public void registerMetaSchema_invalidRelativeRef_shouldThrow(String uri) {
        JSONObject badSchema = loadJSON(RESOURCE_DEFINITION_SCHEMA_PATH);
        badSchema.put("$id", uri);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> {
            baseValidator.registerMetaSchema(SchemaLoader.builder(), badSchema);
        });
    }

    @Test
    public void registerMetaSchema_happyCase() {
        final String uriString = "http://json-schema.org/draft-07/schema";
        JSONObject schema = loadJSON(RESOURCE_DEFINITION_SCHEMA_PATH);
        schema.put("$id", uriString);
        baseValidator.registerMetaSchema(SchemaLoader.builder(), schema);
    }

    @Test
    public void validateObject_validObject_shouldNotThrow() {
        final JSONObject object = new JSONObject().put("propertyA", "abc").put("propertyB", Arrays.asList(1, 2, 3));

        baseValidator.validateObject(object,
            new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH))));
    }

    @Test
    public void validateObject_invalidObjectMissingRequiredProperties_shouldThrow() {
        final String propVal = "abc";
        final JSONObject object = new JSONObject().put("propertyA", propVal);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object,
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
            () -> baseValidator.validateObject(object,
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
            () -> baseValidator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e).hasMessageNotContaining("true");
        assertThat(e.getCausingExceptions()).isEmpty();
        assertThat(e.getKeyword()).isEqualTo("type");
    }

    @ParameterizedTest
    @CsvSource({ "WaaaaaaaayTooLong,maxLength", "TooShort,minLength", "NoPatternMatch,pattern" })
    public void validateObject_invalidStringValue_messageShouldNotContainValue(final String value, final String keyword) {
        final JSONObject object = new JSONObject().put("StringProperty", value);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
            ValidationException.class);

        assertThat(e.getSchemaPointer()).isEqualTo("#/StringProperty");
        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getMessage()).doesNotContain(value);
        assertThat(e.getCausingExceptions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "enum", "const" })
    public void validateObject_invalidEnumValue_messageShouldNotContainValue(final String keyword) {
        final String propName = keyword + "Property";
        final String propVal = "NotPartOfEnum";
        final JSONObject object = new JSONObject().put(propName, propVal);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object,
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
    @CsvSource({ "test-test,uniqueItems", "test,minItems", "test-test2-test3,maxItems", "Y-X,contains" })
    public void validateObject_invalidArrayValue_messageShouldNotContainValue(final String listAsString, final String keyword) {
        final List<String> values = Arrays.asList(listAsString.split("-"));
        final JSONObject object = new JSONObject().put("ArrayProperty", values);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
            ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/ArrayProperty");
        assertThat(e.getCausingExceptions()).isEmpty();

        values.forEach(v -> assertThat(e).hasMessageNotContaining(v));
    }

    @ParameterizedTest
    @CsvSource({ "5,IntProperty,minimum", "300,IntProperty,maximum", "23,IntProperty,multipleOf",
        "5,NumberProperty,exclusiveMinimum", "300,NumberProperty,exclusiveMaximum" })
    public void validateObject_invalidNumValue_messageShouldNotContainValue(final String numAsString,
                                                                            final String propName,
                                                                            final String keyword) {
        final JSONObject object = new JSONObject().put(propName, Integer.valueOf(numAsString));

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
            ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/" + propName);
        assertThat(e).hasMessageNotContaining(numAsString);
        assertThat(e.getCausingExceptions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "test,minProperties", "test-test1-test2,maxProperties", "test-dep,dependencies" })
    public void validateObject_invalidSubObject_messageShouldNotContainValue(final String keysAsString, final String keyword) {
        final String val = "testValue";
        final JSONObject subSchema = new JSONObject();

        final List<String> keys = Arrays.asList(keysAsString.split("-"));
        keys.forEach(k -> subSchema.put(k, val));

        final JSONObject object = new JSONObject().put("ObjectProperty", subSchema);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object,
                new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_VALUE_SCHEMA_PATH)))),
            ValidationException.class);

        assertThat(e.getKeyword()).isEqualTo(keyword);
        assertThat(e.getSchemaPointer()).isEqualTo("#/ObjectProperty");
        assertThat(e).hasMessageNotContaining(val);
        assertThat(e.getCausingExceptions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "test,minProperties", "test-test1-test2,maxProperties", "test-dep,dependencies" })
    public void validateObject_invalidPatternProperties_messageShouldNotContainValue(final String keysAsString,
                                                                                     final String keyword) {
        final String val = "Value";
        final JSONObject object = new JSONObject().put("MapProperty", new JSONObject().put("def", "val"));

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
            ValidationException.class);

        assertThat(e.getSchemaPointer()).isEqualTo("#/MapProperty");
        assertThat(e.getKeyword()).isEqualTo("additionalProperties");
        assertThat(e).hasMessageNotContaining(val);
        assertThat(e.getCausingExceptions()).isEmpty();

    }

    @ParameterizedTest
    @ValueSource(strings = { "anyOf", "allOf", "oneOf" })
    public void validateObject_invalidCombiner_messageShouldNotContainValue(final String keyword) {
        final String propName = keyword + "Property";
        final String propVal = "NotAnInteger";
        final JSONObject object = new JSONObject().put(propName, propVal);

        final ValidationException e = catchThrowableOfType(
            () -> baseValidator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
            () -> baseValidator.validateObject(object, loadJSON(TEST_SCHEMA_PATH)),
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

}
