# Netty直播系统

基于Netty的简易直播系统，支持RTMP推流和WebSocket实时聊天。

## 功能特性

- **多服务器架构**：HTTP服务器、WebSocket服务器、RTMP服务器
- **实时聊天**：基于WebSocket的实时消息系统
- **直播间管理**：支持多个直播间，用户管理
- **推流支持**：简化的RTMP协议支持
- **Web界面**：提供直播间列表和观看页面

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   HTTP Server   │    │ WebSocket Server│    │   RTMP Server   │
│     (8080)      │    │     (8081)      │    │     (1935)      │
│   - Web页面     │    │   - 实时聊天     │    │   - 接收推流     │
│   - REST API    │    │   - 用户管理     │    │   - 协议解析     │
│   - HLS文件服务  │    │   - 消息广播     │    │   - 流处理      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │ LiveRoomManager │    ┌─────────────────┐
                    │   (房间管理)     │────│  StreamManager  │
                    └─────────────────┘    │   (流媒体管理)   │
                                          └─────────────────┘
                                                   │
                                          ┌─────────────────┐
                                          │  HLS输出器      │
                                          │  (格式转换)      │
                                          └─────────────────┘
```

## Netty在系统中的完整作用

### 🚀 **不仅仅是转发！Netty承担核心流媒体处理**

#### 1. **RTMP流媒体服务器** (端口1935)
- ✅ **协议实现**：完整的RTMP握手、连接、命令处理
- ✅ **流接收**：接收OBS等推流软件的音视频数据  
- ✅ **数据解析**：解析FLV标签，区分音频/视频/元数据
- ✅ **流管理**：管理多路并发推流，监控流状态
- ✅ **格式转换**：将RTMP流转换为HLS等Web友好格式
- ✅ **实时分发**：将流数据分发给多个消费者（录制、转码、CDN等）

#### 2. **HTTP流媒体服务器** (端口8080)
- ✅ **HLS服务**：提供`.m3u8`播放列表和`.ts`视频片段
- ✅ **动态内容**：实时生成播放列表，支持直播
- ✅ **跨域支持**：处理CORS，支持Web播放器
- ✅ **缓存控制**：优化流媒体传输性能

#### 3. **WebSocket实时通信** (端口8081)
- ✅ **双向通信**：支持客户端与服务器实时数据交换
- ✅ **消息广播**：向直播间所有用户广播消息
- ✅ **连接管理**：处理大量并发WebSocket连接
- ✅ **状态同步**：实时同步直播状态、用户列表等

### 🎯 **数据流转过程**

```
OBS推流 → RTMP服务器 → 流解析 → StreamManager → HLS转换 → Web播放
   │                                    │
   │                                    ├→ 实时转发给观众
   │                                    ├→ 录制存储  
   │                                    └→ 统计分析
   │
   └→ 直播状态 → LiveRoomManager → WebSocket → 观众端通知
```

## 快速开始

### 1. 环境要求

- Java 11+
- Maven 3.6+

### 2. 编译运行

```bash
# 编译项目
mvn clean compile

# 运行项目
mvn exec:java -Dexec.mainClass="com.live.broadcast.LiveBroadcastApplication"
```

### 3. 访问系统

- **首页**: http://localhost:8080
- **直播间列表**: http://localhost:8080/api/rooms
- **观看直播**: http://localhost:8080/viewer.html?room=room1

### 4. 推流设置

使用OBS等推流软件，设置推流地址：
```
服务器: rtmp://localhost:1935/live/
流密钥: 房间ID (如: room1, room2, room3)
```

## 服务端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| HTTP服务器 | 8080 | 提供Web页面和REST API |
| WebSocket服务器 | 8081 | 处理实时消息和聊天 |
| RTMP服务器 | 1935 | 接收推流数据 |

## API接口

### 获取所有直播间
```
GET /api/rooms
```

### 获取单个直播间信息
```
GET /api/room/{roomId}
```

## WebSocket消息格式

### 加入直播间
```json
{
  "type": "join",
  "roomId": "room1",
  "userId": "user123",
  "username": "用户名"
}
```

### 发送聊天消息
```json
{
  "type": "chat",
  "roomId": "room1",
  "content": "消息内容"
}
```

### 心跳消息
```json
{
  "type": "heartbeat"
}
```

## 目录结构

```
src/
├── main/
│   ├── java/
│   │   └── com/live/broadcast/
│   │       ├── LiveBroadcastApplication.java    # 主启动类
│   │       ├── server/                          # 服务器实现
│   │       │   ├── HttpServer.java             # HTTP服务器
│   │       │   ├── WebSocketServer.java        # WebSocket服务器
│   │       │   └── RtmpServer.java             # RTMP服务器
│   │       ├── handler/                         # 处理器
│   │       │   ├── HttpServerHandler.java      # HTTP请求处理
│   │       │   ├── WebSocketServerHandler.java # WebSocket消息处理
│   │       │   └── RtmpServerHandler.java      # RTMP数据处理
│   │       ├── manager/                         # 管理器
│   │       │   └── LiveRoomManager.java        # 直播间管理
│   │       └── model/                           # 数据模型
│   │           ├── LiveUser.java               # 用户模型
│   │           ├── LiveRoom.java               # 直播间模型
│   │           └── Message.java                # 消息模型
│   └── resources/
│       └── logback.xml                          # 日志配置
└── test/                                        # 测试代码
```

## 扩展功能

### 已实现功能
- ✅ 多直播间支持
- ✅ 实时聊天系统
- ✅ 用户进出提醒
- ✅ 心跳检测
- ✅ **RTMP推流接收和解析**
- ✅ **流媒体数据处理和分发**
- ✅ **HLS格式转换和输出**
- ✅ **多格式流媒体服务**

### 可扩展功能
- 🔲 用户认证和权限管理
- 🔲 **完整的RTMP协议支持（AMF编解码、所有命令类型）**
- 🔲 **多码率自适应流（ABR）**
- 🔲 **实时转码（H.264/H.265, AAC等）**
- 🔲 **DASH流媒体输出**
- 🔲 **WebRTC低延迟直播**
- 🔲 录播功能
- 🔲 弹幕系统
- 🔲 礼物系统
- 🔲 数据统计和监控
- 🔲 CDN分发支持
- 🔲 移动端适配

## 注意事项

1. **RTMP协议**：当前实现支持基本的推流接收和HLS转换，完整的RTMP协议还需要AMF编解码、更多命令类型支持
2. **视频播放**：Web页面集成了HLS播放支持，可使用Video.js、hls.js等播放器
3. **流媒体处理**：实现了基础的流数据分发和HLS切片，生产环境建议集成FFmpeg
4. **性能优化**：生产环境需要考虑连接池、内存管理、负载均衡等
5. **安全性**：需要添加认证、授权、防止恶意攻击等安全措施

## 故障排除

### 常见问题

1. **端口占用**：确保8080、8081、1935端口未被占用
2. **推流失败**：检查RTMP服务器是否正常启动
3. **WebSocket连接失败**：检查防火墙设置
4. **页面无法访问**：确认HTTP服务器启动成功

### 查看日志

日志文件位置：`logs/live-broadcast.log`

## 许可证

MIT License
