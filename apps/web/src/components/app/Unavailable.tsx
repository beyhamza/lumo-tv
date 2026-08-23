import { getTranslations } from "next-intl/server";

/**
 * What an account page shows when the API does not answer.
 *
 * Every page in this zone reads from lumo-api, and lumo-api can be down,
 * restarting or unreachable from this server. The rule this component exists to
 * enforce: a page in that state renders a calm, translated message — never a
 * stack trace, never an empty screen that looks like "you have no sources", and
 * never a crash that takes the whole route down.
 *
 * Telling "no data" apart from "no answer" matters: one invites the user to add
 * a source, the other asks them to come back in a minute.
 */
export async function Unavailable() {
  const t = await getTranslations("App");

  return (
    <div
      role="status"
      className="border-border rounded-xl border border-dashed px-5 py-6"
    >
      <p className="font-medium">{t("unavailableTitle")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("unavailableBody")}</p>
    </div>
  );
}

/** Empty state: the request worked and there is genuinely nothing yet. */
export function EmptyState({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{title}</p>
      <p className="text-muted-foreground mt-1 text-sm">{hint}</p>
    </div>
  );
}
