import { getTranslations } from "next-intl/server";
import { adminNotice, isAdminFailure } from "@/lib/admin/notices";
import { cn } from "@/lib/utils";

/** The result of an action, from the redirect's `notice=`. Failures are an alert. */
export async function AdminNoticeBanner({ value }: { value: unknown }) {
  const notice = adminNotice(value);
  if (!notice) return null;

  const t = await getTranslations("Admin.notices");
  const failure = isAdminFailure(notice);
  return (
    <p
      role={failure ? "alert" : "status"}
      className={cn(
        "rounded-xl border px-4 py-3 text-sm",
        failure ? "border-destructive/40 text-destructive" : "border-border text-foreground",
      )}
    >
      {t(notice)}
    </p>
  );
}
