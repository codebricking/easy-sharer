package tech.brick.easysharer.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 文本分享模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextShare {
    
    /**
     * 唯一标识
     */
    private String id;
    
    /**
     * 分享者IP地址
     */
    private String ipAddress;
    
    /**
     * 分享的文本内容
     */
    private String content;
    
    /**
     * 分享时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shareTime;
    
    /**
     * 分享者昵称（可选）
     */
    private String nickname;
    
    /**
     * 文本类型/标签（如：问题、笔记、代码等）
     */
    private String type;
    
    /**
     * 是否已过期
     */
    private boolean expired;
    
    /**
     * 过期时间（可选）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    
    /**
     * 查看次数
     */
    private int viewCount;
    
    public TextShare(String id, String ipAddress, String content, String nickname, String type) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.content = content;
        this.nickname = nickname;
        this.type = type;
        this.shareTime = LocalDateTime.now();
        this.expired = false;
        this.viewCount = 0;
    }
} 