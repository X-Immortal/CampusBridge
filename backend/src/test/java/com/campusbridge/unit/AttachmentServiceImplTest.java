package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.service.impl.AttachmentServiceImpl;
import com.campusbridge.utils.AliOSSUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {
    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private AliOSSUtils oss;

    @InjectMocks
    private AttachmentServiceImpl attachments;

    @Test
    void explicitInvalidTypeIsRejectedBeforeUploading() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
        assertThatThrownBy(() -> attachments.uploadAttachment(100L, file, "TASK", "UNSUPPORTED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ATTACH_003_INVALID_TYPE);
        verifyNoInteractions(oss, attachmentMapper);
    }
}
