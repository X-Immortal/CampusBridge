package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.ContentItemView;

public interface ContentService {
    PagedResult<ContentItemView> listContent(
            String keyword,
            String itemTypes,
            String categoryCode,
            String campus,
            String locationKeyword,
            String status,
            String rewardType,
            String sortBy,
            Integer page,
            Integer size);
}
