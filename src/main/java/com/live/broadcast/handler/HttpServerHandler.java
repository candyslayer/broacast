package com.live.broadcast.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.broadcast.manager.LiveRoomManager;
import com.live.broadcast.model.LiveRoom;
import com.live.broadcast.record.RecordService;
import com.live.broadcast.record.RecordInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP请求处理器
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        HttpMethod method = request.method();
        
        logger.debug("收到HTTP请求: {} {}", method, uri);
        
        try {
            if (uri.equals("/") || uri.equals("/index.html")) {
                sendHtmlPage(ctx, request, "index.html");
            } else if (uri.startsWith("/api/rooms")) {
                handleRoomsApi(ctx, request);
            } else if (uri.startsWith("/api/room/")) {
                handleRoomApi(ctx, request, uri);
            } else if (uri.startsWith("/api/record")) {
                handleRecordApi(ctx, request, uri);
            } else if (uri.equals("/live.html")) {
                sendHtmlPage(ctx, request, "live.html");
            } else if (uri.equals("/viewer.html")) {
                sendHtmlPage(ctx, request, "viewer.html");
            } else if (uri.startsWith("/hls/")) {
                handleHlsRequest(ctx, request, uri);
            } else {
                sendNotFound(ctx, request);
            }
        } catch (Exception e) {
            logger.error("处理HTTP请求失败: {} {}", method, uri, e);
            sendError(ctx, request, "内部服务器错误");
        }
    }
    
    /**
     * 处理获取所有房间API
     */
    private void handleRoomsApi(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() == HttpMethod.GET) {
            Map<String, LiveRoom> rooms = LiveRoomManager.getInstance().getAllRooms();
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", rooms.values());
            
            sendJsonResponse(ctx, request, response);
        } else {
            sendMethodNotAllowed(ctx, request);
        }
    }
    
    /**
     * 处理单个房间API
     */
    private void handleRoomApi(ChannelHandlerContext ctx, FullHttpRequest request, String uri) throws Exception {
        String[] parts = uri.split("/");
        if (parts.length < 4) {
            sendBadRequest(ctx, request, "房间ID不能为空");
            return;
        }
        
        String roomId = parts[3];
        
        if (request.method() == HttpMethod.GET) {
            LiveRoom room = LiveRoomManager.getInstance().getRoom(roomId);
            Map<String, Object> response = new HashMap<>();
            
            if (room != null) {
                response.put("code", 200);
                response.put("message", "success");
                response.put("data", room);
            } else {
                response.put("code", 404);
                response.put("message", "房间不存在");
                response.put("data", null);
            }
            
            sendJsonResponse(ctx, request, response);
        } else {
            sendMethodNotAllowed(ctx, request);
        }
    }
    
    /**
     * 发送HTML页面
     */
    private void sendHtmlPage(ChannelHandlerContext ctx, FullHttpRequest request, String fileName) {
        String content = getHtmlContent(fileName);
        
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.write(response);
        } else {
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }
        ctx.flush();
    }
    
    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(ChannelHandlerContext ctx, FullHttpRequest request, Object data) throws Exception {
        String json = objectMapper.writeValueAsString(data);
        
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.write(response);
        } else {
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }
        ctx.flush();
    }
    
    /**
     * 发送404响应
     */
    private void sendNotFound(ChannelHandlerContext ctx, FullHttpRequest request) {
        sendError(ctx, request, HttpResponseStatus.NOT_FOUND, "页面未找到");
    }
    
    /**
     * 发送400响应
     */
    private void sendBadRequest(ChannelHandlerContext ctx, FullHttpRequest request, String message) {
        sendError(ctx, request, HttpResponseStatus.BAD_REQUEST, message);
    }
    
    /**
     * 发送405响应
     */
    private void sendMethodNotAllowed(ChannelHandlerContext ctx, FullHttpRequest request) {
        sendError(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "方法不允许");
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, FullHttpRequest request, String message) {
        sendError(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        ctx.flush();
    }
    
    /**
     * 获取HTML内容
     */
    private String getHtmlContent(String fileName) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/" + fileName);
            if (inputStream != null) {
                byte[] bytes = inputStream.readAllBytes();
                inputStream.close();
                return new String(bytes, CharsetUtil.UTF_8);
            }
        } catch (IOException e) {
            logger.error("读取HTML文件失败: {}", fileName, e);
        }
        
        // 如果文件不存在，返回默认页面
        return getDefaultHtmlContent(fileName);
    }
    
    /**
     * 获取默认HTML内容
     */
    private String getDefaultHtmlContent(String fileName) {
        if ("index.html".equals(fileName)) {
            return getIndexHtml();
        } else if ("live.html".equals(fileName)) {
            return getLiveHtml();
        } else if ("viewer.html".equals(fileName)) {
            return getViewerHtml();
        }
        return "<html><body><h1>页面未找到</h1></body></html>";
    }
    
    /**
     * 获取首页HTML
     */
    private String getIndexHtml() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Netty直播系统</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                "        .container { max-width: 800px; margin: 0 auto; }\n" +
                "        .room { border: 1px solid #ddd; padding: 20px; margin: 10px 0; border-radius: 5px; }\n" +
                "        .room.live { border-color: #ff4444; background: #fff5f5; }\n" +
                "        button { padding: 10px 20px; margin: 5px; cursor: pointer; }\n" +
                "        .live-indicator { color: #ff4444; font-weight: bold; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>Netty直播系统</h1>\n" +
                "        <div id=\"roomList\">\n" +
                "            <h2>直播间列表</h2>\n" +
                "            <div id=\"rooms\">加载中...</div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function loadRooms() {\n" +
                "            fetch('/api/rooms')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const roomsDiv = document.getElementById('rooms');\n" +
                "                    if (data.code === 200) {\n" +
                "                        roomsDiv.innerHTML = '';\n" +
                "                        data.data.forEach(room => {\n" +
                "                            const roomDiv = document.createElement('div');\n" +
                "                            roomDiv.className = 'room' + (room.live ? ' live' : '');\n" +
                "                            roomDiv.innerHTML = `\n" +
                "                                <h3>${room.title} ${room.live ? '<span class=\"live-indicator\">● 直播中</span>' : ''}\n</h3>\n" +
                "                                <p>主播: ${room.streamerName}</p>\n" +
                "                                <p>观众数: ${room.viewerCount}</p>\n" +
                "                                <button onclick=\"watchRoom('${room.roomId}')\" ${!room.live ? 'disabled' : ''}>\n" +
                "                                    ${room.live ? '观看直播' : '未开播'}\n" +
                "                                </button>\n" +
                "                            `;\n" +
                "                            roomsDiv.appendChild(roomDiv);\n" +
                "                        });\n" +
                "                    } else {\n" +
                "                        roomsDiv.innerHTML = '加载失败: ' + data.message;\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    document.getElementById('rooms').innerHTML = '加载失败: ' + error.message;\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function watchRoom(roomId) {\n" +
                "            window.open('/viewer.html?room=' + roomId, '_blank');\n" +
                "        }\n" +
                "\n" +
                "        document.addEventListener('DOMContentLoaded', loadRooms);\n" +
                "        setInterval(loadRooms, 10000);\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
    
    /**
     * 获取推流页面HTML
     */
    private String getLiveHtml() {
        return "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"UTF-8\">\n    <title>开始直播</title>\n</head>\n<body>\n    <h1>推流页面</h1>\n    <p>请使用OBS等推流软件推流到: rtmp://localhost:1935/live/房间ID</p>\n</body>\n</html>";
    }
    
    /**
     * 获取观看页面HTML
     */
    private String getViewerHtml() {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\"><title>观看直播</title>" +
                "<style>body{font-family:Arial,sans-serif;margin:20px}.container{max-width:1200px;margin:0 auto}" +
                ".video-container{width:70%;float:left}.chat-container{width:28%;float:right;border:1px solid #ddd;height:500px}" +
                ".chat-messages{height:400px;overflow-y:auto;padding:10px;border-bottom:1px solid #ddd}" +
                ".chat-input{padding:10px}.chat-input input{width:70%;padding:5px}.chat-input button{width:25%;padding:5px}" +
                ".message{margin:5px 0}.system-message{color:#666;font-style:italic}.user-message{color:#333}" +
                ".username{font-weight:bold;color:#0066cc}.clearfix::after{content:\"\";display:table;clear:both}" +
                "</style></head><body>" +
                "<div class=\"container clearfix\">" +
                "<div class=\"video-container\"><h2 id=\"roomTitle\">直播间</h2>" +
                "<div id=\"videoPlayer\"><p>视频播放器区域 (需要集成视频播放器如Video.js或hls.js)</p>" +
                "<p>推流地址: rtmp://localhost:1935/live/<span id=\"roomId\"></span></p></div></div>" +
                "<div class=\"chat-container\">" +
                "<div class=\"chat-messages\" id=\"chatMessages\"></div>" +
                "<div class=\"chat-input\">" +
                "<input type=\"text\" id=\"messageInput\" placeholder=\"输入消息...\" maxlength=\"200\">" +
                "<button onclick=\"sendMessage()\">发送</button></div></div></div>" +
                "<script>" +
                "let websocket;let roomId;let userId='user_'+Math.random().toString(36).substr(2,9);" +
                "let username='Guest_'+Math.random().toString(36).substr(2,5);" +
                "function getUrlParameter(name){const urlParams=new URLSearchParams(window.location.search);return urlParams.get(name);}" +
                "function connectWebSocket(){roomId=getUrlParameter('room')||'room1';" +
                "document.getElementById('roomId').textContent=roomId;" +
                "websocket=new WebSocket('ws://localhost:8081/ws');" +
                "websocket.onopen=function(){console.log('WebSocket连接成功');joinRoom();};" +
                "websocket.onmessage=function(event){const message=JSON.parse(event.data);handleMessage(message);};" +
                "websocket.onclose=function(){console.log('WebSocket连接关闭');addSystemMessage('连接已断开，正在重连...');setTimeout(connectWebSocket,3000);};" +
                "websocket.onerror=function(error){console.error('WebSocket错误:',error);};}" +
                "function joinRoom(){const joinMessage={type:'join',roomId:roomId,userId:userId,username:username};websocket.send(JSON.stringify(joinMessage));}" +
                "function sendMessage(){const input=document.getElementById('messageInput');const content=input.value.trim();" +
                "if(content&&websocket.readyState===WebSocket.OPEN){const message={type:'chat',roomId:roomId,content:content};" +
                "websocket.send(JSON.stringify(message));input.value='';}" +
                "}" +
                "function handleMessage(message){switch(message.type){case 'join_success':addSystemMessage('成功加入直播间');break;" +
                "case 'chat':addChatMessage(message.username,message.content);break;" +
                "case 'user_join':addSystemMessage(message.content);break;" +
                "case 'user_leave':addSystemMessage(message.content);break;" +
                "case 'live_start':addSystemMessage('直播开始了！');break;" +
                "case 'live_stop':addSystemMessage('直播结束了');break;" +
                "case 'error':addSystemMessage('错误: '+message.content);break;}}" +
                "function addChatMessage(username,content){const messagesDiv=document.getElementById('chatMessages');" +
                "const messageDiv=document.createElement('div');messageDiv.className='message user-message';" +
                "messageDiv.innerHTML='<span class=\"username\">'+username+':</span> '+content;" +
                "messagesDiv.appendChild(messageDiv);messagesDiv.scrollTop=messagesDiv.scrollHeight;}" +
                "function addSystemMessage(content){const messagesDiv=document.getElementById('chatMessages');" +
                "const messageDiv=document.createElement('div');messageDiv.className='message system-message';" +
                "messageDiv.textContent=content;messagesDiv.appendChild(messageDiv);messagesDiv.scrollTop=messagesDiv.scrollHeight;}" +
                "document.getElementById('messageInput').addEventListener('keypress',function(e){if(e.key==='Enter'){sendMessage();}});" +
                "document.addEventListener('DOMContentLoaded',connectWebSocket);" +
                "</script></body></html>";
    }
    
    /**
     * 处理录播API
     */
    private void handleRecordApi(ChannelHandlerContext ctx, FullHttpRequest request, String uri) throws Exception {
        String[] parts = uri.split("/");
        
        if (parts.length >= 4) {
            String action = parts[3]; // record后的动作
            
            switch (action) {
                case "start":
                    handleStartRecord(ctx, request);
                    break;
                case "stop":
                    handleStopRecord(ctx, request);
                    break;
                case "list":
                    handleListRecords(ctx, request);
                    break;
                case "history":
                    handleRecordHistory(ctx, request);
                    break;
                case "download":
                    handleDownloadRecord(ctx, request, uri);
                    break;
                case "delete":
                    handleDeleteRecord(ctx, request);
                    break;
                default:
                    sendNotFound(ctx, request);
            }
        } else {
            sendBadRequest(ctx, request, "API路径不正确");
        }
    }
    
    /**
     * 开始录制
     */
    private void handleStartRecord(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.POST) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        String roomId = getQueryParameter(request.uri(), "roomId");
        if (roomId == null || roomId.isEmpty()) {
            sendBadRequest(ctx, request, "roomId参数不能为空");
            return;
        }
        
        boolean success = RecordService.getInstance().startRecord(roomId);
        Map<String, Object> response = new HashMap<>();
        
        if (success) {
            response.put("code", 200);
            response.put("message", "开始录制成功");
            response.put("data", RecordService.getInstance().getRecordInfo(roomId));
        } else {
            response.put("code", 400);
            response.put("message", "开始录制失败");
            response.put("data", null);
        }
        
        sendJsonResponse(ctx, request, response);
    }
    
    /**
     * 停止录制
     */
    private void handleStopRecord(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.POST) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        String roomId = getQueryParameter(request.uri(), "roomId");
        if (roomId == null || roomId.isEmpty()) {
            sendBadRequest(ctx, request, "roomId参数不能为空");
            return;
        }
        
        boolean success = RecordService.getInstance().stopRecord(roomId);
        Map<String, Object> response = new HashMap<>();
        
        if (success) {
            response.put("code", 200);
            response.put("message", "停止录制成功");
        } else {
            response.put("code", 400);
            response.put("message", "停止录制失败");
        }
        response.put("data", null);
        
        sendJsonResponse(ctx, request, response);
    }
    
    /**
     * 获取活跃录制列表
     */
    private void handleListRecords(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.GET) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        Map<String, RecordInfo> activeRecords = RecordService.getInstance().getAllActiveRecords();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", activeRecords);
        
        sendJsonResponse(ctx, request, response);
    }
    
    /**
     * 获取录制历史
     */
    private void handleRecordHistory(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.GET) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        String roomId = getQueryParameter(request.uri(), "roomId");
        Map<String, Object> response = new HashMap<>();
        
        if (roomId != null && !roomId.isEmpty()) {
            response.put("data", RecordService.getInstance().getHistoryRecords(roomId));
        } else {
            response.put("data", RecordService.getInstance().getAllHistoryRecords());
        }
        
        response.put("code", 200);
        response.put("message", "success");
        sendJsonResponse(ctx, request, response);
    }
    
    /**
     * 下载录制文件
     */
    private void handleDownloadRecord(ChannelHandlerContext ctx, FullHttpRequest request, String uri) throws Exception {
        if (request.method() != HttpMethod.GET) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        String roomId = getQueryParameter(request.uri(), "roomId");
        String recordId = getQueryParameter(request.uri(), "recordId");
        
        if (roomId == null || recordId == null) {
            sendBadRequest(ctx, request, "roomId和recordId参数不能为空");
            return;
        }
        
        String filePath = "records/" + roomId + "/" + recordId + ".mp4";
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            sendNotFound(ctx, request);
            return;
        }
        
        try {
            byte[] content = Files.readAllBytes(path);
            
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(content)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "video/mp4");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + recordId + ".mp4\"");
            
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                ctx.write(response);
            } else {
                ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            }
            ctx.flush();
            
        } catch (IOException e) {
            logger.error("读取录制文件失败: {}", filePath, e);
            sendNotFound(ctx, request);
        }
    }
    
    /**
     * 删除录制文件
     */
    private void handleDeleteRecord(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.DELETE && request.method() != HttpMethod.POST) {
            sendMethodNotAllowed(ctx, request);
            return;
        }
        
        String roomId = getQueryParameter(request.uri(), "roomId");
        String recordId = getQueryParameter(request.uri(), "recordId");
        
        if (roomId == null || recordId == null) {
            sendBadRequest(ctx, request, "roomId和recordId参数不能为空");
            return;
        }
        
        boolean success = RecordService.getInstance().deleteRecord(roomId, recordId);
        Map<String, Object> response = new HashMap<>();
        
        if (success) {
            response.put("code", 200);
            response.put("message", "删除成功");
        } else {
            response.put("code", 400);
            response.put("message", "删除失败");
        }
        response.put("data", null);
        
        sendJsonResponse(ctx, request, response);
    }
    
    /**
     * 获取URL查询参数
     */
    private String getQueryParameter(String uri, String paramName) {
        if (uri.contains("?")) {
            String queryString = uri.substring(uri.indexOf("?") + 1);
            String[] params = queryString.split("&");
            
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("HTTP处理异常", cause);
        ctx.close();
    }
    
    /**
     * 处理HLS请求
     */
    private void handleHlsRequest(ChannelHandlerContext ctx, FullHttpRequest request, String uri) throws Exception {
        // uri格式: /hls/roomId/filename
        String[] parts = uri.split("/");
        if (parts.length < 4) {
            sendNotFound(ctx, request);
            return;
        }
        
        String roomId = parts[2];
        String fileName = parts[3];
        String filePath = "hls/" + roomId + "/" + fileName;
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                sendNotFound(ctx, request);
                return;
            }
            
            byte[] content = Files.readAllBytes(path);
            String contentType;
            
            if (fileName.endsWith(".m3u8")) {
                contentType = "application/vnd.apple.mpegurl";
            } else if (fileName.endsWith(".ts")) {
                contentType = "video/mp2t";
            } else {
                contentType = "application/octet-stream";
            }
            
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(content)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                ctx.write(response);
            } else {
                ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            }
            ctx.flush();
            
            logger.debug("服务HLS文件: {}", filePath);
            
        } catch (IOException e) {
            logger.error("读取HLS文件失败: {}", filePath, e);
            sendNotFound(ctx, request);
        }
    }
}
