package com.strangequark.odoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OdocApplication {

    public static void main(String[] args) {
        SpringApplication.run(OdocApplication.class, args);
    }
}
