package com.example.springbootkafkamongodb.controller;

import com.example.springbootkafkamongodb.model.PlayerInfo;
import com.example.springbootkafkamongodb.service.PlayerInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for WasiCycles Player Information
 * Provides endpoints for both MongoDB API and ORDS REST API access
 */
@RestController
@RequestMapping("/api/players")
@CrossOrigin(origins = "*")
public class PlayerInfoController {
    
    private static final Logger logger = LoggerFactory.getLogger(PlayerInfoController.class);
    
    @Autowired
    private PlayerInfoService playerInfoService;
    
    /**
     * Create or update player information
     */
    @PostMapping
    public ResponseEntity<PlayerInfo> createPlayer(@RequestBody PlayerInfo playerInfo) {
        logger.info("REST: Creating/updating player {}", playerInfo.getPlayerId());
        
        try {
            PlayerInfo savedPlayer = playerInfoService.savePlayerDual(playerInfo);
            return ResponseEntity.ok(savedPlayer);
        } catch (Exception e) {
            logger.error("Error creating/updating player", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create player with specific details
     */
    @PostMapping("/create")
    public ResponseEntity<PlayerInfo> createPlayerWithDetails(
            @RequestParam String playerId,
            @RequestParam String playerName,
            @RequestParam String playerEmail,
            @RequestParam(defaultValue = "0") Integer initialScore) {
        
        logger.info("REST: Creating player {} with details", playerId);
        
        try {
            PlayerInfo player = playerInfoService.createPlayer(playerId, playerName, playerEmail, initialScore);
            return ResponseEntity.ok(player);
        } catch (Exception e) {
            logger.error("Error creating player with details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get player by ID (MongoDB)
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerInfo> getPlayer(@PathVariable String playerId) {
        logger.info("REST: Fetching player {} via MongoDB", playerId);
        
        Optional<PlayerInfo> player = playerInfoService.getPlayerMongoDB(playerId);
        return player.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get player by ID via ORDS REST API
     */
    @GetMapping("/{playerId}/ords")
    public CompletableFuture<ResponseEntity<PlayerInfo>> getPlayerORDS(@PathVariable String playerId) {
        logger.info("REST: Fetching player {} via ORDS", playerId);
        
        return playerInfoService.getPlayerORDS(playerId)
                .thenApply(playerOpt -> 
                    playerOpt.map(ResponseEntity::ok)
                            .orElse(ResponseEntity.notFound().build()));
    }
    
    /**
     * Get player by email
     */
    @GetMapping("/by-email/{email}")
    public ResponseEntity<PlayerInfo> getPlayerByEmail(@PathVariable String email) {
        logger.info("REST: Fetching player by email {}", email);
        
        Optional<PlayerInfo> player = playerInfoService.getPlayerByEmail(email);
        return player.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Update player score
     */
    @PutMapping("/{playerId}/score")
    public ResponseEntity<PlayerInfo> updatePlayerScore(
            @PathVariable String playerId,
            @RequestParam Integer score) {
        
        logger.info("REST: Updating score for player {} to {}", playerId, score);
        
        try {
            PlayerInfo updatedPlayer = playerInfoService.updatePlayerScore(playerId, score);
            return ResponseEntity.ok(updatedPlayer);
        } catch (RuntimeException e) {
            logger.error("Error updating player score", e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Unexpected error updating player score", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get leaderboard via MongoDB
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<PlayerInfo>> getLeaderboard() {
        logger.info("Fetching leaderboard via MongoDB");
        
        try {
            List<PlayerInfo> leaderboard = playerInfoService.getLeaderboardMongoDB();
            return ResponseEntity.ok(leaderboard);
        } catch (Exception e) {
            logger.error("Error fetching leaderboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get leaderboard via ORDS REST API
     */
    @GetMapping("/leaderboard/ords")
    public CompletableFuture<ResponseEntity<List<PlayerInfo>>> getLeaderboardORDS() {
        logger.info("REST: Fetching leaderboard via ORDS");
        
        return playerInfoService.getLeaderboardORDS()
                .thenApply(leaderboard -> {
                    if (leaderboard != null && !leaderboard.isEmpty()) {
                        return ResponseEntity.ok(leaderboard);
                    } else {
                        return ResponseEntity.noContent().<List<PlayerInfo>>build();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error fetching ORDS leaderboard", throwable);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<List<PlayerInfo>>build();
                });
    }
    
    /**
     * Get all players
     */
    @GetMapping("/all")
    public ResponseEntity<List<PlayerInfo>> getAllPlayers() {
        logger.info("REST: Fetching all players");
        
        try {
            List<PlayerInfo> players = playerInfoService.getAllPlayers();
            return ResponseEntity.ok(players);
        } catch (Exception e) {
            logger.error("Error fetching all players", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete player
     */
    @DeleteMapping("/{playerId}")
    public ResponseEntity<Void> deletePlayer(@PathVariable String playerId) {
        logger.info("REST: Deleting player {}", playerId);
        
        try {
            if (playerInfoService.playerExists(playerId)) {
                playerInfoService.deletePlayer(playerId);
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error deleting player", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Check if player exists
     */
    @GetMapping("/{playerId}/exists")
    public ResponseEntity<Boolean> playerExists(@PathVariable String playerId) {
        logger.info("REST: Checking if player {} exists", playerId);
        
        boolean exists = playerInfoService.playerExists(playerId);
        return ResponseEntity.ok(exists);
    }
    
    /**
     * Increment player score (for victories)
     */
    @PostMapping("/{playerId}/increment-score")
    public ResponseEntity<PlayerInfo> incrementPlayerScore(@PathVariable String playerId) {
        logger.info("REST: Incrementing score for player {}", playerId);
        
        try {
            // First try to find by player ID
            Optional<PlayerInfo> playerOpt = playerInfoService.getPlayerMongoDB(playerId);
            
            // If not found by ID, try to find by extracting email from player name pattern
            if (!playerOpt.isPresent()) {
                logger.info("Player not found by ID {}, checking all players...", playerId);
                
                // Debug: List all players to see what we have
                List<PlayerInfo> allPlayers = playerInfoService.getLeaderboardMongoDB();
                logger.info("Found {} players in database:", allPlayers.size());
                for (PlayerInfo p : allPlayers) {
                    logger.info("  Player: {} | {} | {}", p.getPlayerId(), p.getPlayerName(), p.getPlayerEmail());
                }
                
                // Try to find player by checking if playerId contains part of stored player info
                playerOpt = allPlayers.stream()
                    .filter(p -> p.getPlayerId() != null && 
                               (p.getPlayerId().equals(playerId) || 
                                playerId.contains(p.getPlayerName()) ||
                                p.getPlayerName().equals(extractNameFromPlayerId(playerId))))
                    .findFirst();
            }
            
            if (playerOpt.isPresent()) {
                PlayerInfo player = playerOpt.get();
                logger.info("Found player: {} with current score: {}", player.getPlayerName(), player.getPlayerScore());
                logger.info("Player details - ID: {}, Name: {}, Email: {}", player.getPlayerId(), player.getPlayerName(), player.getPlayerEmail());
                
                // Use email-based update to handle multiple players with same email
                logger.info("Incrementing score for all players with email {}", player.getPlayerEmail());
                
                // Update all players with this email
                List<PlayerInfo> updatedPlayers = playerInfoService.incrementScoreForAllPlayersWithEmail(player.getPlayerEmail());
                
                // Check if any players were updated
                if (updatedPlayers.isEmpty()) {
                    logger.error("No players were updated for email: {}", player.getPlayerEmail());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
                
                // Return the first updated player (or the original player if none were updated via ORDS)
                PlayerInfo updatedPlayer = updatedPlayers.get(0);
                logger.info("Score incremented for {} players with email {}. First player score: {}", 
                           updatedPlayers.size(), player.getPlayerEmail(), updatedPlayer.getPlayerScore());
                return ResponseEntity.ok(updatedPlayer);
            } else {
                logger.warn("Player {} not found for score increment", playerId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error incrementing score for player {}", playerId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Debug: Search for player by email
     */
    @GetMapping("/search-by-email/{email}")
    public ResponseEntity<PlayerInfo> searchPlayerByEmail(@PathVariable String email) {
        logger.info("REST: Searching for player by email: {}", email);
        
        try {
            List<PlayerInfo> allPlayers = playerInfoService.getLeaderboardMongoDB();
            Optional<PlayerInfo> playerOpt = allPlayers.stream()
                .filter(p -> p.getPlayerEmail() != null && p.getPlayerEmail().equals(email))
                .findFirst();
                
            if (playerOpt.isPresent()) {
                logger.info("Found player by email: {}", playerOpt.get().getPlayerName());
                return ResponseEntity.ok(playerOpt.get());
            } else {
                logger.warn("No player found with email: {}", email);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error searching for player by email {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Debug: List all players with details
     */
    @GetMapping("/debug/all-players")
    public ResponseEntity<List<PlayerInfo>> getAllPlayersDebug() {
        logger.info("REST: Debug - listing all players");
        
        try {
            List<PlayerInfo> allPlayers = playerInfoService.getLeaderboardMongoDB();
            logger.info("Found {} players total", allPlayers.size());
            for (PlayerInfo p : allPlayers) {
                logger.info("Player: ID={}, Name={}, Email={}, Score={}, Runtime={}, TShirtSize={}", 
                    p.getPlayerId(), p.getPlayerName(), p.getPlayerEmail(), 
                    p.getPlayerScore(), p.getRuntime(), p.getTshirtSize());
            }
            return ResponseEntity.ok(allPlayers);
        } catch (Exception e) {
            logger.error("Error getting all players for debug", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Helper method to extract player name from player ID
     */
    private String extractNameFromPlayerId(String playerId) {
        // Extract name from pattern like "human_PlayerName_timestamp"
        if (playerId.startsWith("human_")) {
            String[] parts = playerId.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
