ALTER TABLE delivery_order ADD COLUMN pickup_employee_id VARCHAR(36) NULL, ADD COLUMN picked_up_at DATETIME(3) NULL,
 ADD CONSTRAINT delivery_order_pickup_employee_fk FOREIGN KEY(pickup_employee_id) REFERENCES employee(id);
CREATE TABLE control_request (
 id VARCHAR(36) PRIMARY KEY, request_key VARCHAR(128) NOT NULL UNIQUE, type VARCHAR(30) NOT NULL,
 vehicle_id VARCHAR(36) NOT NULL, task_id VARCHAR(36), order_id VARCHAR(36),
 compartment_ids JSON NOT NULL, status VARCHAR(20) NOT NULL, message VARCHAR(500) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(vehicle_id) REFERENCES vehicle(id), FOREIGN KEY(task_id) REFERENCES vehicle_task(id), FOREIGN KEY(order_id) REFERENCES delivery_order(id),
 INDEX control_vehicle_type(vehicle_id,type,status)
);
