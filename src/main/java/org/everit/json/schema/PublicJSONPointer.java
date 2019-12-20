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
package org.everit.json.schema;

import java.util.List;

import org.json.JSONObject;

public class PublicJSONPointer extends JSONPointer {

    public PublicJSONPointer(final List<String> refTokens) {
        super(refTokens);
    }

    public PublicJSONPointer(final String pointer) {
        super(pointer);
    }

    public List<String> getRefTokens() {
        return super.getRefTokens();
    }

    public boolean isInObject(final JSONObject jsonObject) {
        try {
            return this.queryFrom(jsonObject) != null;
        } catch (JSONPointerException e) {
            return false;
        }
    }
}
