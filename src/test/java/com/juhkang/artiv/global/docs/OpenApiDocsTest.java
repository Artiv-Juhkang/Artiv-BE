package com.juhkang.artiv.global.docs;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juhkang.artiv.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void OpenAPI_문서가_인증없이_생성되고_주요_경로를_포함한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Artiv API"))
                .andExpect(content().string(containsString("/api/series")))
                .andExpect(content().string(containsString("/api/auth/signup")));
    }

    /**
     * 스냅샷 재생성(opt-in) — {@code -Dopenapi.snapshot=write} 일 때만 실행. 라이브 /v3/api-docs 를
     * docs/openapi.json 에 pretty-print 로 기록한다. 평소 빌드에선 비활성(작업트리 무변경).
     * 사용: ./gradlew test --tests '*OpenApiDocsTest*' -Dopenapi.snapshot=write
     */
    @Test
    @EnabledIfSystemProperty(named = "openapi.snapshot", matches = "write")
    void OpenAPI_스냅샷을_파일로_기록한다() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        Object tree = mapper.readValue(json, Object.class);
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        Files.writeString(Path.of("docs/openapi.json"), pretty + "\n");
    }
}
