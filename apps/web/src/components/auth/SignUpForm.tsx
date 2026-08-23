"use client";

import { useActionState, useState } from "react";
import { useFormStatus } from "react-dom";
import { useTranslations } from "next-intl";
import { signUp, type AuthFormState } from "@/actions/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** The minimum the contract accepts, and what US-01 asks to be shown up front. */
const MIN_PASSWORD_LENGTH = 10;

/**
 * The registration form.
 *
 * US-01 is specific about the weak-password case: the submit button stays
 * disabled and the unmet rule is shown **before** submission, not after. So the
 * length rule is stated next to the field from the start and the button follows
 * the input.
 *
 * The same rule is enforced again in the Server Action. A disabled button is a
 * courtesy to the user, never a validation — anyone can post the form directly.
 */
export function SignUpForm() {
  const t = useTranslations("Auth");
  const [state, formAction] = useActionState<AuthFormState, FormData>(
    signUp,
    {},
  );
  const [password, setPassword] = useState("");

  const passwordTooShort =
    password.length > 0 && password.length < MIN_PASSWORD_LENGTH;

  return (
    <form action={formAction} className="space-y-4" noValidate>
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
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          aria-describedby="password-hint"
          aria-invalid={passwordTooShort}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
        <p
          id="password-hint"
          className={
            passwordTooShort
              ? "text-destructive text-sm"
              : "text-muted-foreground text-sm"
          }
        >
          {t("passwordHint")}
        </p>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="display_name" className="text-sm font-medium">
          {t("displayNameLabel")}
        </label>
        <Input
          id="display_name"
          name="display_name"
          type="text"
          autoComplete="name"
          aria-describedby="display-name-hint"
        />
        <p id="display-name-hint" className="text-muted-foreground text-sm">
          {t("displayNameHint")}
        </p>
      </div>

      {state?.error ? (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <SubmitButton
        label={t("submitRegister")}
        pendingLabel={t("submitting")}
        disabled={password.length < MIN_PASSWORD_LENGTH}
      />
    </form>
  );
}

function SubmitButton({
  label,
  pendingLabel,
  disabled,
}: {
  label: string;
  pendingLabel: string;
  disabled: boolean;
}) {
  const { pending } = useFormStatus();

  return (
    <Button type="submit" className="w-full" disabled={disabled || pending}>
      {pending ? pendingLabel : label}
    </Button>
  );
}
