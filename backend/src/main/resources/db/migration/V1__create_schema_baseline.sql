CREATE TABLE schema_baseline (
    id BIGINT NOT NULL AUTO_INCREMENT,
    app_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_baseline_app_name (app_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_baseline (app_name) VALUES ('knowflow_ai');
