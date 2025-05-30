package tech.brick.easysharer.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class FileInfo {
    private String name;
    private String relativePath;
    private boolean isDirectory;
    private long size;
    private LocalDateTime lastModified;
    
    /**
     * 获取格式化的文件大小
     */
    public String getFormattedSize() {
        if (isDirectory) {
            return "-";
        }
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 获取格式化的修改时间
     */
    public String getFormattedLastModified() {
        return lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 获取文件图标类型
     */
    public String getIconType() {
        if (isDirectory) {
            return "folder";
        }
        
        String extension = getFileExtension().toLowerCase();
        switch (extension) {
            case "txt":
            case "md":
            case "log":
                return "file-text";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                return "file-image";
            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
                return "file-video";
            case "mp3":
            case "wav":
            case "flac":
                return "file-audio";
            case "pdf":
                return "file-pdf";
            case "zip":
            case "rar":
            case "7z":
                return "file-archive";
            case "doc":
            case "docx":
                return "file-word";
            case "xls":
            case "xlsx":
                return "file-excel";
            case "ppt":
            case "pptx":
                return "file-powerpoint";
            default:
                return "file";
        }
    }
    
    /**
     * 获取完整的图标CSS类名
     */
    public String getIconClass() {
        if (isDirectory) {
            return "file-icon bi bi-folder-fill folder-icon";
        }
        
        String extension = getFileExtension().toLowerCase();
        switch (extension) {
            case "txt":
            case "md":
            case "log":
                return "file-icon bi bi-file-text-fill file-icon-text";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                return "file-icon bi bi-file-image-fill file-icon-image";
            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
                return "file-icon bi bi-file-play-fill file-icon-video";
            case "mp3":
            case "wav":
            case "flac":
                return "file-icon bi bi-file-music-fill file-icon-audio";
            case "pdf":
                return "file-icon bi bi-file-pdf-fill file-icon-pdf";
            case "zip":
            case "rar":
            case "7z":
                return "file-icon bi bi-file-zip-fill file-icon-archive";
            case "doc":
            case "docx":
                return "file-icon bi bi-file-word-fill file-icon-word";
            case "xls":
            case "xlsx":
                return "file-icon bi bi-file-excel-fill file-icon-excel";
            case "ppt":
            case "pptx":
                return "file-icon bi bi-file-ppt-fill file-icon-powerpoint";
            default:
                return "file-icon bi bi-file-fill file-icon-default";
        }
    }
    
    /**
     * 获取文件扩展名
     */
    public String getFileExtension() {
        if (isDirectory || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1);
    }
} 