CREATE TABLE batch (
 id VARCHAR(36) PRIMARY KEY, number VARCHAR(40) NOT NULL UNIQUE, version INT NOT NULL DEFAULT 0,
 warehouse_id VARCHAR(36) NOT NULL, stop_id VARCHAR(36) NOT NULL, slot_id VARCHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', load_status VARCHAR(20) NOT NULL DEFAULT 'UNCONFIRMED',
 vehicle_id VARCHAR(36), confirmed_by VARCHAR(36), confirmed_at DATETIME(3), confirmed_version INT,
 assignments_hash VARCHAR(64), dispatch_request_id VARCHAR(36), task_id VARCHAR(36),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(stop_id) REFERENCES stop(id),
 FOREIGN KEY(slot_id) REFERENCES reservation_slot(id), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id),
 FOREIGN KEY(confirmed_by) REFERENCES employee(id)
);
CREATE TABLE batch_order (
 id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36) NOT NULL, order_id VARCHAR(36) NOT NULL,
 joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), removed_at DATETIME(3),
 FOREIGN KEY(batch_id) REFERENCES batch(id), FOREIGN KEY(order_id) REFERENCES delivery_order(id),
 INDEX batch_order_members(batch_id,removed_at), INDEX batch_order_history(order_id)
);
CREATE TABLE active_order_batch (
 order_id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES delivery_order(id), FOREIGN KEY(batch_id) REFERENCES batch(id)
);
CREATE TABLE batch_assignment (
 batch_id VARCHAR(36) NOT NULL, order_id VARCHAR(36) NOT NULL, compartment_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(batch_id,order_id,compartment_id),
 FOREIGN KEY(batch_id) REFERENCES batch(id), FOREIGN KEY(order_id) REFERENCES delivery_order(id),
 FOREIGN KEY(compartment_id) REFERENCES compartment(id)
);
CREATE TABLE active_compartment (
 compartment_id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36) NOT NULL,
 FOREIGN KEY(compartment_id) REFERENCES compartment(id), FOREIGN KEY(batch_id) REFERENCES batch(id)
);
