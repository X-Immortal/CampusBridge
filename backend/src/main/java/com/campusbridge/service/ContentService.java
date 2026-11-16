package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.ContentItemView;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.ContentSort;
import com.campusbridge.enums.RewardType;

import java.util.Set;

public interface ContentService {
    PagedResult<ContentItemView> listContent(
            String keyword,
            Set<ContentItemType> itemTypes,
            String categoryCode,
            String campus,
            String locationKeyword,
            String status,
            RewardType rewardType,
            ContentSort sortBy,
            Integer page,
            Integer size);
}
