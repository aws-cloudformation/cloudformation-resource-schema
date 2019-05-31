package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;

import java.io.InputStream;
import java.util.List;

public interface SchemaParser {

    /**
     * Parse a resource schema and extract a list of properties which are identifiers for the resource
     * @param schemaStream  The JSON schema to parse
     * @throws ValidationException  Thrown for any schema validation errors
     */
    List<List<String>> getIdentifiers(InputStream schemaStream) throws ValidationException;

}
