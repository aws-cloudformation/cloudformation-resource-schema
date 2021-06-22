package software.amazon.cloudformation.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;

public class CfnResourceTypeConfigurationValidationTests {

    // This test is to just validate the resource configuration schema against the JSONSchema draft
    private static final String RESOURCE_CONFIGURATION_SCHEMA = "/schema/cloudformation-resource-type-configuration-schema.v1.json";
    private static final String JSON_SCHEMA_PATH = "/schema/schema";

    @Test
    public void validateResourceTypeConfigurationSchema_WithJsonSchemaDraft() {
        final JSONObject resourceConfigurationSchemaJson = loadJSON(RESOURCE_CONFIGURATION_SCHEMA);
        final JSONObject jsonSchema = loadJSON(JSON_SCHEMA_PATH);
        Validator.builder().build().validateObject(resourceConfigurationSchemaJson, jsonSchema);
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
