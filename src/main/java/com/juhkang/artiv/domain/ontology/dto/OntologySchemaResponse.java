package com.juhkang.artiv.domain.ontology.dto;

import java.util.Arrays;
import java.util.List;

import com.juhkang.artiv.domain.ontology.ActionType;
import com.juhkang.artiv.domain.ontology.LinkType;
import com.juhkang.artiv.domain.ontology.ObjectType;

/** 온톨로지 명세 전체. ContentTypeResponse와 같은 "enum 메타를 그대로 내려주는" 패턴. */
public record OntologySchemaResponse(
        List<ObjectSpec> objects,
        List<LinkSpec> links,
        List<ActionSpec> actions) {

    public record ObjectSpec(String key, String label, String backing, boolean derived) {
    }

    public record LinkSpec(String key, String label, String from, String to, String kind) {
    }

    public record ActionSpec(String key, String label, String target, String method, String endpoint) {
    }

    public static OntologySchemaResponse of() {
        return new OntologySchemaResponse(
                Arrays.stream(ObjectType.values())
                        .map(o -> new ObjectSpec(o.name(), o.getLabel(), o.getBacking(), o.isDerived()))
                        .toList(),
                Arrays.stream(LinkType.values())
                        .map(l -> new LinkSpec(l.name(), l.getLabel(), l.getFrom().name(),
                                l.getTo().name(), l.getKind().name()))
                        .toList(),
                Arrays.stream(ActionType.values())
                        .map(a -> new ActionSpec(a.name(), a.getLabel(), a.getTarget().name(),
                                a.getMethod(), a.getEndpoint()))
                        .toList());
    }
}
