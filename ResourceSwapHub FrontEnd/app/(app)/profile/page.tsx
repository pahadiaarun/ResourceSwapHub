import { ProfileForm } from "@/components/profile-form";

export const metadata = {
  title: "Your profile",
};

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
          Your account
        </p>
        <h1 className="mt-1.5 font-display text-3xl font-semibold sm:text-4xl">
          Profile & pickup area
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
          This is what neighbours see when they find your books. Keep it short,
          friendly and accurate.
        </p>
      </div>
      <ProfileForm />
    </div>
  );
}
