"use client";

import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import { useTranslations } from "next-intl";
import { signIn, type AuthFormState } from "@/actions/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/**
 * The sign-in form.
 *
 * A client component, but a thin one: it owns the pending state and where the
 * error message goes, and nothing else. The credentials are posted to a Server
 * Action, so they never pass through a browser-side API call and the resulting
 * tokens never exist in JavaScript (docs/architecture.md §5).
 *
 * @param next where to return after signing in, when the visitor was sent here
 * from a protected page. Carried in a hidden field so it survives a submission
 * without JavaScript.
 */
export function SignInForm({ next }: { next?: string }) {
  const t = useTranslations("Auth");
  const [state, formAction] = useActionState<AuthFormState, FormData>(
    signIn,
    {},
  );

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {next ? <input type="hidden" name="next" value={next} /> : null}

      <div className="space-y-1.5">
        <label htmlFor="email" className="text-sm font-medium">
          {t("emailLabel")}
        </label>
        <Input
          id="email"
          name="email"
          type="email"
          inputMode="email"
          autoComplete="email"
          placeholder={t("emailPlaceholder")}
          required
          autoFocus
        />
      </div>

      <div className="space-y-1.5">
        <label htmlFor="password" className="text-sm font-medium">
          {t("passwordLabel")}
        </label>
        <Input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
      </div>

      {state?.error ? (
        // `role="alert"` so a screen reader announces the failure instead of
        // leaving the user waiting for a page that already answered.
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <SubmitButton label={t("submitLogin")} pendingLabel={t("submitting")} />
    </form>
  );
}

function SubmitButton({
  label,
  pendingLabel,
}: {
  label: string;
  pendingLabel: string;
}) {
  // useFormStatus only reports the status of the form it is rendered inside,
  // which is why this is a separate component rather than a prop.
  const { pending } = useFormStatus();

  return (
    <Button type="submit" className="w-full" disabled={pending}>
      {pending ? pendingLabel : label}
    </Button>
  );
}
