package com.campusbridge.utils;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.Common;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AliOcrUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${aliyun.ocr.endpoint:ocr-api.cn-hangzhou.aliyuncs.com}")
    private String endpoint;

    @Value("${aliyun.ocr.default-type:Advanced}")
    private String defaultType;

    public JsonNode recognizeAllText(String imageUrl) throws Exception {
        return recognizeAllText(imageUrl, defaultType);
    }

    public JsonNode recognizeAllText(String imageUrl, String type) throws Exception {
        if (!StringUtils.hasText(imageUrl)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        String apiType = StringUtils.hasText(type) ? type : defaultType;

        RecognizeAllTextRequest request = new RecognizeAllTextRequest()
                .setUrl(imageUrl)
                .setType(apiType);
        RuntimeOptions runtime = new RuntimeOptions();

        RecognizeAllTextResponse response = createClient().recognizeAllTextWithOptions(request, runtime);
        if (response == null || response.getBody() == null || response.getBody().getData() == null) {
            return OBJECT_MAPPER.createObjectNode();
        }

        String dataJson = Common.toJSONString(response.getBody().getData());
        if (!StringUtils.hasText(dataJson)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return OBJECT_MAPPER.readTree(dataJson);
    }

    private Client createClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
                .setAccessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
        config.setEndpoint(endpoint);
        return new Client(config);
    }
}
