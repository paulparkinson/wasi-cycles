package com.example.springbootkafkamongodb.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class TxEventQMessage {

    @JsonProperty("msg_state")
    private String msgState;

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("corr_id")
    private String corrId;

    @JsonProperty("consumer_name")
    private String consumerName;

    @JsonProperty("enq_timestamp")
    private LocalDateTime enqTimestamp;

    @JsonProperty("deq_timestamp")
    private LocalDateTime deqTimestamp;

    // Default constructor
    public TxEventQMessage() {}

    // Constructor with all fields
    public TxEventQMessage(String msgState, String msgId, String corrId, String consumerName,
                          LocalDateTime enqTimestamp, LocalDateTime deqTimestamp) {
        this.msgState = msgState;
        this.msgId = msgId;
        this.corrId = corrId;
        this.consumerName = consumerName;
        this.enqTimestamp = enqTimestamp;
        this.deqTimestamp = deqTimestamp;
    }

    // Getters and setters
    public String getMsgState() {
        return msgState;
    }

    public void setMsgState(String msgState) {
        this.msgState = msgState;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getCorrId() {
        return corrId;
    }

    public void setCorrId(String corrId) {
        this.corrId = corrId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public LocalDateTime getEnqTimestamp() {
        return enqTimestamp;
    }

    public void setEnqTimestamp(LocalDateTime enqTimestamp) {
        this.enqTimestamp = enqTimestamp;
    }

    public LocalDateTime getDeqTimestamp() {
        return deqTimestamp;
    }

    public void setDeqTimestamp(LocalDateTime deqTimestamp) {
        this.deqTimestamp = deqTimestamp;
    }

    @Override
    public String toString() {
        return "TxEventQMessage{" +
                "msgState='" + msgState + '\'' +
                ", msgId='" + msgId + '\'' +
                ", corrId='" + corrId + '\'' +
                ", consumerName='" + consumerName + '\'' +
                ", enqTimestamp=" + enqTimestamp +
                ", deqTimestamp=" + deqTimestamp +
                '}';
    }
}
