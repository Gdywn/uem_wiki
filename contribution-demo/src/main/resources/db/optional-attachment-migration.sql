-- Existing installations only: allow text-only contributions by making file metadata nullable.
ALTER TABLE contribution
  MODIFY original_filename VARCHAR(255) NULL,
  MODIFY stored_filename VARCHAR(80) NULL,
  MODIFY content_type VARCHAR(120) NULL,
  MODIFY file_size BIGINT NULL;
