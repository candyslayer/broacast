package com.live.broadcast.model;

import io.netty.channel.Channel;

/**
 * 直播用户模型
 */
public class LiveUser {
    private String userId;
    private String username;
    private Channel channel;
    private long joinTime;
    
    public LiveUser(String userId, String username, Channel channel) {
        this.userId = userId;
        this.username = username;
        this.channel = channel;
        this.joinTime = System.currentTimeMillis();
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
    
    public Channel getChannel() {
        return channel;
    }
    
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    public long getJoinTime() {
        return joinTime;
    }
    
    public void setJoinTime(long joinTime) {
        this.joinTime = joinTime;
    }
    
    @Override
    public String toString() {
        return "LiveUser{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", joinTime=" + joinTime +
                '}';
    }
}
