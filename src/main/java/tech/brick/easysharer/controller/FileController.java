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
import tech.brick.easysharer.service.FileService;
import tech.brick.easysharer.service.UploadService;
import tech.brick.easysharer.util.NetworkUtils;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UploadService uploadService;

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
     * 文件下载
     */
    @GetMapping("/download/**")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request) {
        try {
            // 使用多种方法获取文件路径，确保正确处理中文
            String requestURI = request.getRequestURI();
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();
            
            log.info("原始requestURI: {}", requestURI);
            log.info("servletPath: {}", servletPath);
            log.info("pathInfo: {}", pathInfo);
            
            // 从请求路径中提取文件路径
            String filePath = requestURI.substring("/download/".length());
            log.info("提取的文件路径(编码): {}", filePath);
            
            // 尝试解码 - 处理可能的双重编码问题
            String decodedPath = filePath;
            try {
                decodedPath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
                log.info("第一次URL解码: {}", decodedPath);
                
                // 检查是否需要二次解码（某些情况下可能会有双重编码）
                if (decodedPath.contains("%")) {
                    String secondDecode = URLDecoder.decode(decodedPath, StandardCharsets.UTF_8);
                    log.info("第二次URL解码: {}", secondDecode);
                    decodedPath = secondDecode;
                }
            } catch (Exception e) {
                log.warn("URL解码失败，使用原始路径: {}", e.getMessage());
                decodedPath = filePath;
            }
            
            // 清理路径
            String cleanedPath = cleanPath(decodedPath);
            log.info("最终清理后的文件路径: {}", cleanedPath);
            
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
            
            // 构建分享链接 - 使用真实的局域网IP地址
            String baseUrl = getShareBaseUrl(request);
            String shareUrl = baseUrl + "/download/" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
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
} 