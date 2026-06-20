package com.juhkang.apptoon.global.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 업로드 이미지를 검증·리사이즈한 뒤 {@link ObjectStorage}에 저장한다.
 * 저장 백엔드(로컬/S3)는 ObjectStorage 구현으로 분리되어 이 서비스는 무관하다.
 * 저장 key 규약: {seriesId}/{episodeNo}/{order}.{ext}
 */
@Service
public class ImageStorageService {

    private static final int MAX_WIDTH = 800;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final ObjectStorage objectStorage;

    public ImageStorageService(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public record Stored(String path, int width, int height) {
    }

    /** 저장 key의 공개 URL을 반환한다. */
    public String urlFor(String key) {
        return objectStorage.urlFor(key);
    }

    /** 저장된 객체를 삭제한다(없으면 무시). */
    public void delete(String key) {
        objectStorage.delete(key);
    }

    public List<Stored> store(Long seriesId, int episodeNo, List<MultipartFile> images) {
        return store(seriesId + "/" + episodeNo, images);
    }

    /** keyPrefix 하위에 {order}.{ext}로 저장한다(예: inquiries/{id}/{uuid}). 회차/문의가 공유. */
    public List<Stored> store(String keyPrefix, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        List<Stored> result = new ArrayList<>();
        for (int order = 0; order < images.size(); order++) {
            result.add(storeOne(keyPrefix, order, images.get(order)));
        }
        return result;
    }

    private Stored storeOne(String keyPrefix, int order, MultipartFile file) {
        validate(file);
        try {
            BufferedImage source = ImageIO.read(file.getInputStream());
            if (source == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            BufferedImage resized = source.getWidth() > MAX_WIDTH
                    ? Thumbnails.of(source).width(MAX_WIDTH).asBufferedImage()
                    : source;

            String ext = "image/png".equals(file.getContentType()) ? "png" : "jpg";
            String key = keyPrefix + "/" + order + "." + ext;

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(resized, ext, bytes);
            objectStorage.put(key, bytes.toByteArray(), "png".equals(ext) ? "image/png" : "image/jpeg");

            return new Stored(key, resized.getWidth(), resized.getHeight());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_FAILED);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty() || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
