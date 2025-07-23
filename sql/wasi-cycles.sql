-- =============================================
-- WasiCycles Game - Complete Oracle TxEventQ Setup
-- Cross-Runtime Messaging with OKafka Integration
-- =============================================

-- Set up session for better error handling
SET SERVEROUTPUT ON;
SET ECHO ON;

-- =============================================
-- 1. CREATE TOPIC WITH OKAFKA COMPATIBILITY
-- =============================================

DECLARE
    topic_exists NUMBER;
BEGIN
    -- Check if topic already exists
    SELECT COUNT(*) INTO topic_exists 
    FROM user_queues 
    WHERE name = 'WASI_CROSS_RUNTIME_TOPIC';
    
    IF topic_exists = 0 THEN
        -- Create TxEventQ topic with partition_assignment_mode = 1 for OKafka compatibility
        DBMS_AQADM.CREATE_DATABASE_KAFKA_TOPIC(
            topicname => 'WASI_CROSS_RUNTIME_TOPIC',
            partition_num => 5,                    -- 5 partitions for load distribution
            retentiontime => 604800,              -- 7 days retention (7 * 24 * 3600 seconds)
            partition_assignment_mode => 1        -- Required for OKafka compatibility
        );
        
        DBMS_OUTPUT.PUT_LINE('✅ Topic WASI_CROSS_RUNTIME_TOPIC created successfully');
        DBMS_OUTPUT.PUT_LINE('   - Partitions: 5');
        DBMS_OUTPUT.PUT_LINE('   - Retention: 7 days');
        DBMS_OUTPUT.PUT_LINE('   - OKafka Compatible: YES (partition_assignment_mode=1)');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Topic WASI_CROSS_RUNTIME_TOPIC already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('❌ Error creating topic: ' || SQLERRM);
        RAISE;
END;
/

-- =============================================
-- 2. CREATE CONSUMER GROUPS
-- =============================================

-- Spring Boot OKafka Consumer Group
DECLARE
    subscriber sys.aq$_agent;
    group_exists NUMBER;
BEGIN
    -- Check if subscriber already exists
    SELECT COUNT(*) INTO group_exists
    FROM user_aq_agents
    WHERE name = 'springboot_okafka_cross_runtime_grp';
    
    IF group_exists = 0 THEN
        subscriber := sys.aq$_agent('springboot_okafka_cross_runtime_grp', null, 0);
        dbms_aqadm.add_subscriber(
            queue_name => 'WASI_CROSS_RUNTIME_TOPIC',
            subscriber => subscriber
        );
        DBMS_OUTPUT.PUT_LINE('✅ Consumer group: springboot_okafka_cross_runtime_grp created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: springboot_okafka_cross_runtime_grp already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -24033 THEN
            DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: springboot_okafka_cross_runtime_grp already exists');
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error creating OKafka consumer group: ' || SQLERRM);
            RAISE;
        END IF;
END;
/

-- Spring Boot PLSQL Consumer Group
DECLARE
    subscriber sys.aq$_agent;
    group_exists NUMBER;
BEGIN
    -- Check if subscriber already exists
    SELECT COUNT(*) INTO group_exists
    FROM user_aq_agents
    WHERE name = 'springboot_plsql_cross_runtime_grp';
    
    IF group_exists = 0 THEN
        subscriber := sys.aq$_agent('springboot_plsql_cross_runtime_grp', null, 0);
        dbms_aqadm.add_subscriber(
            queue_name => 'WASI_CROSS_RUNTIME_TOPIC',
            subscriber => subscriber
        );
        DBMS_OUTPUT.PUT_LINE('✅ Consumer group: springboot_plsql_cross_runtime_grp created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: springboot_plsql_cross_runtime_grp already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -24033 THEN
            DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: springboot_plsql_cross_runtime_grp already exists');
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error creating PLSQL consumer group: ' || SQLERRM);
            RAISE;
        END IF;
END;
/

-- WASM Runtime Consumer Groups (for WASM engines)
DECLARE
    subscriber sys.aq$_agent;
    group_exists NUMBER;
BEGIN
    -- WASMEdge Consumer Group
    SELECT COUNT(*) INTO group_exists
    FROM user_aq_agents
    WHERE name = 'wasmedge_cross_runtime_grp';
    
    IF group_exists = 0 THEN
        subscriber := sys.aq$_agent('wasmedge_cross_runtime_grp', null, 0);
        dbms_aqadm.add_subscriber(
            queue_name => 'WASI_CROSS_RUNTIME_TOPIC',
            subscriber => subscriber
        );
        DBMS_OUTPUT.PUT_LINE('✅ Consumer group: wasmedge_cross_runtime_grp created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmedge_cross_runtime_grp already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -24033 THEN
            DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmedge_cross_runtime_grp already exists');
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error creating WASMEdge consumer group: ' || SQLERRM);
        END IF;
END;
/

DECLARE
    subscriber sys.aq$_agent;
    group_exists NUMBER;
BEGIN
    -- Wasmer Consumer Group
    SELECT COUNT(*) INTO group_exists
    FROM user_aq_agents
    WHERE name = 'wasmer_cross_runtime_grp';
    
    IF group_exists = 0 THEN
        subscriber := sys.aq$_agent('wasmer_cross_runtime_grp', null, 0);
        dbms_aqadm.add_subscriber(
            queue_name => 'WASI_CROSS_RUNTIME_TOPIC',
            subscriber => subscriber
        );
        DBMS_OUTPUT.PUT_LINE('✅ Consumer group: wasmer_cross_runtime_grp created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmer_cross_runtime_grp already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -24033 THEN
            DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmer_cross_runtime_grp already exists');
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error creating Wasmer consumer group: ' || SQLERRM);
        END IF;
END;
/

DECLARE
    subscriber sys.aq$_agent;
    group_exists NUMBER;
BEGIN
    -- Wasmtime Consumer Group  
    SELECT COUNT(*) INTO group_exists
    FROM user_aq_agents
    WHERE name = 'wasmtime_cross_runtime_grp';
    
    IF group_exists = 0 THEN
        subscriber := sys.aq$_agent('wasmtime_cross_runtime_grp', null, 0);
        dbms_aqadm.add_subscriber(
            queue_name => 'WASI_CROSS_RUNTIME_TOPIC',
            subscriber => subscriber
        );
        DBMS_OUTPUT.PUT_LINE('✅ Consumer group: wasmtime_cross_runtime_grp created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmtime_cross_runtime_grp already exists');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -24033 THEN
            DBMS_OUTPUT.PUT_LINE('ℹ️  Consumer group: wasmtime_cross_runtime_grp already exists');
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error creating Wasmtime consumer group: ' || SQLERRM);
        END IF;
END;
/

-- =============================================
-- 3. CREATE PLSQL ENQUEUE PROCEDURE
-- =============================================

CREATE OR REPLACE PROCEDURE enq_okafka_msg_v1(
    topic IN VARCHAR2,
    partition IN NUMBER,
    key IN VARCHAR2,
    value IN RAW
) AS
    msgprop dbms_aq.message_properties_t;
    okafkaMsg SYS.AQ$_JMS_BYTES_MESSAGE;
    enqopt dbms_aq.enqueue_options_t;
    enq_msgid RAW(16);
BEGIN
    -- Create message object
    okafkaMsg := SYS.AQ$_JMS_BYTES_MESSAGE.CONSTRUCT();
    
    -- Set message properties
    msgprop := dbms_aq.message_properties_t();
    msgprop.CORRELATION := key;
    
    -- Set message content
    okafkaMsg.set_bytes(value);
    
    -- Set partition (multiply by 2 for internal Oracle mapping)
    okafkaMsg.set_Long_Property('AQINTERNAL_PARTITION', (partition * 2));
    
    -- Enqueue message
    dbms_aq.enqueue(topic, enqopt, msgprop, okafkaMsg, enq_msgid);
    
    DBMS_OUTPUT.PUT_LINE('✅ Message enqueued to topic: ' || topic || ', partition: ' || partition || ', key: ' || key);
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('❌ Error in enq_okafka_msg_v1: ' || SQLERRM);
        RAISE;
END enq_okafka_msg_v1;
/

-- =============================================
-- 4. CREATE PLSQL DEQUEUE PROCEDURE  
-- =============================================

CREATE OR REPLACE PROCEDURE deq_okafka_msg_v1(
    topic IN VARCHAR2,
    con_group_name IN VARCHAR2,
    msgid OUT RAW,
    okafkaKey OUT VARCHAR2,
    okafkaValue OUT RAW,
    partition OUT NUMBER
) AS
    deqopt dbms_aq.dequeue_options_t;
    msgprop dbms_aq.message_properties_t;
    okafkaMsg SYS.AQ$_JMS_BYTES_MESSAGE;
BEGIN
    -- Set dequeue options
    deqopt.consumer_name := con_group_name;
    deqopt.navigation := dbms_aq.first_message;
    deqopt.dequeue_mode := dbms_aq.remove;
    deqopt.wait := 3;  -- Wait maximum 3 seconds for a message
    
    -- Dequeue message
    dbms_aq.dequeue(topic, deqopt, msgprop, okafkaMsg, msgid);
    
    -- Extract message data
    okafkaMsg.get_bytes(okafkaValue);
    okafkaKey := msgprop.CORRELATION;
    partition := okafkaMsg.get_long_property('AQINTERNAL_PARTITION') / 2;
    
    DBMS_OUTPUT.PUT_LINE('✅ Message dequeued from topic: ' || topic || ', consumer group: ' || con_group_name || ', key: ' || okafkaKey);
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -25228 THEN  -- ORA-25228: timeout in dequeue
            -- No message available - return NULL values
            msgid := NULL;
            okafkaKey := NULL;
            okafkaValue := NULL;
            partition := NULL;
        ELSE
            DBMS_OUTPUT.PUT_LINE('❌ Error in deq_okafka_msg_v1: ' || SQLERRM);
            RAISE;
        END IF;
END deq_okafka_msg_v1;
/

-- =============================================
-- 5. GRANT NECESSARY PERMISSIONS
-- =============================================

-- Grant execute permissions on procedures (if needed for other users)
-- GRANT EXECUTE ON enq_okafka_msg_v1 TO your_app_user;
-- GRANT EXECUTE ON deq_okafka_msg_v1 TO your_app_user;

-- Grant AQ permissions (if needed)
-- GRANT aq_administrator_role TO your_app_user;
-- GRANT EXECUTE ON dbms_aq TO your_app_user;
-- GRANT EXECUTE ON dbms_aqadm TO your_app_user;

-- =============================================
-- 6. VERIFY SETUP
-- =============================================

-- Check topic creation
SELECT name as topic_name, 
       queue_table,
       queue_type,
       max_retries,
       retention_time
FROM user_queues 
WHERE name = 'WASI_CROSS_RUNTIME_TOPIC';

-- Check consumer groups
SELECT name as consumer_group,
       address,
       protocol
FROM user_aq_agents
WHERE name LIKE '%cross_runtime_grp'
ORDER BY name;

-- Check procedures
SELECT object_name,
       object_type,
       status,
       created,
       last_ddl_time
FROM user_objects
WHERE object_name IN ('ENQ_OKAFKA_MSG_V1', 'DEQ_OKAFKA_MSG_V1')
ORDER BY object_name;

-- =============================================
-- 7. TEST MESSAGE FLOW (OPTIONAL)
-- =============================================

/*
-- Test PLSQL enqueue
DECLARE
    test_key VARCHAR2(100) := 'test-key-' || TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISS');
    test_value RAW(2000);
BEGIN
    test_value := UTL_RAW.CAST_TO_RAW('{"type": "test", "message": "Hello from PLSQL!", "timestamp": "' || SYSTIMESTAMP || '"}');
    
    enq_okafka_msg_v1(
        topic => 'WASI_CROSS_RUNTIME_TOPIC',
        partition => 0,
        key => test_key,
        value => test_value
    );
    
    DBMS_OUTPUT.PUT_LINE('Test message enqueued with key: ' || test_key);
END;
/

-- Test PLSQL dequeue
DECLARE
    v_msgid RAW(16);
    v_key VARCHAR2(128);
    v_value RAW(32000);
    v_partition NUMBER;
BEGIN
    deq_okafka_msg_v1(
        topic => 'WASI_CROSS_RUNTIME_TOPIC',
        con_group_name => 'springboot_plsql_cross_runtime_grp',
        msgid => v_msgid,
        okafkaKey => v_key,
        okafkaValue => v_value,
        partition => v_partition
    );
    
    IF v_msgid IS NOT NULL THEN
        DBMS_OUTPUT.PUT_LINE('Test message dequeued:');
        DBMS_OUTPUT.PUT_LINE('  Key: ' || v_key);
        DBMS_OUTPUT.PUT_LINE('  Partition: ' || v_partition);
        DBMS_OUTPUT.PUT_LINE('  Value: ' || UTL_RAW.CAST_TO_VARCHAR2(v_value));
    ELSE
        DBMS_OUTPUT.PUT_LINE('No messages available for dequeue');
    END IF;
END;
/
*/

-- =============================================
-- SETUP COMPLETE
-- =============================================

PROMPT;
PROMPT ✅ WasiCycles Oracle TxEventQ Setup Complete!
PROMPT;
PROMPT 📋 Summary:
PROMPT    - Topic: WASI_CROSS_RUNTIME_TOPIC (5 partitions, 7 days retention)
PROMPT    - OKafka Compatible: YES (partition_assignment_mode=1)
PROMPT    - Consumer Groups: 5 groups created
PROMPT    - Procedures: enq_okafka_msg_v1, deq_okafka_msg_v1
PROMPT;
PROMPT 🔗 Consumer Groups Created:
PROMPT    - springboot_okafka_cross_runtime_grp (for OKafka clients)
PROMPT    - springboot_plsql_cross_runtime_grp (for PLSQL procedures)
PROMPT    - wasmedge_cross_runtime_grp (for WASMEdge runtime)
PROMPT    - wasmer_cross_runtime_grp (for Wasmer runtime)  
PROMPT    - wasmtime_cross_runtime_grp (for Wasmtime runtime)
PROMPT;
PROMPT 🚀 Ready for Cross-Runtime Messaging!
PROMPT    - Spring Boot ↔ OKafka native clients
PROMPT    - WASM Runtimes ↔ TxEventQ REST endpoints
PROMPT    - All 4 combinations: OKafka/PLSQL enqueue × OKafka/PLSQL dequeue
PROMPT;
