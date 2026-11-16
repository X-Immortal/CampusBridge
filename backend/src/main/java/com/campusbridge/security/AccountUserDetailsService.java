package com.campusbridge.security;

import com.campusbridge.entity.User;
import com.campusbridge.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {
    private final UserMapper users;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = users.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("Account not found");
        }
        return new UserPrincipal(user);
    }
}
