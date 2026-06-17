package com.juhkang.apptoon.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.juhkang.apptoon.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StaticFileServingTest {

    @Autowired
    private MockMvc mockMvc;

    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("apptoon-static-test");
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (storageRoot != null) {
            try (var paths = Files.walk(storageRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void files_경로로_저장된_이미지를_인증없이_서빙한다() throws Exception {
        byte[] content = "fake-png-bytes".getBytes();
        Path file = storageRoot.resolve("1/1/0.png");
        Files.createDirectories(file.getParent());
        Files.write(file, content);

        mockMvc.perform(get("/files/1/1/0.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(content));
    }

    @Test
    void 존재하지_않는_files_경로는_인증차단_401이_아니라_404다() throws Exception {
        mockMvc.perform(get("/files/does-not-exist.png"))
                .andExpect(status().isNotFound());
    }
}
