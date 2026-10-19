import { describe, expect, it, vi } from "vitest";
import { pipAvailable, togglePip } from "./pip";

const supported = () => ({
  pictureInPictureEnabled: true,
  pictureInPictureElement: null as Element | null,
  exitPictureInPicture: vi.fn(async () => {}),
});
const video = (extra: object = {}) =>
  ({ requestPictureInPicture: vi.fn(async () => ({})), ...extra }) as unknown as HTMLVideoElement;

describe("pipAvailable", () => {
  it("offers the button when the browser has the API and allows it", () => {
    expect(pipAvailable(supported(), video())).toBe(true);
  });

  it("hides it without the API", () => {
    expect(pipAvailable({}, video())).toBe(false);
    expect(pipAvailable(supported(), {} as HTMLVideoElement)).toBe(false);
  });

  it("hides it when the user or a policy turned it off", () => {
    expect(pipAvailable({ ...supported(), pictureInPictureEnabled: false }, video())).toBe(false);
    expect(pipAvailable(supported(), video({ disablePictureInPicture: true }))).toBe(false);
  });

  it("hides it before the element exists", () => {
    expect(pipAvailable(supported(), null)).toBe(false);
  });
});

describe("togglePip", () => {
  it("enters the window for an element that is not in it", async () => {
    const doc = supported();
    const v = video();
    expect(await togglePip(doc, v)).toBe(true);
    expect(v.requestPictureInPicture).toHaveBeenCalledOnce();
    expect(doc.exitPictureInPicture).not.toHaveBeenCalled();
  });

  it("leaves it when the element is the one in the window", async () => {
    const v = video();
    const doc = { ...supported(), pictureInPictureElement: v };
    expect(await togglePip(doc, v)).toBe(true);
    expect(doc.exitPictureInPicture).toHaveBeenCalledOnce();
  });

  it("reports a refusal instead of throwing", async () => {
    const v = video({ requestPictureInPicture: vi.fn(async () => Promise.reject(new Error("NotAllowed"))) });
    expect(await togglePip(supported(), v)).toBe(false);
  });
});
