package com.example.springbootkafkamongodb.service;

import com.example.springbootkafkamongodb.model.PlayerInfo;
import com.example.springbootkafkamongodb.repository.PlayerInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing player information with dual access:
 * - MongoDB API for JSON Duality View access
 * - ORDS REST API for direct SQL table access
 */
@Service
public class PlayerInfoService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlayerInfoService.class);
    
    @Autowired
    private PlayerInfoRepository playerInfoRepository;
    
    @Autowired
    private OrdsRestService ordsRestService;
    
    /**
     * Save or update player information using MongoDB API
     */
    public PlayerInfo savePlayerMongoDB(PlayerInfo playerInfo) {
        logger.info("Saving player {} via MongoDB API", playerInfo.getPlayerId());
        
        playerInfo.setGameTimestamp(LocalDateTime.now());
        
        PlayerInfo savedPlayer = playerInfoRepository.save(playerInfo);
        logger.info("Successfully saved player {} via MongoDB", savedPlayer.getPlayerId());
        
        return savedPlayer;
    }
    
    /**
     * Save or update player information using ORDS REST API
     */
    public CompletableFuture<PlayerInfo> savePlayerORDS(PlayerInfo playerInfo) {
        logger.info("Saving player {} via ORDS REST API", playerInfo.getPlayerId());
        
        return ordsRestService.updatePlayer(playerInfo)
                .toFuture()
                .toCompletableFuture()
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to save player {} via ORDS", playerInfo.getPlayerId(), throwable);
                    } else {
                        logger.info("Successfully saved player {} via ORDS", playerInfo.getPlayerId());
                    }
                });
    }
    
    /**
     * Create new player using ORDS REST API
     */
    public CompletableFuture<PlayerInfo> createPlayerORDS(PlayerInfo playerInfo) {
        logger.info("Creating player {} via ORDS REST API", playerInfo.getPlayerId());
        
        return ordsRestService.createPlayer(playerInfo)
                .toFuture()
                .toCompletableFuture()
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to create player {} via ORDS", playerInfo.getPlayerId(), throwable);
                    } else {
                        logger.info("Successfully created player {} via ORDS", playerInfo.getPlayerId());
                    }
                });
    }
    
    /**
     * Save player using both MongoDB and ORDS (dual write)
     */
    public PlayerInfo savePlayerDual(PlayerInfo playerInfo) {
        logger.info("Performing dual save for player {}", playerInfo.getPlayerId());
        
        try {
            // Try MongoDB first
            PlayerInfo mongoResult = savePlayerMongoDB(playerInfo);
            
            // Async backup via ORDS (use create for new players)
            createPlayerORDS(playerInfo)
                    .exceptionally(throwable -> {
                        logger.warn("ORDS backup save failed for player {}, but MongoDB save succeeded", 
                                playerInfo.getPlayerId(), throwable);
                        return null;
                    });
            
            return mongoResult;
            
        } catch (Exception e) {
            logger.warn("MongoDB save failed for player {}, trying ORDS only: {}", 
                    playerInfo.getPlayerId(), e.getMessage());
            
            // If MongoDB fails (e.g., due to duality view constraints), use ORDS only
            try {
                return createPlayerORDS(playerInfo).get();
            } catch (Exception ordsException) {
                logger.error("Both MongoDB and ORDS saves failed for player {}", 
                        playerInfo.getPlayerId(), ordsException);
                throw new RuntimeException("Failed to save player via both MongoDB and ORDS", ordsException);
            }
        }
    }
    
    /**
     * Update player score using ORDS by email (since MongoDB upsert not supported on duality views)
     */
    public PlayerInfo updatePlayerScoreORDSByEmail(String playerEmail, Integer newScore) {
        logger.info("Updating score for player with email {} to {} via ORDS", playerEmail, newScore);
        
        try {
            // Use the ORDS service to update score by email lookup
            PlayerInfo result = ordsRestService.updatePlayerScoreByEmail(playerEmail, newScore)
                    .doOnError(error -> logger.error("Failed to update score via ORDS for player with email {}", playerEmail, error))
                    .onErrorComplete() // Complete the Mono without emitting a value on error
                    .block(); // Convert Mono to synchronous result
                    
            if (result == null) {
                logger.warn("updatePlayerScoreByEmail returned null for email: {}", playerEmail);
            }
            return result;
                    
        } catch (Exception e) {
            logger.error("Error updating player score via ORDS for player with email {}", playerEmail, e);
            return null; // Return null instead of throwing exception
        }
    }
    
    /**
     * Update player score using ORDS by player ID with proper URL encoding
     */
    public PlayerInfo updatePlayerScoreORDSById(String playerId, Integer newScore) {
        logger.info("Updating score for player {} to {} via ORDS", playerId, newScore);
        
        try {
            // Use the ORDS service to update score by player ID with proper encoding
            PlayerInfo result = ordsRestService.updatePlayerScoreById(playerId, newScore)
                    .doOnError(error -> logger.error("Failed to update score via ORDS for player {}", playerId, error))
                    .onErrorComplete() // Complete the Mono without emitting a value on error
                    .block(); // Convert Mono to synchronous result
                    
            if (result == null) {
                logger.warn("updatePlayerScoreById returned null for player: {}", playerId);
            }
            return result;
                    
        } catch (Exception e) {
            logger.error("Error updating player score via ORDS for player {}", playerId, e);
            return null; // Return null instead of throwing exception
        }
    }

    /**
     * Increment score for all players with the same email
     */
    public List<PlayerInfo> incrementScoreForAllPlayersWithEmail(String playerEmail) {
        logger.info("Incrementing score for all players with email {} via ORDS", playerEmail);
        
        try {
            // Get all players to find those with matching email
            List<PlayerInfo> allPlayers = getLeaderboardMongoDB();
            List<PlayerInfo> playersWithEmail = allPlayers.stream()
                    .filter(player -> playerEmail.equals(player.getPlayerEmail()))
                    .toList();
            
            logger.info("Found {} players with email {}", playersWithEmail.size(), playerEmail);
            
            if (playersWithEmail.isEmpty()) {
                logger.warn("No players found with email {}", playerEmail);
                return List.of();
            }
            
            List<PlayerInfo> updatedPlayers = new java.util.ArrayList<>();
            
            // Update each player's score
            for (PlayerInfo player : playersWithEmail) {
                try {
                    Integer newScore = player.getPlayerScore() + 1;
                    logger.info("Incrementing score for player {} (email: {}) from {} to {}", 
                               player.getPlayerId(), playerEmail, player.getPlayerScore(), newScore);
                    
                    // Update this specific player via email (which will find the first one with this email)
                    PlayerInfo updatedPlayer = ordsRestService.updatePlayerScoreByEmail(playerEmail, newScore)
                            .doOnError(error -> logger.error("Failed to update player {} with email {}", player.getPlayerId(), playerEmail, error))
                            .onErrorComplete()
                            .block();
                    
                    if (updatedPlayer != null) {
                        updatedPlayers.add(updatedPlayer);
                    }
                } catch (Exception e) {
                    logger.error("Error updating player {} with email {}", player.getPlayerId(), playerEmail, e);
                    // Continue with other players
                }
            }
            
            logger.info("Successfully updated {} out of {} players with email {}", 
                       updatedPlayers.size(), playersWithEmail.size(), playerEmail);
            
            return updatedPlayers;
            
        } catch (Exception e) {
            logger.error("Error incrementing scores for players with email {}", playerEmail, e);
            return List.of();
        }
    }

    /**
     * Update player score using ORDS (since MongoDB upsert not supported on duality views)
     * @deprecated Use updatePlayerScoreORDSByEmail instead to avoid URL encoding issues
     */
    @Deprecated
    public PlayerInfo updatePlayerScoreORDS(String playerId, Integer newScore) {
        logger.warn("DEPRECATED: updatePlayerScoreORDS(playerId) called - consider using updatePlayerScoreORDSByEmail");
        logger.info("Incrementing score for player {} via ORDS (will increment by 1)", playerId);
        
        try {
            // Try to get player by ID first to get email, then use email-based update
            Optional<PlayerInfo> playerOpt = getPlayerMongoDB(playerId);
            if (playerOpt.isPresent()) {
                return updatePlayerScoreORDSByEmail(playerOpt.get().getPlayerEmail(), newScore);
            } else {
                throw new RuntimeException("Player not found with ID: " + playerId);
            }
                    
        } catch (Exception e) {
            logger.error("Error incrementing player score via ORDS for player {}", playerId, e);
            throw new RuntimeException("Failed to increment player score via ORDS", e);
        }
    }
    
    /**
     * Get player by ID using MongoDB API
     */
    public Optional<PlayerInfo> getPlayerMongoDB(String playerId) {
        logger.info("Fetching player {} via MongoDB API", playerId);
        return playerInfoRepository.findById(playerId);
    }
    
    /**
     * Get player by ID using ORDS REST API
     */
    public CompletableFuture<Optional<PlayerInfo>> getPlayerORDS(String playerId) {
        logger.info("Fetching player {} via ORDS REST API", playerId);
        
        return ordsRestService.getPlayer(playerId)
                .map(Optional::of)
                .onErrorReturn(Optional.empty())
                .toFuture()
                .toCompletableFuture();
    }
    
    /**
     * Get player by email using MongoDB API
     */
    public Optional<PlayerInfo> getPlayerByEmail(String playerEmail) {
        logger.info("Fetching player by email {} via MongoDB API", playerEmail);
        return playerInfoRepository.findByPlayerEmail(playerEmail);
    }
    
    /**
     * Get leaderboard using MongoDB API
     */
    public List<PlayerInfo> getLeaderboardMongoDB() {
        logger.info("Fetching leaderboard via MongoDB API");
        return playerInfoRepository.findTopPlayersByScore();
    }
    
    /**
     * Get leaderboard using ORDS REST API
     */
    public CompletableFuture<List<PlayerInfo>> getLeaderboardORDS() {
        logger.info("Fetching leaderboard via ORDS REST API");
        
        return ordsRestService.getLeaderboard()
                .toFuture()
                .toCompletableFuture()
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to fetch leaderboard via ORDS", throwable);
                    } else {
                        logger.info("Successfully fetched leaderboard via ORDS: {} players", 
                                result != null ? result.size() : 0);
                    }
                });
    }
    
    /**
     * Update player score
     */
    public PlayerInfo updatePlayerScore(String playerId, Integer newScore) {
        logger.info("Updating score for player {} to {}", playerId, newScore);
        
        Optional<PlayerInfo> playerOpt = getPlayerMongoDB(playerId);
        if (playerOpt.isPresent()) {
            PlayerInfo player = playerOpt.get();
            player.setPlayerScore(newScore);
            player.setGameTimestamp(LocalDateTime.now());
            
            return savePlayerDual(player);
        } else {
            logger.warn("Player {} not found for score update", playerId);
            throw new RuntimeException("Player not found: " + playerId);
        }
    }
    
    /**
     * Create new player with game data
     */
    public PlayerInfo createPlayer(String playerId, String playerName, String playerEmail, Integer initialScore) {
        logger.info("Creating new player: {}, name: {}, email: {}", playerId, playerName, playerEmail);
        
        PlayerInfo newPlayer = new PlayerInfo(playerId, playerName, playerEmail, initialScore);
        return savePlayerDual(newPlayer);
    }
    
    /**
     * Get all players (MongoDB API)
     */
    public List<PlayerInfo> getAllPlayers() {
        logger.info("Fetching all players via MongoDB API");
        return playerInfoRepository.findAll();
    }
    
    /**
     * Delete player (MongoDB API)
     */
    public void deletePlayer(String playerId) {
        logger.info("Deleting player {} via MongoDB API", playerId);
        playerInfoRepository.deleteById(playerId);
    }
    
    /**
     * Check if player exists
     */
    public boolean playerExists(String playerId) {
        return playerInfoRepository.existsById(playerId);
    }
}
