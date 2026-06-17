package com.juhkang.apptoon.global.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 업로드 이미지를 검증·리사이즈해서 로컬 스토리지에 저장한다.
 * 저장 경로: {root}/{seriesId}/{episodeNo}/{order}.{ext}
 */
@Service
public class ImageStorageService {

    private static final int MAX_WIDTH = 800;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final Path root;

    public ImageStorageService(@Value("${app.storage.root:storage}") String root) {
        this.root = Path.of(root);
    }

    public record Stored(String path, int width, int height) {
    }

    public List<Stored> store(Long seriesId, int episodeNo, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        List<Stored> result = new ArrayList<>();
        for (int order = 0; order < images.size(); order++) {
            result.add(storeOne(seriesId, episodeNo, order, images.get(order)));
        }
        return result;
    }

    private Stored storeOne(Long seriesId, int episodeNo, int order, MultipartFile file) {
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
            String relative = seriesId + "/" + episodeNo + "/" + order + "." + ext;
            Path destination = root.resolve(relative);
            Files.createDirectories(destination.getParent());
            ImageIO.write(resized, ext, destination.toFile());

            return new Stored(relative, resized.getWidth(), resized.getHeight());
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
