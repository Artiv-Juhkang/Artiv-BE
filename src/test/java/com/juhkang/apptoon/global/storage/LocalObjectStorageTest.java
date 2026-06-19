package com.juhkang.apptoon.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {

    @Test
    void urlFor는_files_접두사를_붙인_앱루트_상대경로다() {
        LocalObjectStorage storage = new LocalObjectStorage("storage");

        assertThat(storage.urlFor("1/2/0.png")).isEqualTo("/files/1/2/0.png");
    }

    @Test
    void put은_key_경로에_바이트를_저장한다(@TempDir Path tempDir) throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(tempDir.toString());
        byte[] content = "hello".getBytes();

        storage.put("3/1/0.png", content, "image/png");

        assertThat(Files.readAllBytes(tempDir.resolve("3/1/0.png"))).isEqualTo(content);
    }
}
