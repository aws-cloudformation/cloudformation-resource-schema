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
package com.amazonaws.cloudformation.resource.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 42L;

    private final List<ValidationException> causingExceptions;
    private final String keyword;
    private final String schemaLocation;

    public ValidationException(final String message,
                               final String keyword,
                               final String schemaLocation) {
        this(message, Collections.emptyList(), keyword, schemaLocation);
    }

    public ValidationException(final org.everit.json.schema.ValidationException validationException) {
        super(validationException.getMessage());
        this.keyword = validationException.getKeyword();
        this.schemaLocation = validationException.getSchemaLocation();

        final List<ValidationException> causingExceptions = new ArrayList<>();
        if (validationException.getCausingExceptions() != null) {
            for (final org.everit.json.schema.ValidationException e : validationException.getCausingExceptions()) {
                causingExceptions.add(new ValidationException(e));
            }
        }
        this.causingExceptions = Collections.unmodifiableList(causingExceptions);
    }

    public ValidationException(final String message,
                               final List<ValidationException> causingExceptions,
                               final String keyword,
                               final String schemaLocation) {
        super(message);
        this.causingExceptions = Collections.unmodifiableList(causingExceptions);
        this.keyword = keyword;
        this.schemaLocation = schemaLocation;
    }
}
