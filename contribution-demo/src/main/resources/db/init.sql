CREATE DATABASE IF NOT EXISTS uem_wiki_contrib CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE uem_wiki_contrib;
CREATE TABLE IF NOT EXISTS contribution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  course_name VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  author_name VARCHAR(100) NOT NULL,
  contact VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NULL,
  stored_filename VARCHAR(80) NULL,
  content_type VARCHAR(120) NULL,
  file_size BIGINT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  publish_status VARCHAR(20) NOT NULL DEFAULT 'NOT_PUBLISHED',
  publish_error TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_review_status (review_status), INDEX idx_publish_status (publish_status)
) ENGINE=InnoDB;
