package tech.brick.easysharer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import tech.brick.easysharer.util.NetworkUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class FileShareConfig implements CommandLineRunner {

    @Value("${file.share.root-path:./shared}")
    private String rootPath;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${file.upload.enabled:false}")
    private boolean uploadEnabled;

    @Override
    public void run(String... args) throws Exception {
        initializeSharedDirectory();
        logStartupInfo();
    }

    /**
     * 初始化共享目录
     */
    private void initializeSharedDirectory() {
        try {
            Path sharedPath = Paths.get(rootPath).toAbsolutePath();
            
            if (!Files.exists(sharedPath)) {
                Files.createDirectories(sharedPath);
                log.info("创建共享目录: {}", sharedPath);
                
                // 创建示例文件
                createSampleFiles(sharedPath);
            } else {
                log.info("使用现有共享目录: {}", sharedPath);
            }
            
        } catch (IOException e) {
            log.error("初始化共享目录失败", e);
            throw new RuntimeException("无法创建共享目录: " + rootPath, e);
        }
    }

    /**
     * 创建示例文件
     */
    private void createSampleFiles(Path sharedPath) {
        try {
            // 创建示例文本文件
            Path readmePath = sharedPath.resolve("README.txt");
            if (!Files.exists(readmePath)) {
                String content = "欢迎使用 Easy Sharer!\n\n" +
                               "这是一个简单易用的局域网文件分享工具。\n\n" +
                               "功能特点:\n" +
                               "- 浏览文件和文件夹\n" +
                               "- 一键下载文件\n" +
                               "- 生成分享链接\n" +
                               "- 简洁直观的Web界面\n" +
                               (uploadEnabled ? "- 文件上传功能\n" : "") +
                               "\n将您要分享的文件放在此目录中即可开始使用。\n\n" +
                               "祝您使用愉快！";
                Files.write(readmePath, content.getBytes("UTF-8"));
                log.info("创建示例文件: {}", readmePath.getFileName());
            }
            
            // 创建示例子目录
            Path exampleDir = sharedPath.resolve("示例文件夹");
            if (!Files.exists(exampleDir)) {
                Files.createDirectory(exampleDir);
                
                // 在子目录中创建文件
                Path exampleFile = exampleDir.resolve("示例文件.txt");
                String exampleContent = "这是一个示例文件，位于子目录中。\n\n" +
                                      "您可以通过点击文件夹名称来浏览子目录。";
                Files.write(exampleFile, exampleContent.getBytes("UTF-8"));
                log.info("创建示例子目录和文件: {}", exampleDir.getFileName());
            }
            
        } catch (IOException e) {
            log.warn("创建示例文件失败", e);
        }
    }

    /**
     * 记录启动信息
     */
    private void logStartupInfo() {
        // 获取所有局域网IP地址
        java.util.List<String> allIps = NetworkUtils.getAllLocalIpAddresses();
        String primaryIp = allIps.isEmpty() ? "localhost" : allIps.get(0);
        
        log.info("=".repeat(60));
        log.info("Easy Sharer 启动成功!");
        log.info("共享目录: {}", Paths.get(rootPath).toAbsolutePath());
        log.info("本地访问: http://localhost:{}", serverPort);
        
        if (allIps.isEmpty()) {
            log.warn("未找到局域网IP地址，只能本地访问");
        } else {
            log.info("局域网访问地址 (按优先级排序):");
            for (int i = 0; i < allIps.size(); i++) {
                String ip = allIps.get(i);
                String url = "http://" + ip + ":" + serverPort;
                if (i == 0) {
                    log.info("  推荐: {} ← 优先使用此地址", url);
                } else {
                    log.info("  备选: {}", url);
                }
            }
            
            // 给出使用建议
            if (allIps.size() > 1) {
                log.info("提示: 如果主要地址无法访问，请尝试备选地址");
                log.info("提示: 确保手机/设备连接到相同的WiFi网络");
            }
        }
        
        log.info("文件上传功能: {}", uploadEnabled ? "已启用" : "已禁用");
        if (!uploadEnabled) {
            log.info("启用上传功能: --file.upload.enabled=true");
        }
        
        // 网络故障排除提示
        if (!allIps.isEmpty()) {
            log.info("故障排除:");
            log.info("  - 如无法访问，请检查Windows防火墙设置");
            log.info("  - 确保8080端口未被其他程序占用");
            log.info("  - 某些路由器可能启用了设备隔离功能");
        }
        
        log.info("=".repeat(60));
    }
} 