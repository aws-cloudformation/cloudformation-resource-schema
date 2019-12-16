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
package software.amazon.cloudformation.resource.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ValidationExceptionTest {

    @Test
    public void ctor_noCausingExceptions() {
        ValidationException e = new ValidationException("some error", "key1", "#/properties");
        assertThat(e.getCausingExceptions()).isNotNull();
        assertThat(e.getCausingExceptions().size()).isEqualTo(0);
        assertThat(e.getMessage()).isEqualTo("some error");
    }

    @Test
    public void buildFullExceptionMessage_single() {
        ValidationException e = new ValidationException("Root error", null, "#");

        final String message = ValidationException.buildFullExceptionMessage(e);

        assertThat(message).isEqualTo(e.getMessage());
    }

    @Test
    public void buildFullExceptionMessage_isNullSafe() {
        ValidationException e = new ValidationException(null, null, null, null);

        final String message = ValidationException.buildFullExceptionMessage(e);

        assertThat(message).isEmpty();
    }

    @Test
    public void buildFullExceptionMessage_multiple() {
        ValidationException e1 = new ValidationException("First error", "key1", "#/properties");
        ValidationException e2 = new ValidationException("Second error", "key2", "#/properties");
        ValidationException e3 = new ValidationException("Third error", "key3", "#/properties");

        final List<ValidationException> causes = new ArrayList<>(Arrays.asList(e1, e2, e3));
        ValidationException e = new ValidationException("Root error", causes, null, "#");

        final String message = ValidationException.buildFullExceptionMessage(e);

        assertThat(message).doesNotEndWith("\n");

        List<String> messageParts = new ArrayList<>(Arrays.asList(message.split("\n")));

        assertThat(messageParts).hasSize(3);

        causes.forEach(ve -> messageParts.contains(ve.getMessage()));
    }
}
