package com.campusbridge.service.impl;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.query.ContentQuery;
import com.campusbridge.dto.view.ContentItemView;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.ContentSort;
import com.campusbridge.enums.RewardType;
import com.campusbridge.mapper.ContentMapper;
import com.campusbridge.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ContentMapper contentMapper;

    @Override
    public PagedResult<ContentItemView> listContent(
            String keyword,
            Set<ContentItemType> itemTypes,
            String categoryCode,
            String campus,
            String locationKeyword,
            String status,
            RewardType rewardType,
            ContentSort sortBy,
            Integer page,
            Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Set<ContentItemType> requestedTypes = itemTypes == null ? Set.of() : itemTypes;
        boolean includePosts = (requestedTypes.isEmpty() || requestedTypes.contains(ContentItemType.POST))
                && !hasText(campus) && !hasText(locationKeyword) && rewardType == null;
        boolean includeTasks = requestedTypes.isEmpty() || requestedTypes.contains(ContentItemType.TASK);
        if (!includePosts && !includeTasks) {
            return new PagedResult<>(List.of(), safePage, safeSize, 0L);
        }

        ContentQuery filters = ContentQuery.builder()
                .includePosts(includePosts)
                .includeTasks(includeTasks)
                .keyword(normalize(keyword))
                .categoryCode(normalize(categoryCode))
                .campus(normalize(campus))
                .locationKeyword(normalize(locationKeyword))
                .status(normalize(status))
                .rewardType(rewardType)
                .orderByDeadline(sortBy == ContentSort.DEADLINE)
                .offset(((long) safePage - 1) * safeSize)
                .limit(safeSize)
                .build();

        long total = contentMapper.count(filters);
        List<ContentItemView> items = contentMapper.selectPage(filters);
        return new PagedResult<>(items, safePage, safeSize, total);
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
