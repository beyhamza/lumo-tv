# ADR 0015 — Administrator role, versioned legal documents, application settings (S16B-08)

- **Date** : 2026-11-01
- **Status** : Proposed (to be accepted by Hamza)
- **Deciders** : Tech Lead (proposes), Hamza (accepts)
- **Supersedes** : none

## Context

Until now every account was equal, and every legal text lived in the web application's translation
files: changing the privacy policy meant a build and a deployment, and nothing kept the previous
version. Hamza asked for administration screens, reserved to administrator accounts, to manage the
application's configuration and to create and update versions of the CGU, CGV and privacy policy,
along with what Google Play needs. This changes the authorisation model, hence this ADR.

## Decision

### 1. `user.is_admin`, read from the database on every request

A boolean column, not a JWT claim. A claim stays valid for the fifteen minutes of the access token;
a right that has been taken away has to stop working at the next call. `SecurityConfig` already
probes the user's row on every request (ADR 0014), so reading the flag in that same primary-key
lookup costs nothing more.

`/v1/admin/**` is closed in the security filter chain, ahead of any controller. Adding an endpoint
under this prefix cannot forget the check. `User.is_admin` in the contract is for display only.

A boolean rather than a roles table: there is one role today, and enumerations in this contract may
grow when a second one exists.

### 2. No endpoint grants the right

The first administrator is set by an `UPDATE` run on the server (`docs/ops/deploy-vps.md`). We
rejected a list of emails in an environment variable: registration does not require a verified
address, so whoever registered a listed email first would become administrator. An endpoint that
promotes accounts would be the most valuable target of the API, for a need that occurs once.

### 3. Legal documents are immutable once published

`legal_document_version` holds both languages in one row: `DRAFT`, then `PUBLISHED`, then `ARCHIVED`.
Only a draft can change or be deleted. A published text is a record of what people were told. The
version number is assigned on publication, so a discarded draft leaves no gap. Two partial unique
indexes hold "at most one draft" and "at most one version in force" per document in the database.

Publishing requires a title and a body in both languages (AGENTS.md §4). The API does not read the
text beyond that; the web refuses to publish a text that still holds a `[to complete]` placeholder.

### 4. A restricted Markdown, rendered without HTML

Bodies are Markdown limited to headings, lists, bold and links, rendered by a small parser into
React elements. HTML typed in a body is shown as text, and links accept `https:` and `mailto:` only.
A compromised administrator account therefore cannot place a script on a public page. Not worth a
dependency on a full Markdown stack for four constructs on pages that must stay static.

### 5. The public pages stay static

`/privacy`, `/terms` and `/terms-of-sale` read `GET /legal/{document_type}`, which is public, through
a cached fetch with no cookie and no header (`apps/web/AGENTS.md` §2). A publication revalidates them
at once from its Server Action. The build needs no API: without one, `/privacy` falls back on the
draft in the translation files, still labelled a draft, until a version is published.

### 6. Settings are typed columns, and quotas are not among them

`app_settings` is one row with one column per setting: a new setting is a contract change, like any
other field. `support_email` (public, shown as the contact) and `registration_open` to start with.
Plan quotas stay in `lumo.plans.*`: what a plan allows is access-rights logic (ADR 0003,
AGENTS.md §9), and it does not move to a screen without its own decision.

## Consequences

- **Web only.** The screens live at `/app/admin`. The Android clients only read `is_admin` as an unused
  field of `User`; closing registration reaches them as an unknown code, which degrades to their
  generic message until they learn it.
- **No record of who published what.** One administrator today, and storing authors would keep a
  personal reference on documents that outlive accounts. To revisit with a second administrator.
- **No record of who accepted which version.** Publishing new terms does not ask anyone to accept
  them. That is a separate piece of work touching registration on three surfaces.

## Revisit if

A second administrator or a second role appears; legal requires proof of acceptance per version.
