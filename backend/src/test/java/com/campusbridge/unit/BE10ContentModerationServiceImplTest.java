package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Task;
import com.campusbridge.service.impl.ContentModerationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BE10ContentModerationServiceImplTest {

    private ContentModerationServiceImpl moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationServiceImpl();
    }

    // ---- Task publish moderation ----

    @Test
    void allowsCleanTaskContent() {
        CreateTaskRequest request = taskRequest("帮取快递", "从校门口取包裹");

        assertThatCode(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksTaskWithProhibitedKeyword() {
        CreateTaskRequest request = taskRequest("代考高数期末", "帮忙代考高数");

        assertThatThrownBy(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_001_BLOCKED);
    }

    @Test
    void blocksTaskWithContactLeakWhenOnlyInAppIsTrue() {
        CreateTaskRequest request = taskRequest("帮取快递", "加我微信联系");
        request.setOnlyInAppContact(true);

        assertThatThrownBy(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_001_BLOCKED);
    }

    @Test
    void allowsContactLeakWhenOnlyInAppIsFalse() {
        CreateTaskRequest request = taskRequest("帮取快递", "加我微信联系");
        request.setOnlyInAppContact(false);

        assertThatCode(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksTaskWithQQInContent() {
        CreateTaskRequest request = taskRequest("帮忙", "加QQ1234567");
        request.setOnlyInAppContact(true);

        assertThatThrownBy(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ignoresNullTaskRequest() {
        assertThatCode(() -> moderationService.assertTaskPublishAllowed(1L, null))
                .doesNotThrowAnyException();
    }

    // ---- Post publish moderation ----

    @Test
    void blocksPostWithProhibitedKeyword() {
        CreatePostRequest request = postRequest("代上课", "帮人代上课");

        assertThatThrownBy(() -> moderationService.assertPostPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_001_BLOCKED);
    }

    @Test
    void allowsCleanPostContent() {
        CreatePostRequest request = postRequest("求助", "找一位同学帮忙搬宿舍");

        assertThatCode(() -> moderationService.assertPostPublishAllowed(1L, request))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksPostWithCheatingKeyword() {
        CreatePostRequest request = postRequest("作弊", "考试作弊器");

        assertThatThrownBy(() -> moderationService.assertPostPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blocksPostWithContactLeak() {
        CreatePostRequest request = postRequest("求组队", "电话13800000001");

        assertThatThrownBy(() -> moderationService.assertPostPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_001_BLOCKED);
    }

    // ---- Case insensitive matching ----

    @Test
    void matchesKeywordsCaseInsensitively() {
        CreateTaskRequest request = taskRequest("帮忙", "加VX联系我");
        request.setOnlyInAppContact(true);

        assertThatThrownBy(() -> moderationService.assertTaskPublishAllowed(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    // ---- helpers ----

    private CreateTaskRequest taskRequest(String title, String description) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setCampus("仙林校区");
        request.setLocation("南门");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        request.setOnlyInAppContact(true);
        return request;
    }

    private CreatePostRequest postRequest(String title, String content) {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle(title);
        request.setContent(content);
        return request;
    }
}