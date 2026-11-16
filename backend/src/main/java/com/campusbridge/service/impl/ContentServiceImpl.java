package com.campusbridge.service.impl;

import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.ContentMapper;
import com.campusbridge.service.ContentService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContentServiceImpl implements ContentService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ContentMapper contentMapper;

    public ContentServiceImpl(ContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    @Override
    public ResponseDtos.PagedResult<ViewDtos.ContentItemView> listContent(
            String keyword,
            String itemTypes,
            String categoryCode,
            String campus,
            String locationKeyword,
            String status,
            String rewardType,
            String sortBy,
            Integer page,
            Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Set<String> requestedTypes = parseItemTypes(itemTypes);
        boolean includePosts = (requestedTypes.isEmpty() || requestedTypes.contains("POST"))
                && !hasText(campus) && !hasText(locationKeyword) && !hasText(rewardType);
        boolean includeTasks = requestedTypes.isEmpty() || requestedTypes.contains("TASK");
        if (!includePosts && !includeTasks) {
            return new ResponseDtos.PagedResult<>(List.of(), safePage, safeSize, 0L);
        }

        Map<String, Object> filters = new HashMap<>();
        filters.put("includePosts", includePosts);
        filters.put("includeTasks", includeTasks);
        filters.put("keyword", normalize(keyword));
        filters.put("categoryCode", normalize(categoryCode));
        filters.put("campus", normalize(campus));
        filters.put("locationKeyword", normalize(locationKeyword));
        filters.put("status", normalize(status));
        filters.put("rewardType", normalize(rewardType));
        filters.put("orderByDeadline", "DEADLINE".equalsIgnoreCase(sortBy));
        filters.put("offset", ((long) safePage - 1) * safeSize);
        filters.put("limit", safeSize);

        long total = contentMapper.count(filters);
        List<ViewDtos.ContentItemView> items = contentMapper.selectPage(filters);
        return new ResponseDtos.PagedResult<>(items, safePage, safeSize, total);
    }

    private Set<String> parseItemTypes(String itemTypes) {
        if (!hasText(itemTypes)) {
            return Set.of();
        }
        return java.util.Arrays.stream(itemTypes.split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
