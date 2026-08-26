"use client";

import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import { useTranslations } from "next-intl";
import { createSource, type SourceFormState } from "@/actions/sources";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { SourceKind } from "@/lib/api/types";

/**
 * The form that registers a source (US-06, US-07).
 *
 * <h2>The two halves are switched by CSS, not by React</h2>
 *
 * A source is either a playlist URL or a set of Xtream credentials, and only one
 * block is useful at a time. Hiding the other from React state would break the
 * form for a visitor with JavaScript disabled: the radio would change and the
 * fields it reveals would stay hidden for ever.
 *
 * So the disclosure is `:has()` on the form — the radio's own `:checked` state
 * drives it, which the browser maintains without a script. Nothing about which
 * half is visible depends on hydration, and the component keeps no state of its
 * own for it.
 *
 * The rest of the form is a client component only because it renders what the
 * Server Action returned. It posts and re-renders perfectly well without
 * JavaScript.
 *
 * <h2>What this component does not do</h2>
 *
 * It does not decide which fields are required for which kind. The API does, per
 * field, and {@link createSource} renders what comes back. A second copy of that
 * rule here would be the copy that is wrong the day the contract gains a kind.
 */
export function AddSourceForm() {
  const t = useTranslations("App");
  const [state, formAction] = useActionState<SourceFormState, FormData>(
    createSource,
    {},
  );

  const chosen = (state.values?.kind as SourceKind) ?? "M3U_URL";

  return (
    <form action={formAction} className="group space-y-6" noValidate>
      <fieldset className="space-y-3">
        <legend className="text-sm font-medium">{t("sourceKindLegend")}</legend>

        <KindChoice
          value="M3U_URL"
          marker="kind-m3u"
          defaultChecked={chosen === "M3U_URL"}
          title={t("sourceKindM3u")}
          hint={t("sourceKindM3uHint")}
        />
        <KindChoice
          value="XTREAM"
          marker="kind-xtream"
          defaultChecked={chosen === "XTREAM"}
          title={t("sourceKindXtream")}
          hint={t("sourceKindXtreamHint")}
        />
      </fieldset>

      <Field
        name="label"
        label={t("sourceLabelLabel")}
        hint={t("sourceLabelHint")}
        defaultValue={state.values?.label}
        error={state.fieldErrors?.label}
        autoFocus
        required
      />

      <fieldset className="hidden space-y-4 group-has-[.kind-m3u:checked]:block">
        <legend className="sr-only">{t("sourceKindM3u")}</legend>
        <Field
          name="m3u_url"
          type="url"
          label={t("sourceM3uUrlLabel")}
          hint={t("sourceM3uUrlHint")}
          defaultValue={state.values?.m3u_url}
          error={state.fieldErrors?.m3u_url}
        />
      </fieldset>

      <fieldset className="hidden space-y-4 group-has-[.kind-xtream:checked]:block">
        <legend className="sr-only">{t("sourceKindXtream")}</legend>
        <Field
          name="host"
          label={t("sourceHostLabel")}
          // The server takes a host with or without scheme, port or trailing
          // slash and normalises it (US-06). Saying so here is what stops
          // someone retyping an address that was already fine.
          hint={t("sourceHostHint")}
          defaultValue={state.values?.host}
          error={state.fieldErrors?.host}
        />
        <Field
          name="username"
          label={t("sourceUsernameLabel")}
          defaultValue={state.values?.username}
          error={state.fieldErrors?.username}
          autoComplete="off"
        />
        <Field
          name="password"
          type="password"
          label={t("sourcePasswordLabel")}
          hint={t("sourcePasswordHint")}
          // Never echoed back: a rejected form must not carry an IPTV
          // credential in its HTML.
          error={state.fieldErrors?.password}
          autoComplete="off"
        />
      </fieldset>

      <Field
        name="epg_url"
        type="url"
        label={t("sourceEpgUrlLabel")}
        hint={t("sourceEpgUrlHint")}
        defaultValue={state.values?.epg_url}
        error={state.fieldErrors?.epg_url}
      />

      {state.error ? (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <SubmitButton label={t("sourceSubmit")} pendingLabel={t("sourceSubmitting")} />
    </form>
  );
}

/**
 * One choice of source kind.
 *
 * `marker` is a class carried by the radio and matched by the `:has()` selectors
 * above. A class rather than an id, because two of these forms on one page would
 * make ids collide and the selector silently pick the first.
 */
function KindChoice({
  value,
  marker,
  defaultChecked,
  title,
  hint,
}: {
  value: SourceKind;
  marker: string;
  defaultChecked: boolean;
  title: string;
  hint: string;
}) {
  return (
    <label className="border-border has-[:checked]:border-primary has-[:checked]:bg-primary/5 flex cursor-pointer gap-3 rounded-xl border px-4 py-3">
      <input
        type="radio"
        name="kind"
        value={value}
        defaultChecked={defaultChecked}
        className={`${marker} mt-1`}
      />
      <span>
        <span className="block font-medium">{title}</span>
        <span className="text-muted-foreground block text-sm">{hint}</span>
      </span>
    </label>
  );
}

function Field({
  name,
  label,
  hint,
  error,
  type = "text",
  ...rest
}: {
  name: string;
  label: string;
  hint?: string;
  error?: string;
  type?: string;
} & React.ComponentProps<typeof Input>) {
  const hintId = hint ? `${name}-hint` : undefined;
  const errorId = error ? `${name}-error` : undefined;

  return (
    <div className="space-y-1.5">
      <label htmlFor={name} className="text-sm font-medium">
        {label}
      </label>
      <Input
        id={name}
        name={name}
        type={type}
        aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
        aria-invalid={error ? true : undefined}
        {...rest}
      />
      {hint ? (
        <p id={hintId} className="text-muted-foreground text-sm">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-destructive text-sm">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function SubmitButton({
  label,
  pendingLabel,
}: {
  label: string;
  pendingLabel: string;
}) {
  const { pending } = useFormStatus();

  return (
    <Button type="submit" disabled={pending}>
      {pending ? pendingLabel : label}
    </Button>
  );
}
