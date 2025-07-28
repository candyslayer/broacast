package com.live.broadcast.record;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;

/**
 * 录制信息模型
 */
public class RecordInfo {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private String roomId;
    private String recordId;
    private String fileName;
    private long startTime;
    private long endTime;
    private long fileSize;
    private String finalFilePath;
    private Status status;
    private String description;
    
    public RecordInfo() {
        this.status = Status.RECORDING;
    }
    
    public RecordInfo(String roomId, String recordId, long startTime) {
        this.roomId = roomId;
        this.recordId = recordId;
        this.startTime = startTime;
        this.endTime = 0;
        this.fileSize = 0;
        this.status = Status.RECORDING;
    }
    
    /**
     * 录制状态枚举
     */
    public enum Status {
        RECORDING("录制中"),
        COMPLETED("已完成"),
        FAILED("录制失败"),
        PROCESSING("后处理中"),
        DELETED("已删除");
        
        private final String description;
        
        Status(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 获取录制时长（毫秒）
     */
    public long getDuration() {
        if (endTime <= startTime) {
            return System.currentTimeMillis() - startTime;
        }
        return endTime - startTime;
    }
    
    /**
     * 获取录制时长（秒）
     */
    public long getDurationInSeconds() {
        return getDuration() / 1000;
    }
    
    /**
     * 获取友好的文件大小显示
     */
    public String getFileSizeDisplay() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 获取友好的时长显示
     */
    public String getDurationDisplay() {
        long seconds = getDurationInSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"JSON转换失败\"}";
        }
    }
    
    /**
     * 从JSON字符串创建对象
     */
    public static RecordInfo fromJson(String json) {
        try {
            return objectMapper.readValue(json, RecordInfo.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    // Getters and Setters
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getRecordId() {
        return recordId;
    }
    
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public Date getStartTimeDate() {
        return new Date(startTime);
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public Date getEndTimeDate() {
        return endTime > 0 ? new Date(endTime) : null;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getFinalFilePath() {
        return finalFilePath;
    }
    
    public void setFinalFilePath(String finalFilePath) {
        this.finalFilePath = finalFilePath;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "RecordInfo{" +
                "roomId='" + roomId + '\'' +
                ", recordId='" + recordId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", fileSize=" + fileSize +
                ", status=" + status +
                ", duration=" + getDurationDisplay() +
                ", fileSizeDisplay=" + getFileSizeDisplay() +
                '}';
    }
}
