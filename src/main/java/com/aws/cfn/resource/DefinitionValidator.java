package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.json.JSONObject;

import java.io.InputStream;

/*
* Validates a resource definition against the resource provider definition schema
* */
public class DefinitionValidator extends Validator {

    private static final String METASCHEMA_PATH = "/schema/provider.definition.schema.v1.json";
    private final InputStream schemaStream;

    public DefinitionValidator(){
        super();
        schemaStream = this.getClass().getResourceAsStream(METASCHEMA_PATH);
    }

    public void validate(final JSONObject definition) throws ValidationException {
        validateModel(definition, schemaStream);
    }
}
