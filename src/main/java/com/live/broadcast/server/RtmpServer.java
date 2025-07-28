package com.live.broadcast.server;

import com.live.broadcast.handler.RtmpServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMP服务器 - 接收推流数据
 * 注意：这是一个简化的RTMP服务器实现，完整的RTMP协议需要更复杂的处理
 */
public class RtmpServer {
    private static final Logger logger = LoggerFactory.getLogger(RtmpServer.class);
    
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    
    public RtmpServer(int port) {
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
                            
                            // 添加RTMP处理器
                            pipeline.addLast(new RtmpServerHandler());
                        }
                    });
            
            channelFuture = bootstrap.bind(port).sync();
            logger.info("RTMP服务器启动成功，端口: {}", port);
            
            channelFuture.channel().closeFuture().sync();
            
        } catch (InterruptedException e) {
            logger.error("RTMP服务器启动失败", e);
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
        logger.info("RTMP服务器已停止");
    }
}
