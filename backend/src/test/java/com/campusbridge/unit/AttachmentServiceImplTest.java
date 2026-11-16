package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.controller.AttachmentController;
import com.campusbridge.entity.Attachment;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.service.impl.AttachmentServiceImpl;
import com.campusbridge.utils.AliOSSUtils;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentServiceImplTest {
    @Test
    void invalidOwnerTypeIsRejectedBeforeCallingUploadService() throws Exception {
        AttachmentService service = mock(AttachmentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new AttachmentController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/v1/attachments").file(file)
                        .header("X-Expected-Owner-Type", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"test.png,image/png", "test.jpg,image/jpeg", "test.webp,image/webp"})
    void supportedImagesAreUploadedAndSaved(String name, String contentType) throws Exception {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AliOSSUtils oss = mock(AliOSSUtils.class);
        var file = new MockMultipartFile("file", name, contentType, new byte[]{1});
        when(oss.upload(file)).thenReturn("https://example.test/" + name);
        when(mapper.insert(any(Attachment.class))).thenReturn(1);

        Attachment result = new AttachmentServiceImpl(mapper, oss)
                .uploadAttachment(1L, file, AttachmentOwnerType.COMPLETION_PROOF);

        assertThat(result.getOwnerType()).isEqualTo(AttachmentOwnerType.COMPLETION_PROOF);
        assertThat(result.getContentType()).isEqualTo(contentType);
        assertThat(result.getFileUrl()).isEqualTo("https://example.test/" + name);
        verify(oss).upload(file);
        verify(mapper).insert(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"application/pdf", "text/plain", "application/zip",
            "application/octet-stream", "image/svg+xml"})
    void unsupportedTypesAreRejectedBeforeUploadOrPersistence(String contentType) {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AliOSSUtils oss = mock(AliOSSUtils.class);
        var file = new MockMultipartFile("file", "proof", contentType, new byte[]{1});

        assertThatThrownBy(() -> new AttachmentServiceImpl(mapper, oss)
                .uploadAttachment(1L, file, AttachmentOwnerType.COMPLETION_PROOF))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_002_UNSUPPORTED_TYPE));
        verifyNoInteractions(oss, mapper);
    }
}
