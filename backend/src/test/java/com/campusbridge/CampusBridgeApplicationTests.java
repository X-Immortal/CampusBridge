package com.campusbridge;

import com.campusbridge.config.SecurityConfig;
import com.campusbridge.controller.AuthController;
import com.campusbridge.controller.ConversationController;
import com.campusbridge.controller.TaskController;
import com.campusbridge.service.AuthService;
import com.campusbridge.service.ConversationService;
import com.campusbridge.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CampusBridgeApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void coreApplicationBeansAreRegistered() {
        assertThat(applicationContext.getBean(CampusBridgeApplication.class)).isNotNull();
        assertThat(applicationContext.getBean(SecurityConfig.class)).isNotNull();
        assertThat(applicationContext.getBean(AuthController.class)).isNotNull();
        assertThat(applicationContext.getBean(TaskController.class)).isNotNull();
        assertThat(applicationContext.getBean(ConversationController.class)).isNotNull();
        assertThat(applicationContext.getBean(AuthService.class)).isNotNull();
        assertThat(applicationContext.getBean(TaskService.class)).isNotNull();
        assertThat(applicationContext.getBean(ConversationService.class)).isNotNull();
    }

    @Test
    void passwordEncoderUsesOneWayHashing() {
        String encoded = passwordEncoder.encode("Campus@12345");

        assertThat(encoded).isNotEqualTo("Campus@12345");
        assertThat(passwordEncoder.matches("Campus@12345", encoded)).isTrue();
    }
}
