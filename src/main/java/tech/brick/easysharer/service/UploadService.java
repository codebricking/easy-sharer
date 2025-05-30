package tech.brick.easysharer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UploadService {

    @Value("${file.share.root-path:./shared}")
    private String rootPath;

    @Value("${file.upload.enabled:false}")
    private boolean uploadEnabled;

    @Value("${file.upload.max-files-per-request:10}")
    private int maxFilesPerRequest;

    @Value("${file.upload.max-file-size:500}")
    private int maxFileSizeMB;

    /**
     * 检查上传功能是否启用
     */
    public boolean isUploadEnabled() {
        log.info("isUploadEnabled() 被调用，返回值: {}", uploadEnabled);
        return uploadEnabled;
    }

    /**
     * 上传文件到指定路径
     */
    public List<String> uploadFiles(List<MultipartFile> files, String relativePath) throws IOException {
        log.info("UploadService.uploadFiles 开始执行");
        log.info("上传功能启用状态: {}", uploadEnabled);
        log.info("根路径配置: {}", rootPath);
        log.info("最大文件数限制: {}", maxFilesPerRequest);
        
        if (!uploadEnabled) {
            throw new IllegalStateException("文件上传功能未启用");
        }

        if (files.size() > maxFilesPerRequest) {
            throw new IllegalArgumentException("一次最多只能上传 " + maxFilesPerRequest + " 个文件");
        }

        // 清理和规范化相对路径
        relativePath = cleanPath(relativePath);
        log.info("上传文件到路径: '{}', 文件数量: {}", relativePath, files.size());

        List<String> uploadedFiles = new ArrayList<>();
        Path basePath = Paths.get(rootPath).toAbsolutePath().normalize();
        log.info("基础路径: {}", basePath);
        
        // 检查根路径是否存在和可写
        if (!Files.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
                log.info("创建根目录: {}", basePath);
            } catch (IOException e) {
                log.error("无法创建根目录: {}", basePath, e);
                throw new IOException("无法创建根目录: " + basePath + " - " + e.getMessage(), e);
            }
        }
        
        if (!Files.isWritable(basePath)) {
            log.error("根目录不可写: {}", basePath);
            throw new SecurityException("根目录不可写: " + basePath);
        }
        
        Path targetDir = basePath;

        // 如果指定了相对路径，则上传到该子目录
        if (relativePath != null && !relativePath.isEmpty()) {
            targetDir = basePath.resolve(relativePath).normalize();
            log.info("目标目录: {}", targetDir);
            
            // 安全检查：确保目标路径在根路径内
            if (!targetDir.startsWith(basePath)) {
                log.error("安全检查失败: 目标路径 {} 不在根路径 {} 内", targetDir, basePath);
                throw new SecurityException("不允许上传到根路径外的目录");
            }
        }

        log.info("最终目标上传目录: {}", targetDir);

        // 确保目标目录存在
        if (!Files.exists(targetDir)) {
            try {
                Files.createDirectories(targetDir);
                log.info("创建目录: {}", targetDir);
            } catch (IOException e) {
                log.error("无法创建目标目录: {}", targetDir, e);
                throw new IOException("无法创建目标目录: " + targetDir + " - " + e.getMessage(), e);
            }
        }
        
        // 检查目标目录是否可写
        if (!Files.isWritable(targetDir)) {
            log.error("目标目录不可写: {}", targetDir);
            throw new SecurityException("目标目录不可写: " + targetDir);
        }

        // 上传每个文件
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                log.error("发现空文件");
                throw new IllegalArgumentException("不能上传空文件");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                log.error("文件名为空");
                throw new IllegalArgumentException("文件名不能为空");
            }

            // 检查文件大小
            long maxFileSizeBytes = (long) maxFileSizeMB * 1024 * 1024;
            if (file.getSize() > maxFileSizeBytes) {
                log.error("文件过大: {} - {} bytes, 限制: {} MB", originalFilename, file.getSize(), maxFileSizeMB);
                throw new IllegalArgumentException("文件 " + originalFilename + " 过大，最大支持" + maxFileSizeMB + "MB");
            }

            // 清理文件名，移除路径分隔符等危险字符
            String cleanFileName = sanitizeFileName(originalFilename);
            if (cleanFileName.isEmpty()) {
                log.error("文件名清理后为空: {}", originalFilename);
                throw new IllegalArgumentException("文件名 " + originalFilename + " 无效");
            }
            
            Path targetFile = targetDir.resolve(cleanFileName);
            log.info("准备保存文件: {} -> {}", originalFilename, targetFile);

            // 如果文件已存在，添加序号
            targetFile = getUniqueFileName(targetFile);
            log.info("最终文件路径: {}", targetFile);

            try {
                // 保存文件
                Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                
                // 记录成功上传的文件
                String relativeFilePath = basePath.relativize(targetFile).toString().replace("\\", "/");
                uploadedFiles.add(relativeFilePath);
                
                log.info("文件上传成功: {} -> {}", originalFilename, targetFile);
            } catch (IOException e) {
                log.error("文件上传失败: {} -> {}", originalFilename, targetFile, e);
                throw new IOException("保存文件失败: " + originalFilename + " - " + e.getMessage(), e);
            }
        }

        log.info("上传完成，成功上传 {} 个文件: {}", uploadedFiles.size(), uploadedFiles);
        return uploadedFiles;
    }

    /**
     * 清理路径
     */
    private String cleanPath(String path) {
        if (path == null) {
            return "";
        }
        
        path = path.trim();
        
        // 移除开头和结尾的斜杠
        while (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }
        while (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        
        // 替换反斜杠为正斜杠
        path = path.replace("\\", "/");
        
        return path;
    }

    /**
     * 清理文件名，移除危险字符
     */
    private String sanitizeFileName(String fileName) {
        // 移除路径分隔符和其他危险字符
        fileName = fileName.replaceAll("[/\\\\:*?\"<>|]", "_");
        
        // 移除开头的点号（隐藏文件）
        while (fileName.startsWith(".")) {
            fileName = fileName.substring(1);
        }
        
        // 如果文件名为空或只有扩展名，给个默认名称
        if (fileName.trim().isEmpty()) {
            fileName = "unnamed_file";
        }
        
        return fileName;
    }

    /**
     * 获取唯一的文件名（如果文件已存在，添加序号）
     */
    private Path getUniqueFileName(Path originalPath) {
        if (!Files.exists(originalPath)) {
            return originalPath;
        }

        String fileName = originalPath.getFileName().toString();
        String nameWithoutExt;
        String extension;

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        } else {
            nameWithoutExt = fileName;
            extension = "";
        }

        Path parentDir = originalPath.getParent();
        int counter = 1;

        Path uniquePath;
        do {
            String uniqueFileName = nameWithoutExt + "_(" + counter + ")" + extension;
            uniquePath = parentDir.resolve(uniqueFileName);
            counter++;
        } while (Files.exists(uniquePath) && counter < 1000); // 防止无限循环

        return uniquePath;
    }
} 