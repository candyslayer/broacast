package com.live.broadcast.stream;

import com.live.broadcast.manager.LiveRoomManager;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 流媒体管理器 - 处理音视频流的转发和分发
 */
public class StreamManager {
    private static final Logger logger = LoggerFactory.getLogger(StreamManager.class);
    private static volatile StreamManager instance;
    
    // 活跃的流 roomId -> StreamInfo
    private final Map<String, StreamInfo> activeStreams = new ConcurrentHashMap<>();
    
    // 观众连接 roomId -> List<StreamConsumer>
    private final Map<String, CopyOnWriteArrayList<StreamConsumer>> streamConsumers = new ConcurrentHashMap<>();
    
    private StreamManager() {}
    
    public static StreamManager getInstance() {
        if (instance == null) {
            synchronized (StreamManager.class) {
                if (instance == null) {
                    instance = new StreamManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 开始推流
     */
    public void startStream(String roomId, String streamKey) {
        StreamInfo streamInfo = new StreamInfo(roomId, streamKey);
        activeStreams.put(roomId, streamInfo);
        streamConsumers.putIfAbsent(roomId, new CopyOnWriteArrayList<>());
        
        logger.info("开始推流: roomId={}, streamKey={}", roomId, streamKey);
        
        // 通知房间管理器
        LiveRoomManager.getInstance().startLive(roomId);
    }
    
    /**
     * 停止推流
     */
    public void stopStream(String roomId) {
        StreamInfo streamInfo = activeStreams.remove(roomId);
        if (streamInfo != null) {
            // 通知所有观众流已结束
            CopyOnWriteArrayList<StreamConsumer> consumers = streamConsumers.get(roomId);
            if (consumers != null) {
                consumers.forEach(consumer -> consumer.onStreamEnd());
                consumers.clear();
            }
            
            logger.info("停止推流: roomId={}", roomId);
            
            // 通知房间管理器
            LiveRoomManager.getInstance().stopLive(roomId);
        }
    }
    
    /**
     * 处理流媒体数据
     */
    public void handleStreamData(String roomId, ByteBuf data, StreamDataType dataType) {
        StreamInfo streamInfo = activeStreams.get(roomId);
        if (streamInfo == null) {
            logger.warn("收到未知流的数据: roomId={}", roomId);
            return;
        }
        
        // 更新流信息
        streamInfo.updateLastDataTime();
        streamInfo.addDataSize(data.readableBytes());
        
        // 分发给所有观众
        CopyOnWriteArrayList<StreamConsumer> consumers = streamConsumers.get(roomId);
        if (consumers != null && !consumers.isEmpty()) {
            // 复制数据给每个消费者
            for (StreamConsumer consumer : consumers) {
                try {
                    ByteBuf dataCopy = data.copy();
                    consumer.onStreamData(dataCopy, dataType);
                } catch (Exception e) {
                    logger.error("分发流数据失败", e);
                    // 移除失效的消费者
                    consumers.remove(consumer);
                }
            }
        }
        
        logger.debug("分发流数据: roomId={}, 数据大小={}, 观众数={}", 
                roomId, data.readableBytes(), consumers != null ? consumers.size() : 0);
    }
    
    /**
     * 添加流消费者（观众）
     */
    public void addStreamConsumer(String roomId, StreamConsumer consumer) {
        streamConsumers.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(consumer);
        logger.info("添加流消费者: roomId={}", roomId);
    }
    
    /**
     * 移除流消费者
     */
    public void removeStreamConsumer(String roomId, StreamConsumer consumer) {
        CopyOnWriteArrayList<StreamConsumer> consumers = streamConsumers.get(roomId);
        if (consumers != null) {
            consumers.remove(consumer);
            logger.info("移除流消费者: roomId={}", roomId);
        }
    }
    
    /**
     * 获取流信息
     */
    public StreamInfo getStreamInfo(String roomId) {
        return activeStreams.get(roomId);
    }
    
    /**
     * 获取所有活跃流
     */
    public Map<String, StreamInfo> getActiveStreams() {
        return activeStreams;
    }
    
    /**
     * 流数据类型
     */
    public enum StreamDataType {
        VIDEO,      // 视频数据
        AUDIO,      // 音频数据
        METADATA    // 元数据
    }
    
    /**
     * 流消费者接口
     */
    public interface StreamConsumer {
        /**
         * 接收流数据
         */
        void onStreamData(ByteBuf data, StreamDataType dataType);
        
        /**
         * 流结束通知
         */
        void onStreamEnd();
    }
    
    /**
     * 流信息
     */
    public static class StreamInfo {
        private final String roomId;
        private final String streamKey;
        private final long startTime;
        private volatile long lastDataTime;
        private volatile long totalDataSize;
        private volatile int bitrate;
        
        public StreamInfo(String roomId, String streamKey) {
            this.roomId = roomId;
            this.streamKey = streamKey;
            this.startTime = System.currentTimeMillis();
            this.lastDataTime = startTime;
            this.totalDataSize = 0;
        }
        
        public void updateLastDataTime() {
            this.lastDataTime = System.currentTimeMillis();
        }
        
        public void addDataSize(int size) {
            this.totalDataSize += size;
            // 简单的码率计算（每5秒更新一次）
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            if (duration > 0) {
                this.bitrate = (int) (totalDataSize * 8 / duration); // bps
            }
        }
        
        // Getters
        public String getRoomId() { return roomId; }
        public String getStreamKey() { return streamKey; }
        public long getStartTime() { return startTime; }
        public long getLastDataTime() { return lastDataTime; }
        public long getTotalDataSize() { return totalDataSize; }
        public int getBitrate() { return bitrate; }
        
        public boolean isActive() {
            return System.currentTimeMillis() - lastDataTime < 30000; // 30秒无数据认为断流
        }
    }
}
