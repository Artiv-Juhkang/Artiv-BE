package com.juhkang.artiv.domain.series;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.series.dto.ContentTypeResponse;

/**
 * 창작물 타입 레지스트리. 프론트가 부팅 시 1회 조회해 타입 탭·뷰어 분기를 동적 렌더한다 →
 * 백엔드에서 ContentType 값을 추가하면 프론트 코드 변경 없이 자동 반영(타입=데이터).
 */
@RestController
@RequestMapping("/api/creativity")
public class CreativityController {

    @GetMapping("/types")
    public List<ContentTypeResponse> types() {
        return Arrays.stream(ContentType.values()).map(ContentTypeResponse::of).toList();
    }
}
