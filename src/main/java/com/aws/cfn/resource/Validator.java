package com.aws.cfn.resource;

import com.aws.cfn.resource.exceptions.ValidationException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class Validator implements SchemaValidator {

    public static InputStream loadStream(String filePath){
        final File file = new File(filePath);
        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }

        return in;
    }

    public void validateModel(final JSONObject modelObject,
                              final InputStream schemaStream) throws ValidationException {
        final JSONObject schemaObject = new JSONObject(new JSONTokener(schemaStream));

        final SchemaLoader loader = SchemaLoader.builder()
            .schemaJson(schemaObject)
            .draftV7Support()
            .build();
        final Schema schema = loader.load().build();

        try {
            schema.validate(modelObject); // throws a ValidationException if this object is invalid
        } catch (org.everit.json.schema.ValidationException e) {
            throw new ValidationException(e);
        }
    }
}
