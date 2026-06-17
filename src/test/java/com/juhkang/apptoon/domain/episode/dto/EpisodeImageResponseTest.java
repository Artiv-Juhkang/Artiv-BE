package com.juhkang.apptoon.domain.episode.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.juhkang.apptoon.domain.episode.EpisodeImage;

class EpisodeImageResponseTest {

    @Test
    void url은_저장경로앞에_files_접두사를_붙인_절대경로다() {
        EpisodeImage image = EpisodeImage.create(null, 0, "1/2/0.png", 800, 1200);

        EpisodeImageResponse response = EpisodeImageResponse.of(image);

        assertThat(response.url()).isEqualTo("/files/1/2/0.png");
    }
}
