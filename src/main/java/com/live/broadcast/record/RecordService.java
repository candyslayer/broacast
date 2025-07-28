package com.live.broadcast.record;

import com.live.broadcast.stream.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 录播服务管理器 - 管理所有录制任务
 */
public class RecordService {
    private static final Logger logger = LoggerFactory.getLogger(RecordService.class);
    private static volatile RecordService instance;
    
    // 活跃的录制任务 roomId -> RecordManager
    private final Map<String, RecordManager> activeRecords = new ConcurrentHashMap<>();
    
    // 录制配置
    private boolean autoRecord = false; // 是否自动录制
    private long maxRecordDuration = 4 * 60 * 60 * 1000; // 最大录制时长4小时
    private long maxFileSize = 2L * 1024 * 1024 * 1024; // 最大文件大小2GB
    
    private RecordService() {
        // 启动时清理异常状态的录制文件
        cleanupRecords();
    }
    
    public static RecordService getInstance() {
        if (instance == null) {
            synchronized (RecordService.class) {
                if (instance == null) {
                    instance = new RecordService();
                }
            }
        }
        return instance;
    }
    
    /**
     * 开始录制
     */
    public boolean startRecord(String roomId) {
        if (activeRecords.containsKey(roomId)) {
            logger.warn("房间已在录制中: {}", roomId);
            return false;
        }
        
        try {
            RecordManager recordManager = new RecordManager(roomId);
            if (recordManager.startRecord()) {
                activeRecords.put(roomId, recordManager);
                
                // 注册为流消费者
                StreamManager.getInstance().addStreamConsumer(roomId, recordManager);
                
                logger.info("开始录制房间: {}", roomId);
                return true;
            }
        } catch (Exception e) {
            logger.error("开始录制失败: roomId={}", roomId, e);
        }
        
        return false;
    }
    
    /**
     * 停止录制
     */
    public boolean stopRecord(String roomId) {
        RecordManager recordManager = activeRecords.remove(roomId);
        if (recordManager == null) {
            logger.warn("房间未在录制中: {}", roomId);
            return false;
        }
        
        try {
            // 移除流消费者
            StreamManager.getInstance().removeStreamConsumer(roomId, recordManager);
            
            // 停止录制
            boolean success = recordManager.stopRecord();
            
            logger.info("停止录制房间: {}, 成功={}", roomId, success);
            return success;
            
        } catch (Exception e) {
            logger.error("停止录制失败: roomId={}", roomId, e);
            return false;
        }
    }
    
    /**
     * 自动开始录制（当直播开始时）
     */
    public void onLiveStart(String roomId) {
        if (autoRecord) {
            logger.info("直播开始，自动开始录制: {}", roomId);
            startRecord(roomId);
        }
    }
    
    /**
     * 自动停止录制（当直播结束时）
     */
    public void onLiveStop(String roomId) {
        if (activeRecords.containsKey(roomId)) {
            logger.info("直播结束，停止录制: {}", roomId);
            stopRecord(roomId);
        }
    }
    
    /**
     * 获取录制状态
     */
    public boolean isRecording(String roomId) {
        RecordManager recordManager = activeRecords.get(roomId);
        return recordManager != null && recordManager.isRecording();
    }
    
    /**
     * 获取录制信息
     */
    public RecordInfo getRecordInfo(String roomId) {
        RecordManager recordManager = activeRecords.get(roomId);
        return recordManager != null ? recordManager.getRecordInfo() : null;
    }
    
    /**
     * 获取所有活跃录制
     */
    public Map<String, RecordInfo> getAllActiveRecords() {
        return activeRecords.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getRecordInfo()
                ));
    }
    
    /**
     * 获取历史录制记录
     */
    public List<RecordInfo> getHistoryRecords(String roomId) {
        List<RecordInfo> records = new ArrayList<>();
        
        try {
            String recordDir = "records/" + roomId;
            Path dirPath = Paths.get(recordDir);
            
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                Files.list(dirPath)
                        .filter(path -> path.toString().endsWith("_info.json"))
                        .forEach(path -> {
                            try {
                                String json = new String(Files.readAllBytes(path));
                                RecordInfo recordInfo = RecordInfo.fromJson(json);
                                if (recordInfo != null) {
                                    records.add(recordInfo);
                                }
                            } catch (IOException e) {
                                logger.warn("读取录制信息失败: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.error("获取历史录制记录失败: roomId={}", roomId, e);
        }
        
        // 按开始时间倒序排列
        records.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        return records;
    }
    
    /**
     * 获取所有房间的历史录制记录
     */
    public List<RecordInfo> getAllHistoryRecords() {
        List<RecordInfo> allRecords = new ArrayList<>();
        
        try {
            Path recordsDir = Paths.get("records");
            if (Files.exists(recordsDir) && Files.isDirectory(recordsDir)) {
                Files.list(recordsDir)
                        .filter(Files::isDirectory)
                        .forEach(roomDir -> {
                            String roomId = roomDir.getFileName().toString();
                            allRecords.addAll(getHistoryRecords(roomId));
                        });
            }
        } catch (IOException e) {
            logger.error("获取所有历史录制记录失败", e);
        }
        
        // 按开始时间倒序排列
        allRecords.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        return allRecords;
    }
    
    /**
     * 删除录制文件
     */
    public boolean deleteRecord(String roomId, String recordId) {
        try {
            String recordDir = "records/" + roomId;
            Path dirPath = Paths.get(recordDir);
            
            if (Files.exists(dirPath)) {
                // 删除相关文件
                Files.list(dirPath)
                        .filter(path -> path.getFileName().toString().startsWith(recordId))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                                logger.debug("删除文件: {}", path);
                            } catch (IOException e) {
                                logger.warn("删除文件失败: {}", path, e);
                            }
                        });
                
                logger.info("删除录制记录: roomId={}, recordId={}", roomId, recordId);
                return true;
            }
        } catch (IOException e) {
            logger.error("删除录制记录失败: roomId={}, recordId={}", roomId, recordId, e);
        }
        
        return false;
    }
    
    /**
     * 清理录制文件
     */
    private void cleanupRecords() {
        try {
            Path recordsDir = Paths.get("records");
            if (Files.exists(recordsDir)) {
                logger.info("启动时清理录制文件");
                // 这里可以添加清理逻辑，比如删除临时文件、标记异常状态等
            }
        } catch (Exception e) {
            logger.error("清理录制文件失败", e);
        }
    }
    
    /**
     * 定时检查录制任务
     */
    public void checkRecordTasks() {
        activeRecords.entrySet().removeIf(entry -> {
            String roomId = entry.getKey();
            RecordManager recordManager = entry.getValue();
            
            // 检查录制时长限制
            if (recordManager.getRecordDuration() > maxRecordDuration) {
                logger.warn("录制时长超限，停止录制: roomId={}, 时长={}ms", 
                        roomId, recordManager.getRecordDuration());
                recordManager.stopRecord();
                return true;
            }
            
            // 检查文件大小限制
            if (recordManager.getRecordedSize() > maxFileSize) {
                logger.warn("录制文件大小超限，停止录制: roomId={}, 大小={}bytes", 
                        roomId, recordManager.getRecordedSize());
                recordManager.stopRecord();
                return true;
            }
            
            // 检查录制状态
            if (!recordManager.isRecording()) {
                logger.debug("录制已结束，移除任务: roomId={}", roomId);
                return true;
            }
            
            return false;
        });
    }
    
    // 配置相关方法
    public boolean isAutoRecord() {
        return autoRecord;
    }
    
    public void setAutoRecord(boolean autoRecord) {
        this.autoRecord = autoRecord;
        logger.info("设置自动录制: {}", autoRecord);
    }
    
    public long getMaxRecordDuration() {
        return maxRecordDuration;
    }
    
    public void setMaxRecordDuration(long maxRecordDuration) {
        this.maxRecordDuration = maxRecordDuration;
    }
    
    public long getMaxFileSize() {
        return maxFileSize;
    }
    
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}
