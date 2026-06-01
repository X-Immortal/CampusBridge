package com.campusbridge.service.impl;

import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ContentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContentServiceImpl implements ContentService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final PostMapper postMapper;
    private final TaskMapper taskMapper;

    public ContentServiceImpl(PostMapper postMapper, TaskMapper taskMapper) {
        this.postMapper = postMapper;
        this.taskMapper = taskMapper;
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
        boolean includePosts = requestedTypes.isEmpty() || requestedTypes.contains("POST");
        boolean includeTasks = requestedTypes.isEmpty() || requestedTypes.contains("TASK");

        List<ViewDtos.ContentItemView> allItems = new ArrayList<>();

        if (includePosts) {
            allItems.addAll(fetchPosts(keyword, categoryCode, status));
        }

        if (includeTasks) {
            allItems.addAll(fetchTasks(keyword, categoryCode, campus, status, rewardType));
        }

        allItems.sort(resolveSortComparator(sortBy));

        long total = allItems.size();
        int offset = (safePage - 1) * safeSize;
        int toIndex = Math.min(offset + safeSize, allItems.size());
        List<ViewDtos.ContentItemView> pageItems =
                offset >= allItems.size() ? List.of() : allItems.subList(offset, toIndex);

        return new ResponseDtos.PagedResult<>(pageItems, safePage, safeSize, total);
    }

    private Set<String> parseItemTypes(String itemTypes) {
        if (itemTypes == null || itemTypes.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(itemTypes.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<ViewDtos.ContentItemView> fetchPosts(String keyword, String categoryCode, String status) {
        int fetchSize = 500;

        String mappedStatus = mapPostStatus(status);
        List<Post> posts = postMapper.selectWithFilters(categoryCode, mappedStatus, keyword, 0, fetchSize);

        return posts.stream()
                .map(this::toContentItem)
                .collect(Collectors.toList());
    }

    private List<ViewDtos.ContentItemView> fetchTasks(
            String keyword, String categoryCode, String campus, String status, String rewardType) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("category", categoryCode);
        params.put("campus", campus);
        params.put("status", status);
        params.put("rewardType", rewardType);
        params.put("offset", 0);
        params.put("limit", 500);

        List<Task> tasks = taskMapper.selectWithFilters(params);

        return tasks.stream()
                .map(this::toContentItem)
                .collect(Collectors.toList());
    }

    private String mapPostStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PUBLISHED";
        }
        return status;
    }

    private ViewDtos.ContentItemView toContentItem(Post post) {
        return new ViewDtos.ContentItemView(
                ApiEnums.ContentItemType.POST,
                post.getId(),
                post.getTitle(),
                truncate(post.getContent(), 120),
                post.getAuthorId(),
                null,
                post.getCategory(),
                post.getStatus(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                null);
    }

    private ViewDtos.ContentItemView toContentItem(Task task) {
        String rewardLabel = task.getRewardType() != null ? task.getRewardType() : null;
        if (task.getRewardDescription() != null && !task.getRewardDescription().isBlank()) {
            rewardLabel = (rewardLabel != null ? rewardLabel + " · " : "") + task.getRewardDescription();
        }

        return new ViewDtos.ContentItemView(
                ApiEnums.ContentItemType.TASK,
                task.getId(),
                task.getTitle(),
                truncate(task.getDescription(), 120),
                task.getRequesterId(),
                null,
                task.getCategory(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                rewardLabel);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private Comparator<ViewDtos.ContentItemView> resolveSortComparator(String sortBy) {
        if ("DEADLINE".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                    ViewDtos.ContentItemView::getSortTime,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(
                ViewDtos.ContentItemView::getSortTime,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
