ALTER TABLE outbox_events
    ADD COLUMN claimed_by UUID,
    ADD COLUMN claim_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_outbox_claim_lease
        CHECK ((claimed_by IS NULL) = (claim_expires_at IS NULL));

CREATE INDEX idx_outbox_claim_lease
    ON outbox_events (claim_expires_at, created_at)
    WHERE published_at IS NULL;
