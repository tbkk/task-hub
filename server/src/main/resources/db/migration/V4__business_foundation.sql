CREATE TABLE idempotency_record (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, workspace VARCHAR(20) NOT NULL,
 route VARCHAR(160) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
 request_hash CHAR(64) NOT NULL, authorization_hash CHAR(64) NOT NULL, response JSON NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY idempotency_scope(actor_id,workspace,route,idempotency_key),
 FOREIGN KEY(actor_id) REFERENCES employee(id)
);
CREATE TABLE audit_event (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, actor_name VARCHAR(80) NOT NULL,
 workspace VARCHAR(20) NOT NULL, object_type VARCHAR(40) NOT NULL, object_id VARCHAR(36) NOT NULL,
 action VARCHAR(80) NOT NULL, before_state JSON NULL, after_state JSON NULL, reason VARCHAR(1000),
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX audit_object(object_type,object_id,occurred_at), INDEX audit_actor(actor_id,occurred_at)
);
CREATE TABLE business_event (
 id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(36) NOT NULL,
 payload JSON NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 processed_at DATETIME(3) NULL, INDEX event_pending(processed_at,created_at)
);
