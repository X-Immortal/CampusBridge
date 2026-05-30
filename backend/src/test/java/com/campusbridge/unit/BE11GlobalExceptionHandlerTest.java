package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.ErrorResponse;
import com.campusbridge.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class BE11GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessExceptionReturnsConfiguredStatusAndCode() {
        BusinessException exception = new BusinessException(
                ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);

        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .isEqualTo("TASK_002_INVALID_STATUS_TRANSITION");
    }

    @Test
    void handleUnexpectedExceptionReturnsInternalErrorCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpectedException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .isEqualTo("COMMON_999_INTERNAL_ERROR");
    }
}
