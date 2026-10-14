CREATE TABLE IF NOT EXISTS contribution (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  course_name VARCHAR(200) NOT NULL,
  body CLOB NOT NULL,
  author_name VARCHAR(100) NOT NULL,
  contact VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(80) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  publish_status VARCHAR(20) NOT NULL DEFAULT 'NOT_PUBLISHED',
  publish_error CLOB NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_review_status ON contribution(review_status);
CREATE INDEX IF NOT EXISTS idx_publish_status ON contribution(publish_status);
