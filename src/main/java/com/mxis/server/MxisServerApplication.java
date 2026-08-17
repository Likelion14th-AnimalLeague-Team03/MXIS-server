package com.mxis.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MxisServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MxisServerApplication.class, args);
    }
}
