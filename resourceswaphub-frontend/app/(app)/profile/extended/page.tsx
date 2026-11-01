import { ExtendedProfileForm } from "@/components/extended-profile-form";
import { EducationEditor } from "@/components/education-editor";
import { EmploymentEditor } from "@/components/employment-editor";
import { TopicSelector } from "@/components/topic-selector";
import { PrivacyControls } from "@/components/privacy-controls";

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
