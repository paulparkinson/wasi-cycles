package com.example.springbootkafkamongodb.repository;

import com.example.springbootkafkamongodb.model.PlayerInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for PlayerInfo
 * Accesses Oracle JSON Duality View via MongoDB API
 */
@Repository
public interface PlayerInfoRepository extends MongoRepository<PlayerInfo, String> {
    
    /**
     * Find player by email address
     */
    Optional<PlayerInfo> findByPlayerEmail(String playerEmail);
    
    /**
     * Find players by name (case-insensitive)
     */
    @Query("{'playerName': {$regex: ?0, $options: 'i'}}")
    List<PlayerInfo> findByPlayerNameIgnoreCase(String playerName);
    
    /**
     * Find top players by score (leaderboard)
     */
    @Query(value = "{}", sort = "{'playerScore': -1, 'gameTimestamp': -1}")
    List<PlayerInfo> findTopPlayersByScore();
    
    /**
     * Find players with score greater than specified value
     */
    List<PlayerInfo> findByPlayerScoreGreaterThan(Integer minScore);
    
    /**
     * Find players with score between range
     */
    List<PlayerInfo> findByPlayerScoreBetween(Integer minScore, Integer maxScore);
    
    /**
     * Count players with score above threshold
     */
    long countByPlayerScoreGreaterThan(Integer threshold);
}
