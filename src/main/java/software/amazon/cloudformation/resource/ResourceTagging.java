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

import lombok.AllArgsConstructor;
import lombok.Data;

import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.Schema;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

@Data
@AllArgsConstructor
public class ResourceTagging {
    private boolean taggable;
    private boolean tagOnCreate;
    private boolean tagUpdatable;
    private boolean cloudFormationSystemTags;
    private JSONPointer tagProperty;

    public void resetTaggable(final boolean taggableValue) {
        taggable = taggableValue;
        tagOnCreate = taggableValue;
        tagUpdatable = taggableValue;
        cloudFormationSystemTags = taggableValue;
    }

    public void validateTaggingMetadata(final boolean containUpdateHandler, final Schema schema) {
        if (tagUpdatable && !containUpdateHandler) {
            throw new ValidationException("Invalid tagUpdatable value since update handler is missing", "tagging",
                                          "#/tagging/tagUpdatable");
        }
        final String propertyName = tagProperty.toString().substring(tagProperty.toString().lastIndexOf('/') + 1);
        if (taggable && !schema.definesProperty(propertyName)) {
            final String errorMessage = String.format("Invalid tagProperty value since %s not found in schema", propertyName);
            throw new ValidationException(errorMessage, "tagging", "#/tagging/tagProperty");
        }
    }
}
