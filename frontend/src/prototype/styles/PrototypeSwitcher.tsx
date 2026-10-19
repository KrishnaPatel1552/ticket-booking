import { useEffect } from "react";
import { STYLE_VARIANTS, type VariantKey } from "./model.ts";

type PrototypeSwitcherProps = {
  current: VariantKey;
  onChange: (next: VariantKey) => void;
};

function step(current: VariantKey, direction: -1 | 1): VariantKey {
  const index = STYLE_VARIANTS.findIndex((variant) => variant.key === current);
  const next = (index + direction + STYLE_VARIANTS.length) % STYLE_VARIANTS.length;
  return STYLE_VARIANTS[next].key;
}

export default function PrototypeSwitcher({ current, onChange }: PrototypeSwitcherProps) {
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.metaKey || event.altKey || event.ctrlKey) {
        return;
      }
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
        return;
      }
      const target = event.target;
      if (target instanceof HTMLElement) {
        const tag = target.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable) {
          return;
        }
      }
      event.preventDefault();
      onChange(step(current, event.key === "ArrowLeft" ? -1 : 1));
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [current, onChange]);

  if (import.meta.env.PROD) {
    return null;
  }

  const variant = STYLE_VARIANTS.find((item) => item.key === current) ?? STYLE_VARIANTS[0];

  return (
    <div className="proto-switch">
      <p className="proto-note">Sample seats</p>
      <div className="proto-bar" role="navigation" aria-label="Style variants">
        <button type="button" onClick={() => onChange(step(current, -1))} aria-label="Previous style">
          <Arrow direction="left" />
        </button>
        <p className="proto-label" aria-live="polite">
          {variant.key} · {variant.name}
        </p>
        <button type="button" onClick={() => onChange(step(current, 1))} aria-label="Next style">
          <Arrow direction="right" />
        </button>
      </div>
    </div>
  );
}

function Arrow({ direction }: { direction: "left" | "right" }) {
  const path = direction === "left" ? "M13 5 L7 12 L13 19" : "M11 5 L17 12 L11 19";
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.75" />
    </svg>
  );
}
