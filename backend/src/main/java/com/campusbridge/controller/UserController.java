package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.pojo.UserProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public Result getCurrentUser() {
        return Result.success();
    }

    @GetMapping("/me/profile")
    public Result getMyProfile() {
        return Result.success();
    }

    @PutMapping("/me/profile")
    public Result updateMyProfile(@RequestBody UserProfile request) {
        return Result.success();
    }

    @GetMapping("/{userId}/profile")
    public Result getUserHomepage(@PathVariable Long userId) {
        return Result.success();
    }
}
