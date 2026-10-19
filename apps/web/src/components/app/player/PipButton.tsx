"use client";

import { type RefObject, useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { pipAvailable, togglePip } from "@/lib/playback/pip";

/**
 * "Floating window" under a web player (US-026, S15-03).
 *
 * Shown only where the browser offers Picture-in-Picture for this element; no
 * button that cannot work. The window is the element itself, so progress,
 * watched time and the pause save carry on through the player's own events.
 * Closing the window pauses the video, which the player already saves.
 *
 * A refusal (the browser wants a gesture, or has no frame yet) is said once
 * under the button and cleared by the next attempt.
 */
export function PipButton({
  videoRef,
  className,
}: {
  videoRef: RefObject<HTMLVideoElement | null>;
  className: string;
}) {
  const t = useTranslations("Player");
  // Read after mount: the server has no `document`, and the element exists
  // only once the player rendered it.
  const [available, setAvailable] = useState(false);
  const [inWindow, setInWindow] = useState(false);
  const [refused, setRefused] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    setAvailable(pipAvailable(document, video));
    if (!video) return;
    setInWindow(document.pictureInPictureElement === video);
    const enter = () => setInWindow(true);
    const leave = () => setInWindow(false);
    video.addEventListener("enterpictureinpicture", enter);
    video.addEventListener("leavepictureinpicture", leave);
    return () => {
      video.removeEventListener("enterpictureinpicture", enter);
      video.removeEventListener("leavepictureinpicture", leave);
    };
  }, [videoRef]);

  if (!available) return null;

  return (
    <>
      <button
        type="button"
        aria-pressed={inWindow}
        onClick={async () => {
          const video = videoRef.current;
          if (!video) return;
          setRefused(!(await togglePip(document, video)));
        }}
        className={className}
      >
        {inWindow ? t("pipExit") : t("pipEnter")}
      </button>
      {refused ? (
        <p role="status" className="text-muted-foreground basis-full text-sm">
          {t("pipRefused")}
        </p>
      ) : null}
    </>
  );
}
