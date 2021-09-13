package software.amazon.cloudformation.resource;

import org.everit.json.schema.JSONPointer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceTaggingTest {
    @Test
    public void testResetTaggable() {

        final ResourceTagging resourceTagging = new ResourceTagging(true, true, true,
            true, new JSONPointer("/properties/tags"));
        resourceTagging.resetTaggable(false);

        assertThat(resourceTagging.isTaggable()).isEqualTo(false);
        assertThat(resourceTagging.isTagOnCreate()).isEqualTo(false);
        assertThat(resourceTagging.isTagUpdatable()).isEqualTo(false);
        assertThat(resourceTagging.isCloudFormationSystemTags()).isEqualTo(false);
        assertThat(resourceTagging.getTagProperty().toString()).isEqualTo("/properties/tags");
    }
}
