package edu.uemwiki.contrib;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("contribution")
public class Contribution {
    @TableId(type = IdType.AUTO) public Long id;
    public String title, courseName, body, authorName, contact, originalFilename, storedFilename, contentType;
    public Long fileSize;
    public String reviewStatus, publishStatus, publishError;
    public LocalDateTime createdAt, reviewedAt, updatedAt;
}
