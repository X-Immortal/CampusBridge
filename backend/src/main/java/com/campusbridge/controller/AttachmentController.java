package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String expectedOwnerType,
            @RequestParam(required = false) String type) {
        return Result.success();
    }
}
