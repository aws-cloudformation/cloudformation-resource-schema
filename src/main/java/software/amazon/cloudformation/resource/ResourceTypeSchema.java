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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;

import org.apache.commons.lang3.RegExUtils;
import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.JSONPointerException;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

@Getter
public class ResourceTypeSchema extends ObjectSchema {

    private final Map<String, Object> unprocessedProperties = new HashMap<>();

    private final String sourceUrl;
    private final String documentationUrl;
    private final String typeName;
    private final List<JSONPointer> createOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> deprecatedProperties = new ArrayList<>();
    private final List<JSONPointer> primaryIdentifier = new ArrayList<>();
    private final List<List<JSONPointer>> additionalIdentifiers = new ArrayList<>();
    private final List<JSONPointer> readOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> writeOnlyProperties = new ArrayList<>();

    public ResourceTypeSchema(final ObjectSchema.Builder builder) {
        super(builder);

        super.getUnprocessedProperties().forEach(this.unprocessedProperties::put);

        this.sourceUrl = this.unprocessedProperties.containsKey("sourceUrl")
            ? this.unprocessedProperties.get("sourceUrl").toString()
            : null;
        this.unprocessedProperties.remove("sourceUrl");

        this.documentationUrl = this.unprocessedProperties.containsKey("documentationUrl")
            ? this.unprocessedProperties.get("documentationUrl").toString()
            : null;
        this.unprocessedProperties.remove("documentationUrl");

        // typeName is mandatory by schema
        this.typeName = this.unprocessedProperties.get("typeName").toString();
        this.unprocessedProperties.remove("typeName");

        this.unprocessedProperties.computeIfPresent("createOnlyProperties", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.createOnlyProperties.add(new JSONPointer(p.toString())));
            return null;
        });

        this.unprocessedProperties.computeIfPresent("deprecatedProperties", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.deprecatedProperties.add(new JSONPointer(p.toString())));
            return null;
        });

        this.unprocessedProperties.computeIfPresent("primaryIdentifier", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.primaryIdentifier.add(new JSONPointer(p.toString())));
            return null;
        });

        this.unprocessedProperties.computeIfPresent("additionalIdentifiers", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> {
                final ArrayList<JSONPointer> identifiers = new ArrayList<>();
                ((ArrayList<?>) p).forEach(pi -> identifiers.add(new JSONPointer(pi.toString())));
                this.additionalIdentifiers.add(identifiers);
            });
            return null;
        });

        this.unprocessedProperties.computeIfPresent("readOnlyProperties", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.readOnlyProperties.add(new JSONPointer(p.toString())));
            return null;
        });

        this.unprocessedProperties.computeIfPresent("writeOnlyProperties", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.writeOnlyProperties.add(new JSONPointer(p.toString())));
            return null;
        });
    }

    public static ResourceTypeSchema load(final JSONObject schemaJson) {
        // first validate incoming resource schema against definition schema
        Validator.builder().build().validateObject(schemaJson, new JSONObject(new JSONTokener(ResourceTypeSchema.class
            .getResourceAsStream(SchemaValidator.DEFINITION_SCHEMA_PATH))));

        // now extract identifiers from resource schema
        final SchemaLoader loader = SchemaLoader.builder().schemaJson(schemaJson)
            // registers the local schema with the draft-07 url
            .draftV7Support().build();

        final ObjectSchema.Builder builder = (ObjectSchema.Builder) loader.load();

        return new ResourceTypeSchema(builder);
    }

    public List<String> getCreateOnlyPropertiesAsStrings() throws ValidationException {
        return this.createOnlyProperties.stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<String> getDeprecatedPropertiesAsStrings() throws ValidationException {
        return this.deprecatedProperties.stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<String> getPrimaryIdentifierAsStrings() throws ValidationException {
        return this.primaryIdentifier.stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<List<String>> getAdditionalIdentifiersAsStrings() throws ValidationException {
        final List<List<String>> identifiers = new ArrayList<>();
        this.additionalIdentifiers.forEach(i -> identifiers.add(i.stream().map(Object::toString).collect(Collectors.toList())));
        return identifiers;
    }

    public List<String> getReadOnlyPropertiesAsStrings() throws ValidationException {
        return this.readOnlyProperties.stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    public List<String> getWriteOnlyPropertiesAsStrings() throws ValidationException {
        return this.writeOnlyProperties.stream().map(JSONPointer::toString).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getUnprocessedProperties() {
        return Collections.unmodifiableMap(this.unprocessedProperties);
    }

    public boolean hasWriteOnlyProperties(final JSONObject resourceModel) {
        return this.getWriteOnlyPropertiesAsStrings().stream()
            .anyMatch(writeOnlyProperty -> hasProperty(RegExUtils.removeFirst(writeOnlyProperty, "/properties"), resourceModel));
    }

    public boolean hasProperty(final String property, final JSONObject resourceModel) {
        return hasProperty(new JSONPointer(property), resourceModel);
    }

    public boolean hasProperty(final JSONPointer property, final JSONObject resourceModel) {
        try {
            return property.queryFrom(resourceModel) != null;
        } catch (JSONPointerException e) {
            return false;
        }
    }

    public void removeWriteOnlyProperties(final JSONObject resourceModel) {
        this.getWriteOnlyPropertiesAsStrings().stream()
            .forEach(writeOnlyProp -> removeProperty(new JSONPointer(RegExUtils.removeFirst(writeOnlyProp, "/properties")),
                resourceModel));
    }

    public static void removeProperty(final JSONPointer property, final JSONObject resourceModel) {
        String refParts = property.toString();
        // split pointer into tokens, removing first delimiter
        List<String> refTokens = Arrays.asList(RegExUtils.removeFirst(refParts, "/").split("/"));
        final String key = refTokens.get(refTokens.size() - 1);
        try {
            // if size is more than one, fetch parent object/array of key to remove so that
            // we can remove
            if (refTokens.size() > 1) {
                // use sublist to specify to point at the parent object
                final JSONPointer parentObjectPointer = new JSONPointer(refTokens.subList(0, refTokens.size() - 1));
                final JSONObject parentObject = (JSONObject) parentObjectPointer.queryFrom(resourceModel);
                parentObject.remove(key);
            } else {
                resourceModel.remove(key);
            }
        } catch (JSONPointerException | NumberFormatException e) {
            // do nothing, as this indicates the model does not have a value for the pointer
        }
    }
}
