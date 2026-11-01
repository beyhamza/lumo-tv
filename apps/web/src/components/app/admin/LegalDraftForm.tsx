"use client";

import { useActionState } from "react";
import { saveLegalDraft, type LegalDraftFormState } from "@/actions/admin";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type Labels = {
  titleFr: string;
  titleEn: string;
  bodyFr: string;
  bodyEn: string;
  bodyHint: string;
  changeSummary: string;
  save: string;
  review: string;
};

/**
 * Editing a draft. A client component for one reason: `useActionState` gives back
 * what was typed when a save is refused (see `saveLegalDraft`). It still posts
 * without JavaScript. Every string arrives translated from the server.
 */
export function LegalDraftForm({
  id,
  initial,
  labels,
  failureMessages,
}: {
  id: string;
  initial: NonNullable<LegalDraftFormState["values"]>;
  labels: Labels;
  failureMessages: Record<NonNullable<LegalDraftFormState["failure"]>, string>;
}) {
  const [state, action, pending] = useActionState<LegalDraftFormState, FormData>(saveLegalDraft, {});
  const values = state.values ?? initial;
  const textarea =
    "border-input focus-visible:border-ring focus-visible:ring-ring/50 dark:bg-input/30 min-h-72 w-full rounded-lg border bg-transparent px-2.5 py-2 font-mono text-[13px] leading-relaxed outline-none focus-visible:ring-3";

  return (
    <form action={action} className="flex flex-col gap-5">
      <input type="hidden" name="id" value={id} />

      {state.failure ? (
        <p role="alert" className="border-destructive/40 text-destructive rounded-xl border px-4 py-3 text-sm">
          {failureMessages[state.failure]}
        </p>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-2">
        <div className="flex min-w-0 flex-col gap-2">
          <Label htmlFor="title_fr">{labels.titleFr}</Label>
          <Input id="title_fr" name="title_fr" lang="fr" defaultValue={values.title_fr} maxLength={200} />
          <Label htmlFor="body_fr" className="mt-3">
            {labels.bodyFr}
          </Label>
          <textarea id="body_fr" name="body_fr" lang="fr" defaultValue={values.body_fr} maxLength={200000} className={textarea} />
        </div>
        <div className="flex min-w-0 flex-col gap-2">
          <Label htmlFor="title_en">{labels.titleEn}</Label>
          <Input id="title_en" name="title_en" lang="en" defaultValue={values.title_en} maxLength={200} />
          <Label htmlFor="body_en" className="mt-3">
            {labels.bodyEn}
          </Label>
          <textarea id="body_en" name="body_en" lang="en" defaultValue={values.body_en} maxLength={200000} className={textarea} />
        </div>
      </div>
      <p className="text-muted-foreground -mt-2 text-[13px]">{labels.bodyHint}</p>

      <div className="flex max-w-xl flex-col gap-2">
        <Label htmlFor="change_summary">{labels.changeSummary}</Label>
        <Input id="change_summary" name="change_summary" defaultValue={values.change_summary} maxLength={500} />
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="submit" name="intent" value="save" variant="outline" disabled={pending}>
          {labels.save}
        </Button>
        <Button type="submit" name="intent" value="review" disabled={pending}>
          {labels.review}
        </Button>
      </div>
    </form>
  );
}
