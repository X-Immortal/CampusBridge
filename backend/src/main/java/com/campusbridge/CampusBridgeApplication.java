package com.campusbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CampusBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusBridgeApplication.class, args);
    }
}
