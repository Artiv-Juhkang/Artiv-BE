package com.juhkang.artiv.global.dto;

/** "작품별 최대 회차번호" 집계 쿼리용 프로젝션(인터페이스 기반). seriesId·maxNo 별칭과 매칭. */
public interface SeriesMaxNo {
    Long getSeriesId();

    int getMaxNo();
}
