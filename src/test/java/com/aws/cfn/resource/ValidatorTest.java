package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ValidatorTest {
    private static final String TEST_SCHEMA_PATH= "/test-schema.json";
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
            assertThat(
                e.getCausingExceptions().size(),
                is(0)
            );
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
            assertThat(
                e.getCausingExceptions().size(),
                is(0)
            );
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
            assertThat(
                e.getCausingExceptions().size(),
                is(3)
            );
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
}
