package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.ContentItemView;
import com.campusbridge.service.ContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    public ApiResponse<PagedResult<ContentItemView>> listContent(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String itemTypes,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String locationKeyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false, defaultValue = "CREATED_AT") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.success(contentService.listContent(
                keyword, itemTypes, categoryCode, campus, locationKeyword,
                status, rewardType, sortBy, page, size));
    }
}
