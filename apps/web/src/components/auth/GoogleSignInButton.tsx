"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { GOOGLE_LOGIN_PATH, googleClientId } from "@/lib/auth/google";

/**
 * "Sign in with Google", on the sign-in and registration pages alike (US-03).
 *
 * <h2>Google draws its own button</h2>
 *
 * `renderButton` puts Google's official control in this element — their logo,
 * their wording, in the visitor's language. That is not only convenient: their
 * branding rules require their asset, and no third-party logo enters this
 * repository (AGENTS.md §1). Drawing it ourselves would mean shipping one.
 *
 * <h2>The credential never passes through this component</h2>
 *
 * `ux_mode: "redirect"` makes Google's own script post the ID token straight to
 * `/api/auth/google`, where the exchange and the session cookie happen on the
 * server. The alternative — a JavaScript callback holding the credential — would
 * put it in this bundle's memory for no gain, on a site whose sign-in form goes
 * through a Server Action precisely to avoid that (docs/architecture.md §5).
 *
 * <h2>Nothing is drawn without a client ID</h2>
 *
 * Not a disabled button, not a message: nothing, and no script request either.
 * That is the state of a fresh checkout, since no client ID may be committed,
 * and a button certain to fail teaches a visitor that the site is broken. The
 * email form beside it is complete on its own.
 *
 * <h2>No JavaScript, no button — and that is the honest outcome</h2>
 *
 * The rest of this zone works with JavaScript disabled, because Next posts the
 * forms to Server Actions. This cannot: the whole flow is Google's script. A
 * `<noscript>` button would be a button that does nothing.
 */
export function GoogleSignInButton() {
  const clientId = googleClientId();
  const locale = useLocale();
  const t = useTranslations("Auth");
  const container = useRef<HTMLDivElement>(null);
  const [scriptReady, setScriptReady] = useState(false);

  useEffect(() => {
    const target = container.current;
    if (!scriptReady || !clientId || !target) return;

    const google = window.google;
    if (!google) return;

    google.accounts.id.initialize({
      client_id: clientId,
      // Absolute, because that is what Google matches against the authorised
      // redirect URI registered in the console.
      login_uri: new URL(GOOGLE_LOGIN_PATH, window.location.origin).toString(),
      ux_mode: "redirect",
    });

    google.accounts.id.renderButton(target, {
      type: "standard",
      theme: "outline",
      size: "large",
      text: "continue_with",
      shape: "rectangular",
      logo_alignment: "center",
      locale,
    });

    // Redrawn on a locale switch, so the button is not the one thing on the page
    // still speaking the previous language.
    return () => {
      target.replaceChildren();
    };
  }, [scriptReady, clientId, locale]);

  if (!clientId) return null;

  return (
    <div className="mt-6">
      <Script
        src="https://accounts.google.com/gsi/client"
        strategy="afterInteractive"
        // Fires on load and also when the script is already there, which is what
        // makes this work after a client-side navigation between the two pages.
        onReady={() => setScriptReady(true)}
      />

      <div className="flex items-center gap-3">
        <span className="bg-border h-px flex-1" />
        <span className="text-muted-foreground text-xs uppercase">{t("orSeparator")}</span>
        <span className="bg-border h-px flex-1" />
      </div>

      <div ref={container} className="mt-4 flex justify-center" />
    </div>
  );
}

/**
 * The slice of Google Identity Services this file uses.
 *
 * Declared here rather than pulled in as a `@types/google.accounts` dependency:
 * two calls and six option fields do not justify a package, and a hand-written
 * shape that is wrong fails at the first click rather than compiling into
 * something plausible.
 */
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: {
            client_id: string;
            login_uri: string;
            ux_mode: "popup" | "redirect";
          }): void;
          renderButton(
            parent: HTMLElement,
            options: {
              type?: "standard" | "icon";
              theme?: "outline" | "filled_blue" | "filled_black";
              size?: "small" | "medium" | "large";
              text?: "signin_with" | "signup_with" | "continue_with" | "signin";
              shape?: "rectangular" | "pill" | "circle" | "square";
              logo_alignment?: "left" | "center";
              locale?: string;
            },
          ): void;
        };
      };
    };
  }
}
