package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class ValidatorTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String TYPE_NAME_KEY = "typeName";
    private static final String PROPERTIES_KEY = "properties";
    private static final String DESCRIPTION_KEY = "description";
    private static final String EXAMPLE_TYPE_NAME = "Organization::Service::Resource";
    private static final String EXAMPLE_DESCRIPTION = "Resource provider descriptions are important for customers to know what the resource is expected to do.";

    private Validator validator;

    @BeforeEach
    public void setUp() {
        validator = new Validator();
    }

    @Test
    public void validateObject_validObject_shouldNotThrow() {
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
        final JSONObject object = new JSONObject()
                .put("propertyB", Arrays.asList(1, 2, 3));

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(
                        object,
                        this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
                ),
                ValidationException.class
        );

        assertThat(e).hasNoCause().hasMessageContaining("propertyA");
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateObject_invalidObjectAdditionalProperties_shouldThrow() {
        final JSONObject object = new JSONObject()
                .put("propertyA", "abc")
                .put("propertyB", Arrays.asList(1, 2, 3))
                .put("propertyC", "notpartofschema");

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(
                        object,
                        this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
                ),
                ValidationException.class
        );

        assertThat(e).hasNoCause().hasMessageContaining("propertyC");
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateObject_invalidObjectMultiple_shouldThrow() {
        final JSONObject object = new JSONObject()
                .put("propertyA", 123)
                .put("propertyB", Arrays.asList(1, 2, 3))
                .put("propertyC", "notpartofschema")
                .put("propertyD", "notpartofschema");

        final ValidationException e = catchThrowableOfType(
                () -> validator.validateObject(
                        object,
                        this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
                ),
                ValidationException.class
        );

        assertThat(e.getCausingExceptions()).hasSize(3);
        assertThat(e).hasMessage("#: 3 schema violations found");
        assertThat(e.getSchemaLocation()).isEqualTo("#");
    }

    @Test
    public void validateDefinition_validMinimalDefinition_shouldNotThrow() {
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION)
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
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION);

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> validator.validateResourceDefinition(definition))
                .withNoCause()
                .withMessage("#: required key [" + PROPERTIES_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoDescriptionKey_shouldThrow() {
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(PROPERTIES_KEY, new JSONObject().put("property", new JSONObject()));

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> validator.validateResourceDefinition(definition))
                .withNoCause()
                .withMessage("#: required key [" + DESCRIPTION_KEY + "] not found");
    }

    @Test
    public void validateDefinition_invalidDefinitionNoProperties_shouldThrow() {
        final JSONObject definition = new JSONObject()
                .put(TYPE_NAME_KEY, EXAMPLE_TYPE_NAME)
                .put(DESCRIPTION_KEY, EXAMPLE_DESCRIPTION)
                .put(PROPERTIES_KEY, new JSONObject());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> validator.validateResourceDefinition(definition))
                .withNoCause()
                .withMessage("#/properties: minimum size: [1], found: [0]");
    }
}
