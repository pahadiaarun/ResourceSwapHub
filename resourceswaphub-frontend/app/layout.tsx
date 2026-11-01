import type { Metadata, Viewport } from "next";
import { Fraunces, Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const fraunces = Fraunces({
  variable: "--font-display",
  subsets: ["latin"],
  display: "swap",
});

/** Public origin for canonical/OpenGraph URLs. Unset in local development. */
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL;

/**
 * Favicons are cached aggressively by browsers; bump this to force a refresh when
 * the icon assets change.
 */
const ICON_VERSION = "v=2";

export const metadata: Metadata = {
  ...(siteUrl ? { metadataBase: new URL(siteUrl) } : {}),
  title: {
    default: "ResourceSwapHub — resources find their next student",
    template: "%s · ResourceSwapHub",
  },
  description:
    "A student-first resource sharing community. Give away, sell or find school and college resources in your city.",
  applicationName: "ResourceSwapHub",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "ResourceSwapHub",
  },
  icons: {
    icon: [
      { url: `/favicon.svg?${ICON_VERSION}`, type: "image/svg+xml", sizes: "any" },
      { url: `/favicon.ico?${ICON_VERSION}`, type: "image/x-icon" },
      { url: `/favicon-32x32.png?${ICON_VERSION}`, type: "image/png", sizes: "32x32" },
      { url: `/favicon-16x16.png?${ICON_VERSION}`, type: "image/png", sizes: "16x16" },
    ],
    apple: [{ url: `/icons/apple-touch-icon.png?${ICON_VERSION}`, type: "image/png", sizes: "180x180" }],
  },
  openGraph: {
    title: "ResourceSwapHub — resources find their next student",
    description:
      "Give away, sell or find school and college resources in your city.",
    ...(siteUrl ? { url: siteUrl, siteName: "ResourceSwapHub" } : {}),
    type: "website",
  },
  ...(siteUrl ? { alternates: { canonical: siteUrl } } : {}),
};

export const viewport: Viewport = {
  themeColor: "#2f6b58",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} ${fraunces.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
