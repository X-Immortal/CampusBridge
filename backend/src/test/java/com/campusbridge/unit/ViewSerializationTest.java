package com.campusbridge.unit;

import com.campusbridge.common.GuestTaskMasker;
import com.campusbridge.dto.view.ConversationView;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.entity.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ViewSerializationTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @MethodSource("existingResponseFields")
    void viewKeepsTheExistingFlatJsonFields(Object view, List<String> expectedFields) {
        JsonNode node = json.valueToTree(view);
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    @Test
    void hidingGuestFieldsOnTheViewDoesNotMutateThePersistenceEntity() {
        Task entity = new Task();
        entity.setId(10L);
        entity.setProviderId(2L);
        entity.setRouteHint("Internal route");
        entity.setSensitiveReason("Contact details");
        TaskView view = TaskView.from(entity);
        view.setProviderName("Provider");

        GuestTaskMasker.maskForGuest(view, null);

        assertThat(view.getProviderId()).isNull();
        assertThat(view.getProviderName()).isNull();
        assertThat(view.getRouteHint()).isNull();
        assertThat(view.getSensitiveReason()).isNull();
        assertThat(entity.getProviderId()).isEqualTo(2L);
        assertThat(entity.getRouteHint()).isEqualTo("Internal route");
        assertThat(entity.getSensitiveReason()).isEqualTo("Contact details");
    }

    private static Stream<Arguments> existingResponseFields() {
        return Stream.of(
                Arguments.of(new TaskView(), List.of(
                        "id", "requesterId", "providerId", "requesterName", "providerName", "category", "title",
                        "description", "campus", "location", "deliveryLocation", "deadline", "rewardType",
                        "rewardDescription", "rewardAmount", "completionStandard", "requiresProof", "onlyInAppContact",
                        "allowOfflineContact", "isInstantTask", "routeHint", "riskDisclosureConfirmed", "sensitiveReason",
                        "status", "createdAt", "updatedAt")),
                Arguments.of(new PostView(), List.of(
                        "id", "authorId", "authorName", "category", "title", "content", "status", "replyCount", "likeCount",
                        "markCount", "acceptedReplyId", "createdAt", "updatedAt")),
                Arguments.of(new ReplyView(), List.of(
                        "id", "postId", "authorId", "parentReplyId", "content", "status", "likeCount", "createdAt",
                        "authorName")),
                Arguments.of(new ConversationView(), List.of(
                        "id", "type", "title", "ownerId", "peerUserId", "relatedType", "relatedId", "replyable",
                        "lastMessageAt", "lastMessagePreview", "unreadCount", "lastReadAt", "createdAt")));
    }
}
