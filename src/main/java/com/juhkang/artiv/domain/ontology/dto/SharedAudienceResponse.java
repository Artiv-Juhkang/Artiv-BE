package com.juhkang.artiv.domain.ontology.dto;

import java.util.List;

/**
 * "내 독자가 함께 보는 작품" — 온톨로지가 아니면 만들 수 없는 파생 링크.
 *
 * 담지 않는 것이 담는 것만큼 중요하다: **절대 겹침 수·상대 작품의 독자 수·완독률·작가 닉네임·
 * 태그를 담지 않는다.** 절대 겹침 수는 상대 작품 독자 수의 하한을 직접 주므로, 방향성 제약을
 * "규칙"이 아니라 "표현 불가능성"으로 만들기 위해 필드 자체를 두지 않았다.
 * (그래도 myAudienceSize × share로 근사 역산은 남는다 — docs/fde/04-permissions.md §6에 적었다.)
 *
 * myAudienceSize가 null이면 분모가 너무 작아 링크를 계산하지 않았다는 뜻이다.
 */
public record SharedAudienceResponse(Long myAudienceSize, List<Link> links) {

    public record Link(Long workId, String title, String contentType, String medium,
                       double shareOfMyAudience) {
    }

    public static SharedAudienceResponse suppressed() {
        return new SharedAudienceResponse(null, List.of());
    }
}
