-- Fail migration on conflicting active links rather than silently choosing a product.
-- Resolve existing duplicate active device links before deploying this constraint.
ALTER TABLE product_devices
    ADD COLUMN active_device_id BIGINT AS (
        CASE WHEN detached_at IS NULL THEN device_id ELSE NULL END
    ) VIRTUAL,
    ADD CONSTRAINT uq_active_product_device UNIQUE (active_device_id);

CREATE INDEX idx_product_devices_device_interval
    ON product_devices (device_id, attached_at, detached_at);

-- A bounded, durable coalescing queue: at most one row per product and requested period.
CREATE TABLE care_diagnosis_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    analysis_period VARCHAR(3) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    requested_version BIGINT NOT NULL DEFAULT 1,
    claimed_version BIGINT NULL,
    requested_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until DATETIME(6) NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_diagnosis_jobs_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_diagnosis_jobs_product_period UNIQUE (product_id, analysis_period),
    CONSTRAINT ck_diagnosis_jobs_period CHECK (analysis_period IN ('7D', '30D', '1Y')),
    CONSTRAINT ck_diagnosis_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_diagnosis_jobs_pending ON care_diagnosis_jobs (status, available_at, id);
CREATE INDEX idx_diagnosis_jobs_lease ON care_diagnosis_jobs (status, lease_until, id);
