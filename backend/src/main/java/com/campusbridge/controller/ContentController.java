package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    @GetMapping
    public Result listContent(
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
        return Result.success();
    }
}
