package com.live.broadcast.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直播间模型
 */
public class LiveRoom {
    private String roomId;
    private String title;
    private String streamerId;
    private String streamerName;
    private boolean isLive;
    private long createTime;
    private int viewerCount;
    
    // 观众列表
    private final Set<LiveUser> viewers = ConcurrentHashMap.newKeySet();
    
    public LiveRoom(String roomId, String title, String streamerId, String streamerName) {
        this.roomId = roomId;
        this.title = title;
        this.streamerId = streamerId;
        this.streamerName = streamerName;
        this.createTime = System.currentTimeMillis();
        this.isLive = false;
        this.viewerCount = 0;
    }
    
    /**
     * 添加观众
     */
    public synchronized void addViewer(LiveUser viewer) {
        viewers.add(viewer);
        viewerCount = viewers.size();
    }
    
    /**
     * 移除观众
     */
    public synchronized void removeViewer(LiveUser viewer) {
        viewers.remove(viewer);
        viewerCount = viewers.size();
    }
    
    /**
     * 获取所有观众
     */
    public Set<LiveUser> getViewers() {
        return viewers;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getStreamerId() {
        return streamerId;
    }
    
    public void setStreamerId(String streamerId) {
        this.streamerId = streamerId;
    }
    
    public String getStreamerName() {
        return streamerName;
    }
    
    public void setStreamerName(String streamerName) {
        this.streamerName = streamerName;
    }
    
    public boolean isLive() {
        return isLive;
    }
    
    public void setLive(boolean live) {
        isLive = live;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public int getViewerCount() {
        return viewerCount;
    }
    
    public void setViewerCount(int viewerCount) {
        this.viewerCount = viewerCount;
    }
    
    @Override
    public String toString() {
        return "LiveRoom{" +
                "roomId='" + roomId + '\'' +
                ", title='" + title + '\'' +
                ", streamerId='" + streamerId + '\'' +
                ", streamerName='" + streamerName + '\'' +
                ", isLive=" + isLive +
                ", createTime=" + createTime +
                ", viewerCount=" + viewerCount +
                '}';
    }
}
