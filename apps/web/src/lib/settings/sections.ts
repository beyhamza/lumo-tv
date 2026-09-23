/**
 * The sections of Settings, and which URL opens which (US-025, S8-06).
 *
 * <h2>Why every section is a route</h2>
 *
 * Settings is a list on the left and one section on the right
 * (docs/design/0.2.0/settings.md). The selected section lives in the path —
 * `/app/settings/account` — rather than in component state: it works without
 * JavaScript, it survives a reload, a link to it can be sent, and the account
 * rail marks "Settings" as current on every one of them with no special case
 * (`currentEntryIndex` matches on the `/app/settings` prefix).
 *
 * <h2>What is a section and what is not</h2>
 *
 * "My sources" is in the list, but it is not a section: it opens `/app/sources`
 * (US-024), because a second screen managing the same sources would be a second
 * place for the same bugs. "Playback" is absent altogether: its settings do not
 * exist yet (sprint 13), and an empty section is a control that leads nowhere.
 *
 * <h2>An unknown section is a 404, not the first section</h2>
 *
 * `/app/settings/billing` names something this application does not have.
 * Answering with the account section would be a page that says "account" under
 * a URL that says "billing" — and a bookmark somebody keeps to a section that
 * was retired would keep opening, silently, on the wrong one.
 */
export const SETTINGS_SECTIONS = ["account", "application", "about"] as const;

export type SettingsSection = (typeof SETTINGS_SECTIONS)[number];

/** Where `/app/settings` itself lands. */
export const DEFAULT_SETTINGS_SECTION: SettingsSection = "account";

/**
 * Narrows a path segment to a section, or nothing.
 *
 * Exact match on the closed list: `Account`, `account/` and `accounts` are
 * all "nothing", so the page answers 404 instead of guessing.
 */
export function resolveSettingsSection(segment: unknown): SettingsSection | null {
  return typeof segment === "string" &&
    (SETTINGS_SECTIONS as readonly string[]).includes(segment)
    ? (segment as SettingsSection)
    : null;
}

/** The path of a section, without its locale prefix. */
export function settingsSectionPath(section: SettingsSection): string {
  return `/app/settings/${section}`;
}
