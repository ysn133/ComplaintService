package com.mycompany.dto;

public class WebRTCSignalDTO {
    private String callId;
    private String type; // "offer", "answer", "ice-candidate"
    private String data; // SDP or ICE candidate data
    private Long fromUserId;
    private Long toUserId;
    private String toUserType; // "CLIENT" or "SUPPORT"

    // Getters and setters
    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getToUserType() {
        return toUserType;
    }

    public void setToUserType(String toUserType) {
        this.toUserType = toUserType;
    }
}