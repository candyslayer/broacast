package com.live.broadcast.model;

/**
 * 消息模型
 */
public class Message {
    private String type;
    private String content;
    private String roomId;
    private String userId;
    private String username;
    private long timestamp;
    
    public Message() {
    }
    
    public Message(String type, String content, String roomId) {
        this.type = type;
        this.content = content;
        this.roomId = roomId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", content='" + content + '\'' +
                ", roomId='" + roomId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
