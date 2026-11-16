package com.campusbridge.unit;

import com.campusbridge.controller.ContentController;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.ContentSort;
import com.campusbridge.enums.RewardType;
import com.campusbridge.service.ContentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentControllerTest {
    @Test
    void commaSeparatedTypesAndFiltersBindToEnums() throws Exception {
        ContentService service = mock(ContentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ContentController(service)).build();

        mvc.perform(get("/api/v1/content").param("itemTypes", "POST,TASK")
                        .param("rewardType", "POINTS").param("sortBy", "DEADLINE"))
                .andExpect(status().isOk());

        verify(service).listContent(null, Set.of(ContentItemType.POST, ContentItemType.TASK),
                null, null, null, null, RewardType.POINTS, ContentSort.DEADLINE, 1, 20);
    }

    @Test
    void unknownFilterValuesAreRejectedBeforeCallingService() throws Exception {
        ContentService service = mock(ContentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ContentController(service)).build();

        for (String parameter : new String[]{"itemTypes", "rewardType", "sortBy"}) {
            mvc.perform(get("/api/v1/content").param(parameter, "UNKNOWN"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
