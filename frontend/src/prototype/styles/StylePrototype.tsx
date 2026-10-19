import { useEffect, useState } from "react";
import PrototypeSwitcher from "./PrototypeSwitcher.tsx";
import VariantCounter from "./VariantCounter.tsx";
import VariantPlan from "./VariantPlan.tsx";
import VariantProgram from "./VariantProgram.tsx";
import { usePreview, type VariantKey } from "./model.ts";
import "./styles.css";

const FONT_HREF =
  "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:ital,wght@0,400;0,500;0,600;1,400&family=Newsreader:opsz,wght@6..72,500;6..72,600&family=Outfit:wght@400;500;600&display=swap";

export function readStyleVariant(): VariantKey | null {
  if (import.meta.env.PROD) {
    return null;
  }
  const value = new URLSearchParams(window.location.search).get("variant");
  if (value === "A" || value === "B" || value === "C") {
    return value;
  }
  return null;
}

function writeVariant(next: VariantKey) {
  const url = new URL(window.location.href);
  url.searchParams.set("variant", next);
  window.history.replaceState(null, "", url);
}

type StylePrototypeProps = {
  initialVariant: VariantKey;
};

export default function StylePrototype({ initialVariant }: StylePrototypeProps) {
  const [variant, setVariant] = useState(initialVariant);
  const preview = usePreview();

  useEffect(() => {
    const id = "style-proto-fonts";
    if (document.getElementById(id) != null) {
      return;
    }
    const link = document.createElement("link");
    link.id = id;
    link.rel = "stylesheet";
    link.href = FONT_HREF;
    document.head.appendChild(link);
  }, []);

  function onChange(next: VariantKey) {
    writeVariant(next);
    setVariant(next);
  }

  return (
    <div className={`style-root style-${variant}`}>
      <a className="skip" href="#seats">
        Skip to seats
      </a>
      {renderVariant(variant, preview)}
      <PrototypeSwitcher current={variant} onChange={onChange} />
    </div>
  );
}

function renderVariant(variant: VariantKey, preview: ReturnType<typeof usePreview>) {
  switch (variant) {
    case "A":
      return <VariantProgram preview={preview} />;
    case "B":
      return <VariantCounter preview={preview} />;
    case "C":
      return <VariantPlan preview={preview} />;
    default: {
      const unexpected: never = variant;
      return unexpected;
    }
  }
}
