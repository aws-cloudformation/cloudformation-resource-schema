package com.aws.cfn.resource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserTest {
    private static final String TEST_SCHEMA_PATH = "/test-schema.json";

    @Test
    public void test_getCreateOnlyProperties() {
        final Parser parser = new Parser();

        List<String> result = parser.getCreateOnlyProperties(
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("/properties/propertyA");
        assertThat(result.get(1)).isEqualTo("/properties/propertyD");
    }

    @Test
    public void test_getDeprecatedProperties() {
        final Parser parser = new Parser();

        List<String> result = parser.getDeprecatedProperties(
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );

        assertThat(result).hasSize(0);
    }

    @Test
    public void test_getIdentifiers() {
        final Parser parser = new Parser();

        List<List<String>> result = parser.getIdentifiers(
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isEqualTo("/properties/propertyA");
    }

    @Test
    public void test_getReadOnlyProperties() {
        final Parser parser = new Parser();

        List<String> result = parser.getReadOnlyProperties(
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("/properties/propertyB");
    }

    @Test
    public void test_getWriteOnlyProperties() {
        final Parser parser = new Parser();

        List<String> result = parser.getWriteOnlyProperties(
            this.getClass().getResourceAsStream(TEST_SCHEMA_PATH)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("/properties/propertyC");
    }
}
