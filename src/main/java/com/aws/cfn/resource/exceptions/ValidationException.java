package com.aws.cfn.resource.exceptions;

import java.util.Collections;
import java.util.List;

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

    public ValidationException(org.everit.json.schema.ValidationException validationException) {
       this(validationException.getMessage(),
           Collections.emptyList(),
           validationException.getKeyword(),
           validationException.getSchemaLocation());

        if (validationException.getCausingExceptions() != null) {
            for (org.everit.json.schema.ValidationException e : validationException.getCausingExceptions()) {
                this.causingExceptions.add(new ValidationException(e));
            }
        }
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

    public List<ValidationException> getCausingExceptions() {
        return this.causingExceptions;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public String getSchemaLocation() {
        return this.schemaLocation;
    }

}
