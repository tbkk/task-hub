ALTER TABLE control_request DROP INDEX request_key,
 ADD COLUMN actor_id VARCHAR(36) NULL AFTER request_key,
 ADD COLUMN request_hash VARCHAR(64) NULL AFTER actor_id,
 ADD CONSTRAINT control_request_actor_fk FOREIGN KEY(actor_id) REFERENCES employee(id),
 ADD UNIQUE KEY control_request_actor_key(actor_id,request_key);
