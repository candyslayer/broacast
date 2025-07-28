package com.live.broadcast.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.broadcast.model.LiveRoom;
import com.live.broadcast.model.LiveUser;
import com.live.broadcast.model.Message;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直播间管理器 - 单例模式
 */
public class LiveRoomManager {
    private static final Logger logger = LoggerFactory.getLogger(LiveRoomManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static volatile LiveRoomManager instance;
    
    // 直播间映射 roomId -> LiveRoom
    private final Map<String, LiveRoom> rooms = new ConcurrentHashMap<>();
    
    // 用户与房间的映射 Channel -> roomId
    private final Map<Channel, String> userRoomMap = new ConcurrentHashMap<>();
    
    private LiveRoomManager() {
        // 创建一些默认直播间
        createDefaultRooms();
    }
    
    public static LiveRoomManager getInstance() {
        if (instance == null) {
            synchronized (LiveRoomManager.class) {
                if (instance == null) {
                    instance = new LiveRoomManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 创建默认直播间
     */
    private void createDefaultRooms() {
        createRoom("room1", "游戏直播间", "streamer1", "主播小明");
        createRoom("room2", "音乐直播间", "streamer2", "主播小红");
        createRoom("room3", "聊天直播间", "streamer3", "主播小强");
        logger.info("创建了 {} 个默认直播间", rooms.size());
    }
    
    /**
     * 创建直播间
     */
    public LiveRoom createRoom(String roomId, String title, String streamerId, String streamerName) {
        LiveRoom room = new LiveRoom(roomId, title, streamerId, streamerName);
        rooms.put(roomId, room);
        logger.info("创建直播间: {}", room);
        return room;
    }
    
    /**
     * 获取直播间
     */
    public LiveRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    /**
     * 获取所有直播间
     */
    public Map<String, LiveRoom> getAllRooms() {
        return rooms;
    }
    
    /**
     * 用户加入直播间
     */
    public void joinRoom(String roomId, LiveUser user) {
        LiveRoom room = rooms.get(roomId);
        if (room == null) {
            logger.warn("直播间不存在: {}", roomId);
            return;
        }
        
        // 如果用户已在其他房间，先离开
        String currentRoomId = userRoomMap.get(user.getChannel());
        if (currentRoomId != null && !currentRoomId.equals(roomId)) {
            leaveRoom(user, user.getChannel());
        }
        
        // 加入新房间
        room.addViewer(user);
        userRoomMap.put(user.getChannel(), roomId);
        
        // 广播用户加入消息
        Message joinMessage = new Message("user_join", user.getUsername() + " 加入了直播间", roomId);
        joinMessage.setUserId(user.getUserId());
        joinMessage.setUsername(user.getUsername());
        broadcastToRoom(roomId, joinMessage);
        
        // 发送房间信息给新用户
        sendRoomInfo(user.getChannel(), room);
        
        logger.info("用户 {} 加入直播间 {}, 当前观众数: {}", 
                user.getUsername(), roomId, room.getViewerCount());
    }
    
    /**
     * 用户离开直播间
     */
    public void leaveRoom(LiveUser user, Channel channel) {
        String roomId = userRoomMap.remove(channel);
        if (roomId == null) {
            return;
        }
        
        LiveRoom room = rooms.get(roomId);
        if (room != null) {
            room.removeViewer(user);
            
            // 广播用户离开消息
            Message leaveMessage = new Message("user_leave", user.getUsername() + " 离开了直播间", roomId);
            leaveMessage.setUserId(user.getUserId());
            leaveMessage.setUsername(user.getUsername());
            broadcastToRoom(roomId, leaveMessage);
            
            logger.info("用户 {} 离开直播间 {}, 当前观众数: {}", 
                    user.getUsername(), roomId, room.getViewerCount());
        }
    }
    
    /**
     * 向房间广播消息
     */
    public void broadcastToRoom(String roomId, Message message) {
        LiveRoom room = rooms.get(roomId);
        if (room == null) {
            logger.warn("广播消息失败，房间不存在: {}", roomId);
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(message);
            TextWebSocketFrame frame = new TextWebSocketFrame(json);
            
            room.getViewers().forEach(viewer -> {
                Channel channel = viewer.getChannel();
                if (channel.isActive()) {
                    channel.writeAndFlush(frame.retain());
                } else {
                    // 清理无效的连接
                    room.removeViewer(viewer);
                    userRoomMap.remove(channel);
                }
            });
            
            frame.release();
        } catch (Exception e) {
            logger.error("广播消息失败", e);
        }
    }
    
    /**
     * 发送房间信息
     */
    private void sendRoomInfo(Channel channel, LiveRoom room) {
        try {
            Message roomInfoMessage = new Message("room_info", "房间信息", room.getRoomId());
            // 这里可以添加更多房间信息，比如观众列表等
            
            String json = objectMapper.writeValueAsString(roomInfoMessage);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            logger.error("发送房间信息失败", e);
        }
    }
    
    /**
     * 开始直播
     */
    public void startLive(String roomId) {
        LiveRoom room = rooms.get(roomId);
        if (room != null) {
            room.setLive(true);
            Message liveStartMessage = new Message("live_start", "直播开始", roomId);
            broadcastToRoom(roomId, liveStartMessage);
            logger.info("直播间 {} 开始直播", roomId);
        }
    }
    
    /**
     * 停止直播
     */
    public void stopLive(String roomId) {
        LiveRoom room = rooms.get(roomId);
        if (room != null) {
            room.setLive(false);
            Message liveStopMessage = new Message("live_stop", "直播结束", roomId);
            broadcastToRoom(roomId, liveStopMessage);
            logger.info("直播间 {} 停止直播", roomId);
        }
    }
    
    /**
     * 获取统计信息
     */
    public void printStatistics() {
        logger.info("=== 直播系统统计 ===");
        logger.info("直播间总数: {}", rooms.size());
        logger.info("在线用户总数: {}", userRoomMap.size());
        
        rooms.forEach((roomId, room) -> {
            logger.info("房间 {}: {} 观众, 状态: {}", 
                    roomId, room.getViewerCount(), room.isLive() ? "直播中" : "未直播");
        });
        logger.info("==================");
    }
}
