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
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
            log.info("API请求文件列表，原始路径参数: '{}'", path);
            
            // 使用统一的路径清理方法
            String cleanedPath = cleanPath(path);
            log.info("清理后的路径: '{}'", cleanedPath);
            
            List<FileInfo> files = fileService.listFiles(cleanedPath);
            
            FilesResponse response = new FilesResponse();
            response.setFiles(files);
            response.setCurrentPath(cleanedPath);
            response.setRootPath(fileService.getRootPath());
            response.setUploadEnabled(uploadService.isUploadEnabled());
            response.setSuccess(true);
            response.setMessage("文件列表获取成功");
            
            log.info("返回文件列表: 路径='{}', 文件数量={}", cleanedPath, files.size());
            return ResponseEntity.ok(response);
            
        } catch (SecurityException e) {
            log.error("安全错误 - 尝试访问非法路径: {}", path, e);
            FilesResponse errorResponse = new FilesResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("访问被拒绝：路径不安全");
            return ResponseEntity.status(403).body(errorResponse);
        } catch (IllegalArgumentException e) {
            log.error("参数错误 - 路径无效: {}", path, e);
            FilesResponse errorResponse = new FilesResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("路径参数无效: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("获取文件列表失败: 路径='{}', 错误={}", path, e.getMessage(), e);
            FilesResponse errorResponse = new FilesResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("获取文件列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
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
        log.debug("清理路径开始: 原始路径='{}'", path);
        
        if (path == null || path.trim().isEmpty()) {
            log.debug("路径为空，返回根路径");
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
            log.warn("检测到可能的路径遍历攻击: 原始路径='{}', 清理中...", path);
            path = path.replace("..", "");
            if (path.contains("..")) {
                // 再次检查，防止嵌套的路径遍历
                throw new SecurityException("检测到路径遍历攻击尝试");
            }
        }
        
        // 检查路径是否包含非法字符
        if (path.matches(".*[<>:\"|?*].*")) {
            log.warn("路径包含非法字符: '{}'", path);
            throw new IllegalArgumentException("路径包含非法字符");
        }
        
        log.debug("路径清理完成: 最终路径='{}'", path);
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
            log.info("生成分享链接请求，原始文件路径: '{}'", filePath);
            
            // 清理文件路径
            String cleanedPath = cleanPath(filePath);
            log.info("清理后的文件路径: '{}'", cleanedPath);
            
            if (!fileService.fileExists(cleanedPath)) {
                log.warn("文件不存在，无法生成分享链接: '{}'", cleanedPath);
                return ResponseEntity.notFound().build();
            }
            
            // 构建分享链接 - 使用查询参数形式
            String baseUrl = getShareBaseUrl(request);
            String shareUrl = baseUrl + "/download?path=" + URLEncoder.encode(cleanedPath, StandardCharsets.UTF_8);
            
            log.info("生成分享链接成功: 文件='{}', 链接='{}'", cleanedPath, shareUrl);
            return ResponseEntity.ok(shareUrl);
            
        } catch (SecurityException e) {
            log.error("生成分享链接失败 - 安全错误: {}", filePath, e);
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            log.error("生成分享链接失败 - 参数错误: {}", filePath, e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("生成分享链接失败: 文件='{}'", filePath, e);
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
            log.info("收到上传请求，文件数量: {}, 原始目标路径: '{}'", files.size(), path);
            log.info("上传功能启用状态: {}", uploadService.isUploadEnabled());
            
            // 清理上传路径
            String cleanedPath = cleanPath(path);
            log.info("清理后的上传路径: '{}'", cleanedPath);
            
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

            log.info("开始调用uploadService.uploadFiles，使用清理后的路径: '{}'", cleanedPath);
            List<String> uploadedFiles = uploadService.uploadFiles(files, cleanedPath);
            
            String message = String.format("成功上传 %d 个文件到路径: %s", uploadedFiles.size(), 
                    cleanedPath.isEmpty() ? "根目录" : cleanedPath);
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
        private boolean success;
        private String message;

        // Getters and setters
        public List<FileInfo> getFiles() { return files; }
        public void setFiles(List<FileInfo> files) { this.files = files; }
        public String getCurrentPath() { return currentPath; }
        public void setCurrentPath(String currentPath) { this.currentPath = currentPath; }
        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public boolean isUploadEnabled() { return uploadEnabled; }
        public void setUploadEnabled(boolean uploadEnabled) { this.uploadEnabled = uploadEnabled; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
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

    /**
     * 文件夹下载 - 流式ZIP打包
     */
    @GetMapping("/download-folder")
    public void downloadFolder(@RequestParam("path") String folderPath, 
                              HttpServletResponse response) {
        try {
            log.info("文件夹下载请求，原始路径: '{}'", folderPath);
            
            // 清理路径
            String cleanedPath = cleanPath(folderPath);
            log.info("清理后的文件夹路径: '{}'", cleanedPath);
            
            // 检查是否为文件夹
            if (!fileService.isDirectory(cleanedPath)) {
                log.warn("路径不是文件夹: '{}'", cleanedPath);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("指定路径不是文件夹");
                return;
            }
            
            // 获取文件夹名称作为ZIP文件名
            String folderName = getFolderNameFromPath(cleanedPath);
            String zipFileName = folderName.isEmpty() ? "shared_files" : folderName;
            
            // 设置响应头
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", 
                "attachment; filename*=UTF-8''" + 
                URLEncoder.encode(zipFileName + ".zip", StandardCharsets.UTF_8));
            
            log.info("开始流式打包文件夹: '{}' -> '{}.zip'", cleanedPath, zipFileName);
            
            // 使用流式ZIP打包
            try (ZipOutputStream zipOut = new ZipOutputStream(
                    new BufferedOutputStream(response.getOutputStream(), 8192))) {
                
                // 设置压缩级别（平衡速度和压缩率）
                zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);
                
                // 递归添加文件夹内容到ZIP
                addDirectoryToZip(cleanedPath, "", zipOut);
                
                zipOut.finish();
                zipOut.flush();
            }
            
            log.info("文件夹打包下载完成: '{}'", cleanedPath);
            
        } catch (SecurityException e) {
            log.error("安全错误 - 尝试下载非法路径: {}", folderPath, e);
            try {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("访问被拒绝：路径不安全");
            } catch (IOException ioException) {
                log.error("写入错误响应失败", ioException);
            }
        } catch (IllegalArgumentException e) {
            log.error("参数错误 - 文件夹路径无效: {}", folderPath, e);
            try {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("文件夹路径无效: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("写入错误响应失败", ioException);
            }
        } catch (IOException e) {
            log.error("文件夹下载失败 - IO错误: {}", folderPath, e);
            try {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("下载失败: " + e.getMessage());
                }
            } catch (IOException ioException) {
                log.error("写入错误响应失败", ioException);
            }
        } catch (Exception e) {
            log.error("文件夹下载失败 - 未知错误: {}", folderPath, e);
            try {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("下载失败: " + e.getMessage());
                }
            } catch (IOException ioException) {
                log.error("写入错误响应失败", ioException);
            }
        }
    }

    /**
     * 递归添加目录内容到ZIP流
     */
    private void addDirectoryToZip(String sourceDirPath, String zipDirPath, ZipOutputStream zipOut) 
            throws IOException {
        
        List<FileInfo> files = fileService.listFiles(sourceDirPath);
        log.debug("处理目录: '{}', 包含 {} 个项目", sourceDirPath, files.size());
        
        // 如果是空文件夹，添加一个目录条目
        if (files.isEmpty() && !zipDirPath.isEmpty()) {
            ZipEntry dirEntry = new ZipEntry(zipDirPath + "/");
            dirEntry.setTime(System.currentTimeMillis());
            zipOut.putNextEntry(dirEntry);
            zipOut.closeEntry();
            log.debug("添加空目录: '{}'", zipDirPath);
        }
        
        for (FileInfo fileInfo : files) {
            String sourceFilePath = sourceDirPath.isEmpty() ? 
                fileInfo.getName() : sourceDirPath + "/" + fileInfo.getName();
            String zipFilePath = zipDirPath.isEmpty() ? 
                fileInfo.getName() : zipDirPath + "/" + fileInfo.getName();
            
            if (fileInfo.isDirectory()) {
                // 递归处理子目录
                addDirectoryToZip(sourceFilePath, zipFilePath, zipOut);
            } else {
                // 添加文件到ZIP
                addFileToZip(sourceFilePath, zipFilePath, zipOut);
            }
        }
    }

    /**
     * 添加单个文件到ZIP流
     */
    private void addFileToZip(String sourceFilePath, String zipFilePath, ZipOutputStream zipOut) 
            throws IOException {
        
        try (InputStream fileInputStream = fileService.getFileInputStream(sourceFilePath);
             BufferedInputStream bufferedInput = new BufferedInputStream(fileInputStream, 8192)) {
            
            ZipEntry zipEntry = new ZipEntry(zipFilePath);
            
            // 设置文件修改时间
            try {
                long lastModified = fileService.getLastModified(sourceFilePath);
                zipEntry.setTime(lastModified);
            } catch (Exception e) {
                log.debug("无法获取文件修改时间: {}", sourceFilePath);
                zipEntry.setTime(System.currentTimeMillis());
            }
            
            zipOut.putNextEntry(zipEntry);
            
            // 流式复制文件内容
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                zipOut.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            zipOut.closeEntry();
            log.debug("添加文件到ZIP: '{}' ({} bytes)", zipFilePath, totalBytes);
            
        } catch (Exception e) {
            log.error("添加文件到ZIP失败: '{}' -> '{}'", sourceFilePath, zipFilePath, e);
            // 继续处理其他文件，不因单个文件失败而中断整个打包过程
        }
    }

    /**
     * 从路径中提取文件夹名称
     */
    private String getFolderNameFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        
        // 移除末尾的斜杠
        path = path.replaceAll("[/\\\\]+$", "");
        
        // 提取最后一个路径组件
        int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            return path.substring(lastSeparator + 1);
        }
        
        return path;
    }
} 