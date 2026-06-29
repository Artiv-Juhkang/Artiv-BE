package com.juhkang.artiv.domain.community;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 본문에서 @닉네임 멘션을 추출한다. 닉네임은 고유(unique)하므로 1닉=1유저. */
public final class Mentions {

    // @ 뒤 한글(음절·자모)·영문·숫자·_ 1~20자(닉네임 길이 상한과 일치).
    // (?<![\w가-힣]): 직전이 단어문자/한글이면 매칭 안 함 → 이메일(foo@bar) 오인 방지.
    private static final Pattern MENTION = Pattern.compile("(?<![\\w가-힣])@([\\p{IsHangul}A-Za-z0-9_]{1,20})");
    private static final int MAX_MENTIONS = 10;

    private Mentions() {
    }

    /** 등장순 유지 + 중복 제거 + 상한(스팸 방지). 매칭 없으면 빈 집합. */
    public static Set<String> extract(String content) {
        Set<String> result = new LinkedHashSet<>();
        if (content == null) {
            return result;
        }
        // 저장 닉네임과 같은 표현형(NFC)으로 맞춰 NFD 입력(일부 Apple 경로)에서의 무음 미스 방지.
        Matcher m = MENTION.matcher(Normalizer.normalize(content, Normalizer.Form.NFC));
        while (m.find() && result.size() < MAX_MENTIONS) {
            result.add(m.group(1));
        }
        return result;
    }
}
