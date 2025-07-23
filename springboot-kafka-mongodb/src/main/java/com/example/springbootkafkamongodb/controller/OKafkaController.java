package com.example.springbootkafkamongodb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/okafka")
public class OKafkaController {

    @Autowired
    private KafkaProducer<String, String> oracleKafkaProducer;

    @Autowired
    private KafkaConsumer<String, String> oracleKafkaConsumer;

    @Value("${app.kafka.topic.game-events}")
    private String gameEventsTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Simple in-memory store for consumed messages for testing
    private final List<Map<String, Object>> consumedMessages = new CopyOnWriteArrayList<>();

    /**
     * Health check endpoint compatible with WASM runtimes
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "🏭 WasiCycles Spring Boot OKafka Service is running!");
        response.put("runtime", "springboot-okafka");
        response.put("castle", "Spring Boot Factory");
        response.put("description", "Native Oracle Kafka integration for cross-runtime messaging");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/okafka/");
        endpoints.put("test_kafka", "/okafka/test-kafka (POST)");
        endpoints.put("consume_kafka", "/okafka/consume-kafka");
        endpoints.put("publish_test", "/okafka/publish-test (POST)");
        response.put("endpoints", endpoints);
        
        Map<String, String> integration = new HashMap<>();
        integration.put("okafka", "✅ Connected");
        integration.put("oracle_txeventq", "✅ Connected");
        integration.put("kafka_topic", gameEventsTopic);
        integration.put("consumer_group", "springboot_okafka_cross_runtime_grp");
        response.put("oracle_integration", integration);
        
        response.put("timestamp", Instant.now().getEpochSecond());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test Kafka connectivity - compatible with WASM runtime test endpoints
     */
    @PostMapping("/test-kafka")
    public ResponseEntity<Map<String, Object>> testKafka(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            String playerId = payload != null ? (String) payload.get("playerId") : "test-springboot-okafka";
            String message = payload != null ? (String) payload.get("message") : "default-test";
            
            // Create test event matching WASM runtime format
            Map<String, Object> testEvent = new HashMap<>();
            testEvent.put("type", "connectivity_test");
            testEvent.put("player_id", playerId + "_test_from_springboot_okafka");
            testEvent.put("game_id", "wasicycles-multiplayer");
            testEvent.put("runtime", "springboot-okafka");
            testEvent.put("castle", "Spring Boot Factory");
            testEvent.put("timestamp", System.currentTimeMillis());
            
            Map<String, Double> position = new HashMap<>();
            position.put("x", 0.0);
            position.put("y", 0.0);
            testEvent.put("position", position);
            
            testEvent.put("direction", "test");
            testEvent.put("score", 42);
            testEvent.put("message", message);
            
            String eventJson = objectMapper.writeValueAsString(testEvent);
            
            // Publish using native OKafka producer with transactions (following InventoryProducerService pattern)
            oracleKafkaProducer.beginTransaction();
            Connection conn = oracleKafkaProducer.getDBConnection();
            System.out.println("✅ OKafka producer connection: " + conn);
            
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(gameEventsTopic, playerId, eventJson);
            oracleKafkaProducer.send(producerRecord);
            System.out.println("✅ OKafka message sent: " + eventJson);
            
            oracleKafkaProducer.commitTransaction();
            System.out.println("✅ OKafka message committed");
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("message", "Oracle TxEventQ connectivity test successful via OKafka");
            response.put("test_event", testEvent);
            response.put("kafka_topic", gameEventsTopic);
            response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
            response.put("client_type", "OKafka Native Client");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Consume messages from Oracle TxEventQ using native OKafka consumer (following InventoryConsumerService pattern)
     */
    @GetMapping("/consume-kafka")
    public ResponseEntity<Map<String, Object>> consumeKafka() {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // Subscribe to the topic if not already subscribed
            oracleKafkaConsumer.subscribe(Collections.singletonList(gameEventsTopic));
            
            // Poll for messages with longer timeout to catch recent messages
            ConsumerRecords<String, String> records = oracleKafkaConsumer.poll(Duration.ofMillis(2000));
            
            if (records.count() > 0) {
                System.out.println("🔍 OKafka consumed records: " + records.count());
            }
            
            Connection conn = oracleKafkaConsumer.getDBConnection();
            System.out.println("🔗 OKafka consumer connection: " + conn);
            
            for (ConsumerRecord<String, String> record : records) {
                try {
                    System.out.println("📨 OKafka record value: " + record.value());
                    
                    // Parse the message value
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageData = objectMapper.readValue(record.value(), Map.class);
                    
                    Map<String, Object> processedRecord = new HashMap<>();
                    processedRecord.put("topic", record.topic());
                    processedRecord.put("partition", record.partition());
                    processedRecord.put("offset", record.offset());
                    processedRecord.put("key", record.key());
                    processedRecord.put("data", messageData);
                    processedRecord.put("consumed_by", "springboot-okafka");
                    processedRecord.put("consumed_at", Instant.now().getEpochSecond());
                    processedRecord.put("client_type", "OKafka Native Client");
                    
                    messages.add(processedRecord);
                    
                    System.out.println("✅ OKafka processed message from runtime: " + 
                                     messageData.getOrDefault("runtime", "unknown"));
                    
                } catch (Exception e) {
                    System.err.println("⚠️ Error parsing message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // With auto-commit enabled, offsets are committed automatically
            // oracleKafkaConsumer.commitSync();  // Not needed with auto-commit
            
            // Also add to in-memory store for compatibility
            messages.forEach(this::addConsumedMessage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("endpoint", "consume_kafka");
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("client_type", "OKafka Native Client");
            response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Simple publish endpoint for testing
     */
    @PostMapping("/publish-test")
    public ResponseEntity<Map<String, Object>> publishTest(@RequestBody Map<String, Object> payload) {
        try {
            String key = (String) payload.getOrDefault("key", "test-key");
            String message = (String) payload.getOrDefault("message", "test message from springboot-okafka");
            
            // Publish using native OKafka producer without transactions (for now)
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(gameEventsTopic, key, message);
            oracleKafkaProducer.send(producerRecord);
            System.out.println("✅ OKafka message sent: " + message);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("message", "Message published via OKafka");
            response.put("topic", gameEventsTopic);
            response.put("key", key);
            response.put("payload", message);
            response.put("client_type", "OKafka Native Client");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Debug endpoint: Force consume all messages from the beginning of the topic
     */
    @GetMapping("/consume-all-messages")
    public ResponseEntity<Map<String, Object>> consumeAllMessages() {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // Subscribe to the topic
            oracleKafkaConsumer.subscribe(Collections.singletonList(gameEventsTopic));
            
            // Force assignment and seek to beginning
            oracleKafkaConsumer.poll(Duration.ofMillis(500)); // Initial poll to trigger assignment
            oracleKafkaConsumer.seekToBeginning(oracleKafkaConsumer.assignment());
            
            // Poll for messages multiple times to get all available messages
            for (int i = 0; i < 5; i++) {
                ConsumerRecords<String, String> records = oracleKafkaConsumer.poll(Duration.ofMillis(1000));
                
                if (records.count() > 0) {
                    System.out.println("🔍 Debug: OKafka consumed records batch " + (i+1) + ": " + records.count());
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        System.out.println("📨 Debug: OKafka record value: " + record.value());
                        
                        Map<String, Object> processedRecord = new HashMap<>();
                        processedRecord.put("topic", record.topic());
                        processedRecord.put("partition", record.partition());
                        processedRecord.put("offset", record.offset());
                        processedRecord.put("key", record.key());
                        processedRecord.put("raw_value", record.value());
                        processedRecord.put("consumed_by", "springboot-okafka-debug");
                        processedRecord.put("consumed_at", Instant.now().getEpochSecond());
                        processedRecord.put("client_type", "OKafka Native Client");
                        
                        // Try to parse as JSON
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> messageData = objectMapper.readValue(record.value(), Map.class);
                            processedRecord.put("parsed_data", messageData);
                        } catch (Exception e) {
                            processedRecord.put("parse_error", e.getMessage());
                        }
                        
                        messages.add(processedRecord);
                        
                    } catch (Exception e) {
                        System.err.println("⚠️ Error processing record: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                if (records.count() == 0) {
                    break; // No more messages
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("endpoint", "consume_all_messages");
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("client_type", "OKafka Native Client - Debug Mode");
            response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
            response.put("note", "This endpoint seeks to beginning and polls multiple times");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Create proper Oracle Kafka topic using dbms_aqadm.create_database_kafka_topic
     * This should create a topic that both OKafka and TxEventQ REST API can use
     */
    @PostMapping("/create-kafka-topic")
    public ResponseEntity<Map<String, Object>> createKafkaTopic(@RequestBody Map<String, Object> payload) {
        try {
            String topicName = (String) payload.getOrDefault("topic_name", gameEventsTopic);
            
            // Get database connection from OKafka producer
            Connection conn = oracleKafkaProducer.getDBConnection();
            System.out.println("✅ Got database connection: " + conn);
            
            // Create Oracle Kafka topic using dbms_aqadm
            String createTopicSQL = "BEGIN " +
                "DBMS_AQADM.CREATE_DATABASE_KAFKA_TOPIC(" +
                "topic_name => ?, " +
                "partition_num => 1, " +
                "replication_factor => 1" +
                "); END;";
            
            try (PreparedStatement stmt = conn.prepareStatement(createTopicSQL)) {
                stmt.setString(1, topicName);
                stmt.execute();
                System.out.println("✅ Created Oracle Kafka topic: " + topicName);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("message", "Oracle Kafka topic created successfully");
            response.put("topic_name", topicName);
            response.put("sql_executed", createTopicSQL);
            response.put("note", "Topic should now be accessible by both OKafka and TxEventQ REST API");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("error_class", e.getClass().getSimpleName());
            response.put("note", "Failed to create Oracle Kafka topic");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            e.printStackTrace();
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * EXPERIMENTAL: Create a proper Oracle Kafka topic to test compatibility with TxEventQ REST API
     * This creates a NEW topic to avoid breaking existing WASICYCLES_GAME_EVENTS functionality
     */
    @PostMapping("/create-oracle-kafka-topic")
    public ResponseEntity<Map<String, Object>> createOracleKafkaTopic(@RequestBody Map<String, Object> payload) {
        String testTopicName = (String) payload.getOrDefault("topic_name", "WASICYCLES_TEST_KAFKA_TOPIC");
        
        try {
            // Get database connection from OKafka producer
            Connection conn = oracleKafkaProducer.getDBConnection();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "attempting");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("action", "create_oracle_kafka_topic");
            response.put("test_topic_name", testTopicName);
            response.put("note", "This is EXPERIMENTAL - testing compatibility between OKafka and TxEventQ REST API");
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "BEGIN " +
                "  DBMS_AQADM.CREATE_DATABASE_KAFKA_TOPIC(" +
                "    topic_name => ?," +
                "    partition_num => 3," +
                "    replication_factor => 1" +
                "  );" +
                "END;")) {
                
                stmt.setString(1, testTopicName);
                stmt.execute();
                
                response.put("status", "success");
                response.put("message", "Oracle Kafka topic created successfully");
                response.put("next_steps", "Now test if WASM runtimes can access this topic via TxEventQ REST API");
                
            } catch (SQLException e) {
                if (e.getMessage().contains("already exists") || e.getMessage().contains("ORA-24006")) {
                    response.put("status", "already_exists");
                    response.put("message", "Topic already exists - this is OK for testing");
                } else {
                    response.put("status", "sql_error");
                    response.put("error", e.getMessage());
                    response.put("sql_error_code", e.getErrorCode());
                }
            }
            
            response.put("timestamp", Instant.now().getEpochSecond());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * EXPERIMENTAL: Test consuming from the Oracle Kafka topic we created
     */
    @GetMapping("/test-oracle-kafka-consume")
    public ResponseEntity<Map<String, Object>> testOracleKafkaConsume(@RequestParam(defaultValue = "WASICYCLES_TEST_KAFKA_TOPIC") String topicName) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // Create a new consumer instance for this test topic to avoid affecting main consumer
            Properties testProps = new Properties();
            testProps.put("security.protocol", "SSL");
            testProps.put("oracle.net.tns_admin", "/Users/pparkins/Downloads/Wallet_financialdb");
            testProps.put("tns.alias", "financialdb_high");
            testProps.put("group.id", "test_kafka_topic_consumer_grp");
            testProps.put("enable.auto.commit", "true");
            testProps.put("auto.commit.interval.ms", "1000");
            testProps.put("max.poll.records", 100);
            testProps.put("auto.offset.reset", "earliest");
            testProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            testProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            
            try (KafkaConsumer<String, String> testConsumer = new KafkaConsumer<>(testProps)) {
                testConsumer.subscribe(Collections.singletonList(topicName));
                
                // Poll multiple times to get all messages
                for (int i = 0; i < 3; i++) {
                    ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        Map<String, Object> processedRecord = new HashMap<>();
                        processedRecord.put("topic", record.topic());
                        processedRecord.put("partition", record.partition());
                        processedRecord.put("offset", record.offset());
                        processedRecord.put("key", record.key());
                        processedRecord.put("raw_value", record.value());
                        processedRecord.put("consumed_by", "springboot-okafka-test");
                        processedRecord.put("consumed_at", Instant.now().getEpochSecond());
                        
                        messages.add(processedRecord);
                    }
                    
                    if (records.count() == 0) break;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("endpoint", "test_oracle_kafka_consume");
            response.put("test_topic", topicName);
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("note", "Testing Oracle Kafka topic compatibility");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Query TxEventQ table directly via SQL to inspect message broker contents
     */
    @GetMapping("/query-txeventq")
    public ResponseEntity<Map<String, Object>> queryTxEventQ(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();

            // Get database connection from the OKafka consumer
            Connection conn = oracleKafkaConsumer.getDBConnection();

            // Query the TxEventQ table directly
            String sql = """
                SELECT 
                    q_name,
                    msgid,
                    corrid,
                    priority,
                    state,
                    delay,
                    expiration,
                    enq_time,
                    enq_uid,
                    enq_tid,
                    deq_time,
                    deq_uid,
                    deq_tid,
                    retry_count,
                    exception_qschema,
                    exception_queue,
                    chain_no,
                    local_order_no,
                    time_manager_info,
                    step_no,
                    CAST(user_data AS VARCHAR2(4000)) as message_content
                FROM AQ$_WASICYCLES_GAME_EVENTS_S 
                ORDER BY enq_time DESC 
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, offset);
                stmt.setInt(2, limit);

                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("q_name", rs.getString("q_name"));
                        message.put("msgid", rs.getString("msgid"));
                        message.put("corrid", rs.getString("corrid"));
                        message.put("priority", rs.getInt("priority"));
                        message.put("state", rs.getInt("state"));
                        message.put("delay", rs.getTimestamp("delay"));
                        message.put("expiration", rs.getInt("expiration"));
                        message.put("enq_time", rs.getTimestamp("enq_time"));
                        message.put("enq_uid", rs.getString("enq_uid"));
                        message.put("enq_tid", rs.getString("enq_tid"));
                        message.put("deq_time", rs.getTimestamp("deq_time"));
                        message.put("deq_uid", rs.getString("deq_uid"));
                        message.put("deq_tid", rs.getString("deq_tid"));
                        message.put("retry_count", rs.getInt("retry_count"));
                        message.put("chain_no", rs.getInt("chain_no"));
                        message.put("local_order_no", rs.getInt("local_order_no"));
                        message.put("step_no", rs.getInt("step_no"));
                        message.put("message_content", rs.getString("message_content"));

                        messages.add(message);
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("endpoint", "query_txeventq");
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("limit", limit);
            response.put("offset", offset);
            response.put("table_queried", "AQ$_WASICYCLES_GAME_EVENTS_S");
            response.put("timestamp", Instant.now().getEpochSecond());

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", "SQL Error: " + e.getMessage());
            response.put("sqlstate", e.getSQLState());
            response.put("error_code", e.getErrorCode());
            response.put("timestamp", Instant.now().getEpochSecond());

            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("runtime", "springboot-okafka");
            response.put("castle", "Spring Boot Factory");
            response.put("error", "General Error: " + e.getMessage());
            response.put("timestamp", Instant.now().getEpochSecond());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Internal method to add consumed messages (called by consumer)
     */
    public void addConsumedMessage(Map<String, Object> message) {
        // Keep only last 50 messages to prevent memory issues
        if (consumedMessages.size() >= 50) {
            consumedMessages.remove(0);
        }
        consumedMessages.add(message);
    }
}
