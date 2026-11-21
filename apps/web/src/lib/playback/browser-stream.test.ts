import { describe, expect, it } from "vitest";
import { browserStream } from "./browser-stream";

const plain = "http://panel.example:8080/live/u/p/1.m3u8";
const secure = "https://panel.example:8443/live/u/p/1.m3u8";

describe("browserStream", () => {
  it("takes the announced https address on an https page", () => {
    expect(browserStream({ url: plain, secureUrl: secure }, "https:")).toEqual({
      url: secure,
      upgraded: true,
    });
  });

  it("gives up on an https page when nothing is announced", () => {
    expect(browserStream({ url: plain, secureUrl: null }, "https:")).toBeNull();
    expect(browserStream({ url: plain }, "https:")).toBeNull();
  });

  it("keeps the plain address on an http page, where it is known to work", () => {
    expect(browserStream({ url: plain, secureUrl: secure }, "http:")).toEqual({
      url: plain,
      upgraded: false,
    });
  });

  it("leaves a stream that is already https alone", () => {
    expect(browserStream({ url: secure, secureUrl: null }, "https:")).toEqual({
      url: secure,
      upgraded: false,
    });
  });
});
