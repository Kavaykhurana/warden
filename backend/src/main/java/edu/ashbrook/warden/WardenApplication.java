package edu.ashbrook.warden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WardenApplication {

    public static void main(String[] args) {
        SpringApplication.run(WardenApplication.class, args);
    }
}
