CREATE TABLE ticket (
 id VARCHAR(36) PRIMARY KEY, number VARCHAR(40) NOT NULL UNIQUE, version INT NOT NULL DEFAULT 0,
 order_id VARCHAR(36) NOT NULL, warehouse_id VARCHAR(36) NOT NULL, reporter_id VARCHAR(36) NOT NULL,
 description VARCHAR(2000) NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'OPEN', result VARCHAR(2000),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(order_id) REFERENCES delivery_order(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(reporter_id) REFERENCES employee(id),
 INDEX ticket_reporter(reporter_id,created_at), INDEX ticket_warehouse(warehouse_id,state)
);
CREATE TABLE active_order_ticket (
 order_id VARCHAR(36) PRIMARY KEY, ticket_id VARCHAR(36) NOT NULL UNIQUE,
 FOREIGN KEY(order_id) REFERENCES delivery_order(id), FOREIGN KEY(ticket_id) REFERENCES ticket(id)
);
CREATE TABLE ticket_note (
 id VARCHAR(36) PRIMARY KEY, ticket_id VARCHAR(36) NOT NULL, author_id VARCHAR(36) NOT NULL,
 author_name VARCHAR(80) NOT NULL, text VARCHAR(2000) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(ticket_id) REFERENCES ticket(id), FOREIGN KEY(author_id) REFERENCES employee(id)
);
