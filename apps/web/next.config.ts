import createNextIntlPlugin from "next-intl/plugin";
import type { NextConfig } from "next";

// Picks up src/i18n/request.ts by convention. It is what makes messages
// available to Server Components without a provider, which is what lets the
// marketing zone stay free of client components (docs/architecture.md §4).
const withNextIntl = createNextIntlPlugin();

const nextConfig: NextConfig = {
  // Fail the production build on a type error rather than shipping it. The
  // default already, written out because turning it off "temporarily" is how a
  // broken type reaches production.
  //
  // There is no `eslint` key beside it: Next.js 16 removed that option along
  // with `next lint`. Linting is now the ESLint CLI's job, run as its own step
  // (`pnpm lint`) — see the upgrade guide in
  // node_modules/next/dist/docs/01-app/02-guides/upgrading/version-16.md.
  typescript: { ignoreBuildErrors: false },

  // The marketing zone is meant to be cached at the edge for a long time and
  // revalidated on demand. `expireTime` is the stale-while-revalidate window a
  // CDN may serve while it refetches.
  expireTime: 60 * 60,

  poweredByHeader: false,
};

export default withNextIntl(nextConfig);
