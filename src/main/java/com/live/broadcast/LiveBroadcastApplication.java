package com.live.broadcast;

import com.live.broadcast.server.HttpServer;
import com.live.broadcast.server.WebSocketServer;
import com.live.broadcast.server.RtmpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 直播系统启动类
 */
public class LiveBroadcastApplication {
    private static final Logger logger = LoggerFactory.getLogger(LiveBroadcastApplication.class);
    
    public static void main(String[] args) {
        logger.info("开始启动直播系统...");
        
        try {
            // 启动HTTP服务器 (端口8080)
            HttpServer httpServer = new HttpServer(8080);
            new Thread(httpServer::start, "HTTP-Server").start();
            
            // 启动WebSocket服务器 (端口8081)
            WebSocketServer webSocketServer = new WebSocketServer(8081);
            new Thread(webSocketServer::start, "WebSocket-Server").start();
            
            // 启动RTMP服务器 (端口1935)
            RtmpServer rtmpServer = new RtmpServer(1935);
            new Thread(rtmpServer::start, "RTMP-Server").start();
            
            logger.info("直播系统启动完成!");
            logger.info("HTTP服务器: http://localhost:8080");
            logger.info("WebSocket服务器: ws://localhost:8081");
            logger.info("RTMP服务器: rtmp://localhost:1935");
            
            // 添加优雅关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("正在关闭直播系统...");
                httpServer.stop();
                webSocketServer.stop();
                rtmpServer.stop();
                logger.info("直播系统已关闭");
            }));
            
        } catch (Exception e) {
            logger.error("启动直播系统失败", e);
            System.exit(1);
        }
    }
}
