package com.petroad.backend.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Cloudinary에 보내는 요청만 확인한다. 실제로 위치정보가 지워지는지는 진짜 계정에 사진을 올려 확인해야 한다.
class CloudinaryImageStorageTest {
    private final Uploader uploader = mock(Uploader.class);
    private final CloudinaryImageStorage storage = new CloudinaryImageStorage("test-cloud", "unused-key", "unused-secret");

    @BeforeEach void replaceClient() {
        Cloudinary cloudinary = mock(Cloudinary.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        ReflectionTestUtils.setField(storage, "cloudinary", cloudinary);
    }

    @Test void uploadAsksToRotateAndStripMetadataBeforeStoring() throws Exception {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/v1/petroad/walk_photo/a.jpg";
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", url));

        var stored = storage.save("walk_photo", "a.jpg", new MockMultipartFile("file", new byte[]{1}));

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), options.capture());
        assertThat((Object) options.getValue().get("transformation")).isEqualTo("a_exif,fl_force_strip");
        assertThat((Object) options.getValue().get("public_id")).isEqualTo("petroad/walk_photo/a");
        assertThat(stored).isEqualTo(new ImageStorage.StoredImage("petroad/walk_photo/a", url));
    }

    @Test void deleteAlsoInvalidatesCdnCopiesAndAcceptsAlreadyMissingFiles() throws Exception {
        for (String outcome : new String[]{"ok", "not found"}) {
            when(uploader.destroy(eq("petroad/walk_photo/a"), anyMap())).thenReturn(Map.of("result", outcome));
            assertThatCode(() -> storage.delete("petroad/walk_photo/a")).doesNotThrowAnyException();
        }
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader, org.mockito.Mockito.times(2)).destroy(eq("petroad/walk_photo/a"), options.capture());
        assertThat((Object) options.getValue().get("invalidate")).isEqualTo(true);
    }

    @Test void deleteFailsWhenCloudinaryReportsAnotherResult() throws Exception {
        when(uploader.destroy(eq("petroad/walk_photo/a"), anyMap())).thenReturn(Map.of("result", "error"));

        assertThatThrownBy(() -> storage.delete("petroad/walk_photo/a")).isInstanceOf(IllegalStateException.class);
    }
}
