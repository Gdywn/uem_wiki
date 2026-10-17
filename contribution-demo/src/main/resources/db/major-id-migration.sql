-- Existing installations: add an optional classification ID without rewriting or deleting contributions.
ALTER TABLE contribution
  ADD COLUMN major_id VARCHAR(80) NULL AFTER course_name;
