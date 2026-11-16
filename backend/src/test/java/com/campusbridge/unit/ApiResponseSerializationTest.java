package com.campusbridge.unit;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.ErrorResponse;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.UnreadSummaryView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void emptySuccessPreservesEnvelopeAndExplicitNullData() {
        JsonNode json = mapper.valueToTree(ApiResponse.success());

        assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("code", "message", "data", "meta");
        assertThat(json.get("code").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("message").asText()).isEqualTo("OK");
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.at("/meta/requestId").asText()).isNotBlank();
        assertThat(json.at("/meta/timestamp").isMissingNode()).isFalse();
    }

    @Test
    void businessErrorUsesSeparateEnvelope() {
        JsonNode json = mapper.valueToTree(ErrorResponse.of(ErrorCode.VERIFY_004_NOT_FOUND, "Verification record not found", null));

        assertThat(json.get("code").asText()).isEqualTo("VERIFY_004_NOT_FOUND");
        assertThat(json.get("message").asText()).isEqualTo("Verification record not found");
        assertThat(json.has("data")).isFalse();
        assertThat(json.has("details")).isTrue();
        assertThat(json.at("/meta/requestId").asText()).isNotBlank();
    }

    @Test
    void unreadSummaryPreservesPrivateFieldName() {
        JsonNode json = mapper.valueToTree(ApiResponse.success(new UnreadSummaryView(7, 3, 4)));

        assertThat(json.at("/data/total").asLong()).isEqualTo(7);
        assertThat(json.at("/data/system").asLong()).isEqualTo(3);
        assertThat(json.at("/data/private").asLong()).isEqualTo(4);
        assertThat(json.at("/data").has("privateCount")).isFalse();
    }

    @Test
    void nestedPaginationRetainsTypedItemsDuringJsonRoundTrip() throws Exception {
        PostView post = new PostView();
        post.setId(7L);
        post.setTitle("校园活动");
        ApiResponse<PagedResult<PostView>> response =
                ApiResponse.success(new PagedResult<>(List.of(post), 1, 20, 1L));

        ApiResponse<PagedResult<PostView>> decoded = mapper.readValue(
                mapper.writeValueAsString(response), new TypeReference<ApiResponse<PagedResult<PostView>>>() {});

        assertThat(decoded.getData().getItems().get(0).getTitle()).isEqualTo("校园活动");
        assertThat(decoded.getData().getTotal()).isEqualTo(1L);
        assertThat(decoded.getData().getPage()).isEqualTo(1);
    }
}
