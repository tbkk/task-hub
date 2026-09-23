CREATE TABLE integration_setting (
  id TINYINT NOT NULL PRIMARY KEY,
  provider VARCHAR(40) NOT NULL,
  base_url VARCHAR(255) NOT NULL,
  org_id VARCHAR(120) NOT NULL,
  app_id VARCHAR(120) NOT NULL,
  client_secret VARCHAR(255) NULL,
  access_token VARCHAR(255) NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  version INT NOT NULL DEFAULT 0,
  updated_by VARCHAR(36) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT integration_setting_employee_fk FOREIGN KEY(updated_by) REFERENCES employee(id)
);
CREATE TABLE correction_record (
  id VARCHAR(36) PRIMARY KEY,
  object_type VARCHAR(40) NOT NULL,
  object_id VARCHAR(36) NOT NULL,
  field_name VARCHAR(60) NOT NULL,
  before_value TEXT NULL,
  after_value TEXT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  correction_version INT NOT NULL,
  actor_id VARCHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE(object_type, object_id, correction_version),
  FOREIGN KEY(actor_id) REFERENCES employee(id),
  INDEX correction_object(object_type, object_id, created_at)
);
