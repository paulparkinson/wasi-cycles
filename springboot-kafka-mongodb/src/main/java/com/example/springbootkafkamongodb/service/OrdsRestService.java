package com.example.springbootkafkamongodb.service;

import com.example.springbootkafkamongodb.model.PlayerInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Oracle ORDS REST endpoints
 * Provides direct SQL table access via REST API
 */
@Service
public class OrdsRestService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrdsRestService.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    // Use System.getenv() directly to avoid Spring property resolution issues
    private final String ordsPlayerInfoUrl = System.getenv().getOrDefault(
        "WASICYCLES_PLAYERINFO_ORDS_URL", 
        "https://mydatabase.adb.eu-frankfurt-1.oraclecloudapps.com/ords/admin/playerinfo"
    );
    
    private final String ordsLeaderboardUrl = System.getenv().getOrDefault(
        "WASICYCLES_LEADERBOARD_ORDS_URL", 
        "https://mydatabase.adb.eu-frankfurt-1.oraclecloudapps.com/ords/admin/playerinfo"
    );
    
    public OrdsRestService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        
        // Log the URLs being used for debugging
        logger.info("🔧 ORDS PlayerInfo URL: {}", ordsPlayerInfoUrl);
        logger.info("🔧 ORDS Leaderboard URL: {}", ordsLeaderboardUrl);
    }
    
    /**
     * Get leaderboard via ORDS REST API
     */
    public Mono<List<PlayerInfo>> getLeaderboard() {
        logger.info("Fetching leaderboard from ORDS: {}", ordsLeaderboardUrl);
        
        return webClient.get()
                .uri(ordsLeaderboardUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseLeaderboardResponse)
                .doOnError(error -> logger.error("Error fetching leaderboard from ORDS", error))
                .doOnSuccess(result -> logger.info("Successfully fetched {} players from ORDS leaderboard", 
                        result != null ? result.size() : 0));
    }
    
    /**
     * Create new player via ORDS REST API
     */
    public Mono<PlayerInfo> createPlayer(PlayerInfo playerInfo) {
        logger.info("Creating new player via ORDS: {}", playerInfo.getPlayerId());
        
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("player_id", playerInfo.getPlayerId());
        playerData.put("player_name", playerInfo.getPlayerName());
        playerData.put("player_email", playerInfo.getPlayerEmail());
        playerData.put("player_score", playerInfo.getPlayerScore());
        playerData.put("runtime", playerInfo.getRuntime());
        playerData.put("tshirtsize", playerInfo.getTshirtSize());
        
        // Use PUT to the specific player endpoint (this does upsert in our ORDS setup)
        return webClient.put()
                .uri(ordsPlayerInfoUrl + "/" + playerInfo.getPlayerId())
                .bodyValue(playerData)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    logger.info("ORDS create response: {}", response);
                    return playerInfo; // Return the original player info on success
                })
                .doOnError(error -> logger.error("Error creating player via ORDS", error))
                .doOnSuccess(result -> logger.info("Successfully created player {} via ORDS", 
                        playerInfo.getPlayerId()));
    }

    /**
     * Update existing player via ORDS REST API
     */
    public Mono<PlayerInfo> updatePlayer(PlayerInfo playerInfo) {
        logger.info("Updating player via ORDS: {}", playerInfo.getPlayerId());
        
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("player_id", playerInfo.getPlayerId());
        playerData.put("player_name", playerInfo.getPlayerName());
        playerData.put("player_email", playerInfo.getPlayerEmail());
        playerData.put("player_score", playerInfo.getPlayerScore());
        playerData.put("runtime", playerInfo.getRuntime());
        playerData.put("tshirtsize", playerInfo.getTshirtSize());
        
        return webClient.put()
                .uri(ordsPlayerInfoUrl + "/" + playerInfo.getPlayerId())
                .bodyValue(playerData)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    logger.info("ORDS update response: {}", response);
                    return playerInfo; // Return the original player info on success
                })
                .doOnError(error -> logger.error("Error updating player via ORDS", error))
                .doOnSuccess(result -> logger.info("Successfully updated player {} via ORDS", 
                        playerInfo.getPlayerId()));
    }
    
    /**
     * Get specific player by email via ORDS REST API
     */
    public Mono<PlayerInfo> getPlayerByEmail(String playerEmail) {
        logger.info("Fetching player by email {} from ORDS", playerEmail);
        
        // First get all players, then filter by email (simple approach)
        return webClient.get()
                .uri(ordsPlayerInfoUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseLeaderboardResponse)
                .map(playerList -> {
                    // Filter by email
                    return playerList.stream()
                            .filter(player -> playerEmail.equals(player.getPlayerEmail()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Player not found with email: " + playerEmail));
                })
                .doOnError(error -> logger.error("Error fetching player by email {} from ORDS", playerEmail, error))
                .doOnSuccess(result -> logger.info("Successfully fetched player by email {} from ORDS", playerEmail));
    }
    
    /**
     * Get specific player by ID via ORDS REST API
     */
    public Mono<PlayerInfo> getPlayer(String playerId) {
        logger.info("Fetching player {} from ORDS", playerId);
        
        return webClient.get()
                .uri(ordsPlayerInfoUrl + "/" + playerId)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parsePlayerResponse)
                .doOnError(error -> logger.error("Error fetching player {} from ORDS", playerId, error))
                .doOnSuccess(result -> logger.info("Successfully fetched player {} from ORDS", playerId));
    }
    
    /**
     * Update player score specifically via ORDS REST API using player ID with proper URL encoding
     */
    public Mono<PlayerInfo> updatePlayerScoreById(String playerId, Integer newScore) {
        logger.info("Updating score for player {} to {} via ORDS", playerId, newScore);
        
        // First get the current player data by ID, then update just the score
        return getPlayer(playerId)
                .flatMap(existingPlayer -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("player_id", existingPlayer.getPlayerId());
                    playerData.put("player_name", existingPlayer.getPlayerName());
                    playerData.put("player_email", existingPlayer.getPlayerEmail());
                    playerData.put("player_score", newScore);
                    playerData.put("runtime", existingPlayer.getRuntime());
                    playerData.put("tshirtsize", existingPlayer.getTshirtSize());
                    
                    // Use URL encoding for player ID to handle spaces
                    String encodedPlayerId = playerId.replace(" ", "%20");
                    
                    return webClient.put()
                            .uri(ordsPlayerInfoUrl + "/" + encodedPlayerId)
                            .bodyValue(playerData)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(response -> {
                                logger.info("ORDS score update response: {}", response);
                                existingPlayer.setPlayerScore(newScore);
                                return existingPlayer;
                            });
                })
                .doOnError(error -> logger.error("Error updating score for player {} via ORDS", playerId, error))
                .doOnSuccess(result -> logger.info("Successfully updated score for player {} to {} via ORDS", playerId, newScore));
    }
    
    /**
     * Update player score specifically via ORDS REST API using email lookup
     */
    public Mono<PlayerInfo> updatePlayerScoreByEmail(String playerEmail, Integer newScore) {
        logger.info("Updating score for player with email {} to {} via ORDS", playerEmail, newScore);
        
        // First get the current player data by email, then update just the score
        return getPlayerByEmail(playerEmail)
                .flatMap(existingPlayer -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("player_id", existingPlayer.getPlayerId());
                    playerData.put("player_name", existingPlayer.getPlayerName());
                    playerData.put("player_email", existingPlayer.getPlayerEmail());
                    playerData.put("player_score", newScore);
                    playerData.put("runtime", existingPlayer.getRuntime());
                    playerData.put("tshirtsize", existingPlayer.getTshirtSize());
                    
                    // Use URL encoding for player ID to handle spaces
                    String encodedPlayerId = existingPlayer.getPlayerId().replace(" ", "%20");
                    
                    return webClient.put()
                            .uri(ordsPlayerInfoUrl + "/" + encodedPlayerId)
                            .bodyValue(playerData)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(response -> {
                                logger.info("ORDS score update response: {}", response);
                                existingPlayer.setPlayerScore(newScore);
                                return existingPlayer;
                            });
                })
                .doOnError(error -> logger.error("Error updating score for player with email {} via ORDS", playerEmail, error))
                .doOnSuccess(result -> logger.info("Successfully updated score for player with email {} to {} via ORDS", playerEmail, newScore));
    }
    
    /**
     * Parse ORDS leaderboard JSON response
     */
    @SuppressWarnings("unchecked")
    private List<PlayerInfo> parseLeaderboardResponse(String jsonResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(jsonResponse, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            
            return items.stream()
                    .map(this::mapToPlayerInfo)
                    .toList();
                    
        } catch (JsonProcessingException e) {
            logger.error("Error parsing ORDS leaderboard response", e);
            throw new RuntimeException("Failed to parse ORDS leaderboard response", e);
        }
    }
    
    /**
     * Parse ORDS single player JSON response
     */
    @SuppressWarnings("unchecked")
    private PlayerInfo parsePlayerResponse(String jsonResponse) {
        try {
            Map<String, Object> playerData = objectMapper.readValue(jsonResponse, Map.class);
            return mapToPlayerInfo(playerData);
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing ORDS player response", e);
            throw new RuntimeException("Failed to parse ORDS player response", e);
        }
    }
    
    /**
     * Map ORDS response data to PlayerInfo object
     */
    private PlayerInfo mapToPlayerInfo(Map<String, Object> data) {
        PlayerInfo playerInfo = new PlayerInfo();
        playerInfo.setPlayerId((String) data.get("PLAYER_ID"));
        playerInfo.setPlayerName((String) data.get("PLAYER_NAME"));
        playerInfo.setPlayerEmail((String) data.get("PLAYER_EMAIL"));
        
        Object score = data.get("PLAYER_SCORE");
        if (score instanceof Number) {
            playerInfo.setPlayerScore(((Number) score).intValue());
        }
        
        // Handle new fields
        playerInfo.setRuntime((String) data.get("RUNTIME"));
        playerInfo.setTshirtSize((String) data.get("TSHIRTSIZE"));
        
        // Handle timestamp parsing if needed
        Object timestamp = data.get("GAME_TIMESTAMP");
        if (timestamp != null) {
            // Add timestamp parsing logic based on ORDS format
            logger.debug("Received timestamp: {}", timestamp);
        }
        
        return playerInfo;
    }
}
