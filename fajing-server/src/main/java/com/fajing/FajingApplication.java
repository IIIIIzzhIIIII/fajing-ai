package com.fajing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.fajing.mapper")
@EnableScheduling
@EnableAsync
public class FajingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FajingApplication.class, args);
    }
}
