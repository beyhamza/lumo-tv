import { describe, expect, it } from "vitest";
import { fetched, fetchedResource } from "./fetched";

/**
 * BUG-R020-03-03: the page of a source deleted elsewhere said the service was
 * down. A resource page tells "gone" apart from "broken" and from "not built".
 */
function answer(status: number, body?: unknown, data?: unknown) {
  return async () => ({
    data,
    error: body,
    response: new Response(null, { status }),
  });
}

const problem = (code: string) => ({ type: "about:blank", title: code, status: 404, code });

describe("fetchedResource", () => {
  it("is gone on a 404 carrying the resource's own code", async () => {
    expect(await fetchedResource(answer(404, problem("SOURCE_NOT_FOUND")), "SOURCE_NOT_FOUND"))
      .toEqual({ state: "gone" });
  });

  it("is still not-implemented on the generic NOT_FOUND", async () => {
    expect(await fetchedResource(answer(404, problem("NOT_FOUND")), "SOURCE_NOT_FOUND"))
      .toEqual({ state: "not-implemented" });
  });

  it("is unavailable on another failure, or with no answer at all", async () => {
    expect(await fetchedResource(answer(503, problem("INTERNAL_ERROR")), "SOURCE_NOT_FOUND"))
      .toEqual({ state: "unavailable" });
    expect(await fetchedResource(async () => { throw new TypeError("fetch failed"); }, "SOURCE_NOT_FOUND"))
      .toEqual({ state: "unavailable" });
  });

  it("returns the data when there is some", async () => {
    expect(await fetchedResource(answer(200, undefined, { id: "s1" }), "SOURCE_NOT_FOUND"))
      .toEqual({ state: "ok", data: { id: "s1" } });
  });
});

describe("fetched", () => {
  it("keeps a resource 404 as unavailable: lists and account pages have no gone state", async () => {
    expect(await fetched(answer(404, problem("SOURCE_NOT_FOUND")))).toEqual({ state: "unavailable" });
  });
});
