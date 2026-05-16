-- V2: counter_snapshot table for async DB flush
CREATE TABLE IF NOT EXISTS counter_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(32) NOT NULL,
    metric VARCHAR(16) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_entity_metric (entity_type, entity_id, metric)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
