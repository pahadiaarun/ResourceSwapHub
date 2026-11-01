import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { ExtendedProfileForm } from "@/components/extended-profile-form";
import { EducationEditor } from "@/components/education-editor";
import { EmploymentEditor } from "@/components/employment-editor";
import { TopicSelector } from "@/components/topic-selector";
import { PrivacyControls } from "@/components/privacy-controls";

export const metadata = {
  title: "Extended Profile",
  description: "Complete your educational and professional profile",
};

export const instant = false;

export default function ExtendedProfilePage() {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <Link
          href="/profile"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground transition hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          Back to profile
        </Link>
        <h1 className="mt-3 font-display text-3xl font-semibold">Extended Profile</h1>
        <p className="mt-2 text-muted-foreground">
          Add optional details about your education, work, skills and interests. Everything here is
          optional and private by default — nothing blocks you from using the platform.
        </p>
      </div>
      <ExtendedProfileForm />
      <EducationEditor />
      <EmploymentEditor />
      <TopicSelector />
      <PrivacyControls />
    </div>
  );
}
