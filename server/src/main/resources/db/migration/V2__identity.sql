CREATE TABLE employee (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(80) NOT NULL, phone VARCHAR(20) NOT NULL UNIQUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE admin_credential (
 employee_id VARCHAR(36) PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 must_change_password BOOLEAN NOT NULL DEFAULT TRUE, FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE verified_phone (
 employee_id VARCHAR(36) PRIMARY KEY, phone VARCHAR(20) NOT NULL UNIQUE,
 verified_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE role_grant (
 id VARCHAR(36) PRIMARY KEY, employee_id VARCHAR(36) NOT NULL, role VARCHAR(20) NOT NULL, scope VARCHAR(20) NOT NULL,
 UNIQUE(employee_id,role), FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE grant_warehouse (
 grant_id VARCHAR(36) NOT NULL, warehouse_id VARCHAR(36) NOT NULL, PRIMARY KEY(grant_id,warehouse_id),
 FOREIGN KEY(grant_id) REFERENCES role_grant(id)
);
CREATE TABLE platform_grant (
 id VARCHAR(36) PRIMARY KEY, employee_id VARCHAR(36) NOT NULL, capability VARCHAR(40) NOT NULL, scope VARCHAR(20) NOT NULL,
 UNIQUE(employee_id,capability), FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE platform_grant_warehouse (
 grant_id VARCHAR(36) NOT NULL, warehouse_id VARCHAR(36) NOT NULL, PRIMARY KEY(grant_id,warehouse_id),
 FOREIGN KEY(grant_id) REFERENCES platform_grant(id)
);
CREATE TABLE auth_session (
 token_hash CHAR(64) PRIMARY KEY, employee_id VARCHAR(36) NOT NULL, channel VARCHAR(12) NOT NULL,
 expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX session_employee(employee_id), FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE auth_rate_limit (
 rate_key CHAR(64) PRIMARY KEY, window_start DATETIME(3) NOT NULL, attempts INT NOT NULL
);
CREATE TABLE identity_lock (id INT PRIMARY KEY, initialized BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO identity_lock(id,initialized) VALUES(1,FALSE);
CREATE TABLE wechat_binding (
 id VARCHAR(36) PRIMARY KEY, employee_id VARCHAR(36) NOT NULL, app_id VARCHAR(80) NOT NULL, openid VARCHAR(128) NOT NULL,
 UNIQUE(app_id,openid), UNIQUE(employee_id,app_id), FOREIGN KEY(employee_id) REFERENCES employee(id)
);
CREATE TABLE wechat_binding_challenge (
 token_hash CHAR(64) PRIMARY KEY, app_id VARCHAR(80) NOT NULL, openid VARCHAR(128) NOT NULL, phone VARCHAR(20),
 expires_at DATETIME(3) NOT NULL, consumed_at DATETIME(3), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE sms_challenge (
 id VARCHAR(36) PRIMARY KEY, phone VARCHAR(20) NOT NULL, purpose VARCHAR(8) NOT NULL, code_hash CHAR(64) NOT NULL,
 binding_hash CHAR(64), attempts INT NOT NULL DEFAULT 0, expires_at DATETIME(3) NOT NULL, consumed_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX sms_phone(phone,created_at)
);
