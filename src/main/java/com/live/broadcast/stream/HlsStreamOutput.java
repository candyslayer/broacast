package com.live.broadcast.stream;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HLS流输出器 - 将RTMP流转换为HLS格式
 * 这是一个简化的实现，实际生产环境建议使用FFmpeg等专业工具
 */
public class HlsStreamOutput implements StreamManager.StreamConsumer {
    private static final Logger logger = LoggerFactory.getLogger(HlsStreamOutput.class);
    
    private final String roomId;
    private final String outputDir;
    private final AtomicInteger segmentIndex = new AtomicInteger(0);
    private final List<String> segmentFiles = new ArrayList<>();
    private final int segmentDuration = 10; // 10秒一个片段
    
    private FileOutputStream currentSegment;
    private long segmentStartTime;
    private boolean isRunning = false;
    
    public HlsStreamOutput(String roomId) {
        this.roomId = roomId;
        this.outputDir = "hls/" + roomId;
        
        // 创建输出目录
        try {
            Files.createDirectories(Paths.get(outputDir));
            this.isRunning = true;
            logger.info("HLS输出器创建成功: roomId={}, outputDir={}", roomId, outputDir);
        } catch (IOException e) {
            logger.error("创建HLS输出目录失败", e);
        }
    }
    
    @Override
    public void onStreamData(ByteBuf data, StreamManager.StreamDataType dataType) {
        if (!isRunning) {
            data.release();
            return;
        }
        
        try {
            // 检查是否需要创建新的片段
            if (shouldCreateNewSegment()) {
                createNewSegment();
            }
            
            // 写入数据到当前片段
            if (currentSegment != null && dataType == StreamManager.StreamDataType.VIDEO) {
                byte[] bytes = new byte[data.readableBytes()];
                data.readBytes(bytes);
                currentSegment.write(bytes);
                currentSegment.flush();
            }
            
        } catch (IOException e) {
            logger.error("写入HLS片段失败", e);
        } finally {
            data.release();
        }
    }
    
    @Override
    public void onStreamEnd() {
        logger.info("HLS流结束: roomId={}", roomId);
        closeCurrentSegment();
        updatePlaylist(true); // 最后一次更新播放列表
        isRunning = false;
    }
    
    /**
     * 检查是否需要创建新片段
     */
    private boolean shouldCreateNewSegment() {
        if (currentSegment == null) {
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - segmentStartTime) >= (segmentDuration * 1000);
    }
    
    /**
     * 创建新的HLS片段
     */
    private void createNewSegment() throws IOException {
        // 关闭当前片段
        closeCurrentSegment();
        
        // 创建新片段
        int index = segmentIndex.getAndIncrement();
        String segmentFileName = String.format("segment_%d.ts", index);
        String segmentPath = outputDir + "/" + segmentFileName;
        
        currentSegment = new FileOutputStream(segmentPath);
        segmentStartTime = System.currentTimeMillis();
        segmentFiles.add(segmentFileName);
        
        logger.debug("创建新HLS片段: {}", segmentFileName);
        
        // 更新播放列表
        updatePlaylist(false);
        
        // 保持最近的10个片段
        if (segmentFiles.size() > 10) {
            String oldSegment = segmentFiles.remove(0);
            try {
                Files.deleteIfExists(Paths.get(outputDir + "/" + oldSegment));
            } catch (IOException e) {
                logger.warn("删除旧片段失败: {}", oldSegment, e);
            }
        }
    }
    
    /**
     * 关闭当前片段
     */
    private void closeCurrentSegment() {
        if (currentSegment != null) {
            try {
                currentSegment.close();
                currentSegment = null;
            } catch (IOException e) {
                logger.error("关闭HLS片段失败", e);
            }
        }
    }
    
    /**
     * 更新HLS播放列表
     */
    private void updatePlaylist(boolean isEnd) {
        try {
            String playlistPath = outputDir + "/playlist.m3u8";
            StringBuilder playlist = new StringBuilder();
            
            // M3U8头部
            playlist.append("#EXTM3U\n");
            playlist.append("#EXT-X-VERSION:3\n");
            playlist.append("#EXT-X-TARGETDURATION:").append(segmentDuration).append("\n");
            playlist.append("#EXT-X-MEDIA-SEQUENCE:").append(Math.max(0, segmentIndex.get() - segmentFiles.size())).append("\n");
            
            // 片段列表
            for (String segmentFile : segmentFiles) {
                playlist.append("#EXTINF:").append(segmentDuration).append(".0,\n");
                playlist.append(segmentFile).append("\n");
            }
            
            // 结束标记
            if (isEnd) {
                playlist.append("#EXT-X-ENDLIST\n");
            }
            
            // 写入文件
            Files.write(Paths.get(playlistPath), playlist.toString().getBytes());
            
            logger.debug("更新HLS播放列表: 片段数={}, isEnd={}", segmentFiles.size(), isEnd);
            
        } catch (IOException e) {
            logger.error("更新HLS播放列表失败", e);
        }
    }
    
    /**
     * 获取HLS播放地址
     */
    public String getPlaylistUrl() {
        return "/hls/" + roomId + "/playlist.m3u8";
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        closeCurrentSegment();
        
        try {
            // 删除所有片段和播放列表
            Path outputPath = Paths.get(outputDir);
            if (Files.exists(outputPath)) {
                Files.walk(outputPath)
                        .sorted((a, b) -> b.compareTo(a)) // 先删除文件再删除目录
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("删除文件失败: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.error("清理HLS文件失败", e);
        }
        
        isRunning = false;
        logger.info("HLS输出器已清理: roomId={}", roomId);
    }
}
