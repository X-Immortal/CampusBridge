package com.campusbridge.ai;

import com.campusbridge.ontology.OntologyConcept;
import com.campusbridge.ontology.OntologyConstraint;
import com.campusbridge.ontology.OntologyRegistry;
import com.campusbridge.ontology.OntologyRelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 本体不是文档：加载即校验，改一条约束能改行为。 */
class OntologyRegistryTest {

    private final OntologyRegistry ontology = AiFixture.ontology();

    @Test
    void 达到课件硬指标() {
        List<OntologyConcept> concepts = ontology.snapshot().concepts();
        List<OntologyRelation> relations = ontology.snapshot().relations();
        List<OntologyConstraint> constraints = ontology.snapshot().constraints();

        assertThat(concepts).hasSizeGreaterThanOrEqualTo(15);
        assertThat(relations).hasSizeGreaterThanOrEqualTo(20);
        assertThat(constraints).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void 每个概念都有定义例子和反例() {
        for (OntologyConcept concept : ontology.snapshot().concepts()) {
            assertThat(concept.definition()).as(concept.id() + " 定义").isNotBlank();
            assertThat(concept.examples()).as(concept.id() + " 例子").isNotEmpty();
            assertThat(concept.counterexamples()).as(concept.id() + " 反例").isNotEmpty();
        }
    }

    @Test
    void 关系是有方向的具体谓词而不是都叫关联() {
        Set<String> predicates = ontology.snapshot().relations().stream()
                .map(OntologyRelation::predicate)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(predicates).doesNotContain("关联", "associated_with");
        assertThat(predicates.size()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void 每条约束都有可解释的正反例和实现() {
        for (OntologyConstraint constraint : ontology.snapshot().constraints()) {
            assertThat(constraint.positiveExample()).as(constraint.id()).isNotBlank();
            assertThat(constraint.negativeExample()).as(constraint.id()).isNotBlank();
            assertThat(constraint.evaluator()).as(constraint.id()).isNotBlank();
        }
    }

    @Test
    void 至少两条约束涉及AI或个性化() {
        assertThat(ontology.constraint("K06")).isNotNull();
        assertThat(ontology.constraint("K15")).isNotNull();
        assertThat(ontology.constraint("K03")).isNotNull();
    }

    @Test
    void 现场改一条约束后系统拒绝(@TempDir Path tempDir) throws Exception {
        Path copy = tempDir.resolve("ontology.json");
        try (InputStream input = getClass().getResourceAsStream(
                "/ontology/campus-ontology.v1.json")) {
            Files.copy(input, copy);
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(copy.toFile());
        JsonNode constraints = root.get("constraints");
        String originalHash = ontology.hash();

        for (JsonNode constraint : constraints) {
            if ("K09".equals(constraint.get("id").asText())) {
                ((ObjectNode) constraint.get("params")).put("min", 20);
            }
        }
        Files.writeString(copy, mapper.writeValueAsString(root));

        OntologyRegistry editable = new OntologyRegistry(
                new DefaultResourceLoader(),
                mapper,
                copy.toUri().toString(),
                new com.campusbridge.policy.ConstraintEvaluators());
        assertThat(editable.constraint("K09").params().get("min").asInt()).isEqualTo(20);
        assertThat(editable.hash()).isNotEqualTo(originalHash);
        assertThat(editable.reload().constraints()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void 本体不达标时拒绝启动(@TempDir Path tempDir) throws Exception {
        Path broken = tempDir.resolve("broken.json");
        Files.writeString(broken, """
                {"version":"0.0","concepts":[],"relations":[],"constraints":[]}
                """);
        assertThatThrownBy(() -> new OntologyRegistry(
                new DefaultResourceLoader(), new ObjectMapper(), broken.toUri().toString(),
                new com.campusbridge.policy.ConstraintEvaluators()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("概念不足");
    }
}
