/**
 * Programme guide.
 *
 * <p>The guide has no domain logic of its own in v1: XMLTV parsing lives in
 * {@code tv.lumo.api.ingest.xmltv}, storage and retention in
 * {@code tv.lumo.api.catalog}, and the single read endpoint
 * ({@code GET /channels/&#123;id&#125;/epg}) is served by {@code CatalogController}
 * because it is scoped to a channel.
 *
 * <p>The package is kept because docs/architecture.md §2 names it, and because a
 * richer guide — the v2 "what is on these 40 channels between 20:00 and 23:00"
 * query that motivated PostgreSQL in ADR 0002 — belongs here rather than in the
 * catalogue.
 */
package tv.lumo.api.epg;
