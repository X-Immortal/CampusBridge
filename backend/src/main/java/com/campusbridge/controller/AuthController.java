package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result register(@RequestBody User request) {
        return Result.success();
    }

    @PostMapping("/login")
    public Result login(@RequestBody User request) {
        return Result.success();
    }
}
