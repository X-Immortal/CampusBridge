package com.campusbridge.ontology;

import java.util.List;

/**
 * 一次加载得到的本体快照。事件里记录 {@code version} 与 {@code hash}，
 * 这样「这条规则当时是哪一版」可以事后追。
 */
public record OntologySnapshot(
        String version,
        String hash,
        String updatedAt,
        List<OntologyConcept> concepts,
        List<OntologyRelation> relations,
        List<OntologyConstraint> constraints) {
}
