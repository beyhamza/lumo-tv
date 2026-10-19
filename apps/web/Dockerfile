# ==============================================================================
# lumo-web — production image (S16B-01, docs/ops/deploy-vps.md).
#
# Built from the repository root, like apps/api/Dockerfile:
#   docker build -f apps/web/Dockerfile \
#     --build-arg NEXT_PUBLIC_API_BASE_URL=https://api.example.test/v1 \
#     --build-arg NEXT_PUBLIC_SITE_URL=https://example.test .
#
# The two NEXT_PUBLIC_ values are written into the browser bundle at build time,
# so they are build arguments, not runtime variables. Secrets are runtime only
# (SESSION_SECRET) and never reach an image layer.
#
# The contract's generated TypeScript is copied because the build type-checks
# against it (`@lumo/contracts`). Next.js's standalone output carries only the
# files the server actually uses, so the runtime stage is node and that.
# ==============================================================================

FROM node:22-alpine AS build
WORKDIR /repo/apps/web
RUN corepack enable

COPY packages/contracts/generated /repo/packages/contracts/generated
COPY apps/web/package.json apps/web/pnpm-lock.yaml apps/web/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY apps/web ./

ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_APP_VERSION=""
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL} \
    NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL} \
    NEXT_PUBLIC_APP_VERSION=${NEXT_PUBLIC_APP_VERSION} \
    NEXT_OUTPUT=standalone \
    NEXT_TELEMETRY_DISABLED=1
RUN pnpm build

FROM node:22-alpine AS runtime
RUN addgroup -S -g 10001 lumo && adduser -S -u 10001 -G lumo lumo
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
COPY --from=build --chown=lumo:lumo /repo/apps/web/.next/standalone ./
COPY --from=build --chown=lumo:lumo /repo/apps/web/.next/static ./.next/static
COPY --from=build --chown=lumo:lumo /repo/apps/web/public ./public
USER lumo
EXPOSE 3000
CMD ["node", "server.js"]
