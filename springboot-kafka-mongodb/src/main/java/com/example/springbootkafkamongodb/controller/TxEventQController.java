package com.example.springbootkafkamongodb.controller;

import com.example.springbootkafkamongodb.model.TxEventQMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/txeventq")
public class TxEventQController {

    @Autowired
    @Qualifier("oracleDataSource")
    private DataSource oracleDataSource;

    /**
     * Health check endpoint for TxEventQ controller
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "🏢 TxEventQ Controller is running!");
        response.put("runtime", "springboot-txeventq");
        response.put("description", "Oracle TxEventQ message broker inspection service");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("messages", "/txeventq/messages");
        endpoints.put("messages-raw", "/txeventq/messages/raw");
        endpoints.put("health", "/txeventq/");
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }

    /**
     * Query TxEventQ table for message broker contents
     * Returns structured TxEventQMessage objects
     */
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<TxEventQMessage> messages = new ArrayList<>();
        String query = """
            SELECT MSG_STATE, MSG_ID, CORR_ID, CONSUMER_NAME, ENQ_TIMESTAMP, DEQ_TIMESTAMP 
            FROM ADMIN.AQ$WASICYCLES_GAME_EVENTS 
            ORDER BY ENQ_TIMESTAMP DESC 
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        try (Connection connection = oracleDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, offset);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TxEventQMessage message = new TxEventQMessage();
                    message.setMsgState(resultSet.getString("MSG_STATE"));
                    message.setMsgId(resultSet.getString("MSG_ID"));
                    message.setCorrId(resultSet.getString("CORR_ID"));
                    message.setConsumerName(resultSet.getString("CONSUMER_NAME"));

                    // Handle timestamps safely
                    Timestamp enqTs = resultSet.getTimestamp("ENQ_TIMESTAMP");
                    if (enqTs != null) {
                        message.setEnqTimestamp(enqTs.toLocalDateTime());
                    }

                    Timestamp deqTs = resultSet.getTimestamp("DEQ_TIMESTAMP");
                    if (deqTs != null) {
                        message.setDeqTimestamp(deqTs.toLocalDateTime());
                    }

                    messages.add(message);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TxEventQ messages retrieved successfully");
            response.put("count", messages.size());
            response.put("offset", offset);
            response.put("limit", limit);
            response.put("messages", messages);
            response.put("query_timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Database query failed: " + e.getMessage());
            errorResponse.put("sql_state", e.getSQLState());
            errorResponse.put("error_code", e.getErrorCode());
            errorResponse.put("query_timestamp", LocalDateTime.now());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Query TxEventQ table for message broker contents
     * Returns raw SQL result data for debugging
     */
    @GetMapping("/messages/raw")
    public ResponseEntity<Map<String, Object>> getMessagesRaw(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<Map<String, Object>> messages = new ArrayList<>();
        String query = """
            SELECT MSG_STATE, MSG_ID, CORR_ID, CONSUMER_NAME, ENQ_TIMESTAMP, DEQ_TIMESTAMP 
            FROM ADMIN.AQ$WASICYCLES_GAME_EVENTS 
            ORDER BY ENQ_TIMESTAMP DESC 
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        try (Connection connection = oracleDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, offset);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = resultSet.getObject(i);
                        row.put(columnName.toLowerCase(), value);
                    }
                    messages.add(row);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Raw TxEventQ messages retrieved successfully");
            response.put("count", messages.size());
            response.put("offset", offset);
            response.put("limit", limit);
            response.put("raw_messages", messages);
            response.put("query_timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Database query failed: " + e.getMessage());
            errorResponse.put("sql_state", e.getSQLState());
            errorResponse.put("error_code", e.getErrorCode());
            errorResponse.put("query_timestamp", LocalDateTime.now());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get database connection info for debugging
     */
    @GetMapping("/connection-info")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        try (Connection connection = oracleDataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            Map<String, Object> info = new HashMap<>();
            info.put("database_product_name", metaData.getDatabaseProductName());
            info.put("database_product_version", metaData.getDatabaseProductVersion());
            info.put("driver_name", metaData.getDriverName());
            info.put("driver_version", metaData.getDriverVersion());
            info.put("connection_url", metaData.getURL());
            info.put("username", metaData.getUserName());
            info.put("connection_valid", connection.isValid(5));

            return ResponseEntity.ok(info);

        } catch (SQLException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to get connection info: " + e.getMessage());
            errorResponse.put("sql_state", e.getSQLState());
            errorResponse.put("error_code", e.getErrorCode());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
