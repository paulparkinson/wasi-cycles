package com.example.springbootkafkamongodb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.time.Instant;

/**
 * Spring Boot AI Controller for WasiCycles 3D Tron Game
 * Implements a balanced strategist AI that adapts to game state
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AiController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    @PostMapping("/ai-action")
    public ResponseEntity<JsonNode> getAiAction(@RequestBody JsonNode gameData) {
        try {
            JsonNode action = getSpringBootAiAction(gameData);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("runtime", "springboot");
            response.put("castle", "Data Fortress");
            response.set("action", action);
            response.put("timestamp", Instant.now().toEpochMilli());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "AI processing failed: " + e.getMessage());
            errorResponse.put("runtime", "springboot");
            errorResponse.put("castle", "Data Fortress");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorResponse);
        }
    }

    private JsonNode getSpringBootAiAction(JsonNode gameData) {
        // Spring Boot Data Fortress AI: Balanced strategist with machine learning-inspired decisions
        // Strategy: Analyze patterns, predict opponent moves, maintain optimal positioning
        
        JsonNode arena = gameData.path("arena");
        double arenaSize = arena.path("size").asDouble(20.0);
        JsonNode players = gameData.path("players");
        JsonNode trails = gameData.path("trails");
        
        String myPlayerId = "ai-springboot";
        
        // Find my player data
        JsonNode myPlayer = players.path(myPlayerId);
        if (myPlayer.isMissingNode()) {
            return createAction("turn", "forward", "initializing");
        }
        
        JsonNode myPos = myPlayer.path("position");
        double myX = myPos.path("x").asDouble(0.0);
        double myZ = myPos.path("z").asDouble(0.0);
        int myDirection = myPlayer.path("direction").asInt(0);
        
        // Spring Boot strategy: Data-driven decisions
        
        // 1. Immediate collision avoidance (highest priority)
        Position nextPos = calculateNextPosition(myX, myZ, myDirection);
        if (isPositionDangerous(nextPos.x, nextPos.z, trails, arenaSize)) {
            String safeTurn = findSafestTurn(myX, myZ, myDirection, trails, arenaSize);
            return createAction("turn", safeTurn, "collision_avoidance");
        }
        
        // 2. Analyze opponent patterns and predict their moves
        OpponentAnalysis analysis = analyzeOpponents(players, trails, myX, myZ);
        
        // 3. Calculate optimal path considering multiple factors
        PathDecision decision = calculateOptimalPath(myX, myZ, myDirection, analysis, trails, arenaSize);
        
        if (decision.shouldTurn) {
            return createAction("turn", decision.direction, decision.reason);
        }
        
        // 4. Default: continue forward with strategic reasoning
        return createAction("move", "forward", 
            String.format("strategic_advance_score_%.1f", decision.forwardScore));
    }
    
    private static class Position {
        double x, z;
        Position(double x, double z) { this.x = x; this.z = z; }
    }
    
    private static class OpponentAnalysis {
        int totalOpponents = 0;
        double avgDistance = 0.0;
        Position nearestOpponent = null;
        double nearestDistance = Double.MAX_VALUE;
        int threatsCount = 0;
    }
    
    private static class PathDecision {
        boolean shouldTurn = false;
        String direction = "forward";
        String reason = "continuing";
        double forwardScore = 0.0;
    }
    
    private Position calculateNextPosition(double x, double z, int direction) {
        switch (direction) {
            case 0: return new Position(x, z - 1); // North
            case 1: return new Position(x + 1, z); // East
            case 2: return new Position(x, z + 1); // South
            case 3: return new Position(x - 1, z); // West
            default: return new Position(x, z);
        }
    }
    
    private boolean isPositionDangerous(double x, double z, JsonNode trails, double arenaSize) {
        // Check arena bounds
        if (x < 0 || x >= arenaSize || z < 0 || z >= arenaSize) {
            return true;
        }
        
        // Check trail collisions
        String posKey = String.format("%.0f,%.0f", x, z);
        Iterator<Map.Entry<String, JsonNode>> trailsIter = trails.fields();
        while (trailsIter.hasNext()) {
            Map.Entry<String, JsonNode> entry = trailsIter.next();
            JsonNode trailPositions = entry.getValue();
            if (trailPositions.isArray()) {
                for (JsonNode pos : trailPositions) {
                    if (pos.asText().equals(posKey)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private String findSafestTurn(double x, double z, int direction, JsonNode trails, double arenaSize) {
        int leftDir = (direction - 1 + 4) % 4;
        int rightDir = (direction + 1) % 4;
        
        Position leftPos = calculateNextPosition(x, z, leftDir);
        Position rightPos = calculateNextPosition(x, z, rightDir);
        
        boolean leftSafe = !isPositionDangerous(leftPos.x, leftPos.z, trails, arenaSize);
        boolean rightSafe = !isPositionDangerous(rightPos.x, rightPos.z, trails, arenaSize);
        
        if (leftSafe && rightSafe) {
            // Both safe - calculate distances ahead for each
            int leftDistance = calculateSafeDistance(x, z, leftDir, trails, arenaSize);
            int rightDistance = calculateSafeDistance(x, z, rightDir, trails, arenaSize);
            
            // Add some randomness to prevent predictable patterns
            if (Math.abs(leftDistance - rightDistance) <= 2) {
                return random.nextBoolean() ? "left" : "right";
            }
            
            return leftDistance > rightDistance ? "left" : "right";
        } else if (leftSafe) {
            return "left";
        } else if (rightSafe) {
            return "right";
        } else {
            return "right"; // Desperate move
        }
    }
    
    private int calculateSafeDistance(double x, double z, int direction, JsonNode trails, double arenaSize) {
        int distance = 0;
        double currentX = x;
        double currentZ = z;
        
        for (int i = 0; i < (int)(arenaSize * 2); i++) {
            Position nextPos = calculateNextPosition(currentX, currentZ, direction);
            if (isPositionDangerous(nextPos.x, nextPos.z, trails, arenaSize)) {
                break;
            }
            distance++;
            currentX = nextPos.x;
            currentZ = nextPos.z;
        }
        
        return distance;
    }
    
    private OpponentAnalysis analyzeOpponents(JsonNode players, JsonNode trails, double myX, double myZ) {
        OpponentAnalysis analysis = new OpponentAnalysis();
        
        Iterator<Map.Entry<String, JsonNode>> playersIter = players.fields();
        while (playersIter.hasNext()) {
            Map.Entry<String, JsonNode> entry = playersIter.next();
            String playerId = entry.getKey();
            JsonNode player = entry.getValue();
            
            if (playerId.equals("ai-springboot") || !player.path("alive").asBoolean(true)) {
                continue;
            }
            
            analysis.totalOpponents++;
            
            JsonNode pos = player.path("position");
            double oppX = pos.path("x").asDouble(0.0);
            double oppZ = pos.path("z").asDouble(0.0);
            
            double distance = Math.sqrt(Math.pow(oppX - myX, 2) + Math.pow(oppZ - myZ, 2));
            analysis.avgDistance += distance;
            
            if (distance < analysis.nearestDistance) {
                analysis.nearestDistance = distance;
                analysis.nearestOpponent = new Position(oppX, oppZ);
            }
            
            // Count as threat if close
            if (distance <= 5.0) {
                analysis.threatsCount++;
            }
        }
        
        if (analysis.totalOpponents > 0) {
            analysis.avgDistance /= analysis.totalOpponents;
        }
        
        return analysis;
    }
    
    private PathDecision calculateOptimalPath(double myX, double myZ, int myDirection, 
                                            OpponentAnalysis analysis, JsonNode trails, double arenaSize) {
        PathDecision decision = new PathDecision();
        
        // Calculate scores for each possible direction
        int forwardDistance = calculateSafeDistance(myX, myZ, myDirection, trails, arenaSize);
        int leftDistance = calculateSafeDistance(myX, myZ, (myDirection - 1 + 4) % 4, trails, arenaSize);
        int rightDistance = calculateSafeDistance(myX, myZ, (myDirection + 1) % 4, trails, arenaSize);
        
        decision.forwardScore = forwardDistance;
        
        // Factor in opponent proximity and density
        if (analysis.nearestOpponent != null && analysis.nearestDistance < 8.0) {
            // If opponent is close, consider strategic maneuvering
            double dx = analysis.nearestOpponent.x - myX;
            double dz = analysis.nearestOpponent.z - myZ;
            
            // Calculate if turning would improve our position relative to the threat
            double currentThreatAlignment = calculateThreatAlignment(myDirection, dx, dz);
            double leftThreatAlignment = calculateThreatAlignment((myDirection - 1 + 4) % 4, dx, dz);
            double rightThreatAlignment = calculateThreatAlignment((myDirection + 1) % 4, dx, dz);
            
            // Factor in threat density - more threats = more defensive play
            double threatMultiplier = 1.0 + (analysis.threatsCount * 0.5);
            int minSafeDistance = (int)(3 * threatMultiplier);
            
            // Prefer directions that give us better positioning vs threats
            if (leftDistance > forwardDistance + minSafeDistance && leftThreatAlignment > currentThreatAlignment) {
                decision.shouldTurn = true;
                decision.direction = "left";
                decision.reason = String.format("strategic_threat_avoidance_left_threats_%d", analysis.threatsCount);
            } else if (rightDistance > forwardDistance + minSafeDistance && rightThreatAlignment > currentThreatAlignment) {
                decision.shouldTurn = true;
                decision.direction = "right";
                decision.reason = String.format("strategic_threat_avoidance_right_threats_%d", analysis.threatsCount);
            }
        }
        
        // Consider average opponent distance for positioning
        if (!decision.shouldTurn && analysis.avgDistance > 0 && analysis.avgDistance < 10.0) {
            // Close quarters - prioritize escape routes
            if (leftDistance > rightDistance && leftDistance > forwardDistance + 3) {
                decision.shouldTurn = true;
                decision.direction = "left";
                decision.reason = "close_quarters_escape_left";
            } else if (rightDistance > forwardDistance + 3) {
                decision.shouldTurn = true;
                decision.direction = "right";
                decision.reason = "close_quarters_escape_right";
            }
        }
        
        // Territory expansion consideration
        if (!decision.shouldTurn && forwardDistance < 6) {
            if (leftDistance > rightDistance && leftDistance > forwardDistance + 2) {
                decision.shouldTurn = true;
                decision.direction = "left";
                decision.reason = "territory_expansion_left";
            } else if (rightDistance > forwardDistance + 2) {
                decision.shouldTurn = true;
                decision.direction = "right";
                decision.reason = "territory_expansion_right";
            }
        }
        
        return decision;
    }
    
    private double calculateThreatAlignment(int direction, double dx, double dz) {
        // Calculate how well this direction aligns with avoiding the threat
        // Higher values mean better positioning relative to the threat
        switch (direction) {
            case 0: return -dz; // North - better if threat is south
            case 1: return dx;  // East - better if threat is west
            case 2: return dz;  // South - better if threat is north
            case 3: return -dx; // West - better if threat is east
            default: return 0.0;
        }
    }
    
    private JsonNode createAction(String type, String direction, String reason) {
        ObjectNode action = objectMapper.createObjectNode();
        action.put("type", type);
        action.put("direction", direction);
        action.put("reason", reason);
        return action;
    }
}
