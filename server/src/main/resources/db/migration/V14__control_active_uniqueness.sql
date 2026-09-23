ALTER TABLE control_request
  ADD COLUMN active_key VARCHAR(100)
    GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','UNKNOWN')
      THEN CONCAT(vehicle_id, ':', type) ELSE NULL END) STORED,
  ADD UNIQUE KEY control_active_key(active_key);
