package com.juhkang.artiv.global.storage;

/**
 * 이미지 바이트를 저장하고 공개 URL을 알려주는 추상 스토리지.
 * 구현 교체(로컬 디스크 ↔ S3 호환: MinIO/R2/AWS S3)만으로 백엔드를 전환한다.
 */
public interface ObjectStorage {

    /** key(경로 규약 {seriesId}/{episodeNo}/{order}.{ext})에 바이트를 저장한다. */
    void put(String key, byte[] content, String contentType);

    /** key로 접근할 공개 URL(또는 앱 루트 상대경로)을 반환한다. */
    String urlFor(String key);

    /** key의 객체를 삭제한다. 없는 key는 조용히 무시(멱등). */
    void delete(String key);
}
