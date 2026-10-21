package com.hsf302.trainoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TrainOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainOfficeApplication.class, args);
    }
}