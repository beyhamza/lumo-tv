"use client";

import { useActionState } from "react";
import { useTranslations } from "next-intl";
import { correctSource, type SourceFormState } from "@/actions/sources";
import { Field, SubmitButton } from "@/components/app/AddSourceForm";
import type { SourceKind } from "@/lib/api/types";

/**
 * The connection of an existing source, corrected in place (R-424,
 * BUG-R020-03-01).
 *
 * The add form's fields, for the source's own kind only: an M3U cannot turn
 * into a panel here, and the name has its own form. Pre-filled with what the
 * source holds — except the password, which the API never returns; leaving it
 * empty keeps the stored one.
 */
export function CorrectSourceForm({
  id,
  kind,
  m3uUrl,
  host,
  username,
  epgUrl,
}: {
  id: string;
  kind: SourceKind;
  m3uUrl: string;
  host: string;
  username: string;
  epgUrl: string;
}) {
  const t = useTranslations("App");
  const [state, formAction] = useActionState<SourceFormState, FormData>(correctSource, {});
  const value = (name: string, held: string) => state.values?.[name] ?? held;

  return (
    <form action={formAction} className="space-y-4" noValidate>
      <input type="hidden" name="id" value={id} />
      <input type="hidden" name="kind" value={kind} />

      {kind === "XTREAM" ? (
        <>
          <Field
            name="host"
            label={t("sourceHostLabel")}
            hint={t("sourceHostHint")}
            defaultValue={value("host", host)}
            error={state.fieldErrors?.host}
            autoFocus
          />
          <Field
            name="username"
            label={t("sourceUsernameLabel")}
            defaultValue={value("username", username)}
            error={state.fieldErrors?.username}
            autoComplete="off"
          />
          <Field
            name="password"
            type="password"
            label={t("sourcePasswordLabel")}
            hint={t("sourceCorrectPasswordHint")}
            error={state.fieldErrors?.password}
            autoComplete="off"
          />
        </>
      ) : (
        <Field
          name="m3u_url"
          type="url"
          label={t("sourceM3uUrlLabel")}
          hint={t("sourceM3uUrlHint")}
          defaultValue={value("m3u_url", m3uUrl)}
          error={state.fieldErrors?.m3u_url}
          autoFocus
        />
      )}

      <Field
        name="epg_url"
        type="url"
        label={t("sourceEpgUrlLabel")}
        hint={t("sourceEpgUrlHint")}
        defaultValue={value("epg_url", epgUrl)}
        error={state.fieldErrors?.epg_url}
      />

      {state.error ? (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <SubmitButton label={t("sourceCorrectSubmit")} pendingLabel={t("sourceSubmitting")} />
    </form>
  );
}
