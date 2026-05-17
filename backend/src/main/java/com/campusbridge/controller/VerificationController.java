package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result submitVerification(@RequestBody RequestDtos.CreateVerificationRequest request) {
        return Result.success();
    }

    @GetMapping
    public Result getMyVerification() {
        return Result.success();
    }
}
