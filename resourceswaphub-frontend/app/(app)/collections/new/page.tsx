import { CollectionForm } from "@/components/collection-form";

export const metadata = {
  title: "New collection",
};

export const instant = false;

export default function NewCollectionPage() {
  return <CollectionForm />;
}
