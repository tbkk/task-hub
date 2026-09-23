CREATE TABLE dispatch_request (
 id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36) NOT NULL, vehicle_id VARCHAR(36) NOT NULL,
 from_stop_id VARCHAR(36) NOT NULL, goal_stop_id VARCHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL, task_id VARCHAR(36), dispatch_id VARCHAR(64), message VARCHAR(500) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(batch_id) REFERENCES batch(id), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id),
 FOREIGN KEY(from_stop_id) REFERENCES stop(id), FOREIGN KEY(goal_stop_id) REFERENCES stop(id), INDEX dispatch_pending(status,created_at)
);
CREATE TABLE vehicle_task (
 id VARCHAR(36) PRIMARY KEY, vehicle_id VARCHAR(36) NOT NULL, batch_id VARCHAR(36) NOT NULL,
 dispatch_request_id VARCHAR(36) NOT NULL UNIQUE, provider VARCHAR(30) NOT NULL, dispatch_id VARCHAR(64) NOT NULL,
 state VARCHAR(25) NOT NULL, current_stop_id VARCHAR(36), next_stop_id VARCHAR(36),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE(provider,dispatch_id), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id), FOREIGN KEY(batch_id) REFERENCES batch(id),
 FOREIGN KEY(dispatch_request_id) REFERENCES dispatch_request(id)
);
CREATE TABLE active_vehicle_task (
 vehicle_id VARCHAR(36) PRIMARY KEY, dispatch_request_id VARCHAR(36) NOT NULL UNIQUE, task_id VARCHAR(36),
 FOREIGN KEY(vehicle_id) REFERENCES vehicle(id), FOREIGN KEY(dispatch_request_id) REFERENCES dispatch_request(id), FOREIGN KEY(task_id) REFERENCES vehicle_task(id)
);
CREATE TABLE vehicle_snapshot (
 vehicle_id VARCHAR(36) PRIMARY KEY, online BOOLEAN, speed DECIMAL(12,4), battery_percent DECIMAL(6,2),
 business_status VARCHAR(30), dispatch_id VARCHAR(64), current_stop_id VARCHAR(36), next_stop_id VARCHAR(36),
 reported_at DATETIME(3), received_at DATETIME(3), business_reported_at DATETIME(3), reconciliation BOOLEAN NOT NULL DEFAULT FALSE,
 longitude DECIMAL(12,8), latitude DECIMAL(12,8), distance_to_next_stop_meters DECIMAL(14,3), historical_leg_minutes DECIMAL(12,3),
 FOREIGN KEY(vehicle_id) REFERENCES vehicle(id)
);
CREATE TABLE vehicle_door_snapshot (
 compartment_id VARCHAR(36) PRIMARY KEY, vehicle_id VARCHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL, reported_at DATETIME(3) NOT NULL, received_at DATETIME(3) NOT NULL,
 FOREIGN KEY(compartment_id) REFERENCES compartment(id), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id)
);
CREATE TABLE integration_event (
 id VARCHAR(36) PRIMARY KEY, provider VARCHAR(30) NOT NULL, event_key VARCHAR(128) NOT NULL,
 vehicle_id VARCHAR(36) NOT NULL, dispatch_id VARCHAR(64), reported_at DATETIME(3) NOT NULL,
 received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), payload JSON NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 disposition VARCHAR(30) NOT NULL, UNIQUE(provider,event_key), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id), INDEX vehicle_event_time(vehicle_id,reported_at)
);
