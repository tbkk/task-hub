CREATE TABLE warehouse (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(80) NOT NULL, code VARCHAR(80) NOT NULL UNIQUE,
 enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
ALTER TABLE employee ADD COLUMN home_warehouse_id VARCHAR(36) NULL,
 ADD CONSTRAINT employee_home_warehouse_fk FOREIGN KEY(home_warehouse_id) REFERENCES warehouse(id);
CREATE TABLE stop (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(80) NOT NULL, external_stop_id VARCHAR(100) NOT NULL UNIQUE,
 enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE stop_warehouse (
 stop_id VARCHAR(36) NOT NULL, warehouse_id VARCHAR(36) NOT NULL, PRIMARY KEY(stop_id,warehouse_id),
 FOREIGN KEY(stop_id) REFERENCES stop(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
);
CREATE TABLE vehicle (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(80) NOT NULL, external_vehicle_name VARCHAR(100) NOT NULL UNIQUE,
 warehouse_id VARCHAR(36) NOT NULL, enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
);
CREATE TABLE vehicle_stop (
 vehicle_id VARCHAR(36) NOT NULL, stop_id VARCHAR(36) NOT NULL, PRIMARY KEY(vehicle_id,stop_id),
 FOREIGN KEY(vehicle_id) REFERENCES vehicle(id), FOREIGN KEY(stop_id) REFERENCES stop(id)
);
CREATE TABLE compartment (
 id VARCHAR(36) PRIMARY KEY, vehicle_id VARCHAR(36) NOT NULL, hardware_no VARCHAR(80) NOT NULL,
 label VARCHAR(80) NOT NULL, enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE(vehicle_id,hardware_no), FOREIGN KEY(vehicle_id) REFERENCES vehicle(id)
);
CREATE TABLE warehouse_rule (
 warehouse_id VARCHAR(36) PRIMARY KEY, version INT NOT NULL DEFAULT 0, config JSON NOT NULL,
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
);
CREATE TABLE external_catalog_resource (
 resource VARCHAR(20) NOT NULL, id VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, PRIMARY KEY(resource,id)
);
CREATE TABLE external_catalog_compartment (
 vehicle_name VARCHAR(100) NOT NULL, hardware_no VARCHAR(80) NOT NULL, PRIMARY KEY(vehicle_name,hardware_no)
);
