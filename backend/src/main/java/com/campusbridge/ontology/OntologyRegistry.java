package com.campusbridge.ontology;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本体的唯一起点。JSON 是唯一事实来源，代码里不允许再写一份规则。
 *
 * <p>支持 {@code ai.ontology.location} 指向 <b>文件</b>（默认 classpath）：
 * 评审现场可以改一条约束再调用 {@code POST /api/ontology/reload}，不用重新编译——这就是
 * 「本体能改变 Action 的结果」最直接的证明。</p>
 *
 * <p>加载时做硬校验：概念数、关系数、约束数不达标，概念缺定义/例子/反例，
 * 关系引用了不存在的概念，约束引用了未实现的求值器 —— 一律启动失败，而不是带着半套本体上线。</p>
 */
@Component
public class OntologyRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(OntologyRegistry.class);

    public static final int MIN_CONCEPTS = 15;
    public static final int MIN_RELATIONS = 20;
    public static final int MIN_CONSTRAINTS = 10;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String location;
    private final Set<String> knownEvaluators;

    private volatile OntologySnapshot snapshot;

    public OntologyRegistry(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${ai.ontology.location:classpath:ontology/campus-ontology.v1.json}") String location,
            com.campusbridge.policy.ConstraintEvaluators evaluators) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.location = location;
        this.knownEvaluators = evaluators.names();
        this.snapshot = load();
    }

    public OntologySnapshot snapshot() {
        return snapshot;
    }

    public String hash() {
        return snapshot.hash();
    }

    public List<OntologyConstraint> constraintsFor(String phase) {
        return snapshot.constraints().stream().filter(c -> c.appliesTo(phase)).toList();
    }

    public OntologyConstraint constraint(String id) {
        return snapshot.constraints().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    /** 现场改完约束后调用；新内容不合法就保留旧快照并抛错，绝不让系统带着坏本体跑。 */
    public synchronized OntologySnapshot reload() {
        OntologySnapshot fresh = load();
        this.snapshot = fresh;
        LOG.info("Ontology reloaded: version={}, hash={}, concepts={}, relations={}, constraints={}",
                fresh.version(), fresh.hash(), fresh.concepts().size(),
                fresh.relations().size(), fresh.constraints().size());
        return fresh;
    }

    public Map<String, Object> summary() {
        OntologySnapshot current = snapshot;
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("concepts", current.concepts().size());
        counts.put("relations", current.relations().size());
        counts.put("constraints", current.constraints().size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", current.version());
        result.put("hash", current.hash());
        result.put("updatedAt", current.updatedAt());
        result.put("counts", counts);
        result.put("required", Map.of(
                "concepts", MIN_CONCEPTS,
                "relations", MIN_RELATIONS,
                "constraints", MIN_CONSTRAINTS));
        return result;
    }

    private OntologySnapshot load() {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            OntologyFile file = objectMapper.readValue(bytes, OntologyFile.class);
            validate(file);
            return new OntologySnapshot(
                    file.version(),
                    sha256(bytes),
                    file.updatedAt(),
                    file.concepts(),
                    file.relations(),
                    file.constraints());
        } catch (IOException e) {
            throw new IllegalStateException("无法读取本体文件：" + location, e);
        }
    }

    private void validate(OntologyFile file) {
        List<String> problems = new ArrayList<>();
        if (file.concepts() == null || file.concepts().size() < MIN_CONCEPTS) {
            problems.add("概念不足 " + MIN_CONCEPTS + " 个");
        }
        if (file.relations() == null || file.relations().size() < MIN_RELATIONS) {
            problems.add("关系不足 " + MIN_RELATIONS + " 条");
        }
        if (file.constraints() == null || file.constraints().size() < MIN_CONSTRAINTS) {
            problems.add("约束不足 " + MIN_CONSTRAINTS + " 条");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("本体不达标：" + String.join("；", problems));
        }

        Set<String> conceptNames = new LinkedHashSet<>();
        for (OntologyConcept concept : file.concepts()) {
            conceptNames.add(concept.name());
            if (isBlank(concept.definition())) {
                problems.add(concept.id() + " 缺定义");
            }
            if (concept.examples() == null || concept.examples().isEmpty()) {
                problems.add(concept.id() + " 缺例子");
            }
            if (concept.counterexamples() == null || concept.counterexamples().isEmpty()) {
                problems.add(concept.id() + " 缺反例");
            }
        }
        for (OntologyRelation relation : file.relations()) {
            if (!conceptNames.contains(relation.subject()) || !conceptNames.contains(relation.object())) {
                problems.add(relation.id() + " 引用了不存在的概念");
            }
        }
        for (OntologyConstraint constraint : file.constraints()) {
            if (!knownEvaluators.contains(constraint.evaluator())) {
                problems.add(constraint.id() + " 的求值器未实现：" + constraint.evaluator());
            }
            if (isBlank(constraint.positiveExample()) || isBlank(constraint.negativeExample())) {
                problems.add(constraint.id() + " 缺正例或反例");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("本体校验失败：" + String.join("；", problems));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record OntologyFile(
            String version,
            String updatedAt,
            List<OntologyConcept> concepts,
            List<OntologyRelation> relations,
            List<OntologyConstraint> constraints) {
    }
}
