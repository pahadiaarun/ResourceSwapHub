import { ListingForm } from "@/components/listing-form";

export const metadata = {
  title: "List a book",
};

export const instant = false;

export default function NewListingPage() {
  return <ListingForm />;
}
