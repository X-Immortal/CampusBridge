package com.campusbridge.unit;

import com.campusbridge.entity.CreditRecord;
import com.campusbridge.entity.UserCredit;
import com.campusbridge.mapper.CreditRecordMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.service.impl.CreditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE16CreditServiceImplTest {
    private static final Long USER_ID = 2L;
    private static final Long REPORT_ID = 20L;

    @Mock
    private UserCreditMapper userCreditMapper;

    @Mock
    private CreditRecordMapper creditRecordMapper;

    private CreditServiceImpl creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditServiceImpl(userCreditMapper, creditRecordMapper);
    }

    @Test
    void adjustCreditUpdatesExistingCreditAndWritesRecord() {
        UserCredit existingCredit = new UserCredit(
                1L,
                USER_ID,
                86,
                3,
                LocalDateTime.now().minusDays(1));
        when(userCreditMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(existingCredit);
        when(userCreditMapper.updateById(any(UserCredit.class))).thenReturn(1);
        when(creditRecordMapper.insert(any(CreditRecord.class))).thenReturn(1);

        creditService.adjustCredit(
                USER_ID,
                -10,
                "REPORT_PUNISHED",
                "REPORT",
                REPORT_ID);

        ArgumentCaptor<UserCredit> creditCaptor = ArgumentCaptor.forClass(UserCredit.class);
        verify(userCreditMapper).updateById(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getPoints()).isEqualTo(76);
        assertThat(creditCaptor.getValue().getLevel()).isEqualTo(2);

        ArgumentCaptor<CreditRecord> recordCaptor = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(recordCaptor.getValue().getChangeValue()).isEqualTo(-10);
        assertThat(recordCaptor.getValue().getReason()).isEqualTo("REPORT_PUNISHED");
        assertThat(recordCaptor.getValue().getSourceType()).isEqualTo("REPORT");
        assertThat(recordCaptor.getValue().getSourceId()).isEqualTo(REPORT_ID);
    }
}
