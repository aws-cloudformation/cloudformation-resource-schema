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
package org.everit.json.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class PublicJSONPointerTest {
    @Test
    public void isInObject_objectHasProperty_shouldReturnTrue() {
        final JSONObject jsonObject = new JSONObject().put("propertyThatExists", "value");
        assertThat(new PublicJSONPointer("/propertyThatExists").isInObject(jsonObject)).isTrue();
    }

    @Test
    public void isInObject_nestedObjectHasProperty_shouldReturnTrue() {
        final JSONObject jsonObject = new JSONObject().put("propertyThatExists", new JSONObject().put("nested", "value"));
        assertThat(new PublicJSONPointer(Arrays.asList("propertyThatExists", "nested")).isInObject(jsonObject)).isTrue();
    }

    @Test
    public void isInObject_nestedObjectDoesNotHaveProperty_shouldReturnFalse() {
        final JSONObject jsonObject = new JSONObject();
        assertThat(new PublicJSONPointer(Arrays.asList("propertyThatExists", "nested")).isInObject(jsonObject)).isFalse();
    }

    @Test
    public void isInObject_objectDoesNotHaveProperty_shouldReturnFalse() {
        final JSONObject jsonObject = new JSONObject();
        assertThat(new PublicJSONPointer("/propertyThatDoesNotExist").isInObject(jsonObject)).isFalse();

    }

}
