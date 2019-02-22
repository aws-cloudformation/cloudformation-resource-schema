package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ValidatorTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";

    @Test
    public void validateObject_validObject_shouldNotThrow() {
        final Validator validator = new Validator();

        final JSONObject object = new JSONObject()
                .put("propertyA", "abc")
                .put("propertyB", Arrays.asList(1, 2, 3));

        validator.validateObject(
            object,
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );
    }

    @Test
    public void validateObject_invalidObjectMissingRequiredProperties_shouldThrow() {
        final Validator validator = new Validator();

        final JSONObject object = new JSONObject()
                .put("propertyB", Arrays.asList(1, 2, 3));

        try {
            validator.validateObject(
                    object,
                    this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
            );
            fail("Expected ValidationException not thrown");
        } catch (final ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                e.getMessage(),
                is("#: required key [propertyA] not found")
            );
            assertThat(
                e.getSchemaLocation(),
                is("#")
            );
        }
    }

    @Test
    public void validateObject_invalidObjectAdditionalProperties_shouldThrow() {
        final Validator validator = new Validator();

        final JSONObject object = new JSONObject()
                .put("propertyA", "abc")
                .put("propertyB", Arrays.asList(1, 2, 3))
                .put("propertyC", "notpartofschema");

        try {
            validator.validateObject(
                    object,
                    this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
            );
            fail("Expected ValidationException not thrown");
        } catch (final ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                e.getMessage(),
                is("#: extraneous key [propertyC] is not permitted")
            );
            assertThat(
                e.getSchemaLocation(),
                is("#")
            );
        }
    }

    @Test
    public void validateObject_invalidObjectMultiple_shouldThrow() {
        final Validator validator = new Validator();

        final JSONObject object = new JSONObject()
                .put("propertyA", 123)
                .put("propertyB", Arrays.asList(1, 2, 3))
                .put("propertyC", "notpartofschema")
                .put("propertyD", "notpartofschema");

        try {
            validator.validateObject(
                    object,
                    this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
            );
            fail("Expected ValidationException not thrown");
        } catch (final ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(3));
            assertThat(
                e.getMessage(),
                is("#: 3 schema violations found")
            );
            assertThat(
                e.getSchemaLocation(),
                is("#")
            );
        }
    }

    @Test
    public void validateDefinition_validMinimalDefinition_shouldNotThrow() {
        final Validator validator = new Validator();
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_validExampleDefinition_shouldNotThrow() {
        final Validator validator = new Validator();
        final JSONObject definition = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        validator.validateResourceDefinition(definition);
    }

    @Test
    public void validateDefinition_invalidDefinitionNoPropertiesKey_shouldThrow() {
        final Validator validator = new Validator();
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        try {
            validator.validateResourceDefinition(definition);
        } catch (final ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                    e.getMessage(),
                    is("#: required key [properties] not found")
            );
        }
    }

    @Test
    public void validateDefinition_invalidDefinitionNoProperties_shouldThrow() {
        final Validator validator = new Validator();
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(PROPERTIES_KEY, new JSONObject());
        try {
            validator.validateResourceDefinition(definition);
        } catch (final ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                    e.getMessage(),
                    is("#/properties: minimum size: [1], found: [0]")
            );
        }
    }
}
