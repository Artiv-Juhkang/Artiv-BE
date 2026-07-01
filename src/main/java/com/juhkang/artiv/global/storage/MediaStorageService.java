package com.juhkang.artiv.global.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

/**
 * 비이미지 회차 자산(소설 본문·오디오)을 원본 그대로 {@link ObjectStorage}에 저장한다.
 * 이미지는 검증·리사이즈가 필요해 {@link ImageStorageService}가 전담하고, 여기선
 * 트랜스코딩 없이 원본 바이트만 저장한다. key 규약은 이미지와 동일: {seriesId}/{episodeNo}/{order}.{ext}
 */
@Service
public class MediaStorageService {

    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/markdown");
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/wav", "audio/x-wav", "audio/ogg");

    private final ObjectStorage objectStorage;

    public MediaStorageService(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public record Stored(String path, String mimeType) {
    }

    /** 소설 본문 텍스트 자산을 저장한다. */
    public List<Stored> storeText(Long seriesId, int episodeNo, List<MultipartFile> files) {
        return store(seriesId + "/" + episodeNo, files, TEXT_TYPES);
    }

    /** 오디오 자산을 원본 그대로 저장한다(리사이즈/트랜스코딩 없음). */
    public List<Stored> storeAudio(Long seriesId, int episodeNo, List<MultipartFile> files) {
        return store(seriesId + "/" + episodeNo, files, AUDIO_TYPES);
    }

    private List<Stored> store(String keyPrefix, List<MultipartFile> files, Set<String> allowed) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<Stored> result = new ArrayList<>();
        for (int order = 0; order < files.size(); order++) {
            MultipartFile file = files.get(order);
            String contentType = file.getContentType();
            if (file.isEmpty() || contentType == null || !allowed.contains(contentType)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            String key = keyPrefix + "/" + order + "." + extFor(contentType);
            try {
                objectStorage.put(key, file.getBytes(), contentType);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.STORAGE_FAILED);
            }
            result.add(new Stored(key, contentType));
        }
        return result;
    }

    private String extFor(String contentType) {
        return switch (contentType) {
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4", "audio/x-m4a", "audio/aac" -> "m4a";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/ogg" -> "ogg";
            default -> "bin";
        };
    }
}
