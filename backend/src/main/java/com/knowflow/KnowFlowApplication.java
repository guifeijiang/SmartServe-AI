package com.knowflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.knowflow.mapper")
public class KnowFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowFlowApplication.class, args);
    }
}
