# Easy Sharer - 局域网文件分享工具

一款简单易用的局域网文件分享工具，java和vue3开发。

## 功能特点

- 🗂️ **文件浏览**: 浏览指定目录的文件和文件夹
- ⬇️ **文件下载**: 一键下载任意文件
- 🔗 **分享链接**: 生成文件分享链接，方便复制分享
- ⬆️ **文件上传**: 支持拖拽和多文件上传（可配置启用）
- 🎨 **现代界面**: 基于Bootstrap 5的简洁直观Web界面
- 📱 **响应式设计**: 支持桌面和移动设备
- 🔒 **安全防护**: 防止目录遍历攻击
- 🚀 **简单部署**: 一键运行jar包

## 快速开始

### 环境要求

- Java 17 或更高版本
- Maven 3.6 或更高版本

### 编译运行

1. **克隆项目**
   ```bash
   git clone https://github.com/codebricking/easy-sharer
   cd easy-sharer
   ```

2. **编译项目**
   ```bash
   mvn clean package
   ```

3. **运行应用**
   ```bash
   java -jar target/easy-sharer-0.0.1-SNAPSHOT.jar
   java -jar easy-sharer-0.0.1-SNAPSHOT.jar --server.port=9090 --file.share.root-path=D:\temp\share --file.upload.enabled=true

   ```

4. **访问应用**
   - 本地访问: http://localhost:8080
   - 局域网访问: http://[您的IP地址]:8080

### 配置选项

### 基本配置

在 `src/main/resources/application.properties` 文件中可以配置以下选项：

```properties
# 服务器端口（默认8080）
server.port=8080

# 文件分享根目录（默认./shared）
file.share.root-path=./shared

# 文件上传功能（默认启用）
file.upload.enabled=true

# 单次上传文件数量限制（默认10个）
file.upload.max-files-per-request=10

# 单个文件大小限制，单位MB（默认500MB）
file.upload.max-file-size=500
```

### 文件大小限制配置

如果需要支持更大的文件上传，需要同时修改两个配置：

1. 应用程序配置：`file.upload.max-file-size=1024` (支持1GB文件)
2. Spring Boot配置：`spring.servlet.multipart.max-file-size=1024MB`

**常用文件大小设置示例：**
- 100MB：`file.upload.max-file-size=100`
- 500MB：`file.upload.max-file-size=500` (默认)
- 1GB：`file.upload.max-file-size=1024`
- 2GB：`file.upload.max-file-size=2048`

**注意：** 文件越大，上传时间越长，请确保网络稳定。

#### 2. 命令行参数

```bash
# 指定端口
java -jar easy-sharer-0.0.1-SNAPSHOT.jar --server.port=9090

# 指定共享目录
java -jar easy-sharer-0.0.1-SNAPSHOT.jar --file.share.root-path=D:\temp\share

# 启用上传功能
java -jar easy-sharer-0.0.1-SNAPSHOT.jar --file.upload.enabled=true

# 组合使用
java -jar easy-sharer-0.0.1-SNAPSHOT.jar --server.port=9090 --file.share.root-path=D:\temp\share --file.upload.enabled=true
```

## 使用说明

### 文件管理

1. **浏览文件**: 在Web界面中点击文件夹名称可以进入子目录
2. **返回上级**: 使用面包屑导航快速返回上级目录
3. **文件信息**: 界面显示文件大小、修改时间等信息

### 文件下载

1. **直接下载**: 点击文件旁边的"下载"按钮
2. **分享链接**: 点击"分享"按钮生成下载链接，可复制分享给他人

### 文件上传（需启用）

1. **启用上传**: 通过配置 `file.upload.enabled=true` 开启上传功能
2. **拖拽上传**: 将文件拖拽到上传区域
3. **点击上传**: 点击"选择文件"按钮选择文件
4. **多文件上传**: 支持同时选择多个文件进行上传
5. **文件覆盖**: 同名文件会自动重命名，避免覆盖

### 添加文件

- 直接将文件复制到共享目录中，刷新页面即可看到
- 如果启用了上传功能，可以通过Web界面直接上传

## 目录结构

```
easy-sharer/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── tech/brick/easysharer/
│   │   │       ├── EasySharerApplication.java     # 主应用类
│   │   │       ├── config/
│   │   │       │   └── FileShareConfig.java       # 配置类
│   │   │       ├── controller/
│   │   │       │   └── FileController.java        # 控制器
│   │   │       ├── model/
│   │   │       │   └── FileInfo.java              # 文件信息模型
│   │   │       ├── service/
│   │   │       │   ├── FileService.java           # 文件服务
│   │   │       │   └── UploadService.java         # 上传服务
│   │   │       └── util/
│   │   │           └── NetworkUtils.java          # 网络工具类
│   │   └── resources/
│   │       ├── application.properties              # 应用配置
│   │       └── templates/
│   │           ├── file-list.html                  # 文件列表页面
│   │           └── error.html                      # 错误页面
│   └── test/
├── shared/                                         # 默认共享目录
├── start.bat                                       # Windows启动脚本
├── pom.xml                                         # Maven配置
├── README.md                                       # 项目说明
```

## 技术栈

- **后端**: Spring Boot 3.5.0
- **前端**:vue3
- **构建工具**: Maven\vite
- **Java版本**: 17

## 安全特性

- **路径验证**: 防止目录遍历攻击，确保只能访问共享目录内的文件
- **文件名清理**: 上传时自动清理危险字符
- **文件类型检查**: 支持多种文件类型的图标显示
- **错误处理**: 友好的错误页面和日志记录
- **可配置功能**: 上传功能默认关闭，需手动启用

## 开发说明

### 本地开发

1. 导入IDE（推荐IntelliJ IDEA）
2. 运行`EasySharerApplication.java`
3. 访问 http://localhost:8080

### 自定义开发

- 修改`FileService.java`添加新的文件操作功能
- 修改`file-list.html`自定义界面样式
- 在`application.properties`中添加新的配置项

## 常见问题

### Q: 如何更改共享目录？
A: 修改`application.properties`中的`file.share.root-path`配置，或使用命令行参数`--file.share.root-path=/your/path`

### Q: 如何启用上传功能？
A: 设置`file.upload.enabled=true`，或使用命令行参数`--file.upload.enabled=true`

### Q: 如何在局域网中访问？
A: 确保防火墙允许8080端口访问，然后使用`http://[服务器IP]:8080`访问

### Q: 支持哪些文件类型？
A: 支持所有文件类型的下载和上传，界面会根据文件扩展名显示不同的图标

### Q: 文件大小有限制吗？
A: 默认支持最大100MB的文件，可通过配置`spring.servlet.multipart.max-file-size`修改

### Q: 上传的文件保存在哪里？
A: 文件保存在配置的共享目录中，支持上传到子目录

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 贡献

欢迎提交Issue和Pull Request来改进这个项目！ 