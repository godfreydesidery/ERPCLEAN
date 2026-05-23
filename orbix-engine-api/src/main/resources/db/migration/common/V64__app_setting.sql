-- Global, UI-editable configuration overrides. Each row overrides the
-- compiled-in default (the orbix.* value in application.yml) for the whole
-- deployment; absence of a row means "use the default". See SettingsService /
-- SettingKey. Values are stored as text and parsed per the key's declared type.
CREATE TABLE app_setting (
    setting_key  VARCHAR(120) NOT NULL PRIMARY KEY,
    value        TEXT         NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    updated_by   BIGINT       NOT NULL
);

-- Permission gating the settings screen. permission_seq was bumped past 100
-- (V4_1), so hard-coded id 68 cannot collide with JPA-allocated ids.
INSERT INTO permission (id, code, description, module) VALUES
    (68, 'ADMIN.MANAGE_SETTINGS', 'View and change global configuration defaults', 'admin');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 68;
