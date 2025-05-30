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
                               "- 简洁直观的Web界面\n\n" +
                               "将您要分享的文件放在此目录中即可开始使用。\n\n" +
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
        String localIp = NetworkUtils.getLocalIpAddress();
        
        log.info("=".repeat(60));
        log.info("Easy Sharer 启动成功!");
        log.info("共享目录: {}", Paths.get(rootPath).toAbsolutePath());
        log.info("本地访问: http://localhost:{}", serverPort);
        log.info("局域网访问: http://{}:{}", localIp, serverPort);
        log.info("=".repeat(60));
    }
} 