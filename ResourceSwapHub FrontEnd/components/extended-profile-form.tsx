"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import { InstitutionSearch } from "./institution-search";

const USER_TYPES = [
  { value: "SCHOOL_STUDENT", label: "School Student" },
  { value: "COLLEGE_STUDENT", label: "College / University Student" },
  { value: "WORKING_PROFESSIONAL", label: "Working Professional" },
  { value: "TEACHER_EDUCATOR", label: "Teacher / Educator" },
  { value: "OTHER", label: "Other" },
];

const BOARDS = ["CBSE", "ICSE", "STATE_BOARD", "IB", "CAMBRIDGE", "OTHER"];
const CLASSES = ["6", "7", "8", "9", "10", "11", "12"];

export function ExtendedProfileForm() {
  const queryClient = useQueryClient();
  const [formData, setFormData] = useState<Record<string, any>>({});

  const { data: profile, isLoading } = useQuery({
    queryKey: ["extended-profile"],
    queryFn: apiClient.getExtendedProfile,
  });

  const updateProfile = useMutation({
    mutationFn: (data: Record<string, unknown>) => apiClient.updateExtendedProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["extended-profile"] });
      toast.success("Profile updated successfully");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  function handleChange(key: string, value: any) {
    setFormData((prev) => ({ ...prev, [key]: value }));
  }

  function handleSubmit() {
    updateProfile.mutate(formData);
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="size-6 animate-spin" />
      </div>
    );
  }

  const userType = formData.userType || profile?.userType;

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-border bg-card p-6">
        <h2 className="mb-4 text-lg font-semibold">About You (Optional)</h2>
        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium">Bio</label>
            <textarea
              className="w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm"
              rows={3}
              placeholder="Tell us a bit about yourself..."
              defaultValue={profile?.bio || ""}
              onChange={(e) => handleChange("bio", e.target.value)}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-medium">State</label>
              <Input
                defaultValue={profile?.state || ""}
                onChange={(e) => handleChange("state", e.target.value)}
                className="h-11 rounded-xl"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Country</label>
              <Input
                defaultValue={profile?.country || "India"}
                onChange={(e) => handleChange("country", e.target.value)}
                className="h-11 rounded-xl"
              />
            </div>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-border bg-card p-6">
        <h2 className="mb-4 text-lg font-semibold">What best describes you currently?</h2>
        <p className="mb-4 text-sm text-muted-foreground">
          This helps us show relevant fields. You can skip and update later.
        </p>
        <div className="grid gap-2 sm:grid-cols-2">
          {USER_TYPES.map((type) => (
            <button
              key={type.value}
              type="button"
              onClick={() => handleChange("userType", type.value)}
              className={`rounded-xl border p-3 text-left text-sm transition ${
                userType === type.value
                  ? "border-primary bg-primary/5"
                  : "border-border hover:border-primary/50"
              }`}
            >
              {type.label}
            </button>
          ))}
        </div>
      </div>

      {userType === "SCHOOL_STUDENT" && (
        <div className="rounded-2xl border border-border bg-card p-6">
          <h2 className="mb-4 text-lg font-semibold">School Information</h2>
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">School Name</label>
              <InstitutionSearch
                placeholder="Search for your school..."
                onSelect={(inst) => handleChange("schoolName", inst.name)}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Class / Grade</label>
                <select
                  className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                  defaultValue={profile?.schoolClass || ""}
                  onChange={(e) => handleChange("schoolClass", e.target.value)}
                >
                  <option value="">Select</option>
                  {CLASSES.map((c) => (
                    <option key={c} value={c}>
                      Class {c}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Board</label>
                <select
                  className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                  defaultValue={profile?.schoolBoard || ""}
                  onChange={(e) => handleChange("schoolBoard", e.target.value)}
                >
                  <option value="">Select</option>
                  {BOARDS.map((b) => (
                    <option key={b} value={b}>
                      {b}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">School City</label>
                <Input
                  defaultValue={profile?.schoolLocationCity || ""}
                  onChange={(e) => handleChange("schoolLocationCity", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Expected Graduation Year</label>
                <Input
                  type="number"
                  defaultValue={profile?.schoolExpectedGraduationYear || ""}
                  onChange={(e) => handleChange("schoolExpectedGraduationYear", parseInt(e.target.value) || null)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {userType === "COLLEGE_STUDENT" && (
        <div className="rounded-2xl border border-border bg-card p-6">
          <h2 className="mb-4 text-lg font-semibold">College Information</h2>
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">College / University</label>
              <InstitutionSearch
                placeholder="Search for your college..."
                onSelect={(inst) => handleChange("collegeName", inst.name)}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Course / Degree</label>
                <Input
                  defaultValue={profile?.collegeCourse || ""}
                  onChange={(e) => handleChange("collegeCourse", e.target.value)}
                  placeholder="B.Tech, B.Sc, etc."
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Branch / Specialization</label>
                <Input
                  defaultValue={profile?.collegeBranch || ""}
                  onChange={(e) => handleChange("collegeBranch", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Current Year / Semester</label>
                <Input
                  defaultValue={profile?.collegeCurrentYearSemester || ""}
                  onChange={(e) => handleChange("collegeCurrentYearSemester", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Expected Graduation Year</label>
                <Input
                  type="number"
                  defaultValue={profile?.collegeExpectedGraduationYear || ""}
                  onChange={(e) => handleChange("collegeExpectedGraduationYear", parseInt(e.target.value) || null)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {userType === "WORKING_PROFESSIONAL" && (
        <div className="rounded-2xl border border-border bg-card p-6">
          <h2 className="mb-4 text-lg font-semibold">Professional Information</h2>
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Job Title / Designation</label>
                <Input
                  defaultValue={profile?.professionalDesignation || ""}
                  onChange={(e) => handleChange("professionalDesignation", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Company / Organization</label>
                <Input
                  defaultValue={profile?.professionalCompany || ""}
                  onChange={(e) => handleChange("professionalCompany", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Industry</label>
                <Input
                  defaultValue={profile?.professionalIndustry || ""}
                  onChange={(e) => handleChange("professionalIndustry", e.target.value)}
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Years of Experience</label>
                <Input
                  type="number"
                  defaultValue={profile?.professionalYearsExperience || ""}
                  onChange={(e) => handleChange("professionalYearsExperience", parseInt(e.target.value) || null)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {userType === "TEACHER_EDUCATOR" && (
        <div className="rounded-2xl border border-border bg-card p-6">
          <h2 className="mb-4 text-lg font-semibold">Teaching Information</h2>
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Institution Name</label>
              <Input
                defaultValue={profile?.teacherInstitutionName || ""}
                onChange={(e) => handleChange("teacherInstitutionName", e.target.value)}
                className="h-11 rounded-xl"
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-sm font-medium">Subjects Taught</label>
                <Input
                  defaultValue={profile?.teacherSubjects || ""}
                  onChange={(e) => handleChange("teacherSubjects", e.target.value)}
                  placeholder="Mathematics, Physics..."
                  className="h-11 rounded-xl"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium">Years of Experience</label>
                <Input
                  type="number"
                  defaultValue={profile?.teacherYearsExperience || ""}
                  onChange={(e) => handleChange("teacherYearsExperience", parseInt(e.target.value) || null)}
                  className="h-11 rounded-xl"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {userType === "OTHER" && (
        <div className="rounded-2xl border border-border bg-card p-6">
          <h2 className="mb-4 text-lg font-semibold">Additional Information</h2>
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium">Current Occupation / Role</label>
              <Input
                defaultValue={profile?.otherOccupation || ""}
                onChange={(e) => handleChange("otherOccupation", e.target.value)}
                className="h-11 rounded-xl"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium">Learning Goals</label>
              <textarea
                className="w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm"
                rows={2}
                defaultValue={profile?.otherLearningGoals || ""}
                onChange={(e) => handleChange("otherLearningGoals", e.target.value)}
              />
            </div>
          </div>
        </div>
      )}

      <div className="flex justify-end">
        <Button
          onClick={handleSubmit}
          disabled={updateProfile.isPending}
          className="gap-2 rounded-xl"
        >
          {updateProfile.isPending && <Loader2 className="size-4 animate-spin" />}
          Save Extended Profile
        </Button>
      </div>

      <p className="text-xs text-muted-foreground">
        All additional profile information is optional. You can skip any section and update it later.
      </p>
    </div>
  );
}
