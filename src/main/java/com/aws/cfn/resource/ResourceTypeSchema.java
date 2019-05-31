package com.aws.cfn.resource;

import lombok.Getter;
import org.everit.json.schema.JSONPointer;
import org.everit.json.schema.ObjectSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ResourceTypeSchema extends ObjectSchema {

    private final Map<String, Object> unprocessedProperties = new HashMap<>();

    private final String sourceUrl;
    private final String typeName;
    private final List<JSONPointer> createOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> deprecatedProperties = new ArrayList<>();
    private final List<List<JSONPointer>> identifiers = new ArrayList<>();
    private final List<JSONPointer> readOnlyProperties = new ArrayList<>();
    private final List<JSONPointer> writeOnlyProperties = new ArrayList<>();

    public ResourceTypeSchema(final ObjectSchema.Builder builder) {
        super(builder);

        super.getUnprocessedProperties().forEach(this.unprocessedProperties::put);

        this.sourceUrl = this.unprocessedProperties.containsKey("sourceUrl")
            ? this.unprocessedProperties.get("sourceUrl").toString()
            : null;
        this.unprocessedProperties.remove("sourceUrl");

        this.typeName = this.unprocessedProperties.containsKey("typeName")
            ? this.unprocessedProperties.get("typeName").toString()
            : null;
        this.unprocessedProperties.remove("typeName");

        if (this.unprocessedProperties.containsKey("createOnlyProperties")) {
            ((ArrayList<?>) this.unprocessedProperties.get("createOnlyProperties"))
                .forEach(p -> this.createOnlyProperties.add(new JSONPointer(p.toString())));
        }
        this.unprocessedProperties.remove("createOnlyProperties");

        if (this.unprocessedProperties.containsKey("deprecatedProperties")) {
            ((ArrayList<?>) this.unprocessedProperties.get("deprecatedProperties"))
                .forEach(p -> this.deprecatedProperties.add(new JSONPointer(p.toString())));
        }
        this.unprocessedProperties.remove("deprecatedProperties");

        if (this.unprocessedProperties.containsKey("identifiers")) {
            ((ArrayList<?>) this.unprocessedProperties.get("identifiers")).forEach(p -> {
                final ArrayList<JSONPointer> identifiers = new ArrayList<>();
                ((ArrayList<?>) p).forEach(pi -> identifiers.add(new JSONPointer(pi.toString())));
                this.identifiers.add(identifiers);
            });
        }
        this.unprocessedProperties.remove("identifiers");

        if (this.unprocessedProperties.containsKey("readOnlyProperties")) {
            ((ArrayList<?>) this.unprocessedProperties.get("readOnlyProperties"))
                .forEach(p -> this.readOnlyProperties.add(new JSONPointer(p.toString())));
        }
        this.unprocessedProperties.remove("readOnlyProperties");

        if (this.unprocessedProperties.containsKey("writeOnlyProperties")) {
            ((ArrayList<?>) this.unprocessedProperties.get("writeOnlyProperties"))
                .forEach(p -> this.writeOnlyProperties.add(new JSONPointer(p.toString())));
        }
        this.unprocessedProperties.remove("writeOnlyProperties");
    }

    @Override
    public Map<String, Object> getUnprocessedProperties() {
        return Collections.unmodifiableMap(this.unprocessedProperties);
    }
}
