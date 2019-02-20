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
    private static final String TEST_SCHEMA_PATH= "/test-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";

    @Test
    public void test_ValidateModel_Valid() {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyA", "abc");
        model.put("propertyB", Arrays.asList(1, 2, 3));

        validator.validateModel(
            model,
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );
    }

    @Test
    public void test_ValidateModel_MissingRequiredProperties() {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyB", Arrays.asList(1, 2, 3));

        try {
            validator.validateModel(
                    model,
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
    public void test_ValidateModel_InvalidAdditionalProperties() {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyA", "abc");
        model.put("propertyB", Arrays.asList(1, 2, 3));
        model.put("propertyC", "notpartofschema");

        try {
            validator.validateModel(
                    model,
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
    public void test_ValidateModel_MultipleValidationFailures() {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyA", 123);
        model.put("propertyB", Arrays.asList(1, 2, 3));
        model.put("propertyC", "notpartofschema");
        model.put("propertyD", "notpartofschema");

        try {
            validator.validateModel(
                    model,
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
    public void test_ValidateDefinitionSchema_ValidMinimal() {
        final Validator validator = new Validator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        model.put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));
        validator.validateResourceDefinition(model);
    }

    @Test
    public void test_ValidateDefinitionExampleSchema() {
        final Validator validator = new Validator();
        final JSONObject schema = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        validator.validateResourceDefinition(schema);
    }

    @Test
    public void test_ValidateDefinition_InvalidNoPropertiesKey() {
        final Validator validator = new Validator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        try {
            validator.validateResourceDefinition(model);
        } catch (ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                    e.getMessage(),
                    is("#: required key [properties] not found")
            );
        }
    }

    @Test
    public void test_ValidateDefinition_InvalidNoProperties() {
        final Validator validator = new Validator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        model.put(PROPERTIES_KEY, new JSONObject());
        try {
            validator.validateResourceDefinition(model);
        } catch (ValidationException e) {
            assertThat(e.getCausingExceptions(), hasSize(0));
            assertThat(
                    e.getMessage(),
                    is("#/properties: minimum size: [1], found: [0]")
            );
        }
    }
}
