package com.campusbridge.ontology;

import java.util.List;

/** 本体里的一个概念。没有反例的概念说明边界还没想清楚，加载时会校验。 */
public record OntologyConcept(
        String id,
        String name,
        String zh,
        String definition,
        List<String> attributes,
        List<String> examples,
        List<String> counterexamples,
        String owner,
        String version) {
}
