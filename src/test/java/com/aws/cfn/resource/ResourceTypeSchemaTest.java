package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ResourceTypeSchemaTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";
    private static final String EMPTY_SCHEMA_PATH = "/empty-schema.json";
    private static final String MINIMAL_SCHEMA_PATH = "/minimal-schema.json";

    @Test
    public void getProperties() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        assertThat(schema.getDescription()).isEqualTo("A test schema for unit tests.");
        assertThat(schema.getSourceUrl()).isEqualTo("my-repo.git");
        assertThat(schema.getTypeName()).isEqualTo("AWS::Test::TestModel");
        assertThat(schema.getUnprocessedProperties()).hasSize(0);
    }

    @Test
    public void getCreateOnlyProperties() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getCreateOnlyPropertiesAsStrings();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("/properties/propertyA");
        assertThat(result.get(1)).isEqualTo("/properties/propertyD");
    }

    @Test
    public void getDeprecatedProperties() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getDeprecatedPropertiesAsStrings();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("/properties/propertyC");
    }

    @Test
    public void getIdentifiers() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<List<String>> result = schema.getIdentifiersAsStrings();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isEqualTo("/properties/propertyA");
    }

    @Test
    public void getReadOnlyProperties() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getReadOnlyPropertiesAsStrings();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("/properties/propertyB");
    }

    @Test
    public void getWriteOnlyProperties() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        List<String> result = schema.getWriteOnlyPropertiesAsStrings();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("/properties/propertyC");
    }

    @Test
    public void invalidSchema_shouldThrow() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(EMPTY_SCHEMA_PATH)));

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> ResourceTypeSchema.load(o))
            .withNoCause()
            .withMessage("#/properties: minimum size: [1], found: [0]");
    }

    @Test
    public void minimalSchema_hasNoSemantics() {
        JSONObject o = new JSONObject(new JSONTokener(this.getClass().getResourceAsStream(MINIMAL_SCHEMA_PATH)));
        final ResourceTypeSchema schema = ResourceTypeSchema.load(o);

        assertThat(schema.getDescription()).isEqualTo("A test schema for unit tests.");
        assertThat(schema.getSourceUrl()).isNull();
        assertThat(schema.getTypeName()).isEqualTo("AWS::Test::TestModel");
        assertThat(schema.getUnprocessedProperties()).hasSize(0);
        assertThat(schema.getCreateOnlyPropertiesAsStrings()).hasSize(0);
        assertThat(schema.getDeprecatedPropertiesAsStrings()).hasSize(0);
        assertThat(schema.getIdentifiersAsStrings()).hasSize(0);
        assertThat(schema.getReadOnlyPropertiesAsStrings()).hasSize(0);
        assertThat(schema.getWriteOnlyPropertiesAsStrings()).hasSize(0);
    }
}
