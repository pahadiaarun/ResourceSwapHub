import { EditCollectionWorkspace } from "@/components/edit-collection-workspace";

export const metadata = {
  title: "Edit collection",
};

// Routes under (app) authenticate in the browser, so this page never renders during
// prerender and instant-navigation validation cannot run for this segment.
export const instant = false;

export default function EditCollectionPage() {
  return <EditCollectionWorkspace />;
}
