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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;

import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.JSONPointerException;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.PublicJSONPointer;
import org.everit.json.schema.Schema;
import org.json.JSONObject;

import software.amazon.cloudformation.resource.exceptions.ValidationException;

@Getter
public class ResourceTypeSchema {

    private static final Validator VALIDATOR = new Validator();
    private static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 120;

    private final Map<String, Object> unprocessedProperties = new HashMap<>();

    private final String sourceUrl;
    private final String documentationUrl;
    private final String typeName;
    private final String schemaUrl; // $schema
    @Getter(AccessLevel.NONE)
    private JSONObject schemaDefinition = null;

    private final String replacementStrategy;
    private final boolean taggable;
    private ResourceTagging tagging = ResourceTagging.DEFAULT;
    private boolean hasConfiguredTagging = false;
    private final List<JSONPointer> createOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> conditionalCreateOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> deprecatedProperties = new ArrayList<>();
    private final List<JSONPointer> primaryIdentifier = new ArrayList<>();
    private final List<List<JSONPointer>> additionalIdentifiers = new ArrayList<>();
    private final List<JSONPointer> readOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> writeOnlyProperties = new ArrayList<>();
    private final Map<String, String> propertyTransform = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private final Map<String, Handler> handlers = new HashMap<>();
    private final Schema schema;

    public ResourceTypeSchema(Schema schema) {
        this.schema = schema;
        schema.getUnprocessedProperties().forEach(this.unprocessedProperties::put);

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

        this.schemaUrl = this.unprocessedProperties.containsKey("$schema")
            ? this.unprocessedProperties.get("$schema").toString()
            : null;
        this.unprocessedProperties.remove("$schema");

        this.replacementStrategy = this.unprocessedProperties.containsKey("replacementStrategy")
            ? this.unprocessedProperties.get("replacementStrategy").toString()
            : "create_then_delete";
        this.unprocessedProperties.remove("replacementStrategy");

        this.unprocessedProperties.computeIfPresent("conditionalCreateOnlyProperties", (k, v) -> {
            ((ArrayList<?>) v).forEach(p -> this.conditionalCreateOnlyProperties.add(new JSONPointer(p.toString())));
            return null;
        });

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
        this.unprocessedProperties.computeIfPresent("propertyTransform", (k, v) -> {
            ((Map<?, ?>) v).forEach((key, value) -> {
                this.propertyTransform.put(key.toString(), value.toString());
            });
            return null;
        });
        this.unprocessedProperties.computeIfPresent("handlers", (k, v) -> {
            ((HashMap<?, ?>) v).keySet().forEach(handlerKey -> {
                HashMap<?, ?> handlerInfo = (HashMap<?, ?>) ((HashMap<?, ?>) v).get(handlerKey);
                HashSet<String> handlerPermissions = new HashSet<>();
                ((List<?>) handlerInfo.get("permissions")).forEach(permission -> handlerPermissions.add(permission.toString()));
                Integer timeoutInMinutes = handlerInfo.containsKey("timeoutInMinutes")
                    ? ((Integer) handlerInfo.get("timeoutInMinutes"))
                    : DEFAULT_TIMEOUT_IN_MINUTES;
                this.handlers.put(handlerKey.toString(), new Handler(handlerPermissions, timeoutInMinutes));
            });
            return null;
        });

        this.unprocessedProperties.computeIfPresent("tagging", (k, v) -> {
            hasConfiguredTagging = true;
            final ResourceTagging taggingValue = new ResourceTagging(true);
            ((Map<?, ?>) v).forEach((key, value) -> {
                if (key.equals(ResourceTagging.TAGGABLE)) {
                    taggingValue.setTaggable(Boolean.parseBoolean(value.toString()));
                } else if (key.equals(ResourceTagging.TAG_ON_CREATE)) {
                    taggingValue.setTagOnCreate(Boolean.parseBoolean(value.toString()));
                } else if (key.equals(ResourceTagging.TAG_UPDATABLE)) {
                    taggingValue.setTagUpdatable(Boolean.parseBoolean(value.toString()));
                } else if (key.equals(ResourceTagging.CLOUDFORMATION_SYSTEM_TAGS)) {
                    taggingValue.setCloudFormationSystemTags(Boolean.parseBoolean(value.toString()));
                } else if (key.equals(ResourceTagging.TAG_PROPERTY)) {
                    taggingValue.setTagProperty(new JSONPointer(value.toString()));
                } else if (key.equals(ResourceTagging.TAG_PERMISSIONS)) {
                    List<String> tagPermissions = new ArrayList<>();
                    ((List<?>) value).forEach(p -> tagPermissions.add(p.toString()));
                    taggingValue.setTagPermissions(tagPermissions);
                } else {
                    throw new ValidationException("Unexpected tagging metadata attribute", "tagging", "#/tagging/" + key);
                }
            });
            if (!taggingValue.isTaggable()) {
                // reset other metadata values if resource is not taggable
                taggingValue.resetTaggable(taggingValue.isTaggable());
            }
            taggingValue.validateTaggingMetadata(this.handlers.containsKey("update"), this.schema);
            this.tagging = taggingValue;
            return null;
        });

        if (this.unprocessedProperties.containsKey("taggable")) {
            this.taggable = Boolean.parseBoolean(this.unprocessedProperties.get("taggable").toString());
            if (!hasConfiguredTagging) {
                // set tagging metadata based on deprecated taggable value
                this.tagging = new ResourceTagging(this.taggable);
            } else {
                throw new ValidationException("More than one configuration found for taggable value." +
                    " Please remove the deprecated taggable property.", "tagging", "#/tagging/taggable");
            }
        } else {
            this.taggable = true;
        }
        this.unprocessedProperties.remove("taggable");
    }

    public static ResourceTypeSchema load(final JSONObject resourceDefinition) {
        Schema schema = VALIDATOR.loadResourceDefinitionSchema(resourceDefinition);
        return new ResourceTypeSchema(schema);
    }

    public String getDescription() {
        return schema.getDescription();
    }

    public List<String> getConditionalCreateOnlyPropertiesAsStrings() throws ValidationException {
        return this.conditionalCreateOnlyProperties.stream().map(JSONPointer::toString).collect(Collectors.toList());
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

    public Set<String> getHandlerPermissions(String action) {
        return handlers.containsKey(action) ? handlers.get(action).getPermissions() : null;
    }

    public Integer getHandlerTimeoutInMinutes(String action) {
        return handlers.containsKey(action) ? handlers.get(action).getTimeoutInMinutes() : null;
    }

    public Boolean hasHandler(String action) {
        return handlers.containsKey(action);
    }

    public Map<String, Object> getUnprocessedProperties() {
        return Collections.unmodifiableMap(this.unprocessedProperties);
    }

    public void removeWriteOnlyProperties(final JSONObject resourceModel) {
        this.getWriteOnlyPropertiesAsStrings().stream().forEach(writeOnlyProperty -> removeProperty(
            new PublicJSONPointer(writeOnlyProperty.replaceFirst("^/properties", "")), resourceModel));
    }

    public String getReplacementStrategy() {
        return this.replacementStrategy;
    }

    public static void removeProperty(final PublicJSONPointer property, final JSONObject resourceModel) {
        List<String> refTokens = property.getRefTokens();
        if (refTokens.size() > 0) {
            final String key = refTokens.get(refTokens.size() - 1);
            try {
                // if size is more than one, fetch parent object/array of key to remove so that
                // we can remove
                if (refTokens.size() > 1) {
                    // use sublist to specify to point at the parent object
                    final JSONPointer parentObjectPointer = new JSONPointer(refTokens.subList(0, refTokens.size() - 1));
                    final Optional<JSONObject> parentObject = Optional.ofNullable(
                        (JSONObject) parentObjectPointer.queryFrom(resourceModel));
                    parentObject.ifPresent(jsonObject -> jsonObject.remove(key));
                } else {
                    resourceModel.remove(key);
                }
            } catch (JSONPointerException | NumberFormatException e) {
                // do nothing, as this indicates the model does not have a value for the pointer
            }
        }
    }

    public boolean definesProperty(String field) {
        // when schema contains combining properties
        // (keywords for combining schemas together, with options being "oneOf", "anyOf", and "allOf"),
        // schema will be a CombinedSchema with
        // - an allOf criterion
        // - subschemas
        // - an ObjectSchema that contains properties to be checked
        // - other CombinedSchemas corresponding to the usages of combining properties.
        // These CombinedSchemas should be ignored. Otherwise, JSON schema's definesProperty method
        // will search for field as a property in the CombinedSchema, which is not desired.
        Schema schemaToCheck = schema instanceof CombinedSchema
            ? ((CombinedSchema) schema).getSubschemas().stream()
                .filter(subschema -> subschema instanceof ObjectSchema)
                .findFirst().get()
            : schema;
        return schemaToCheck.definesProperty(field);
    }

    public void validate(JSONObject json) {
        getSchema().validate(json);
    }
}
