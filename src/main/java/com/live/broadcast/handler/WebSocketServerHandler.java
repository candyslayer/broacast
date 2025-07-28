package com.live.broadcast.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.broadcast.manager.LiveRoomManager;
import com.live.broadcast.model.LiveUser;
import com.live.broadcast.model.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket消息处理器
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private LiveUser user;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端连接: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端断开连接: {}", ctx.channel().remoteAddress());
        
        // 用户离开直播间
        if (user != null) {
            LiveRoomManager.getInstance().leaveRoom(user, ctx.channel());
        }
        
        super.channelInactive(ctx);
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            logger.info("WebSocket握手完成: {}", ctx.channel().remoteAddress());
        } else if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            if (idleEvent.state() == IdleState.READER_IDLE) {
                logger.warn("客户端读取超时，关闭连接: {}", ctx.channel().remoteAddress());
                ctx.channel().close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        String text = msg.text();
        logger.debug("收到消息: {}", text);
        
        try {
            JsonNode jsonNode = objectMapper.readTree(text);
            String type = jsonNode.get("type").asText();
            
            switch (type) {
                case "join":
                    handleJoin(ctx, jsonNode);
                    break;
                case "leave":
                    handleLeave(ctx, jsonNode);
                    break;
                case "chat":
                    handleChat(ctx, jsonNode);
                    break;
                case "heartbeat":
                    handleHeartbeat(ctx);
                    break;
                default:
                    logger.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            logger.error("处理消息失败: {}", text, e);
            sendError(ctx, "消息格式错误");
        }
    }
    
    /**
     * 处理加入直播间
     */
    private void handleJoin(ChannelHandlerContext ctx, JsonNode jsonNode) {
        String roomId = jsonNode.get("roomId").asText();
        String userId = jsonNode.get("userId").asText();
        String username = jsonNode.get("username").asText();
        
        user = new LiveUser(userId, username, ctx.channel());
        LiveRoomManager.getInstance().joinRoom(roomId, user);
        
        // 发送加入成功消息
        Message joinMessage = new Message("join_success", "加入直播间成功", roomId);
        sendMessage(ctx, joinMessage);
        
        logger.info("用户 {} 加入直播间: {}", username, roomId);
    }
    
    /**
     * 处理离开直播间
     */
    private void handleLeave(ChannelHandlerContext ctx, JsonNode jsonNode) {
        if (user != null) {
            LiveRoomManager.getInstance().leaveRoom(user, ctx.channel());
            logger.info("用户 {} 离开直播间", user.getUsername());
        }
    }
    
    /**
     * 处理聊天消息
     */
    private void handleChat(ChannelHandlerContext ctx, JsonNode jsonNode) {
        if (user == null) {
            sendError(ctx, "请先加入直播间");
            return;
        }
        
        String content = jsonNode.get("content").asText();
        String roomId = jsonNode.get("roomId").asText();
        
        Message chatMessage = new Message("chat", content, roomId);
        chatMessage.setUserId(user.getUserId());
        chatMessage.setUsername(user.getUsername());
        chatMessage.setTimestamp(System.currentTimeMillis());
        
        // 广播消息到直播间所有用户
        LiveRoomManager.getInstance().broadcastToRoom(roomId, chatMessage);
        
        logger.info("用户 {} 在直播间 {} 发送消息: {}", user.getUsername(), roomId, content);
    }
    
    /**
     * 处理心跳
     */
    private void handleHeartbeat(ChannelHandlerContext ctx) {
        Message pongMessage = new Message("pong", "pong", null);
        sendMessage(ctx, pongMessage);
    }
    
    /**
     * 发送消息
     */
    private void sendMessage(ChannelHandlerContext ctx, Message message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            logger.error("发送消息失败", e);
        }
    }
    
    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, String error) {
        Message errorMessage = new Message("error", error, null);
        sendMessage(ctx, errorMessage);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocket处理异常", cause);
        ctx.close();
    }
}
