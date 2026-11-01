import { describe, expect, it } from "vitest";
import { adminFailure, adminNotice, isAdminFailure } from "./notices";

describe("adminNotice", () => {
  it("believes only the listed values", () => {
    expect(adminNotice("published")).toBe("published");
    expect(adminNotice("failed")).toBe("failed");
    expect(adminNotice("<b>hello</b>")).toBeNull();
    expect(adminNotice(["published"])).toBeNull();
    expect(adminNotice(undefined)).toBeNull();
  });

  it("tells a failure from a success", () => {
    expect(isAdminFailure("placeholders")).toBe(true);
    expect(isAdminFailure("draft-saved")).toBe(false);
  });
});

describe("adminFailure", () => {
  it("maps the codes of the contract", () => {
    expect(adminFailure("LEGAL_DRAFT_ALREADY_EXISTS")).toBe("draft-exists");
    expect(adminFailure("LEGAL_VERSION_NOT_DRAFT")).toBe("not-draft");
    expect(adminFailure("LEGAL_VERSION_INCOMPLETE")).toBe("incomplete");
    expect(adminFailure("VALIDATION_FAILED")).toBe("invalid");
  });

  it("degrades an unknown code or no answer", () => {
    expect(adminFailure("SOMETHING_NEW")).toBe("failed");
    expect(adminFailure(undefined)).toBe("failed");
  });
});
