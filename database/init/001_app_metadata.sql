-- The database/user are provisioned by the MySQL container or the operator.
-- This table only verifies the application's database connectivity.
CREATE TABLE IF NOT EXISTS application_metadata (
    meta_key VARCHAR(64) NOT NULL PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO application_metadata (meta_key, meta_value)
VALUES ('schema_version', '1')
ON DUPLICATE KEY UPDATE meta_value = '1';
