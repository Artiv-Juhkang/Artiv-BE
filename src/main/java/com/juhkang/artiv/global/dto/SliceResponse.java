package com.juhkang.artiv.global.dto;

import java.util.List;

import org.springframework.data.domain.Slice;

/** 무한스크롤용 페이징 응답. 전체 개수 없이 다음 페이지 존재 여부만 내려준다. */
public record SliceResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext
) {

    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return new SliceResponse<>(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext()
        );
    }
}
