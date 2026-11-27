package com.petroad.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PetRoadApplication {
    public static void main(String[] args) { SpringApplication.run(PetRoadApplication.class, args); }
}
