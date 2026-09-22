ALTER TABLE business_event ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
 ADD COLUMN next_attempt_at DATETIME(3) NULL;
CREATE TABLE event_delivery (
 event_id VARCHAR(36) PRIMARY KEY, delivered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(event_id) REFERENCES business_event(id)
);
CREATE TABLE notification (
 id VARCHAR(36) PRIMARY KEY, event_id VARCHAR(36) NOT NULL, employee_id VARCHAR(36) NOT NULL,
 title VARCHAR(100) NOT NULL, body VARCHAR(500) NOT NULL,
 target_type VARCHAR(20) NOT NULL, target_id VARCHAR(36) NOT NULL, target_workspace VARCHAR(20) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), read_at DATETIME(3),
 UNIQUE(event_id,employee_id), FOREIGN KEY(event_id) REFERENCES business_event(id), FOREIGN KEY(employee_id) REFERENCES employee(id),
 INDEX notification_account(employee_id,created_at)
);
