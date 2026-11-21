import { redirect } from "@/i18n/navigation";

/** The administration opens on the legal documents, as `/app/settings` opens on its first section. */
export default async function AdminPage({ params }: PageProps<"/[locale]/app/admin">) {
  const { locale } = await params;
  redirect({ href: "/app/admin/legal", locale });
}
