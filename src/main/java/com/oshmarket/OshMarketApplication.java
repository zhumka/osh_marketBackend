package com.oshmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OshMarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(OshMarketApplication.class, args);
    }
}
