package com.campusbridge.ontology;

/** 本体里的一条有向关系。谓词必须是动词，禁止全叫「关联」。 */
public record OntologyRelation(
        String id,
        String subject,
        String predicate,
        String object,
        String cardinality,
        String description) {
}
