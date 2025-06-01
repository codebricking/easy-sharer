package tech.brick.easysharer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.brick.easysharer.model.TextShare;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文本分享服务
 */
@Slf4j
@Service
public class TextShareService {
    
    @Value("${file.share.root-path:./shared}")
    private String rootPath;
    
    /**
     * 内存中存储文本分享数据
     * Key: 分享ID, Value: TextShare对象
     */
    private final Map<String, TextShare> textShares = new ConcurrentHashMap<>();
    
    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;
    
    /**
     * 文本分享存储目录
     */
    private Path textShareDir;
    
    /**
     * 数据文件名
     */
    private static final String DATA_FILE_NAME = "text_shares.json";
    
    /**
     * 默认过期时间（小时）
     */
    private static final int DEFAULT_EXPIRE_HOURS = 24;
    
    /**
     * 最大存储数量
     */
    private static final int MAX_SHARES = 1000;
    
    public TextShareService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 初始化服务，创建存储目录并加载数据
     */
    @PostConstruct
    public void init() {
        try {
            // 创建text目录
            textShareDir = Paths.get(rootPath, "text").toAbsolutePath();
            if (!Files.exists(textShareDir)) {
                Files.createDirectories(textShareDir);
                log.info("创建文本分享存储目录: {}", textShareDir);
            } else {
                log.info("文本分享存储目录已存在: {}", textShareDir);
            }
            
            // 从文件加载数据
            loadDataFromFile();
            
            // 清理过期数据
            cleanupExpiredShares();
            
            log.info("文本分享服务初始化完成，当前分享数量: {}", textShares.size());
            
        } catch (Exception e) {
            log.error("文本分享服务初始化失败", e);
        }
    }
    
    /**
     * 从文件加载数据
     */
    private void loadDataFromFile() {
        Path dataFile = textShareDir.resolve(DATA_FILE_NAME);
        
        if (!Files.exists(dataFile)) {
            log.info("文本分享数据文件不存在，将创建新文件: {}", dataFile);
            return;
        }
        
        try {
            String jsonContent = Files.readString(dataFile);
            if (jsonContent.trim().isEmpty()) {
                log.info("文本分享数据文件为空");
                return;
            }
            
            // 解析JSON数组
            TextShare[] sharesArray = objectMapper.readValue(jsonContent, TextShare[].class);
            
            // 加载到内存中，同时验证数据有效性
            int loadedCount = 0;
            int expiredCount = 0;
            
            for (TextShare share : sharesArray) {
                if (share != null && share.getId() != null) {
                    // 检查是否过期
                    if (share.getExpireTime() != null && LocalDateTime.now().isAfter(share.getExpireTime())) {
                        expiredCount++;
                        continue;
                    }
                    
                    textShares.put(share.getId(), share);
                    loadedCount++;
                }
            }
            
            log.info("从文件加载文本分享数据: 总数={}, 有效={}, 过期={}, 文件路径={}", 
                    sharesArray.length, loadedCount, expiredCount, dataFile);
                    
        } catch (Exception e) {
            log.error("加载文本分享数据失败: {}", dataFile, e);
        }
    }
    
    /**
     * 保存数据到文件
     */
    private void saveDataToFile() {
        Path dataFile = textShareDir.resolve(DATA_FILE_NAME);
        
        try {
            // 获取所有有效的分享
            List<TextShare> validShares = textShares.values().stream()
                    .filter(share -> !share.isExpired())
                    .sorted((a, b) -> b.getShareTime().compareTo(a.getShareTime()))
                    .collect(Collectors.toList());
            
            // 序列化为JSON
            String jsonContent = objectMapper.writeValueAsString(validShares);
            
            // 写入文件
            Files.writeString(dataFile, jsonContent);
            
            log.debug("文本分享数据已保存到文件: {} (数量: {})", dataFile, validShares.size());
            
        } catch (Exception e) {
            log.error("保存文本分享数据失败: {}", dataFile, e);
        }
    }
    
    /**
     * 创建文本分享
     */
    public TextShare createTextShare(String content, String ipAddress, String nickname, String type) {
        try {
            // 检查内容是否为空
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("分享内容不能为空");
            }
            
            // 检查内容长度
            if (content.length() > 10000) {
                throw new IllegalArgumentException("分享内容过长，最大支持10000字符");
            }
            
            // 生成唯一ID
            String id = generateId();
            
            // 创建分享对象
            TextShare textShare = new TextShare(id, ipAddress, content.trim(), nickname, type);
            
            // 设置过期时间
            textShare.setExpireTime(LocalDateTime.now().plusHours(DEFAULT_EXPIRE_HOURS));
            
            // 检查存储空间
            cleanupExpiredShares();
            if (textShares.size() >= MAX_SHARES) {
                // 删除最旧的分享
                removeOldestShare();
            }
            
            // 存储分享
            textShares.put(id, textShare);
            
            // 保存到文件
            saveDataToFile();
            
            log.info("创建文本分享成功: ID={}, IP={}, 类型={}", id, ipAddress, type);
            return textShare;
            
        } catch (Exception e) {
            log.error("创建文本分享失败: IP={}, 错误={}", ipAddress, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取所有有效的文本分享（按时间倒序）
     */
    public List<TextShare> getAllTextShares() {
        cleanupExpiredShares();
        
        return textShares.values().stream()
                .filter(share -> !share.isExpired())
                .sorted((a, b) -> b.getShareTime().compareTo(a.getShareTime()))
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取文本分享
     */
    public TextShare getTextShare(String id) {
        TextShare share = textShares.get(id);
        if (share != null && !share.isExpired()) {
            // 增加查看次数
            share.setViewCount(share.getViewCount() + 1);
            
            // 保存更新到文件
            saveDataToFile();
            
            log.info("获取文本分享: ID={}, 查看次数={}", id, share.getViewCount());
            return share;
        }
        return null;
    }
    
    /**
     * 根据IP地址获取文本分享
     */
    public List<TextShare> getTextSharesByIp(String ipAddress) {
        cleanupExpiredShares();
        
        return textShares.values().stream()
                .filter(share -> !share.isExpired() && ipAddress.equals(share.getIpAddress()))
                .sorted((a, b) -> b.getShareTime().compareTo(a.getShareTime()))
                .collect(Collectors.toList());
    }
    
    /**
     * 删除文本分享（只有创建者可以删除）
     */
    public boolean deleteTextShare(String id, String ipAddress) {
        TextShare share = textShares.get(id);
        if (share != null && ipAddress.equals(share.getIpAddress())) {
            textShares.remove(id);
            
            // 保存到文件
            saveDataToFile();
            
            log.info("删除文本分享: ID={}, IP={}", id, ipAddress);
            return true;
        }
        return false;
    }
    
    /**
     * 清理过期的分享
     */
    public void cleanupExpiredShares() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expiredIds = new ArrayList<>();
        
        for (Map.Entry<String, TextShare> entry : textShares.entrySet()) {
            TextShare share = entry.getValue();
            if (share.getExpireTime() != null && now.isAfter(share.getExpireTime())) {
                share.setExpired(true);
                expiredIds.add(entry.getKey());
            }
        }
        
        // 删除过期的分享
        boolean hasExpired = false;
        for (String id : expiredIds) {
            textShares.remove(id);
            hasExpired = true;
        }
        
        // 如果有过期的分享，保存到文件
        if (hasExpired) {
            saveDataToFile();
            log.info("清理过期分享: 数量={}", expiredIds.size());
        }
    }
    
    /**
     * 删除最旧的分享
     */
    private void removeOldestShare() {
        Optional<Map.Entry<String, TextShare>> oldest = textShares.entrySet().stream()
                .min((a, b) -> a.getValue().getShareTime().compareTo(b.getValue().getShareTime()));
                
        if (oldest.isPresent()) {
            String oldestId = oldest.get().getKey();
            textShares.remove(oldestId);
            saveDataToFile();
            log.info("删除最旧分享: ID={}", oldestId);
        }
    }
    
    /**
     * 生成唯一ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        cleanupExpiredShares();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShares", textShares.size());
        stats.put("totalViews", textShares.values().stream().mapToInt(TextShare::getViewCount).sum());
        stats.put("uniqueIps", textShares.values().stream().map(TextShare::getIpAddress).distinct().count());
        stats.put("dataFilePath", textShareDir.resolve(DATA_FILE_NAME).toString());
        
        // 按类型统计
        Map<String, Long> typeStats = textShares.values().stream()
                .collect(Collectors.groupingBy(share -> share.getType() != null ? share.getType() : "未分类", 
                        Collectors.counting()));
        stats.put("typeStats", typeStats);
        
        return stats;
    }
    
    /**
     * 获取文本分享存储目录路径
     */
    public String getTextShareDirectory() {
        return textShareDir != null ? textShareDir.toString() : "未初始化";
    }
} 