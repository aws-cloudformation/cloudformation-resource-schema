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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.cloudformation.resource.ValidatorTest.loadJSON;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

/**
 *
 */
@ExtendWith(MockitoExtension.class)
public class ValidatorRefResolutionTests {

    public static final String RESOURCE_DEFINITION_PATH = "/valid-with-refs.json";
    private final static String COMMON_TYPES_PATH = "/common.types.v1.json";
    private final String expectedRefUrl = "https://schema.cloudformation.us-east-1.amazonaws.com/common.types.v1.json";

    @Mock
    private SchemaClient downloader;
    private Validator validator;

    @BeforeEach
    public void beforeEach() {
        when(downloader.get(expectedRefUrl)).thenAnswer(x -> ValidatorTest.getResourceAsStream(COMMON_TYPES_PATH));

        this.validator = new Validator(downloader);
    }

    @Test
    public void loadResourceSchema_validRelativeRef_shouldSucceed() {

        JSONObject schema = loadJSON(RESOURCE_DEFINITION_PATH);
        validator.validateResourceDefinition(schema);

        // valid-with-refs.json contains two refs pointing at locations inside
        // common.types.v1.json
        // Everit will attempt to download the remote schema once for each $ref - it
        // doesn't cache
        // remote schemas. Expect the downloader to be called twice
        verify(downloader, twice()).get(expectedRefUrl);
    }

    /**
     * expect a valid resource schema contains a ref to a non-existent property in a
     * remote meta-schema
     */
    @Test
    public void loadResourceSchema_invalidRelativeRef_shouldThrow() {

        JSONObject badSchema = loadJSON("/invalid-bad-ref.json");

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> {
            validator.validateResourceDefinition(badSchema);
        });
    }

    /** example of using Validator to validate a json data files */
    @Test
    public void validateTemplateAgainstResourceSchema_valid_shouldSucceed() {

        JSONObject resourceDefinition = loadJSON(RESOURCE_DEFINITION_PATH);
        Schema schema = validator.loadResourceSchema(resourceDefinition);

        schema.validate(getSampleTemplate());
    }

    /**
     * template that contains an invalid value in one of its properties fails
     * validation
     */
    @Test
    public void validateTemplateAgainsResourceSchema_invalid_shoudThrow() {
        JSONObject resourceDefinition = loadJSON(RESOURCE_DEFINITION_PATH);
        Schema schema = validator.loadResourceSchema(resourceDefinition);

        final JSONObject template = getSampleTemplate();
        template.put("propertyB", "not.an.IP.address");

        assertThatExceptionOfType(org.everit.json.schema.ValidationException.class).isThrownBy(() -> schema.validate(template));
    }

    /**
     * resource schema located at RESOURCE_DEFINITION_PATH declares two properties:
     * "Time" in ISO 8601 format (UTC only) and "propertyB" - an IP address Both
     * fields are declares as refs to common.types.v1.json. "Time" is marked as
     * required property getSampleTemplate constructs a JSON object with a single
     * Time property.
     */
    private JSONObject getSampleTemplate() {
        final JSONObject template = new JSONObject();
        template.put("Time", "2019-12-12T10:10:22.212Z");
        return template;
    }

    private static VerificationMode twice() {
        return Mockito.times(2);
    }

}
