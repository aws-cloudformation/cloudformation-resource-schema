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

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.Schema;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

@Data
@AllArgsConstructor
public class ResourceTagging {
    public static final String TAGGABLE = "taggable";
    public static final String TAG_ON_CREATE = "tagOnCreate";
    public static final String TAG_UPDATABLE = "tagUpdatable";
    public static final String CLOUDFORMATION_SYSTEM_TAGS = "cloudFormationSystemTags";
    public static final String TAG_PROPERTY = "tagProperty";
    public static final String TAG_PERMISSIONS = "permissions";
    public static final ResourceTagging DEFAULT = new ResourceTagging(true);

    private boolean taggable;
    private boolean tagOnCreate;
    private boolean tagUpdatable;
    private boolean cloudFormationSystemTags;
    private JSONPointer tagProperty;
    private List<String> tagPermissions;

    public ResourceTagging(final boolean taggableValue) {
        this.taggable = taggableValue;
        this.tagOnCreate = taggableValue;
        this.tagUpdatable = taggableValue;
        this.cloudFormationSystemTags = taggableValue;
        this.tagProperty = new JSONPointer("/properties/Tags");
        this.tagPermissions = new ArrayList<>();
    }

    public void resetTaggable(final boolean taggableValue) {
        this.taggable = taggableValue;
        this.tagOnCreate = taggableValue;
        this.tagUpdatable = taggableValue;
        this.cloudFormationSystemTags = taggableValue;
    }

    public void validateTaggingMetadata(final boolean containUpdateHandler, final Schema schema) {
        if (this.tagUpdatable && !containUpdateHandler) {
            throw new ValidationException("Invalid tagUpdatable value since update handler is missing", "tagging",
                                          "#/tagging/tagUpdatable");
        }

        final String propertiesPrefix = "/properties/";
        if (!this.tagProperty.toString().startsWith(propertiesPrefix)) {
            final String errorMessage = String.format("Invalid tagProperty value %s must start with \"/properties\"",
                this.tagProperty.toString());
            throw new ValidationException(errorMessage, "tagging", "#/tagging/tagProperty");
        }

        final String propertyName = this.tagProperty.toString().substring(propertiesPrefix.length());
        if (this.taggable && !schema.definesProperty(propertyName)) {
            final String errorMessage = String.format("Invalid tagProperty value since %s not found in schema", propertyName);
            throw new ValidationException(errorMessage, "tagging", "#/tagging/tagProperty");
        }
    }
}
