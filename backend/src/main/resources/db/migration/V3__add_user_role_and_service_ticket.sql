-- Incremental SmartServe ticket schema. Applies only to the current JDBC database (knowflow_ai).
-- Existing users receive CUSTOMER via column default. Do not modify V1/V2.

ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER' AFTER status;

CREATE TABLE service_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_no VARCHAR(64) NOT NULL,
    customer_user_id BIGINT NOT NULL,
    assigned_agent_id BIGINT NULL,
    title VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_ticket_customer (customer_user_id),
    KEY idx_ticket_agent (assigned_agent_id),
    KEY idx_ticket_status (status),
    CONSTRAINT fk_ticket_customer
        FOREIGN KEY (customer_user_id) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ticket_agent
        FOREIGN KEY (assigned_agent_id) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    remark VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_op_ticket (ticket_id),
    CONSTRAINT fk_ticket_op_ticket
        FOREIGN KEY (ticket_id) REFERENCES service_ticket (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ticket_op_user
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
