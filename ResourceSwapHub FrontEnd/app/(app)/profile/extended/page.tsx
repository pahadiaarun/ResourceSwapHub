import { ExtendedProfileForm } from "@/components/extended-profile-form";

export const metadata = {
  title: "Extended Profile",
  description: "Complete your educational and professional profile",
};

export default function ExtendedProfilePage() {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-3xl font-semibold">Extended Profile</h1>
        <p className="mt-2 text-muted-foreground">
          Add optional details about your education, profession, and interests.
        </p>
      </div>
      <ExtendedProfileForm />
    </div>
  );
}
