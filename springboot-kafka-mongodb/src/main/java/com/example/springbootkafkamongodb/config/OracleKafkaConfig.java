package com.example.springbootkafkamongodb.config;

import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class OracleKafkaConfig {

    @Value("${okafka.oracle.tns_admin}")
    private String tnsAdmin;

    @Value("${okafka.oracle.tns_alias}")
    private String tnsAlias;

    @Bean
    public KafkaProducer<String, String> oracleKafkaProducer() {
        Properties properties = new Properties();
        properties.put("security.protocol", "SSL");
        // Dynamic location containing Oracle Wallet, tnsname.ora and ojdbc.properties file
        properties.put("oracle.net.tns_admin", tnsAdmin);
        properties.put("tns.alias", tnsAlias);
        properties.put("enable.idempotence", "true");
        // Enable transactional producer for proper OKafka message commitment
        properties.put("oracle.transactional.producer", "true");
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
        // Initialize transactions for transactional producer
        producer.initTransactions();
        return producer;
    }

    @Bean
    public KafkaConsumer<String, String> oracleKafkaConsumer() {
        Properties properties = new Properties();
        properties.put("security.protocol", "SSL");
        // Dynamic location containing Oracle Wallet, tnsname.ora and ojdbc.properties file
        properties.put("oracle.net.tns_admin", tnsAdmin);
        properties.put("tns.alias", tnsAlias);
        properties.put("group.id", "springboot_okafka_cross_runtime_grp");
        
        // Critical: Manual commit control for proper message handling
        properties.put("enable.auto.commit", "false");  // Manual commit for reliable processing
        properties.put("max.poll.records", 2000);
        properties.put("auto.offset.reset", "earliest");  // Always start from earliest to catch all messages
        properties.put("isolation.level", "read_committed");  // Read only committed transactional messages
        
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        
        return new KafkaConsumer<>(properties);
    }
}
