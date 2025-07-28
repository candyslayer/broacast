package com.live.broadcast.record;

import com.live.broadcast.stream.StreamManager;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 录播管理器 - 负责录制直播流并保存为文件
 */
public class RecordManager implements StreamManager.StreamConsumer {
    private static final Logger logger = LoggerFactory.getLogger(RecordManager.class);
    
    private final String roomId;
    private final String recordDir;
    private final String recordFileName;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicLong recordedSize = new AtomicLong(0);
    
    private FileOutputStream videoOutputStream;
    private FileOutputStream audioOutputStream;
    private FileOutputStream metadataOutputStream;
    private long recordStartTime;
    private RecordInfo recordInfo;
    
    public RecordManager(String roomId) {
        this.roomId = roomId;
        this.recordDir = "records/" + roomId;
        
        // 生成录制文件名 (时间戳格式)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        this.recordFileName = "record_" + timestamp;
        
        // 创建录制目录
        try {
            Files.createDirectories(Paths.get(recordDir));
            logger.info("创建录制目录: {}", recordDir);
        } catch (IOException e) {
            logger.error("创建录制目录失败", e);
        }
    }
    
    /**
     * 开始录制
     */
    public synchronized boolean startRecord() {
        if (isRecording.get()) {
            logger.warn("录制已在进行中: roomId={}", roomId);
            return false;
        }
        
        try {
            // 创建输出流
            String videoPath = recordDir + "/" + recordFileName + "_video.flv";
            String audioPath = recordDir + "/" + recordFileName + "_audio.flv";
            String metadataPath = recordDir + "/" + recordFileName + "_metadata.json";
            
            videoOutputStream = new FileOutputStream(videoPath);
            audioOutputStream = new FileOutputStream(audioPath);
            metadataOutputStream = new FileOutputStream(metadataPath);
            
            recordStartTime = System.currentTimeMillis();
            recordedSize.set(0);
            isRecording.set(true);
            
            // 创建录制信息
            recordInfo = new RecordInfo(roomId, recordFileName, recordStartTime);
            
            logger.info("开始录制: roomId={}, 文件={}", roomId, recordFileName);
            return true;
            
        } catch (IOException e) {
            logger.error("开始录制失败", e);
            cleanup();
            return false;
        }
    }
    
    /**
     * 停止录制
     */
    public synchronized boolean stopRecord() {
        if (!isRecording.get()) {
            logger.warn("没有进行中的录制: roomId={}", roomId);
            return false;
        }
        
        try {
            // 关闭输出流
            closeOutputStreams();
            
            // 更新录制信息
            if (recordInfo != null) {
                recordInfo.setEndTime(System.currentTimeMillis());
                recordInfo.setFileSize(recordedSize.get());
                recordInfo.setStatus(RecordInfo.Status.COMPLETED);
                
                // 保存录制信息到数据库或文件
                saveRecordInfo();
                
                // 生成合并后的完整视频文件
                mergeRecordFiles();
            }
            
            isRecording.set(false);
            logger.info("录制完成: roomId={}, 时长={}ms, 大小={}bytes", 
                    roomId, 
                    recordInfo.getDuration(), 
                    recordInfo.getFileSize());
            return true;
            
        } catch (Exception e) {
            logger.error("停止录制失败", e);
            if (recordInfo != null) {
                recordInfo.setStatus(RecordInfo.Status.FAILED);
            }
            return false;
        }
    }
    
    @Override
    public void onStreamData(ByteBuf data, StreamManager.StreamDataType dataType) {
        if (!isRecording.get()) {
            data.release();
            return;
        }
        
        try {
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            
            // 根据数据类型写入不同的文件
            switch (dataType) {
                case VIDEO:
                    if (videoOutputStream != null) {
                        videoOutputStream.write(bytes);
                        videoOutputStream.flush();
                    }
                    break;
                case AUDIO:
                    if (audioOutputStream != null) {
                        audioOutputStream.write(bytes);
                        audioOutputStream.flush();
                    }
                    break;
                case METADATA:
                    if (metadataOutputStream != null) {
                        metadataOutputStream.write(bytes);
                        metadataOutputStream.flush();
                    }
                    break;
            }
            
            recordedSize.addAndGet(bytes.length);
            
        } catch (IOException e) {
            logger.error("写入录制数据失败", e);
            // 录制出错，停止录制
            stopRecord();
        } finally {
            data.release();
        }
    }
    
    @Override
    public void onStreamEnd() {
        logger.info("直播流结束，停止录制: roomId={}", roomId);
        stopRecord();
    }
    
    /**
     * 关闭输出流
     */
    private void closeOutputStreams() {
        try {
            if (videoOutputStream != null) {
                videoOutputStream.close();
                videoOutputStream = null;
            }
            if (audioOutputStream != null) {
                audioOutputStream.close();
                audioOutputStream = null;
            }
            if (metadataOutputStream != null) {
                metadataOutputStream.close();
                metadataOutputStream = null;
            }
        } catch (IOException e) {
            logger.error("关闭录制输出流失败", e);
        }
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        closeOutputStreams();
        isRecording.set(false);
    }
    
    /**
     * 保存录制信息
     */
    private void saveRecordInfo() {
        try {
            // 这里可以保存到数据库，现在先保存到JSON文件
            String infoPath = recordDir + "/" + recordFileName + "_info.json";
            String json = recordInfo.toJson();
            Files.write(Paths.get(infoPath), json.getBytes());
            
            logger.debug("保存录制信息: {}", infoPath);
        } catch (IOException e) {
            logger.error("保存录制信息失败", e);
        }
    }
    
    /**
     * 合并录制文件 (使用FFmpeg或简单的文件合并)
     */
    private void mergeRecordFiles() {
        try {
            String videoPath = recordDir + "/" + recordFileName + "_video.flv";
            String audioPath = recordDir + "/" + recordFileName + "_audio.flv";
            String outputPath = recordDir + "/" + recordFileName + ".mp4";
            
            // 简化实现：这里应该使用FFmpeg进行音视频合并和转码
            // ffmpeg -i video.flv -i audio.flv -c copy output.mp4
            
            // 临时实现：直接复制视频文件作为最终文件
            if (Files.exists(Paths.get(videoPath))) {
                Files.copy(Paths.get(videoPath), Paths.get(outputPath));
                recordInfo.setFinalFilePath(outputPath);
                logger.info("生成最终录制文件: {}", outputPath);
            }
            
        } catch (IOException e) {
            logger.error("合并录制文件失败", e);
        }
    }
    
    /**
     * 获取录制状态
     */
    public boolean isRecording() {
        return isRecording.get();
    }
    
    /**
     * 获取录制信息
     */
    public RecordInfo getRecordInfo() {
        return recordInfo;
    }
    
    /**
     * 获取已录制大小
     */
    public long getRecordedSize() {
        return recordedSize.get();
    }
    
    /**
     * 获取录制时长（毫秒）
     */
    public long getRecordDuration() {
        if (!isRecording.get() || recordStartTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - recordStartTime;
    }
}
