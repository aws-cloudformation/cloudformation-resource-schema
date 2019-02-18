package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;


public class DefinitionValidatorTest {
    private static final String TEST_SCHEMA_PATH= "/test-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";


    @Test
    public void test_ValidateSchema_ValidMinimal() {
        final DefinitionValidator validator = new DefinitionValidator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        model.put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));
        validator.validate(model);
    }

    @Test
    public void test_ValidateExampleSchema() {
        final DefinitionValidator validator = new DefinitionValidator();
        final JSONObject schema = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        validator.validate(schema);
    }

    @Test
    public void test_ValidateSchema_InvalidNoPropertiesKey() {
        final DefinitionValidator validator = new DefinitionValidator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        try {
            validator.validate(model);
        } catch (ValidationException e) {
            assertThat(
                    e.getCausingExceptions().size(),
                    is(0)
            );
            assertThat(
                    e.getMessage(),
                    is("#: required key [properties] not found")
            );
        }
    }

    @Test
    public void test_ValidateSchema_InvalidNoProperties() {
        final DefinitionValidator validator = new DefinitionValidator();
        final JSONObject model = new JSONObject();
        model.put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME);
        model.put(PROPERTIES_KEY, new JSONObject());
        try {
            validator.validate(model);
        } catch (ValidationException e) {
            assertThat(
                    e.getCausingExceptions().size(),
                    is(0)
            );
            assertThat(
                    e.getMessage(),
                    is("#/properties: minimum size: [1], found: [0]")
            );
        }
    }

}
