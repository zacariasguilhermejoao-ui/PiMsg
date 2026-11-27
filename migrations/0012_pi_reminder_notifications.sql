-- Store whether a reminder has already triggered its push notification.
ALTER TABLE bot_reminders ADD COLUMN notified_at INTEGER;
