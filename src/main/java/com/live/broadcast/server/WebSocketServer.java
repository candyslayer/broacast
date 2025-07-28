package com.live.broadcast.server;

import com.live.broadcast.handler.WebSocketServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket服务器 - 处理客户端连接和实时消息
 */
public class WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);
    
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    
    public WebSocketServer(int port) {
        this.port = port;
    }
    
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // HTTP编解码器
                            pipeline.addLast(new HttpServerCodec());
                            
                            // HTTP消息聚合器
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            
                            // 支持大数据流传输
                            pipeline.addLast(new ChunkedWriteHandler());
                            
                            // 空闲检测处理器 (读空闲60秒，写空闲30秒，读写空闲90秒)
                            pipeline.addLast(new IdleStateHandler(60, 30, 90, TimeUnit.SECONDS));
                            
                            // WebSocket协议处理器
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 65536));
                            
                            // 自定义业务处理器
                            pipeline.addLast(new WebSocketServerHandler());
                        }
                    });
            
            channelFuture = bootstrap.bind(port).sync();
            logger.info("WebSocket服务器启动成功，端口: {}", port);
            
            channelFuture.channel().closeFuture().sync();
            
        } catch (InterruptedException e) {
            logger.error("WebSocket服务器启动失败", e);
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
    }
    
    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("WebSocket服务器已停止");
    }
}
