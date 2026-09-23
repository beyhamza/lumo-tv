import type {
  EpgChannelProgrammes,
  EpgGrid,
  EpgImportStatus,
  EpgMappingStatus,
  EpgProgramme,
} from "@/lib/api/types";

/**
 * The bounded split of a grouped guide read (C1, D2 — S9-03).
 *
 * <h2>What the server promises, and what that leaves to the client</h2>
 *
 * `GET /sources/{id}/epg` answers **whole or not at all**: past 5 000 programme
 * occurrences or 4 MiB of JSON it is `422 EPG_WINDOW_TOO_LARGE` with no
 * programme in it, never a list trimmed to fit under a `200`. So a client that
 * asked for too much has to ask again for less, and *how* it asks again is the
 * part the contract deliberately leaves to it. This module is that rule, once,
 * for every screen of the web.
 *
 * <h2>The rule, as D2 fixes it</h2>
 *
 * - **Channels first, then the window.** A batch that is too large is cut in
 *   two halves of channels; a single channel that is still too large has its
 *   window halved. Halving the window first would double the number of
 *   requests for every channel of the batch, when the usual cause is simply a
 *   batch of a hundred channels over four days.
 * - **At most two requests in flight**, whatever the depth. A grid is one
 *   screen of one person; sixteen parallel requests because the first one was
 *   refused is how a client turns one refusal into a load.
 * - **At most three levels of splitting.** A hundred channels become fifty,
 *   twenty-five, thirteen — and a thirteen-channel batch that is still over
 *   the ceiling is a guide nobody can display in one screen anyway. Past that
 *   the answer is the refusal, said as such: no recursion until something
 *   fits, and no silent success made of the parts that did.
 * - **Merged per channel by programme id.** A programme that overlaps the cut
 *   between two half-windows comes back in both halves, and must be one row
 *   entry. The merge is *within* a channel only: the contract is explicit that
 *   two channels sharing a `tvg_id` each get the same programme, and a global
 *   de-duplication would empty one of the two rows.
 *
 * <h2>Pure, and fed a fetcher</h2>
 *
 * Nothing here knows about tokens or `openapi-fetch`; the fetcher is injected,
 * which is what makes the splitting testable without a server — the same
 * reason `resolveChannels` (S6-09) is shaped this way. The only thing under
 * test is the rule above, and it is the only thing that can be wrong in a way
 * nobody would see: a split that sends four requests at once, or a merge that
 * loses the programme spanning the cut, both render a grid that looks fine.
 *
 * <h2>One failure is the whole failure</h2>
 *
 * A sub-request that fails for any other reason — the API restarting, a
 * `CHANNEL_NOT_FOUND` because a channel was dropped between two reads — fails
 * the whole read. Rendering the rows that did answer would be exactly the
 * partial grid the contract refuses to produce server-side, reproduced
 * client-side.
 */

/** The ceiling of `channelIds` per request, by contract. */
export const CHANNELS_PER_REQUEST = 100;

/** Requests in flight at once, whatever the depth of splitting (D2). */
export const MAX_CONCURRENT = 2;

/** How many times a request may be split before the refusal is final (D2). */
export const MAX_SPLIT_DEPTH = 3;

/**
 * A window this narrow is not halved any further: a single channel over one
 * minute that still exceeds the ceiling is a data problem, not a window size.
 */
const MIN_WINDOW_MS = 60_000;

export type BatchRequest = {
  channelIds: readonly string[];
  /** Inclusive. */
  from: Date;
  /** Exclusive. */
  to: Date;
};

/** What one request came back as. The fetcher classifies; this module decides. */
export type BatchAnswer =
  | { kind: "ok"; grid: EpgGrid }
  | { kind: "too-large" }
  | { kind: "failed"; code?: string };

export type SplitResult =
  | {
      kind: "ok";
      /** One entry per distinct requested identifier, in the order requested. */
      channels: EpgChannelProgrammes[];
      epg: EpgImportStatus;
      generatedAt: string;
    }
  /** Still over the ceiling after every split allowed. */
  | { kind: "too-large" }
  | { kind: "failed"; code?: string };

type Work = {
  channelIds: readonly string[];
  from: Date;
  to: Date;
  /** How many splits produced this piece. The original chunks are at 0. */
  depth: number;
};

/**
 * Reads one window for a list of channels, in as many requests as the
 * ceilings require and no more than the rule allows.
 *
 * Above {@link CHANNELS_PER_REQUEST} identifiers the list is chunked before
 * anything is sent: that is the contract's hard cap and not a ceiling to
 * discover. Duplicate identifiers are dropped — the server refuses a batch
 * with a duplicate — and the answer has one entry per distinct identifier.
 *
 * @param fetch one request. Must not throw; a throw is read as `failed`.
 * @returns never throws. An empty list of identifiers is `failed`: there is
 *   nothing to read, and no `epg` metadata to hand back without a request.
 */
export function fetchBounded(
  request: BatchRequest,
  fetch: (batch: BatchRequest) => Promise<BatchAnswer>,
): Promise<SplitResult> {
  const ids = [...new Set(request.channelIds)];
  const queue: Work[] = [];
  for (let index = 0; index < ids.length; index += CHANNELS_PER_REQUEST) {
    queue.push({
      channelIds: ids.slice(index, index + CHANNELS_PER_REQUEST),
      from: request.from,
      to: request.to,
      depth: 0,
    });
  }

  const programmes = new Map<string, EpgProgramme[]>();
  const mapping = new Map<string, EpgMappingStatus>();
  // The metadata of the first answer to arrive. Every sub-request of one read
  // is seconds apart, and the server reads metadata and programmes in one
  // snapshot per request; picking one is picking a clock, not a truth.
  let meta: { epg: EpgImportStatus; generatedAt: string } | undefined;

  return new Promise((resolve) => {
    let inFlight = 0;
    let settled = false;

    const finish = (result: SplitResult) => {
      if (settled) return;
      settled = true;
      resolve(result);
    };

    const collect = (): SplitResult => {
      if (meta === undefined) return { kind: "failed" };
      return {
        kind: "ok",
        channels: ids.flatMap((channelId) => {
          const rows = programmes.get(channelId);
          // Cannot happen under the contract — one entry per requested id —
          // and is a dropped row rather than an invented empty one if it does.
          if (rows === undefined) return [];
          return [
            {
              channel_id: channelId,
              mapping_status: mapping.get(channelId) ?? "MAPPED",
              programmes: rows,
            },
          ];
        }),
        epg: meta.epg,
        generatedAt: meta.generatedAt,
      };
    };

    const run = async (work: Work) => {
      let answer: BatchAnswer;
      try {
        answer = await fetch({ channelIds: work.channelIds, from: work.from, to: work.to });
      } catch {
        answer = { kind: "failed" };
      }
      // A read that already failed ignores what comes back afterwards (D4:
      // "annulent/ignorent les réponses" of a request that no longer matters).
      if (settled) return;

      switch (answer.kind) {
        case "ok":
          for (const row of answer.grid.channels) {
            const known = programmes.get(row.channel_id);
            programmes.set(
              row.channel_id,
              known ? mergeProgrammes(known, row.programmes) : [...row.programmes],
            );
            mapping.set(row.channel_id, row.mapping_status);
          }
          meta ??= { epg: answer.grid.epg, generatedAt: answer.grid.generated_at };
          return;
        case "too-large": {
          const halves = halve(work);
          if (halves === null) {
            finish({ kind: "too-large" });
            return;
          }
          queue.push(...halves);
          return;
        }
        case "failed":
          finish(answer);
          return;
      }
    };

    // A small scheduler rather than `Promise.all` over two workers: a worker
    // that finds the queue empty while its twin's request is about to split
    // into two would exit, and the read would carry on at half its allowance.
    const pump = () => {
      if (settled) return;
      while (inFlight < MAX_CONCURRENT && queue.length > 0) {
        const work = queue.shift() as Work;
        inFlight += 1;
        void run(work).then(() => {
          inFlight -= 1;
          pump();
        });
      }
      if (inFlight === 0 && queue.length === 0) finish(collect());
    };

    pump();
  });
}

/**
 * The two pieces a refused request becomes, or `null` when the rule says
 * stop: three splits already, or a single channel over a window too narrow
 * to halve.
 */
function halve(work: Work): Work[] | null {
  if (work.depth >= MAX_SPLIT_DEPTH) return null;
  const depth = work.depth + 1;

  if (work.channelIds.length > 1) {
    const middle = Math.ceil(work.channelIds.length / 2);
    return [
      { ...work, channelIds: work.channelIds.slice(0, middle), depth },
      { ...work, channelIds: work.channelIds.slice(middle), depth },
    ];
  }

  const span = work.to.getTime() - work.from.getTime();
  if (span < 2 * MIN_WINDOW_MS) return null;
  const cut = new Date(work.from.getTime() + Math.floor(span / 2));
  return [
    { ...work, to: cut, depth },
    { ...work, from: cut, depth },
  ];
}

/**
 * Two lists of one channel's programmes as one, each id once, in the
 * contract's order — `starts_at` then `id`, the total order it promises so
 * that a client can merge on it.
 */
function mergeProgrammes(
  known: readonly EpgProgramme[],
  more: readonly EpgProgramme[],
): EpgProgramme[] {
  const byId = new Map<string, EpgProgramme>();
  for (const programme of known) byId.set(programme.id, programme);
  for (const programme of more) byId.set(programme.id, programme);
  return [...byId.values()].sort(
    (a, b) => a.starts_at.localeCompare(b.starts_at) || a.id.localeCompare(b.id),
  );
}
