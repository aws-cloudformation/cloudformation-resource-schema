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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.everit.json.schema.JSONPointer;
import org.junit.jupiter.api.Test;

public class ResourceTaggingTest {
    @Test
    public void testResetTaggable() {

        final ResourceTagging resourceTagging = new ResourceTagging(true, true, true,
                                                                    true, new JSONPointer("/properties/tags"), new ArrayList<>());
        resourceTagging.resetTaggable(false);

        assertThat(resourceTagging.isTaggable()).isEqualTo(false);
        assertThat(resourceTagging.isTagOnCreate()).isEqualTo(false);
        assertThat(resourceTagging.isTagUpdatable()).isEqualTo(false);
        assertThat(resourceTagging.isCloudFormationSystemTags()).isEqualTo(false);
        assertThat(resourceTagging.getTagProperty().toString()).isEqualTo("/properties/tags");
        assertThat(resourceTagging.getTagPermissions().isEmpty());
    }
}
