"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { api } from "@/lib/api/client";
import { homeWithNotice, performHide, readHide } from "@/lib/home/hide-continue";
import { requireSession } from "@/lib/session/session";

/**
 * Removes a card from "Continue watching" on every device, keeping its position
 * (S12-04, CW-10). See `lib/home/hide-continue.ts` for the form, the outcome
 * and why it travels in the redirect.
 */
export async function hideFromContinue(formData: FormData): Promise<void> {
  const session = await requireSession();
  const outcome = await performHide(readHide(formData), async (request) => {
    const { response } = await api(session.accessToken).POST("/me/progress/hide", {
      body: {
        source_id: request.sourceId,
        item_type: request.itemType,
        item_ref: request.itemRef,
      },
    });
    return response.status;
  });

  const locale = await getLocale();
  // The rail is read again whatever happened: after a success the card is gone,
  // after a failure it is still there — the server's word either way (CW-26).
  revalidatePath(`/${locale}/app`);
  redirect({ href: homeWithNotice(outcome), locale });
}
