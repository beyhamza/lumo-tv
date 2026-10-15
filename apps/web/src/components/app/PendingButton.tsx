"use client";

import { useFormStatus } from "react-dom";

/**
 * A submit button that says it is working and cannot be pressed twice while
 * its form is in flight (S11-03: "display an action in progress and avoid
 * double launches" for a removal from every group).
 *
 * Progressive enhancement only. Without JavaScript the form still posts and the
 * page still comes back re-read; what is lost is the in-flight state, and a
 * second press then re-sends the same favourite ids — which the server answers
 * `404 FAVORITE_NOT_FOUND` for, read as "already gone".
 *
 * Every string arrives translated from the server: this component needs no
 * intl provider of its own.
 */
export function PendingButton({
  label,
  pendingLabel,
  ariaLabel,
  className,
}: {
  label: string;
  pendingLabel: string;
  ariaLabel?: string;
  className?: string;
}) {
  const { pending } = useFormStatus();

  return (
    <button
      type="submit"
      disabled={pending}
      aria-disabled={pending}
      aria-label={pending ? pendingLabel : ariaLabel}
      className={className}
    >
      {pending ? pendingLabel : label}
    </button>
  );
}
