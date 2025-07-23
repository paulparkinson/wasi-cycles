package com.example.springbootkafkamongodb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;

@Configuration
@EnableMongoRepositories(basePackages = "com.example.springbootkafkamongodb.repository")
public class MongoConfig extends AbstractMongoClientConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);
    
    @Value("${mongodb.oracle.uri:mongodb://admin:mypassword@IMINELPE-MYDB.adb.eu-frankfurt-1.oraclecloudapps.com:27017/admin?authMechanism=PLAIN&authSource=$external&ssl=true&retryWrites=false&loadBalanced=true}")
    private String mongoUri;
    
    @Override
    @NonNull
    protected String getDatabaseName() {
        logger.info("MongoDB Database Name: admin");
        return "admin";
    }
    
    @Override
    @Bean
    public MongoClient mongoClient() {
        logger.info("Creating MongoDB client with URI: {}", maskPassword(mongoUri));
        
        try {
            // Parse the connection string
            ConnectionString connectionString = new ConnectionString(mongoUri);
            
            // Build client settings with explicit SSL configuration
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();
            
            logger.info("MongoDB client settings: {}", settings);
            
            MongoClient client = MongoClients.create(settings);
            logger.info("MongoDB client created successfully");
            
            // Test the connection
            try {
                client.getDatabase("admin").listCollectionNames().first();
                logger.info("✅ MongoDB connection test successful");
            } catch (Exception e) {
                logger.warn("⚠️ MongoDB connection test failed, but client created: {}", e.getMessage());
            }
            
            return client;
            
        } catch (Exception e) {
            logger.error("❌ Failed to create MongoDB client", e);
            throw new RuntimeException("Failed to create MongoDB client", e);
        }
    }
    
    @Bean
    @NonNull
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(java.util.List.of());
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Mask password in URI for logging
     */
    private String maskPassword(String uri) {
        return uri.replaceAll(":[^:@]*@", ":****@");
    }
}
