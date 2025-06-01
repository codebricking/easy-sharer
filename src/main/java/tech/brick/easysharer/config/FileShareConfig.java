package tech.brick.easysharer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
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
    
    @Value("${file.upload.enabled:false}")
    private boolean uploadEnabled;
    
    private final ApplicationContext applicationContext;
    
    public FileShareConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

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
                               "- 文本分享功能\n" +
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
     * 获取实际运行的端口号
     */
    private int getActualPort() {
        try {
            if (applicationContext instanceof ServletWebServerApplicationContext) {
                ServletWebServerApplicationContext webServerAppContext = 
                    (ServletWebServerApplicationContext) applicationContext;
                return webServerAppContext.getWebServer().getPort();
            }
        } catch (Exception e) {
            log.debug("无法获取实际端口号，使用默认值", e);
        }
        
        // 回退到配置值
        String portProperty = applicationContext.getEnvironment().getProperty("server.port", "8080");
        try {
            return Integer.parseInt(portProperty);
        } catch (NumberFormatException e) {
            log.warn("端口配置无效: {}, 使用默认值8080", portProperty);
            return 8080;
        }
    }

    /**
     * 记录启动信息
     */
    private void logStartupInfo() {
        // 获取实际端口号
        int actualPort = getActualPort();
        
        // 获取所有局域网IP地址
        java.util.List<String> allIps = NetworkUtils.getAllLocalIpAddresses();
        String primaryIp = allIps.isEmpty() ? "localhost" : allIps.get(0);
        
        log.info("=".repeat(60));
        log.info("Easy Sharer 启动成功!");
        log.info("共享目录: {}", Paths.get(rootPath).toAbsolutePath());
        log.info("本地访问: http://localhost:{}", actualPort);
        
        if (allIps.isEmpty()) {
            log.warn("未找到局域网IP地址，只能本地访问");
        } else {
            log.info("局域网访问地址 (按优先级排序):");
            for (int i = 0; i < allIps.size(); i++) {
                String ip = allIps.get(i);
                String url = "http://" + ip + ":" + actualPort;
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
        log.info("文本分享功能: 已启用");
        if (!uploadEnabled) {
            log.info("启用上传功能: --file.upload.enabled=true");
        }
        
        // 网络故障排除提示 - 使用实际端口号
        if (!allIps.isEmpty()) {
            log.info("故障排除:");
            log.info("  - 如无法访问，请检查Windows防火墙设置");
            log.info("  - 确保{}端口未被其他程序占用", actualPort);
            log.info("  - 某些路由器可能启用了设备隔离功能");
            log.info("  - 文本分享数据存储在: {}/text/", Paths.get(rootPath).toAbsolutePath());
        }
        
        log.info("=".repeat(60));
    }
} 