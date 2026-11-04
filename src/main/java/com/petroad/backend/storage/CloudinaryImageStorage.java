package com.petroad.backend.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cloudinary")
public class CloudinaryImageStorage implements ImageStorage {
    private final Cloudinary cloudinary;

    public CloudinaryImageStorage(@Value("${cloudinary.cloud-name}") String cloudName,
                                  @Value("${cloudinary.api-key}") String apiKey,
                                  @Value("${cloudinary.api-secret}") String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET 환경변수가 필요합니다.");
        }
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
    }

    @Override
    public String save(String folder, String filename, MultipartFile file) {
        String publicId = "petroad/" + folder + "/" + stripExtension(filename);
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId, "resource_type", "image", "overwrite", false));
            Object url = result.get("secure_url");
            if (url == null) throw new IllegalStateException("이미지 저장소가 주소를 돌려주지 않았습니다.");
            return url.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 저장에 실패했습니다.", e);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}