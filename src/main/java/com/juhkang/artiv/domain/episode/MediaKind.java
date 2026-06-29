package com.juhkang.artiv.domain.episode;

/**
 * 회차 자산(EpisodeImage)의 매체 종류 — 다매체 일반화(슬라이스 1).
 * IMAGE: 웹툰 스트립·일러스트·사진(픽셀 치수 있음). AUDIO: 음악/오디오(duration). TEXT: 소설 본문.
 * 기존 행은 전부 IMAGE 백필. 새 종류는 값 추가(가산).
 */
public enum MediaKind {
    IMAGE,
    AUDIO,
    TEXT
}
