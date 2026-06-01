package com.campusbridge.service;

import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;

public interface ContentService {
    ResponseDtos.PagedResult<ViewDtos.ContentItemView> listContent(
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
