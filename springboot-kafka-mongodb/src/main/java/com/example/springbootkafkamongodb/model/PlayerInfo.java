package com.example.springbootkafkamongodb.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * PlayerInfo entity representing WasiCycles game player information
 * Maps to both MongoDB collection and Oracle JSON Duality View
 */
@Document(collection = "WASICYCLES_PLAYERINFO_DV")
public class PlayerInfo {
    
    @Id
    @JsonProperty("_id")
    @JsonAlias("playerId")
    private String playerId;
    
    @Field("playerName")
    @JsonProperty("playerName")
    private String playerName;
    
    @Field("playerEmail")
    @JsonProperty("playerEmail")
    private String playerEmail;
    
    @Field("gameTimestamp")
    @JsonProperty("gameTimestamp")
    private LocalDateTime gameTimestamp;
    
    @Field("playerScore")
    @JsonProperty("playerScore")
    private Integer playerScore;
    
    @Field("runtime")
    @JsonProperty("runtime")
    private String runtime;
    
    @Field("tshirtSize")
    @JsonProperty("tshirtSize")
    private String tshirtSize;
    
    // Default constructor
    public PlayerInfo() {
        this.gameTimestamp = LocalDateTime.now();
        this.playerScore = 0;
    }
    
    // Constructor with required fields
    public PlayerInfo(String playerId, String playerName, String playerEmail) {
        this();
        this.playerId = playerId;
        this.playerName = playerName;
        this.playerEmail = playerEmail;
    }
    
    // Constructor with all fields
    public PlayerInfo(String playerId, String playerName, String playerEmail, String runtime, String tshirtSize) {
        this(playerId, playerName, playerEmail);
        this.runtime = runtime;
        this.tshirtSize = tshirtSize;
    }
    
    // Constructor with score
    public PlayerInfo(String playerId, String playerName, String playerEmail, Integer playerScore) {
        this(playerId, playerName, playerEmail);
        this.playerScore = playerScore;
    }
    
    // Getters and Setters
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public String getPlayerEmail() {
        return playerEmail;
    }
    
    public void setPlayerEmail(String playerEmail) {
        this.playerEmail = playerEmail;
    }
    
    public LocalDateTime getGameTimestamp() {
        return gameTimestamp;
    }
    
    public void setGameTimestamp(LocalDateTime gameTimestamp) {
        this.gameTimestamp = gameTimestamp;
    }
    
    public Integer getPlayerScore() {
        return playerScore;
    }
    
    public void setPlayerScore(Integer playerScore) {
        this.playerScore = playerScore;
    }
    
    public String getRuntime() {
        return runtime;
    }
    
    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }
    
    public String getTshirtSize() {
        return tshirtSize;
    }
    
    public void setTshirtSize(String tshirtSize) {
        this.tshirtSize = tshirtSize;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerInfo that = (PlayerInfo) o;
        return Objects.equals(playerId, that.playerId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }
    
    @Override
    public String toString() {
        return "PlayerInfo{" +
                "playerId='" + playerId + '\'' +
                ", playerName='" + playerName + '\'' +
                ", playerEmail='" + playerEmail + '\'' +
                ", gameTimestamp=" + gameTimestamp +
                ", playerScore=" + playerScore +
                ", runtime='" + runtime + '\'' +
                ", tshirtSize='" + tshirtSize + '\'' +
                '}';
    }
}
