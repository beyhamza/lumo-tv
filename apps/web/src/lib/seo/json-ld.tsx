import { siteUrl } from "@/lib/env.public";
import type { Locale } from "@/i18n/routing";

/**
 * Structured data, as a Server Component.
 *
 * It renders a `<script type="application/ld+json">`, which is inert markup —
 * no client component, no hydration, nothing shipped to the browser beyond the
 * text itself. That matters: the marketing zone is prerendered and must stay
 * free of client JavaScript (docs/architecture.md §4).
 *
 * `JSON.stringify` output is injected with `dangerouslySetInnerHTML` because
 * React would otherwise escape the quotes and the parser would reject the block.
 * The input is our own translated copy, never user input; the `<` replacement
 * below closes the one injection route that would still exist if that ever
 * changed.
 */
export function JsonLd({ data }: { data: Record<string, unknown> }) {
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{
        __html: JSON.stringify(data).replace(/</g, "\\u003c"),
      }}
    />
  );
}

export function organizationJsonLd(name: string, tagline: string) {
  const base = siteUrl().replace(/\/$/, "");
  return {
    "@context": "https://schema.org",
    "@type": "Organization",
    name,
    description: tagline,
    url: base,
    logo: `${base}/icon.svg`,
  };
}

export function softwareApplicationJsonLd(options: {
  name: string;
  description: string;
  locale: Locale;
}) {
  const base = siteUrl().replace(/\/$/, "");
  return {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: options.name,
    description: options.description,
    applicationCategory: "MultimediaApplication",
    operatingSystem: "Android, Android TV, Web",
    inLanguage: options.locale,
    url: `${base}/${options.locale}`,
  };
}

export function articleJsonLd(options: {
  title: string;
  description: string;
  path: string;
  locale: Locale;
  updatedIso: string;
}) {
  const base = siteUrl().replace(/\/$/, "");
  return {
    "@context": "https://schema.org",
    "@type": "TechArticle",
    headline: options.title,
    description: options.description,
    inLanguage: options.locale,
    dateModified: options.updatedIso,
    mainEntityOfPage: `${base}${options.path}`,
    author: { "@type": "Organization", name: "Lumo TV" },
  };
}

export function breadcrumbJsonLd(
  items: Array<{ name: string; path: string }>,
) {
  const base = siteUrl().replace(/\/$/, "");
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: `${base}${item.path}`,
    })),
  };
}
