package com.example.easybank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.mybatis.spring.annotation.MapperScan;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@MapperScan("com.example.easybank.repository")
public class EasyBankApplication {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("Easy Bank API Documentation")
                        .description("REST API documentation for Easy Bank transaction processing system")
                        .version("1.0.0"));
    }

    public static void main(String[] args) {
        SpringApplication.run(EasyBankApplication.class, args);
    }
}