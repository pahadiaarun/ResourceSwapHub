import { LibraryWorkspace } from "@/components/library-workspace";

export const metadata = {
  title: "My library",
};

// Routes under (app) authenticate in the browser, so this page never renders during
// prerender and instant-navigation validation cannot run for this segment.
export const instant = false;

export default function LibraryPage() {
  return <LibraryWorkspace />;
}
