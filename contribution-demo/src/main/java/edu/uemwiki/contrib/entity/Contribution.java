package edu.uemwiki.contrib.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("contribution")
public class Contribution {
    @TableId(type = IdType.AUTO) public Long id;
    public String title, courseName, body, authorName, contact, originalFilename, storedFilename, contentType;
    @TableField("major_id") public String majorId;
    public Long fileSize;
    public String reviewStatus, publishStatus, publishError;
    public LocalDateTime createdAt, reviewedAt, updatedAt;
}
