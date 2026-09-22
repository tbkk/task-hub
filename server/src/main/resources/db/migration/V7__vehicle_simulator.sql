CREATE TABLE simulator_scenario (
  operation VARCHAR(20) PRIMARY KEY,
  outcome VARCHAR(20) NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE simulator_command (
  request_id VARCHAR(128) PRIMARY KEY,
  operation VARCHAR(20) NOT NULL,
  command_json JSON NOT NULL,
  result_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
