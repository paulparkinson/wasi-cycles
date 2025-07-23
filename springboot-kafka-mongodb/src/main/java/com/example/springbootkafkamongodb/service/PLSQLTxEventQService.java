package com.example.springbootkafkamongodb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for native Oracle TxEventQ operations using PLSQL procedures
 * Supports all combinations: OKafka ↔ PLSQL enqueue/dequeue operations
 */
@Service
public class PLSQLTxEventQService {

    @Value("${okafka.oracle.tns_admin}")
    private String tnsAdmin;

    @Value("${okafka.oracle.username}")
    private String username;

    @Value("${okafka.oracle.password}")
    private String password;

    @Value("${okafka.oracle.tns_alias}")
    private String tnsAlias;

    @Value("${app.kafka.topic.game-events}")
    private String gameEventsTopic;

    private static final String CONSUMER_GROUP = "springboot_plsql_cross_runtime_grp";

    /**
     * Create the TxEventQ topic with partition_assignment_mode = 1 for OKafka compatibility
     */
    public Map<String, Object> createTopic(String topicName, int partitionNum, int retentionDays) {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = getConnection()) {
            String sql = "BEGIN " +
                        "DBMS_AQADM.CREATE_DATABASE_KAFKA_TOPIC(" +
                        "topicname => ?, " +
                        "partition_num => ?, " +
                        "retentiontime => ?, " +
                        "partition_assignment_mode => 1); " +
                        "END;";
            
            try (CallableStatement stmt = conn.prepareCall(sql)) {
                stmt.setString(1, topicName);
                stmt.setInt(2, partitionNum);
                stmt.setInt(3, retentionDays * 24 * 3600); // Convert days to seconds
                stmt.execute();
                
                result.put("status", "success");
                result.put("message", "Topic created successfully with partition_assignment_mode=1");
                result.put("topic", topicName);
                result.put("partitions", partitionNum);
                result.put("retention_days", retentionDays);
                
            }
        } catch (SQLException e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("sql_code", e.getErrorCode());
        }
        
        return result;
    }

    /**
     * Create consumer group for PLSQL dequeue operations
     */
    public Map<String, Object> createConsumerGroup(String topicName, String consumerGroupName) {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = getConnection()) {
            String sql = "DECLARE " +
                        "subscriber sys.aq$_agent; " +
                        "BEGIN " +
                        "subscriber := sys.aq$_agent(?, null, 0); " +
                        "dbms_aqadm.add_subscriber(" +
                        "queue_name => ?, " +
                        "subscriber => subscriber); " +
                        "END;";
            
            try (CallableStatement stmt = conn.prepareCall(sql)) {
                stmt.setString(1, consumerGroupName);
                stmt.setString(2, topicName);
                stmt.execute();
                
                result.put("status", "success");
                result.put("message", "Consumer group created successfully");
                result.put("topic", topicName);
                result.put("consumer_group", consumerGroupName);
                
            }
        } catch (SQLException e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("sql_code", e.getErrorCode());
            
            // Consumer group might already exist
            if (e.getErrorCode() == 24033) {
                result.put("status", "success");
                result.put("message", "Consumer group already exists");
            }
        }
        
        return result;
    }

    /**
     * PLSQL Enqueue - Native Oracle TxEventQ enqueue using PLSQL procedures
     */
    public Map<String, Object> plsqlEnqueue(String topicName, int partition, String key, String value) {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = getConnection()) {
            // Create the PLSQL enqueue procedure if it doesn't exist
            createEnqueueProcedure(conn);
            
            String sql = "BEGIN enq_okafka_msg_v1(?, ?, ?, ?); END;";
            
            try (CallableStatement stmt = conn.prepareCall(sql)) {
                stmt.setString(1, topicName);
                stmt.setInt(2, partition);
                stmt.setString(3, key);
                stmt.setBytes(4, value.getBytes());
                stmt.execute();
                
                result.put("status", "success");
                result.put("message", "Message enqueued via PLSQL");
                result.put("topic", topicName);
                result.put("partition", partition);
                result.put("key", key);
                result.put("value_length", value.length());
                result.put("enqueue_method", "PLSQL");
                
            }
        } catch (SQLException e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("sql_code", e.getErrorCode());
        }
        
        return result;
    }

    /**
     * PLSQL Dequeue - Native Oracle TxEventQ dequeue using PLSQL procedures
     */
    public Map<String, Object> plsqlDequeue(String topicName, String consumerGroupName, int maxMessages) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        
        try (Connection conn = getConnection()) {
            // Create the PLSQL dequeue procedure if it doesn't exist
            createDequeueProcedure(conn);
            
            for (int i = 0; i < maxMessages; i++) {
                String sql = "DECLARE " +
                           "v_msgid RAW(16); " +
                           "v_key VARCHAR2(128); " +
                           "v_value RAW(32000); " +
                           "v_partition NUMBER; " +
                           "v_topic VARCHAR2(128) := ?; " +
                           "v_consumer_group VARCHAR2(128) := ?; " +
                           "BEGIN " +
                           "deq_okafka_msg_v1(v_topic, v_consumer_group, v_msgid, v_key, v_value, v_partition); " +
                           "? := v_msgid; " +
                           "? := v_key; " +
                           "? := v_value; " +
                           "? := v_partition; " +
                           "END;";
                
                try (CallableStatement stmt = conn.prepareCall(sql)) {
                    stmt.setString(1, topicName);
                    stmt.setString(2, consumerGroupName);
                    stmt.registerOutParameter(3, Types.VARBINARY);
                    stmt.registerOutParameter(4, Types.VARCHAR);
                    stmt.registerOutParameter(5, Types.VARBINARY);
                    stmt.registerOutParameter(6, Types.NUMERIC);
                    
                    stmt.execute();
                    
                    byte[] msgIdBytes = stmt.getBytes(3);
                    String key = stmt.getString(4);
                    byte[] valueBytes = stmt.getBytes(5);
                    int partition = stmt.getInt(6);
                    
                    if (msgIdBytes != null && valueBytes != null) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("msg_id", bytesToHex(msgIdBytes));
                        message.put("key", key);
                        message.put("value", new String(valueBytes));
                        message.put("partition", partition);
                        message.put("dequeue_method", "PLSQL");
                        message.put("consumed_by", "springboot-plsql");
                        message.put("topic", topicName);
                        message.put("consumer_group", consumerGroupName);
                        
                        messages.add(message);
                    } else {
                        // No more messages
                        break;
                    }
                    
                } catch (SQLException e) {
                    if (e.getErrorCode() == 25228) {
                        // No more messages available
                        break;
                    } else {
                        throw e;
                    }
                }
            }
            
            result.put("status", "success");
            result.put("message", "Messages dequeued via PLSQL");
            result.put("topic", topicName);
            result.put("consumer_group", consumerGroupName);
            result.put("messages", messages);
            result.put("count", messages.size());
            result.put("dequeue_method", "PLSQL");
            
        } catch (SQLException e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("sql_code", e.getErrorCode());
        }
        
        return result;
    }

    /**
     * Initialize topic and consumer group for cross-runtime messaging
     */
    public Map<String, Object> initializeCrossRuntimeMessaging() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> operations = new ArrayList<>();
        
        // Create topic
        Map<String, Object> topicResult = createTopic(gameEventsTopic, 5, 7);
        operations.add(topicResult);
        
        // Create consumer group
        Map<String, Object> consumerResult = createConsumerGroup(gameEventsTopic, CONSUMER_GROUP);
        operations.add(consumerResult);
        
        result.put("status", "success");
        result.put("message", "Cross-runtime messaging initialized");
        result.put("topic", gameEventsTopic);
        result.put("consumer_group", CONSUMER_GROUP);
        result.put("operations", operations);
        result.put("partition_assignment_mode", 1);
        
        return result;
    }

    private Connection getConnection() throws SQLException {
        System.setProperty("oracle.net.tns_admin", tnsAdmin);
        
        String url = "jdbc:oracle:thin:@" + tnsAlias;
        Connection conn = DriverManager.getConnection(url, username, password);
        conn.setAutoCommit(true);
        return conn;
    }

    private void createEnqueueProcedure(Connection conn) throws SQLException {
        String sql = "CREATE OR REPLACE PROCEDURE enq_okafka_msg_v1(" +
                    "topic IN VARCHAR2, " +
                    "partition IN NUMBER, " +
                    "key IN VARCHAR2, " +
                    "value IN RAW) AS " +
                    "msgprop dbms_aq.message_properties_t; " +
                    "okafkaMsg SYS.AQ$_JMS_BYTES_MESSAGE; " +
                    "enqopt dbms_aq.enqueue_options_t; " +
                    "enq_msgid RAW(16); " +
                    "BEGIN " +
                    "okafkaMsg := SYS.AQ$_JMS_BYTES_MESSAGE.CONSTRUCT(); " +
                    "msgprop := dbms_aq.message_properties_t(); " +
                    "msgprop.CORRELATION := key; " +
                    "okafkaMsg.set_bytes(value); " +
                    "okafkaMsg.set_Long_Property('AQINTERNAL_PARTITION', (partition*2)); " +
                    "dbms_aq.enqueue(topic, enqopt, msgprop, okafkaMsg, enq_msgid); " +
                    "END;";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    private void createDequeueProcedure(Connection conn) throws SQLException {
        String sql = "CREATE OR REPLACE PROCEDURE deq_okafka_msg_v1(" +
                    "topic IN VARCHAR2, " +
                    "con_group_name IN VARCHAR2, " +
                    "msgid OUT RAW, " +
                    "okafkaKey OUT VARCHAR2, " +
                    "okafkaValue OUT RAW, " +
                    "partition OUT NUMBER) AS " +
                    "deqopt dbms_aq.dequeue_options_t; " +
                    "msgprop dbms_aq.message_properties_t; " +
                    "okafkaMsg SYS.AQ$_JMS_BYTES_MESSAGE; " +
                    "BEGIN " +
                    "deqopt.consumer_name := con_group_name; " +
                    "deqopt.navigation := dbms_aq.first_message; " +
                    "deqopt.dequeue_mode := dbms_aq.remove; " +
                    "deqopt.wait := 3; " +  // Wait maximum 3 seconds for a message
                    "dbms_aq.dequeue(topic, deqopt, msgprop, okafkaMsg, msgid); " +
                    "okafkaMsg.get_bytes(okafkaValue); " +
                    "okafkaKey := msgprop.CORRELATION; " +
                    "partition := okafkaMsg.get_long_property('AQINTERNAL_PARTITION')/2; " +
                    "EXCEPTION " +
                    "WHEN OTHERS THEN " +
                    "IF SQLCODE = -25228 THEN " +  // ORA-25228: timeout in dequeue
                    "msgid := NULL; " +
                    "okafkaKey := NULL; " +
                    "okafkaValue := NULL; " +
                    "partition := NULL; " +
                    "ELSE " +
                    "RAISE; " +
                    "END IF; " +
                    "END;";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
