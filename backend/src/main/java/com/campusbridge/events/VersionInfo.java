package com.campusbridge.events;

import com.campusbridge.config.AiProperties;
import com.campusbridge.ontology.OntologyRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件版本血缘（M3/M4）：每条事件都带五类版本，事后才能把一次行为归因到具体模型/本体/提示词/Schema/工具。
 *
 * <p>没有配置的版本写 {@code unknown}，不阻断业务；事件表结构不变，版本放在 payload JSON 里。</p>
 */
@Component
public class VersionInfo {

    private final AiProperties properties;
    private final OntologyRegistry ontology;

    public VersionInfo(AiProperties properties, OntologyRegistry ontology) {
        this.properties = properties;
        this.ontology = ontology;
    }

    public Map<String, Object> current() {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("modelVersion", text(properties.getModel().getName()));
        versions.put("ontologyVersion", ontologyVersion());
        versions.put("promptVersion", text(properties.getPromptVersion()));
        versions.put("schemaVersion", text(properties.getSchemaVersion()));
        versions.put("toolVersion", text(properties.getToolVersion()));
        return versions;
    }

    private String ontologyVersion() {
        try {
            String version = ontology.snapshot().version();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
