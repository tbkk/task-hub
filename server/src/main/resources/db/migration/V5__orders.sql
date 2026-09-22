CREATE TABLE reservation_slot (
 id VARCHAR(36) PRIMARY KEY, warehouse_id VARCHAR(36) NOT NULL, start_at DATETIME(3) NOT NULL, end_at DATETIME(3) NOT NULL,
 used INT NOT NULL DEFAULT 0, UNIQUE(warehouse_id,start_at), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), CHECK(used>=0)
);
CREATE TABLE delivery_order (
 id VARCHAR(36) PRIMARY KEY, number VARCHAR(40) NOT NULL UNIQUE, version INT NOT NULL DEFAULT 0,
 warehouse_id VARCHAR(36) NOT NULL, stop_id VARCHAR(36) NOT NULL, slot_id VARCHAR(36) NOT NULL,
 applicant_id VARCHAR(36) NOT NULL, receiver_id VARCHAR(36), receiver_name VARCHAR(80) NOT NULL, receiver_phone VARCHAR(20) NOT NULL,
 description TEXT NOT NULL, size TEXT NOT NULL, remark TEXT NOT NULL, status VARCHAR(30) NOT NULL,
 reservation_released BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(stop_id) REFERENCES stop(id), FOREIGN KEY(slot_id) REFERENCES reservation_slot(id),
 FOREIGN KEY(applicant_id) REFERENCES employee(id), FOREIGN KEY(receiver_id) REFERENCES employee(id),
 INDEX order_applicant(applicant_id,created_at), INDEX order_receiver(receiver_id,created_at), INDEX order_scope(warehouse_id,status,slot_id)
);
CREATE TABLE order_cancellation (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL,
 reason VARCHAR(1000) NOT NULL, result VARCHAR(1000), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(order_id) REFERENCES delivery_order(id), INDEX cancellation_order(order_id,created_at)
);
CREATE TABLE favorite_stop (
 employee_id VARCHAR(36) NOT NULL, stop_id VARCHAR(36) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(employee_id,stop_id), FOREIGN KEY(employee_id) REFERENCES employee(id), FOREIGN KEY(stop_id) REFERENCES stop(id)
);
