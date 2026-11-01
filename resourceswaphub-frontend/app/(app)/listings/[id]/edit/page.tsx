import { EditListingWorkspace } from "@/components/edit-listing-workspace";

export const metadata = {
  title: "Edit listing",
};

// Routes under (app) authenticate in the browser, so this page never renders during
// prerender and instant-navigation validation cannot run for this segment.
export const instant = false;

export default function EditListingPage() {
  return <EditListingWorkspace />;
}
