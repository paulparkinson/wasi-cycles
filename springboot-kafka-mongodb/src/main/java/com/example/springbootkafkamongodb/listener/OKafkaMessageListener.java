package com.example.springbootkafkamongodb.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * OKafka Message Listener - Based on working InventoryConsumerService pattern
 * Uses manual thread management and direct KafkaConsumer polling for reliable message handling
 */
@Service
public class OKafkaMessageListener implements AutoCloseable {

    @Autowired
    private KafkaConsumer<String, String> oracleKafkaConsumer;

    @Value("${app.kafka.topic.game-events}")
    private String gameEventsTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean running = true;
    private Thread consumerThread;

    // Store consumed messages for REST endpoint access
    private final List<Map<String, Object>> consumedMessages = new ArrayList<>();

    @PostConstruct
    public void startConsumerThread() {
        consumerThread = new Thread(this::runConsumer);
        consumerThread.setName("OKafkaMessageListenerThread");
        consumerThread.setDaemon(true);  // Allow JVM to exit
        consumerThread.start();
        System.out.println("🚀 OKafka Message Listener started for topic: " + gameEventsTopic);
    }

    public void runConsumer() {
        System.out.println("📡 OKafkaMessageListener starting consumer loop...");
        
        try {
            // Subscribe to the game events topic
            oracleKafkaConsumer.subscribe(Collections.singletonList(gameEventsTopic));
            
            while (running) {
                try {
                    // Poll for messages with short timeout to allow thread shutdown
                    ConsumerRecords<String, String> records = oracleKafkaConsumer.poll(Duration.ofMillis(1000));
                    
                    if (records.count() > 0) {
                        System.out.println("📨 Received " + records.count() + " messages from topic: " + gameEventsTopic);
                        
                        // Process each message
                        for (ConsumerRecord<String, String> record : records) {
                            processMessage(record);
                        }
                        
                        //Optionally any type of database work (including AI, spatial, graph, SQL, JSON, etc.)
                        //can be done in the same transaction, atomically! Simply by getting the connection this way...
                        //Connection jdbcConnection = consumer.getDBConnection();
                        
                        // Manual commit after processing all messages
                        oracleKafkaConsumer.commitSync();
                        System.out.println("✅ Committed " + records.count() + " messages");
                    }
                    
                } catch (Exception e) {
                    System.err.println("❌ Error processing messages: " + e.getMessage());
                    e.printStackTrace();
                    // Continue running even on errors
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Fatal error in consumer loop: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("🛑 OKafka consumer loop stopped");
        }
    }

    private void processMessage(ConsumerRecord<String, String> record) {
        try {
            // Extract message type from headers (if present)
            String messageType = extractMessageType(record);
            
            // Parse message first to extract runtime info
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(record.value(), Map.class);
            String runtime = (String) messageData.get("runtime");
            String playerIdOrMessage = (String) messageData.get("player_id");
            if (playerIdOrMessage == null) {
                playerIdOrMessage = (String) messageData.get("message");
            }
            
            // Enhanced message log with runtime and content preview
            System.out.println("📋 Message from " + (runtime != null ? runtime : "unknown") + 
                             " [" + record.topic() + ":" + record.partition() + ":" + record.offset() + "]" +
                             (playerIdOrMessage != null ? " - " + playerIdOrMessage : ""));

            // Create processed message record
            Map<String, Object> processedMessage = new HashMap<>();
            processedMessage.put("topic", record.topic());
            processedMessage.put("partition", record.partition());
            processedMessage.put("offset", record.offset());
            processedMessage.put("key", record.key());
            processedMessage.put("messageType", messageType);
            processedMessage.put("data", messageData);
            processedMessage.put("processedAt", Instant.now().getEpochSecond());
            processedMessage.put("consumerGroup", "springboot_okafka_cross_runtime_grp");

            // Determine message combination based on source
            if (runtime != null) {
                if ("springboot-cross-runtime".equals(runtime)) {
                    processedMessage.put("combination", "Spring Boot OKafka → Spring Boot OKafka");
                } else {
                    processedMessage.put("combination", "WASM TxEventQ REST → Spring Boot OKafka");
                }
            }

            // Store for REST endpoint access (keep last 100 messages)
            synchronized (consumedMessages) {
                consumedMessages.add(processedMessage);
                if (consumedMessages.size() > 100) {
                    consumedMessages.remove(0);
                }
            }

            // Process different message types
            processGameEvent(messageData, runtime);

        } catch (Exception e) {
            System.err.println("⚠️ Error parsing message: " + e.getMessage());
            // Don't throw - continue processing other messages
        }
    }

    private String extractMessageType(ConsumerRecord<String, String> record) {
        if (record.headers() != null) {
            Header typeHeader = record.headers().lastHeader("type");
            if (typeHeader != null) {
                return new String(typeHeader.value(), StandardCharsets.UTF_8);
            }
        }
        return "unknown";
    }

    private void processGameEvent(Map<String, Object> messageData, String runtime) {
        // Process game events based on type
        String eventType = (String) messageData.get("event_type");
        if (eventType != null) {
            switch (eventType) {
                case "player_move":
                    System.out.println("🎮 Player move from runtime: " + runtime);
                    // Update game state, detect collisions, etc.
                    break;
                case "player_crash":
                    System.out.println("💥 Player crash from runtime: " + runtime);
                    // Handle game over logic
                    break;
                case "game_start":
                    System.out.println("🏁 Game start from runtime: " + runtime);
                    // Initialize game session
                    break;
                case "game_end":
                    System.out.println("🏆 Game end from runtime: " + runtime);
                    // Update leaderboard, determine winner
                    break;
                default:
                    System.out.println("📦 Generic message from runtime: " + runtime);
                    break;
            }
        }
    }

    // Public method to get consumed messages for REST endpoints
    public List<Map<String, Object>> getConsumedMessages() {
        synchronized (consumedMessages) {
            return new ArrayList<>(consumedMessages);
        }
    }

    // Public method to get message count
    public int getMessageCount() {
        synchronized (consumedMessages) {
            return consumedMessages.size();
        }
    }

    @Override
    public void close() {
        System.out.println("🛑 Shutting down OKafka Message Listener...");
        running = false;
        
        if (consumerThread != null && consumerThread.isAlive()) {
            try {
                consumerThread.join(5000);  // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (oracleKafkaConsumer != null) {
            oracleKafkaConsumer.close();
        }
        
        System.out.println("✅ OKafka Message Listener shut down complete");
    }
}