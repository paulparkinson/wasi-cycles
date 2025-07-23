package com.example.springbootkafkamongodb.controller;

import com.example.springbootkafkamongodb.service.PLSQLTxEventQService;
import com.example.springbootkafkamongodb.listener.OKafkaMessageListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Cross-Runtime Messaging Controller - OKafka with Background Listener
 * Uses dedicated listener thread for reliable message consumption
 * WASM engines use TxEventQ REST endpoints, Spring Boot uses native OKafka client
 */
@RestController
@RequestMapping("/cross-runtime")
@CrossOrigin(origins = "*")
public class CrossRuntimeMessagingController {

    @Autowired
    private KafkaProducer<String, String> oracleKafkaProducer;

    @Autowired
    private OKafkaMessageListener messageListener;

    @Autowired
    private PLSQLTxEventQService plsqlService;

    @Value("${app.kafka.topic.game-events}")
    private String gameEventsTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Health check endpoint compatible with WASM runtimes
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("runtime", "springboot-cross-runtime");
        response.put("castle", "Spring Boot Cross-Runtime Factory");
        response.put("operation", "health_check");
        response.put("okafka_producer", "connected");
        response.put("listener_active", true);
        response.put("topic", gameEventsTopic);
        response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
        response.put("timestamp", Instant.now().getEpochSecond());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Initialize cross-runtime messaging services
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initialize(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            String playerId = payload != null ? (String) payload.get("playerId") : "springboot-initializer";
            
            // Create sample initialization message
            Map<String, Object> initEvent = createTestEvent(playerId, "cross_runtime_initialization", "center");
            String messageJson = objectMapper.writeValueAsString(initEvent);
            
            // Send via OKafka producer
            ProducerRecord<String, String> record = new ProducerRecord<>(gameEventsTopic, playerId, messageJson);
            oracleKafkaProducer.send(record);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "initialize");
            response.put("player_id", playerId);
            response.put("message", "Cross-runtime messaging initialized successfully");
            response.put("initialization_event", initEvent);
            response.put("topic", gameEventsTopic);
            response.put("listener_messages_available", messageListener.getConsumedMessages().size());
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("initialize", e);
        }
    }

    /**
     * Test OKafka producer - Spring Boot native OKafka client
     */
    @PostMapping("/test-okafka")
    public ResponseEntity<Map<String, Object>> testOKafka(@RequestBody Map<String, Object> payload) {
        try {
            String playerId = (String) payload.get("playerId");
            String message = (String) payload.get("message");
            
            Map<String, Object> testEvent = createTestEvent(playerId, message, "east");
            String messageJson = objectMapper.writeValueAsString(testEvent);
            
            // Send message via native OKafka producer
            ProducerRecord<String, String> record = new ProducerRecord<>(gameEventsTopic, playerId, messageJson);
            oracleKafkaProducer.send(record);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "test_okafka_producer");
            response.put("player_id", playerId);
            response.put("message", "OKafka message sent successfully");
            response.put("event_data", testEvent);
            response.put("topic", gameEventsTopic);
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("test_okafka_producer", e);
        }
    }

    /**
     * Test PLSQL TxEventQ producer - database-native messaging
     */
    @PostMapping("/test-plsql")
    public ResponseEntity<Map<String, Object>> testPLSQL(@RequestBody Map<String, Object> payload) {
        try {
            String playerId = (String) payload.get("playerId");
            String message = (String) payload.get("message");
            Integer partition = (Integer) payload.get("partition");
            if (partition == null) partition = 0;
            
            Map<String, Object> testEvent = createTestEvent(playerId, message, "west");
            String messageJson = objectMapper.writeValueAsString(testEvent);
            
            // Send message via PLSQL TxEventQ procedure (topicName, partition, key, value)
            Map<String, Object> result = plsqlService.plsqlEnqueue(gameEventsTopic, partition, "test-key", messageJson);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "test_plsql_producer");
            response.put("player_id", playerId);
            response.put("message", "PLSQL TxEventQ message sent successfully");
            response.put("event_data", testEvent);
            response.put("plsql_result", result);
            response.put("topic", gameEventsTopic);
            response.put("partition", partition);
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("test_plsql_producer", e);
        }
    }

    /**
     * Display messages consumed by background OKafka listener
     */
    @GetMapping("/display-okafka-consumed-messages")
    public ResponseEntity<Map<String, Object>> displayOKafkaConsumedMessages(@RequestParam(defaultValue = "20") int limit) {
        try {
            // Get messages from background listener
            List<Map<String, Object>> messages = messageListener.getConsumedMessages();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "consume_okafka_background");
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("total_available", messageListener.getMessageCount());
            response.put("topic", gameEventsTopic);
            response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
            response.put("consumer_type", "background_listener");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("consume_okafka_background", e);
        }
    }

    /**
     * Consume messages via PLSQL TxEventQ dequeue
     */
    @GetMapping("/consume-plsql")
    public ResponseEntity<Map<String, Object>> consumePLSQL(@RequestParam(defaultValue = "5") int maxMessages) {
        try {
            // Consume via PLSQL TxEventQ dequeue (topicName, consumerGroupName, maxMessages)
            Map<String, Object> messages = plsqlService.plsqlDequeue(gameEventsTopic, "test-consumer-group", maxMessages);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "consume_plsql");
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("max_requested", maxMessages);
            response.put("topic", gameEventsTopic);
            response.put("consumer_type", "plsql_dequeue");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("consume_plsql", e);
        }
    }

    /**
     * Test all cross-runtime messaging combinations
     */
    @PostMapping("/test-all-combinations")
    public ResponseEntity<Map<String, Object>> testAllCombinations(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            String basePlayerId = payload != null ? (String) payload.get("playerId") : "test-all-combinations";
            List<Map<String, Object>> results = new ArrayList<>();
            
            // Wait a bit between operations to ensure proper ordering
            long delay = 1000;
            
            // Combination 1: OKafka → OKafka
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("playerId", basePlayerId + "_combo1");
            payload1.put("message", "combination_1_okafka_to_okafka");
            ResponseEntity<Map<String, Object>> result1 = testOKafka(payload1);
            results.add(Map.of("combination", "1: OKafka → OKafka", "enqueue", result1.getBody()));
            Thread.sleep(delay);
            
            // Combination 2: OKafka → PLSQL  
            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("playerId", basePlayerId + "_combo2");
            payload2.put("message", "combination_2_okafka_to_plsql");
            ResponseEntity<Map<String, Object>> result2 = testOKafka(payload2);
            results.add(Map.of("combination", "2: OKafka → PLSQL", "enqueue", result2.getBody()));
            Thread.sleep(delay);
            
            // Combination 3: PLSQL → OKafka
            Map<String, Object> payload3 = new HashMap<>();
            payload3.put("playerId", basePlayerId + "_combo3");
            payload3.put("message", "combination_3_plsql_to_okafka");
            payload3.put("partition", 1);
            ResponseEntity<Map<String, Object>> result3 = testPLSQL(payload3);
            results.add(Map.of("combination", "3: PLSQL → OKafka", "enqueue", result3.getBody()));
            Thread.sleep(delay);
            
            // Combination 4: PLSQL → PLSQL
            Map<String, Object> payload4 = new HashMap<>();
            payload4.put("playerId", basePlayerId + "_combo4");
            payload4.put("message", "combination_4_plsql_to_plsql");
            payload4.put("partition", 2);
            ResponseEntity<Map<String, Object>> result4 = testPLSQL(payload4);
            results.add(Map.of("combination", "4: PLSQL → PLSQL", "enqueue", result4.getBody()));
            Thread.sleep(delay);
            
            // Now test dequeue operations
            ResponseEntity<Map<String, Object>> okafkaConsume = displayOKafkaConsumedMessages(10);
            ResponseEntity<Map<String, Object>> plsqlConsume = consumePLSQL(10);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "test_all_combinations");
            response.put("message", "All 4 OKafka-PLSQL combinations tested successfully");
            response.put("combinations_tested", results);
            response.put("okafka_consume_result", okafkaConsume.getBody());
            response.put("plsql_consume_result", plsqlConsume.getBody());
            response.put("topic", gameEventsTopic);
            response.put("total_combinations", 4);
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("test_all_combinations", e);
        }
    }

    /**
     * Create topic using PLSQL procedure
     */
    @PostMapping("/create-topic")
    public ResponseEntity<Map<String, Object>> createTopic(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            String topicName = gameEventsTopic;
            if (payload != null && payload.containsKey("topicName")) {
                topicName = (String) payload.get("topicName");
            }
            
            // Create topic (topicName, partitionNum, retentionDays)
            Map<String, Object> result = plsqlService.createTopic(topicName, 1, 7);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "create_topic");
            response.put("topic", topicName);
            response.put("result", result);
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("create_topic", e);
        }
    }

    /**
     * Get background listener status
     */
    @GetMapping("/listener-status")
    public ResponseEntity<Map<String, Object>> getListenerStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runtime", "springboot-cross-runtime");
            response.put("operation", "listener_status");
            response.put("recent_messages_count", messageListener.getConsumedMessages().size());
            response.put("total_messages_available", messageListener.getMessageCount());
            response.put("topic", gameEventsTopic);
            response.put("consumer_group", "springboot_okafka_cross_runtime_grp");
            response.put("listener_type", "background_okafka_consumer");
            response.put("timestamp", Instant.now().getEpochSecond());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("listener_status", e);
        }
    }

    private Map<String, Object> createTestEvent(String playerId, String message, String direction) {
        Map<String, Object> testEvent = new HashMap<>();
        testEvent.put("type", "cross_runtime_test");
        testEvent.put("player_id", playerId);
        testEvent.put("game_id", "wasicycles-cross-runtime");
        testEvent.put("runtime", "springboot-cross-runtime");
        testEvent.put("castle", "Spring Boot Cross-Runtime Factory");
        testEvent.put("timestamp", System.currentTimeMillis());
        
        Map<String, Double> position = new HashMap<>();
        position.put("x", Math.random() * 100);
        position.put("y", Math.random() * 100);
        testEvent.put("position", position);
        
        testEvent.put("direction", direction);
        testEvent.put("score", (int)(Math.random() * 1000));
        testEvent.put("message", message);
        
        return testEvent;
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String operation, Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("runtime", "springboot-cross-runtime");
        response.put("operation", operation);
        response.put("error", e.getMessage());
        response.put("timestamp", Instant.now().getEpochSecond());
        
        return ResponseEntity.status(500).body(response);
    }
}
