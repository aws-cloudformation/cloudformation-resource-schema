package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ValidatorTest {

    private static final String TEST_DATA_BASE_PATH = "src/test/java/com/aws/cfn/resource/data/%s";

    private InputStream loadRequestStream(final String fileName) {
        final File file = new File(String.format(TEST_DATA_BASE_PATH, fileName));
        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }

        return in;
    }

    @Test
    public void test_ValidateModel_Valid() throws IOException {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyA", "abc");
        model.put("propertyB", Arrays.asList(1, 2, 3));

        validator.validateModel(
            model,
            loadRequestStream("test-schema.json"));
    }

    @Test
    public void test_ValidateModel_MissingRequiredProperties() throws IOException {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyB", Arrays.asList(1, 2, 3));

        try {
            validator.validateModel(
                model,
                loadRequestStream("test-schema.json"));
            fail("Expected ValidationException not thrown");
        } catch (final ValidationException e) {
            assertThat(
                e.getCausingExceptions().size(),
                is(equalTo(0))
            );
            assertThat(
                e.getMessage(),
                is(equalTo("#: required key [propertyA] not found"))
            );
            assertThat(
                e.getSchemaLocation(),
                is(equalTo("#"))
            );
        }
    }

    @Test
    public void test_ValidateModel_InvalidAdditionalProperties() throws IOException {
        final Validator validator = new Validator();

        final JSONObject model = new JSONObject();
        model.put("propertyA", "abc");
        model.put("propertyB", Arrays.asList(1, 2, 3));
        model.put("propertyC", "notpartofschema");

        try {
            validator.validateModel(
                model,
                loadRequestStream("test-schema.json"));
            fail("Expected ValidationException not thrown");
        } catch (final ValidationException e) {
            assertThat(
                e.getCausingExceptions().size(),
                is(equalTo(0))
            );
            assertThat(
                e.getMessage(),
                is(equalTo("#: extraneous key [propertyC] is not permitted"))
            );
            assertThat(
                e.getSchemaLocation(),
                is(equalTo("#"))
            );
        }
    }

}
