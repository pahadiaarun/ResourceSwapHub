import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "ResourceSwapHub — resources find their next student",
    short_name: "ResourceSwapHub",
    description:
      "Discover, share and exchange school and college resources in your city.",
    id: "/",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait-primary",
    background_color: "#faf7ef",
    theme_color: "#2f6b58",
    categories: ["education", "books", "shopping", "social"],
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
    shortcuts: [
      {
        name: "Discover resources",
        url: "/discover",
        description: "Search free and affordable resources near you",
      },
      {
        name: "List a resource",
        url: "/listings/new",
        description: "Give a resource its next reader",
      },
      {
        name: "Inbox",
        url: "/inbox",
        description: "Continue your conversations",
      },
    ],
  };
}
