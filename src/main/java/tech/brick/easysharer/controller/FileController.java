package tech.brick.easysharer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.brick.easysharer.model.FileInfo;
import tech.brick.easysharer.model.TextShare;
import tech.brick.easysharer.service.FileService;
import tech.brick.easysharer.service.UploadService;
import tech.brick.easysharer.service.TextShareService;
import tech.brick.easysharer.util.NetworkUtils;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UploadService uploadService;
    private final TextShareService textShareService;

    @Value("${file.upload.max-file-size:500}")
    private int maxFileSizeMB;

    /**
     * 首页 - 返回Vue应用
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * API: 获取文件列表
     */
    @GetMapping("/api/files")
    @ResponseBody
    public ResponseEntity<FilesResponse> getFiles(@RequestParam(value = "path", defaultValue = "") String path) {
        try {
            // 清理路径
            if (path == null) {
                path = "";
            }
            path = path.trim();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith("/") && !path.isEmpty()) {
                path = path.substring(0, path.length() - 1);
            }
            
            log.info("API请求文件列表，路径: '{}'", path);
            
            List<FileInfo> files = fileService.listFiles(path);
            
            FilesResponse response = new FilesResponse();
            response.setFiles(files);
            response.setCurrentPath(path);
            response.setRootPath(fileService.getRootPath());
            response.setUploadEnabled(uploadService.isUploadEnabled());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取文件列表失败: {}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * API: 获取服务器信息
     */
    @GetMapping("/api/server-info")
    @ResponseBody
    public ResponseEntity<ServerInfo> getServerInfo(HttpServletRequest request) {
        try {
            ServerInfo serverInfo = new ServerInfo();
            serverInfo.setPort(request.getServerPort());
            serverInfo.setLocalIps(NetworkUtils.getAllLocalIpAddresses());
            
            return ResponseEntity.ok(serverInfo);
        } catch (Exception e) {
            log.error("获取服务器信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件下载 - 优化版：使用查询参数传递路径，避免URL解析问题
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String filePath) {
        try {
            log.info("下载文件请求，原始路径参数: {}", filePath);
            
            // 清理路径
            String cleanedPath = cleanPath(filePath);
            log.info("清理后的文件路径: {}", cleanedPath);
            
            // 检查文件是否存在
            if (!fileService.fileExists(cleanedPath)) {
                log.warn("文件不存在: {}", cleanedPath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = fileService.getFileAsResource(cleanedPath);
            
            // 获取文件名
            String filename = resource.getFilename();
            if (filename == null) {
                // 从路径中提取文件名
                if (cleanedPath.contains("/")) {
                    filename = cleanedPath.substring(cleanedPath.lastIndexOf("/") + 1);
                } else {
                    filename = cleanedPath;
                }
                if (filename.isEmpty()) {
                    filename = "download";
                }
            }
            
            log.info("开始下载文件: {} (文件名: {})", cleanedPath, filename);
            
            // 设置响应头，确保中文文件名正确显示
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename*=UTF-8''" + encodedFilename);
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
                
        } catch (SecurityException e) {
            log.error("安全错误 - 尝试访问非法路径", e);
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            log.error("参数错误 - 文件路径无效", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("文件下载失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 清理文件路径 - 增强版
     */
    private String cleanPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        
        path = path.trim();
        
        // 统一使用正斜杠
        path = path.replace("\\", "/");
        
        // 移除可能的双斜杠
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        
        // 移除开头的斜杠
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        // 移除结尾的斜杠（除非是根路径）
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
        // 处理路径遍历攻击
        if (path.contains("..")) {
            log.warn("检测到可能的路径遍历攻击: {}", path);
            path = path.replace("..", "");
        }
        
        return path;
    }

    /**
     * 获取分享链接
     */
    @PostMapping("/api/share")
    @ResponseBody
    public ResponseEntity<String> getShareLink(@RequestParam String filePath, 
                                             HttpServletRequest request) {
        try {
            if (!fileService.fileExists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            
            // 构建分享链接 - 使用查询参数形式
            String baseUrl = getShareBaseUrl(request);
            String shareUrl = baseUrl + "/download?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            return ResponseEntity.ok(shareUrl);
            
        } catch (Exception e) {
            log.error("生成分享链接失败: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件上传
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<UploadResponse> uploadFiles(@RequestParam("files") List<MultipartFile> files,
                                                     @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            log.info("收到上传请求，文件数量: {}, 目标路径: '{}'", files.size(), path);
            log.info("上传功能启用状态: {}", uploadService.isUploadEnabled());
            
            // 输出每个文件的详细信息
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                log.info("文件[{}]: 名称='{}', 大小={} bytes, 类型='{}'", 
                        i, file.getOriginalFilename(), file.getSize(), file.getContentType());
            }
            
            if (!uploadService.isUploadEnabled()) {
                log.warn("上传功能未启用，返回错误");
                return ResponseEntity.badRequest()
                    .body(new UploadResponse(false, "文件上传功能未启用", null));
            }

            // 验证文件
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    log.warn("发现空文件: {}", file.getOriginalFilename());
                    return ResponseEntity.badRequest()
                        .body(new UploadResponse(false, "不能上传空文件", null));
                }
                
                String filename = file.getOriginalFilename();
                if (filename == null || filename.trim().isEmpty()) {
                    log.warn("发现无名文件");
                    return ResponseEntity.badRequest()
                        .body(new UploadResponse(false, "文件名不能为空", null));
                }
                
                // 检查文件大小
                long maxFileSizeBytes = (long) maxFileSizeMB * 1024 * 1024;
                if (file.getSize() > maxFileSizeBytes) {
                    log.warn("文件过大: {} - {} bytes, 限制: {} MB", filename, file.getSize(), maxFileSizeMB);
                    return ResponseEntity.badRequest()
                        .body(new UploadResponse(false, "文件 " + filename + " 过大，最大支持" + maxFileSizeMB + "MB", null));
                }
            }

            log.info("开始调用uploadService.uploadFiles...");
            List<String> uploadedFiles = uploadService.uploadFiles(files, path);
            
            String message = String.format("成功上传 %d 个文件", uploadedFiles.size());
            log.info("上传成功: {}, 文件: {}", message, uploadedFiles);
            return ResponseEntity.ok(new UploadResponse(true, message, uploadedFiles));
            
        } catch (IllegalStateException e) {
            log.error("文件上传失败 - 状态错误: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(new UploadResponse(false, "上传失败: " + e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("文件上传失败 - 参数错误: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(new UploadResponse(false, "上传失败: " + e.getMessage(), null));
        } catch (SecurityException e) {
            log.error("文件上传失败 - 安全错误: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(new UploadResponse(false, "上传失败: " + e.getMessage(), null));
        } catch (java.io.IOException e) {
            log.error("文件上传失败 - IO错误: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new UploadResponse(false, "上传失败: 文件写入错误 - " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("文件上传失败 - 未知错误", e);
            return ResponseEntity.internalServerError()
                .body(new UploadResponse(false, "上传失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取分享链接的基础URL（使用真实IP地址）
     */
    private String getShareBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = NetworkUtils.getLocalIpAddress(); // 使用真实IP地址
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        return url.toString();
    }

    /**
     * 调试端点 - 检查配置状态
     */
    @GetMapping("/api/debug/config")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> getConfigStatus() {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("uploadEnabled", uploadService.isUploadEnabled());
        config.put("timestamp", java.time.LocalDateTime.now().toString());
        config.put("serverRunning", true);
        
        return ResponseEntity.ok(config);
    }

    /**
     * API: 创建文本分享
     */
    @PostMapping("/api/text-share")
    @ResponseBody
    public ResponseEntity<TextShareResponse> createTextShare(@RequestBody CreateTextShareRequest request, 
                                                           HttpServletRequest httpRequest) {
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            
            log.info("创建文本分享请求: IP={}, 类型={}, 内容长度={}", 
                    ipAddress, request.getType(), request.getContent() != null ? request.getContent().length() : 0);
            
            TextShare textShare = textShareService.createTextShare(
                    request.getContent(), 
                    ipAddress, 
                    request.getNickname(), 
                    request.getType()
            );
            
            return ResponseEntity.ok(new TextShareResponse(true, "分享创建成功", textShare));
            
        } catch (IllegalArgumentException e) {
            log.warn("创建文本分享失败 - 参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new TextShareResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("创建文本分享失败", e);
            return ResponseEntity.internalServerError()
                    .body(new TextShareResponse(false, "分享创建失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * API: 获取所有文本分享
     */
    @GetMapping("/api/text-shares")
    @ResponseBody
    public ResponseEntity<List<TextShare>> getAllTextShares() {
        try {
            List<TextShare> shares = textShareService.getAllTextShares();
            log.info("获取文本分享列表: 数量={}", shares.size());
            return ResponseEntity.ok(shares);
        } catch (Exception e) {
            log.error("获取文本分享列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * API: 根据ID获取文本分享
     */
    @GetMapping("/api/text-share/{id}")
    @ResponseBody
    public ResponseEntity<TextShare> getTextShare(@PathVariable String id) {
        try {
            TextShare share = textShareService.getTextShare(id);
            if (share != null) {
                return ResponseEntity.ok(share);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取文本分享失败: ID={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * API: 根据IP获取文本分享
     */
    @GetMapping("/api/text-shares/my")
    @ResponseBody
    public ResponseEntity<List<TextShare>> getMyTextShares(HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            List<TextShare> shares = textShareService.getTextSharesByIp(ipAddress);
            log.info("获取我的文本分享: IP={}, 数量={}", ipAddress, shares.size());
            return ResponseEntity.ok(shares);
        } catch (Exception e) {
            log.error("获取我的文本分享失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * API: 删除文本分享
     */
    @DeleteMapping("/api/text-share/{id}")
    @ResponseBody
    public ResponseEntity<SimpleResponse> deleteTextShare(@PathVariable String id, 
                                                        HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            boolean deleted = textShareService.deleteTextShare(id, ipAddress);
            
            if (deleted) {
                log.info("删除文本分享成功: ID={}, IP={}", id, ipAddress);
                return ResponseEntity.ok(new SimpleResponse(true, "分享删除成功"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new SimpleResponse(false, "无法删除分享：分享不存在或无权限"));
            }
        } catch (Exception e) {
            log.error("删除文本分享失败: ID={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(new SimpleResponse(false, "删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * API: 获取文本分享统计信息
     */
    @GetMapping("/api/text-shares/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTextShareStats() {
        try {
            Map<String, Object> stats = textShareService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取文本分享统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 文件列表响应对象
     */
    public static class FilesResponse {
        private List<FileInfo> files;
        private String currentPath;
        private String rootPath;
        private boolean uploadEnabled;

        // Getters and setters
        public List<FileInfo> getFiles() { return files; }
        public void setFiles(List<FileInfo> files) { this.files = files; }
        public String getCurrentPath() { return currentPath; }
        public void setCurrentPath(String currentPath) { this.currentPath = currentPath; }
        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public boolean isUploadEnabled() { return uploadEnabled; }
        public void setUploadEnabled(boolean uploadEnabled) { this.uploadEnabled = uploadEnabled; }
    }

    /**
     * 服务器信息响应对象
     */
    public static class ServerInfo {
        private int port;
        private List<String> localIps;

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public List<String> getLocalIps() { return localIps; }
        public void setLocalIps(List<String> localIps) { this.localIps = localIps; }
    }

    /**
     * 上传响应对象
     */
    public static class UploadResponse {
        private final boolean success;
        private final String message;
        private final List<String> uploadedFiles;
        
        public UploadResponse(boolean success, String message, List<String> uploadedFiles) {
            this.success = success;
            this.message = message;
            this.uploadedFiles = uploadedFiles;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getUploadedFiles() { return uploadedFiles; }
    }

    /**
     * 文本分享响应对象
     */
    public static class TextShareResponse {
        private final boolean success;
        private final String message;
        private final TextShare textShare;
        
        public TextShareResponse(boolean success, String message, TextShare textShare) {
            this.success = success;
            this.message = message;
            this.textShare = textShare;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public TextShare getTextShare() { return textShare; }
    }

    /**
     * 文本分享请求对象
     */
    public static class CreateTextShareRequest {
        private String content;
        private String nickname;
        private String type;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    /**
     * 简单响应对象
     */
    public static class SimpleResponse {
        private final boolean success;
        private final String message;
        
        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
} 