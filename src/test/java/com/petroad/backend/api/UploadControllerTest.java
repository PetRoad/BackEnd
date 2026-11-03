package com.petroad.backend.api;

import com.petroad.backend.security.JwtInterceptor;
import com.petroad.backend.security.JwtService;
import com.petroad.backend.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {
    // png 파일의 맨 앞 글자(매직 바이트) 12칸. 진짜 png처럼 보이게 만든 가짜 파일이에요.
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final ImageStorage storage = mock(ImageStorage.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        JwtService jwt = mock(JwtService.class);
        when(jwt.parseUserId("valid")).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new UploadController(storage))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new JwtInterceptor(jwt))
                .build();
    }

    @Test
    void uploadsPngAndReturnsStorageUrl() throws Exception {
        when(storage.save(eq("walk_photo"), endsWith(".png"), any()))
                .thenReturn("https://res.cloudinary.com/test/image/upload/a.png");

        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("purpose", "WALK_PHOTO")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/test/image/upload/a.png"));
    }

    @Test
    void rejectsFileThatIsNotAnImage() throws Exception {
        byte[] text = "이건 그림이 아니에요 hello".getBytes();

        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", text))
                        .param("purpose", "WALK_PHOTO")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnknownPurpose() throws Exception {
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("purpose", "HACK")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("purpose", "WALK_PHOTO"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }
}