package tech.brick.easysharer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import tech.brick.easysharer.model.FileInfo;
import tech.brick.easysharer.service.FileService;
import tech.brick.easysharer.util.NetworkUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 首页 - 显示根目录文件列表
     */
    @GetMapping("/")
    public String index(Model model, HttpServletRequest request) {
        return listFiles("", model, request);
    }

    /**
     * 浏览指定路径的文件
     */
    @GetMapping("/browse/**")
    public String browse(@RequestParam(value = "path", defaultValue = "") String path,
                        Model model, HttpServletRequest request) {
        return listFiles(path, model, request);
    }

    /**
     * 文件下载
     */
    @GetMapping("/download/**")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request) {
        // 从请求路径中提取文件路径
        String requestPath = request.getRequestURI();
        String filePath = requestPath.substring("/download/".length());
        
        try {
            Resource resource = fileService.getFileAsResource(filePath);
            
            // 获取文件名并进行URL编码
            String filename = resource.getFilename();
            if (filename == null) {
                filename = "download";
            }
            
            // 设置响应头
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"")
                .body(resource);
                
        } catch (Exception e) {
            log.error("文件下载失败: {}", filePath, e);
            return ResponseEntity.notFound().build();
        }
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
     * 通用文件列表方法
     */
    private String listFiles(String path, Model model, HttpServletRequest request) {
        try {
            List<FileInfo> files = fileService.listFiles(path);
            
            model.addAttribute("files", files);
            model.addAttribute("currentPath", path);
            model.addAttribute("rootPath", fileService.getRootPath());
            model.addAttribute("baseUrl", getBaseUrl(request));
            
            // 构建面包屑导航
            model.addAttribute("breadcrumbs", buildBreadcrumbs(path));
            
            return "file-list";
            
        } catch (Exception e) {
            log.error("列出文件失败: {}", path, e);
            model.addAttribute("error", "无法访问指定路径: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 构建面包屑导航
     */
    private List<BreadcrumbItem> buildBreadcrumbs(String path) {
        List<BreadcrumbItem> breadcrumbs = new java.util.ArrayList<>();
        breadcrumbs.add(new BreadcrumbItem("首页", ""));
        
        if (path != null && !path.isEmpty()) {
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();
            
            for (String part : parts) {
                if (!part.isEmpty()) {
                    if (currentPath.length() > 0) {
                        currentPath.append("/");
                    }
                    currentPath.append(part);
                    breadcrumbs.add(new BreadcrumbItem(part, currentPath.toString()));
                }
            }
        }
        
        return breadcrumbs;
    }

    /**
     * 获取基础URL（用于页面访问）
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
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
     * 面包屑导航项
     */
    public static class BreadcrumbItem {
        private final String name;
        private final String path;
        
        public BreadcrumbItem(String name, String path) {
            this.name = name;
            this.path = path;
        }
        
        public String getName() { return name; }
        public String getPath() { return path; }
    }
} 