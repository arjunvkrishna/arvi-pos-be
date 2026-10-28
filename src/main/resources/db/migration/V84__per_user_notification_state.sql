-- ============================================================
-- NERAM POS - Notifications are personal (V84)
-- Reading or clearing a notification now only affects the manager who did it. The notification itself stays
-- for everyone else until they read or clear it too. Notifications the system itself has resolved
-- (system_notifications.status = 'READ': a handled leave request, a printer that came back) stay read for everybody.
-- ============================================================

CREATE TABLE IF NOT EXISTS notification_user_state (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES system_notifications(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_user UNIQUE (notification_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_notification_state_user ON notification_user_state(user_id);
