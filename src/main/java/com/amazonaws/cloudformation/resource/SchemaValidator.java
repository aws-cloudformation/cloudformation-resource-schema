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
package com.amazonaws.cloudformation.resource;

import com.amazonaws.cloudformation.resource.exceptions.ValidationException;

import org.json.JSONObject;

public interface SchemaValidator {

    String DEFINITION_SCHEMA_PATH = "/schema/provider.definition.schema.v1.json";

    /**
     * Perform JSON Schema validation for the input model against the specified
     * schema
     *
     * @param modelObject JSON-encoded resource model
     * @param schemaObject The JSON schema object to validate the model against
     * @throws ValidationException Thrown for any schema validation errors
     */
    void validateObject(JSONObject modelObject, JSONObject schemaObject) throws ValidationException;

}
