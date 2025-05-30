package tech.brick.easysharer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import tech.brick.easysharer.model.FileInfo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileService {

    @Value("${file.share.root-path:./shared}")
    private String rootPath;

    /**
     * 获取指定路径下的文件列表
     */
    public List<FileInfo> listFiles(String relativePath) {
        List<FileInfo> fileInfos = new ArrayList<>();
        
        try {
            Path basePath = Paths.get(rootPath).toAbsolutePath().normalize();
            Path targetPath = basePath;
            
            if (relativePath != null && !relativePath.isEmpty() && !relativePath.equals("/")) {
                targetPath = basePath.resolve(relativePath).normalize();
                
                // 安全检查：确保目标路径在根路径内
                if (!targetPath.startsWith(basePath)) {
                    log.warn("尝试访问根路径外的目录: {}", targetPath);
                    return fileInfos;
                }
            }
            
            if (!Files.exists(targetPath)) {
                log.warn("目录不存在: {}", targetPath);
                return fileInfos;
            }
            
            try (Stream<Path> paths = Files.list(targetPath)) {
                paths.forEach(path -> {
                    try {
                        FileInfo fileInfo = createFileInfo(path, basePath);
                        fileInfos.add(fileInfo);
                    } catch (IOException e) {
                        log.error("读取文件信息失败: {}", path, e);
                    }
                });
            }
            
            // 排序：目录在前，文件在后，同类型按名称排序
            fileInfos.sort((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            
        } catch (IOException e) {
            log.error("列出文件失败", e);
        }
        
        return fileInfos;
    }
    
    /**
     * 获取文件资源用于下载
     */
    public Resource getFileAsResource(String relativePath) throws MalformedURLException {
        log.info("FileService.getFileAsResource - 相对路径: {}", relativePath);
        
        Path basePath = Paths.get(rootPath).toAbsolutePath().normalize();
        log.info("FileService.getFileAsResource - 基础路径: {}", basePath);
        
        Path filePath = basePath.resolve(relativePath).normalize();
        log.info("FileService.getFileAsResource - 完整文件路径: {}", filePath);
        
        // 安全检查
        if (!filePath.startsWith(basePath)) {
            log.error("安全检查失败 - 文件路径不在基础路径内: 文件={}, 基础={}", filePath, basePath);
            throw new SecurityException("不允许访问根路径外的文件");
        }
        
        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            log.error("文件不存在: {}", filePath);
            throw new RuntimeException("文件不存在: " + relativePath);
        }
        
        // 检查是否为文件
        if (!Files.isRegularFile(filePath)) {
            log.error("路径不是文件: {}", filePath);
            throw new RuntimeException("路径不是文件: " + relativePath);
        }
        
        Resource resource = new UrlResource(filePath.toUri());
        if (resource.exists() && resource.isReadable()) {
            log.info("文件资源创建成功: {}", filePath);
            return resource;
        } else {
            log.error("文件资源创建失败或不可读: {}", filePath);
            throw new RuntimeException("文件不存在或不可读: " + relativePath);
        }
    }
    
    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String relativePath) {
        try {
            Path basePath = Paths.get(rootPath).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(relativePath).normalize();
            
            if (!filePath.startsWith(basePath)) {
                return false;
            }
            
            return Files.exists(filePath) && Files.isRegularFile(filePath);
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", relativePath, e);
            return false;
        }
    }
    
    /**
     * 获取根路径
     */
    public String getRootPath() {
        return Paths.get(rootPath).toAbsolutePath().toString();
    }
    
    /**
     * 创建文件信息对象
     */
    private FileInfo createFileInfo(Path path, Path basePath) throws IOException {
        String relativePath = basePath.relativize(path).toString().replace("\\", "/");
        String name = path.getFileName().toString();
        boolean isDirectory = Files.isDirectory(path);
        long size = isDirectory ? 0 : Files.size(path);
        LocalDateTime lastModified = LocalDateTime.ofInstant(
            Files.getLastModifiedTime(path).toInstant(),
            ZoneId.systemDefault()
        );
        
        return FileInfo.builder()
            .name(name)
            .relativePath(relativePath)
            .isDirectory(isDirectory)
            .size(size)
            .lastModified(lastModified)
            .build();
    }
} 