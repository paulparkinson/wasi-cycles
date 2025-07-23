package com.example.springbootkafkamongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class SpringbootKafkaMongodbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootKafkaMongodbApplication.class, args);
    }
}
