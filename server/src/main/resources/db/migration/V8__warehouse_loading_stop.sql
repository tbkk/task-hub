CREATE TABLE warehouse_loading_stop (
 warehouse_id VARCHAR(36) PRIMARY KEY,
 stop_id VARCHAR(36) NOT NULL,
 version INT NOT NULL DEFAULT 1,
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
 FOREIGN KEY(stop_id) REFERENCES stop(id)
);
