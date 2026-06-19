package com.juhkang.apptoon.global.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

/**
 * 로컬 디스크 저장 + {@code /files/**} 정적 서빙(기본 구현).
 * {@code app.storage.type} 미설정/local 일 때 활성화. 운영에서 s3 로 바꾸면 비활성.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;

    public LocalObjectStorage(@Value("${app.storage.root:storage}") String root) {
        this.root = Path.of(root);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            Path destination = root.resolve(key);
            Files.createDirectories(destination.getParent());
            Files.write(destination, content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_FAILED);
        }
    }

    @Override
    public String urlFor(String key) {
        return "/files/" + key;
    }
}
