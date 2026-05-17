package com.campusbridge.controller;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @GetMapping
    public Result listConversations(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result createConversation(@RequestBody RequestDtos.CreateConversationRequest request) {
        return Result.success();
    }

    @GetMapping("/{conversationId}/messages")
    public Result listMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success();
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Result sendMessage(
            @PathVariable Long conversationId,
            @RequestBody RequestDtos.SendMessageRequest request) {
        return Result.success();
    }
}
