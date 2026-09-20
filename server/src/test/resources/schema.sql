CREATE TABLE IF NOT EXISTS application_metadata (
    meta_key VARCHAR(64) PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL
);

MERGE INTO application_metadata (meta_key, meta_value)
KEY (meta_key) VALUES ('schema_version', '1');
