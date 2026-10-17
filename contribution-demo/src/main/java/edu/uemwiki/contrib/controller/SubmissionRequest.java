package edu.uemwiki.contrib.controller;

import org.springframework.web.multipart.MultipartFile;

/** 投稿 multipart/form-data 请求；附件为可选字段。 */
public class SubmissionRequest {
    public String title;
    public String course;
    public String majorId;
    public String body;
    public String author;
    public String contact;
    public MultipartFile file;

    // Spring 的 @ModelAttribute 使用 JavaBean 属性访问绑定 multipart 字段；
    // 仅有 public 字段不会自动写入这些值。
    public void setTitle(String title) { this.title = title; }
    public void setCourse(String course) { this.course = course; }
    public void setMajorId(String majorId) { this.majorId = majorId; }
    public void setBody(String body) { this.body = body; }
    public void setAuthor(String author) { this.author = author; }
    public void setContact(String contact) { this.contact = contact; }
    public void setFile(MultipartFile file) { this.file = file; }
}
