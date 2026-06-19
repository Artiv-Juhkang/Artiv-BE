package com.juhkang.apptoon.domain.episode.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.juhkang.apptoon.domain.episode.EpisodeImage;

class EpisodeImageResponseTest {

    @Test
    void of는_전달받은_url과_이미지_메타를_매핑한다() {
        EpisodeImage image = EpisodeImage.create(null, 0, "1/2/0.png", 800, 1200);

        EpisodeImageResponse response = EpisodeImageResponse.of(image, "/files/1/2/0.png");

        assertThat(response.url()).isEqualTo("/files/1/2/0.png");
        assertThat(response.sortOrder()).isZero();
        assertThat(response.width()).isEqualTo(800);
        assertThat(response.height()).isEqualTo(1200);
    }
}
