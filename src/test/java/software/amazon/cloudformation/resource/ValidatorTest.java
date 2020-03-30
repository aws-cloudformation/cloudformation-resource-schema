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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

public class ValidatorTest {
    private static final String RESOURCE_DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TEST_VALUE_SCHEMA_PATH = "/scrubbed-values-schema.json";
    private static final String SCHEMA_WITH_HANDLERS_PATH = "/valid-with-handlers-schema.json";
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

    private JSONObject baseSchema() {
        return baseSchema(new JSONObject().put("property", new JSONObject()));
    }

    private JSONObject baseSchema(final JSONObject propertiesValue) {
        return new JSONObject().put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME).put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION)
            .put(PROPERTIES_KEY, propertiesValue)
            .put(PRIMARY_IDENTIFIER_KEY, Collections.singletonList(EXAMPLE_PRIMARY_IDENTIFIER))
            .put(ADDITIONAL_PROPERTIES_KEY, ADDITIONAL_PROPERTIES_VALUE);
    }

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
    @CsvSource({ "WaaaaaaaayTooLong,maxLength", "TooShort,minLength", "NoPatternMatch,pattern" })
    public void validateObject_invalidStringValue_messageShouldNotContainValue(final String value, final String keyword) {
        final JSONObject object = new JSONObject().put("StringProperty", value);

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
    @CsvSource({ "test-test,uniqueItems", "test,minItems", "test-test2-test3,maxItems", "Y-X,contains" })
    public void validateObject_invalidArrayValue_messageShouldNotContainValue(final String listAsString, final String keyword) {
        final List<String> values = Arrays.asList(listAsString.split("-"));
        final JSONObject object = new JSONObject().put("ArrayProperty", values);

        final ValidationException e = catchThrowableOfType(
            () -> validator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
            () -> validator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
            () -> validator.validateObject(object,
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
            () -> validator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
            () -> validator.validateObject(object, loadJSON(TEST_VALUE_SCHEMA_PATH)),
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
            () -> validator.validateObject(object, loadJSON(TEST_SCHEMA_PATH)),
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
        final JSONObject definition = baseSchema();
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_validExampleDefinition_shouldNotThrow() {
        final JSONObject definition = loadJSON(TEST_SCHEMA_PATH);
        validator.validateResourceDefinition(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = { TYPE_NAME_KEY, PROPERTIES_KEY, DESCRIPTION_KEY, PRIMARY_IDENTIFIER_KEY, ADDITIONAL_PROPERTIES_KEY })
    public void validateDefinition_requiredKeyMissing_shouldThrow(final String requiredKey) {
        final JSONObject definition = baseSchema();
        definition.remove(requiredKey);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#: required key [" + requiredKey + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoProperties_shouldThrow() {
        final JSONObject definition = baseSchema(new JSONObject());

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#/properties: minimum size: [1], found: [0]");
    }

    @Test
    public void validateDefinition_invalidHandlerSection_shouldThrow() {
        final JSONObject definition = loadJSON("/invalid-handlers-schema.json");

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessage("#/handlers/read: required key [permissions] not found");
    }

    @ParameterizedTest
    @MethodSource("generateValidReplacementStrategies")
    public void validateDefinition_validReplacementStrategy_shouldNotThrow(final String replacementStrategy) {
        final JSONObject definition = baseSchema().put("replacementStrategy", replacementStrategy);

        validator.validateResourceDefinition(definition);
    }

    @ParameterizedTest
    @MethodSource("generateInValidReplacementStrategies")
    public void validateDefinition_DuplicateReplacementStrategy_shouldThrow(final String replacementStrategy) {
        final JSONObject definition = baseSchema().put("replacementStrategy", replacementStrategy);
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/replacementStrategy");
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 721 })
    public void validateDefinition_invalidTimeout_shouldThrow(final int timeout) {
        // modifying the valid-with-handlers.json to add invalid timeout
        final JSONObject definition = loadJSON(SCHEMA_WITH_HANDLERS_PATH);

        final JSONObject createDefinition = definition.getJSONObject("handlers").getJSONObject("create");
        createDefinition.put("timeoutInMinutes", timeout);

        final String keyword = timeout == 1 ? "minimum" : "maximum";

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withNoCause().withMessageContaining("#/handlers/create/timeoutInMinutes").withMessageContaining(keyword);
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 120, 720 })
    public void validateDefinition_withTimeout_shouldNotThrow(final int timeout) {
        final JSONObject definition = loadJSON(SCHEMA_WITH_HANDLERS_PATH);

        final JSONObject createDefinition = definition.getJSONObject("handlers").getJSONObject("create");
        createDefinition.put("timeoutInMinutes", timeout);

        validator.validateResourceDefinition(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = { "create", "update", "delete", "read", "list" })
    public void validateDefinition_timeoutAllowed_shouldNotThrow(final String handlerType) {
        final JSONObject definition = loadJSON(SCHEMA_WITH_HANDLERS_PATH);

        final JSONObject handlerDefinition = definition.getJSONObject("handlers").getJSONObject(handlerType);
        handlerDefinition.put("timeoutInMinutes", 30);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_validHandlerSection_shouldNotThrow() {
        final JSONObject definition = loadJSON(SCHEMA_WITH_HANDLERS_PATH);

        validator.validateResourceDefinition(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com", "http://example.com", "git://example.com", "https://", })
    public void validateDefinition_nonMatchingDocumentationUrl_shouldThrow(final String documentationUrl) {
        final JSONObject definition = baseSchema().put("documentationUrl", documentationUrl);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageNotContaining(documentationUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingDocumentationUrl_shouldNotThrow(final String documentationUrl) {
        final JSONObject definition = baseSchema().put("documentationUrl", documentationUrl);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongDocumentationUrl_shouldThrow() {
        final String documentationUrl = "https://much-too-l" + String.join("", Collections.nCopies(5000, "s")) + "ng.com/";
        final JSONObject definition = baseSchema().put("documentationUrl", documentationUrl);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/documentationUrl").withMessageContaining(Integer.toString(documentationUrl.length()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com", "http://example.com", "git://example.com", "https://", })
    public void validateDefinition_nonMatchingSourceUrls_shouldThrow(final String sourceUrl) {
        final JSONObject definition = baseSchema().put("sourceUrl", sourceUrl);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageNotContaining((sourceUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/aws-cloudformation/aws-cloudformation-rpdk",
        "https://github.com/aws-cloudformation/aws-cloudformation-rpdk.git", "https://example.com/%F0%9F%8E%85", })
    public void validateDefinition_matchingSourceUrl_shouldNotThrow(final String sourceUrl) {
        final JSONObject definition = baseSchema().put("sourceUrl", sourceUrl);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_tooLongSourceUrl_shouldThrow() {
        final String sourceUrl = "https://much-too-l" + String.join("", Collections.nCopies(5000, "s")) + "ng.com/";
        final JSONObject definition = baseSchema().put("sourceUrl", sourceUrl);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/sourceUrl").withMessageContaining(Integer.toString(sourceUrl.length()));
    }

    @Test
    public void validateDefinition_schemaKeyword_shouldBeAllowed() {
        final JSONObject definition = baseSchema().put("$schema", "https://json-schema.org/draft-07/schema#");

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_relativeTemplateUri_shouldBeAllowed() {
        final JSONObject resourceLink = new JSONObject().put("templateUri", "/cloudformation/home").put("mappings",
            new JSONObject());
        final JSONObject definition = baseSchema().put("resourceLink", resourceLink);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_httpsTemplateUri_shouldBeAllowed() {
        final JSONObject resourceLink = new JSONObject()
            .put("templateUri", "https://eu-central-1.console.aws.amazon.com/cloudformation/home")
            .put("mappings", new JSONObject());
        final JSONObject definition = baseSchema().put("resourceLink", resourceLink);

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_httpTemplateUri_shouldThrow() {
        final JSONObject resourceLink = new JSONObject()
            .put("templateUri", "http://eu-central-1.console.aws.amazon.com/cloudformation/home")
            .put("mappings", new JSONObject());
        final JSONObject definition = baseSchema().put("resourceLink", resourceLink);

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> validator.validateResourceDefinition(definition))
            .withMessageContaining("#/resourceLink/templateUri");
    }

    @Test
    public void validateDefinition_idKeyword_shouldBeAllowed() {
        final JSONObject definition = baseSchema().put("$id",
            "https://schema.cloudformation.us-east-1.amazonaws.com/aws-ec2-instance.json#");

        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateExample_exampleResourceProviderSchema_shouldBeValid() throws IOException {
        final JSONObject example = loadJSON("/examples/resource/initech.tps.report.v1.json");
        validator.validateResourceDefinition(example);
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
            validator.registerMetaSchema(SchemaLoader.builder(), badSchema);
        });
    }

    /**
     * trivial coverage test: cannot cache a schema if it has no $id
     */
    @Test
    public void registerMetaSchema_nullId_shouldThrow() {
        JSONObject badSchema = loadJSON(RESOURCE_DEFINITION_SCHEMA_PATH);
        badSchema.remove("$id");
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> {
            validator.registerMetaSchema(SchemaLoader.builder(), badSchema);
        });
    }

    private static Stream<Arguments> generateValidReplacementStrategies() {
        return Stream.of(
            Arguments.of("create_then_delete"),
            Arguments.of("delete_then_create"));
    }

    private static Stream<Arguments> generateInValidReplacementStrategies() {
        return Stream.of(
            Arguments.of("delete"),
            Arguments.of(""),
            Arguments.of("random string"));
    }

    static JSONObject loadJSON(String path) {
        try (InputStream stream = getResourceAsStream(path)) {
            return new JSONObject(new JSONTokener(stream));
        } catch (IOException ex) {
            System.out.println("path: " + path);
            throw new UncheckedIOException(ex);
        } catch (Throwable ex) {
            System.out.println("path: " + path);
            throw ex;
        }
    }

    static InputStream getResourceAsStream(String path) {
        return ValidatorRefResolutionTests.class.getResourceAsStream(path);
    }

}
