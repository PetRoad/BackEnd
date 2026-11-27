package com.petroad.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlPolicyTest {
    private static final String FILE = "12345678-1234-1234-1234-123456789abc.jpg";
    private static final String CLOUD = "https://res.cloudinary.com/our-cloud/image/upload/v123/petroad/";

    @TempDir Path dir;

    @Test void cloudinaryAllowsOnlyCanonicalUrlsInOurCloudAndUploadFolders() {
        ImageStorage storage = new CloudinaryImageStorage("our-cloud", "unused-key", "unused-secret");
        for (String folder : List.of("walk_photo", "course_cover", "dog_profile")) {
            for (String extension : List.of("jpg", "png", "webp")) {
                assertThat(storage.isOwnUrl(CLOUD + folder + "/" + FILE.replace("jpg", extension))).isTrue();
            }
        }
        assertThat(storage.isOwnUrl(null)).isFalse();
        for (String url : List.of("", "https://example.com/" + FILE,
                CLOUD.replace("our-cloud", "other-cloud") + "walk_photo/" + FILE,
                CLOUD.replace("https:", "http:") + "walk_photo/" + FILE,
                CLOUD.replace("image/upload/v123", "image/fetch") + "walk_photo/" + FILE,
                CLOUD.replace("image/upload/v123", "image/upload/w_500/v123") + "walk_photo/" + FILE,
                CLOUD.replace("res.cloudinary.com", "res.cloudinary.com.evil.test") + "walk_photo/" + FILE,
                CLOUD.replace("res.cloudinary.com", "res.cloudinary.com@evil.test") + "walk_photo/" + FILE,
                CLOUD + "walk_photo/" + FILE + "?redirect=1", CLOUD + "walk_photo/" + FILE + "#fragment",
                CLOUD + "walk_photo/../" + FILE, CLOUD + "walk_photo/%2e%2e/" + FILE,
                CLOUD + "other/" + FILE, CLOUD + "walk_photo/file.jpg", CLOUD + "walk_photo/" + FILE + "\n")) {
            assertThat(storage.isOwnUrl(url)).as(url).isFalse();
        }
    }

    @Test void localStorageAcceptsItsOwnGeneratedUrlAndRejectsOtherOriginsAndPaths() {
        ImageStorage storage = new LocalImageStorage(dir.toString(), "https://api.example.test/");
        ImageStorage.StoredImage stored = storage.save("walk_photo", FILE, new MockMultipartFile("file", new byte[]{1, 2, 3}));
        String url = stored.url();
        assertThat(stored.publicId()).isEqualTo("walk_photo/" + FILE);
        assertThat(url).isEqualTo("https://api.example.test/uploads/walk_photo/" + FILE);
        assertThat(storage.isOwnUrl(url)).isTrue();
        assertThat(storage.isOwnUrl(null)).isFalse();
        for (String invalid : List.of(url.replace("api.example.test", "api.example.test.evil.test"),
                url + "?x=1", url + "#fragment", url.replace("/walk_photo/", "/walk_photo/../"),
                "https://api.example.test/uploads-evil/walk_photo/" + FILE)) {
            assertThat(storage.isOwnUrl(invalid)).as(invalid).isFalse();
        }
    }
}
