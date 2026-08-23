import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/env.public";

/**
 * What crawlers may read.
 *
 * The account and activation zones are disallowed rather than merely
 * unlinked. A crawler that follows a stray link into `/app` gets a redirect to
 * sign-in, which at best wastes crawl budget and at worst gets the sign-in page
 * indexed under a dozen URLs. `/activate?code=…` is worse still: the code is
 * short-lived and single-use, and it has no business appearing in an index.
 *
 * The per-page `robots` metadata (see `pageMetadata`) is the second half of
 * this: robots.txt keeps crawlers away, `noindex` keeps a page that was reached
 * anyway out of the results.
 */
export default function robots(): MetadataRoute.Robots {
  const base = siteUrl().replace(/\/$/, "");

  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/*/app", "/*/app/", "/*/activate"],
      },
    ],
    sitemap: `${base}/sitemap.xml`,
    host: base,
  };
}
