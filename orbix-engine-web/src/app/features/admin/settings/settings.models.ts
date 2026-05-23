/** Mirrors the backend SettingDto / SettingType (see SettingsController). */

export type SettingType = 'PERCENT' | 'MONEY' | 'DECIMAL' | 'INTEGER' | 'DAYS' | 'BOOLEAN' | 'STRING';

export interface Setting {
  code: string;
  group: string;
  label: string;
  description: string;
  type: SettingType;
  value: string;
  defaultValue: string;
  overridden: boolean;
}

export interface UpdateSettingItem {
  code: string;
  /** null/blank resets the key to its compiled-in default. */
  value: string | null;
}
