package com.petroad.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {
    @TempDir Path dir;

    @Test void deleteRemovesOnlyThatFileAndIgnoresMissingOnes() {
        var storage = new LocalImageStorage(dir.toString(), "");
        var removed = storage.save("walk_photo", "a.jpg", new MockMultipartFile("file", new byte[]{1}));
        storage.save("walk_photo", "b.jpg", new MockMultipartFile("file", new byte[]{2}));

        storage.delete(removed.publicId());
        storage.delete(removed.publicId());

        assertThat(dir.resolve("walk_photo/a.jpg")).doesNotExist();
        assertThat(dir.resolve("walk_photo/b.jpg")).exists();
    }

    @Test void deleteRejectsPathsOutsideTheUploadFolder() throws IOException {
        Path root = Files.createDirectory(dir.resolve("uploads"));
        Path outside = Files.writeString(dir.resolve("outside.txt"), "keep");
        var storage = new LocalImageStorage(root.toString(), "");

        for (String publicId : List.of("../outside.txt", "walk_photo/../../outside.txt", "")) {
            assertThatThrownBy(() -> storage.delete(publicId)).as(publicId).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(outside).exists();
        assertThat(root).exists();
    }
}
