package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createReport(@RequestBody RequestDtos.CreateReportRequest request) {
        return Result.success();
    }

    @GetMapping
    public Result listReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success();
    }
}
