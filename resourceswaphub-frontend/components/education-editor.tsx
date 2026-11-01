"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Plus, Pencil, Trash2, GraduationCap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { apiClient, errorMessage } from "@/lib/api";
import type { Education, EducationInput } from "@/lib/types";
import { InstitutionSearch } from "./institution-search";
import { VISIBILITY_OPTIONS, VisibilityBadge } from "./visibility";

const LEVELS = [
  { value: "SCHOOL", label: "School" },
  { value: "HIGHER_SECONDARY", label: "Higher Secondary (11-12)" },
  { value: "DIPLOMA", label: "Diploma" },
  { value: "UNDERGRADUATE", label: "Undergraduate" },
  { value: "POSTGRADUATE", label: "Postgraduate" },
  { value: "DOCTORATE", label: "Doctorate" },
  { value: "OTHER", label: "Other" },
];

const STATUSES = [
  { value: "CURRENT", label: "Currently studying" },
  { value: "COMPLETED", label: "Completed" },
  { value: "PAUSED", label: "Paused" },
  { value: "DISCONTINUED", label: "Discontinued" },
];

type FormState = EducationInput;

const EMPTY: FormState = { visibility: "PRIVATE", educationStatus: "CURRENT", current: false };

export function EducationEditor() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [institutionLabel, setInstitutionLabel] = useState<string | undefined>();

  const { data: entries = [], isLoading } = useQuery({
    queryKey: ["my-education"],
    queryFn: apiClient.myEducation,
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["my-education"] });
  }

  const save = useMutation({
    mutationFn: (payload: { id?: string; body: EducationInput }) =>
      payload.id
        ? apiClient.updateEducation(payload.id, payload.body)
        : apiClient.createEducation(payload.body),
    onSuccess: () => {
      invalidate();
      toast.success("Education saved");
      close();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.deleteEducation(id),
    onSuccess: () => {
      invalidate();
      toast.success("Education removed");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  function openNew() {
    setForm(EMPTY);
    setInstitutionLabel(undefined);
    setEditing("new");
  }

  function openEdit(entry: Education) {
    setForm({ ...entry });
    setInstitutionLabel(entry.institutionName ?? entry.institutionNameManual ?? undefined);
    setEditing(entry.id);
  }

  function close() {
    setEditing(null);
    setForm(EMPTY);
    setInstitutionLabel(undefined);
  }

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function submit() {
    save.mutate({ id: editing === "new" ? undefined : editing ?? undefined, body: form });
  }

  const institutionName = (e: Education) => e.institutionName ?? e.institutionNameManual ?? "Institution";

  return (
    <section className="rounded-2xl border border-border bg-card p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <GraduationCap className="size-5 text-primary" /> Education
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Add schools, colleges and degrees. Each entry has its own visibility.
          </p>
        </div>
        {editing === null && (
          <Button type="button" size="sm" variant="outline" className="gap-1.5 rounded-xl" onClick={openNew}>
            <Plus className="size-4" /> Add
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="flex justify-center p-6"><Loader2 className="size-5 animate-spin" /></div>
      ) : (
        editing === null && (
          <ul className="space-y-2">
            {entries.length === 0 && (
              <li className="rounded-xl border border-dashed border-border p-4 text-sm text-muted-foreground">
                No education added yet.
              </li>
            )}
            {entries.map((entry) => (
              <li
                key={entry.id}
                className="flex items-start justify-between gap-3 rounded-xl border border-border p-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{institutionName(entry)}</p>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">
                    {[
                      entry.degree ?? entry.course,
                      entry.branchSpecialization,
                      entry.startYear && entry.endYear ? `${entry.startYear}–${entry.endYear}` : entry.startYear,
                    ]
                      .filter(Boolean)
                      .join(" • ")}
                  </p>
                  <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                    {entry.current && <Badge variant="secondary">Current</Badge>}
                    {entry.primary && <Badge variant="secondary">Primary</Badge>}
                    {entry.educationStatus && (
                      <Badge variant="outline">{STATUSES.find((s) => s.value === entry.educationStatus)?.label ?? entry.educationStatus}</Badge>
                    )}
                    <VisibilityBadge value={entry.visibility} />
                  </div>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button type="button" size="icon" variant="ghost" aria-label="Edit education" onClick={() => openEdit(entry)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="Delete education"
                    onClick={() => remove.mutate(entry.id)}
                    disabled={remove.isPending}
                  >
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )
      )}

      {editing !== null && (
        <div className="space-y-4 rounded-xl border border-border bg-muted/30 p-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium" htmlFor="edu-institution">Institution</label>
            <InstitutionSearch
              id="edu-institution"
              value={institutionLabel}
              onSelect={(inst) => {
                set("institutionId", inst.id || null);
                set("institutionNameManual", inst.id ? null : inst.name);
                setInstitutionLabel(inst.name);
              }}
              onUseManual={(name) => set("institutionNameManual", name)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Level" htmlFor="edu-level">
              <select
                id="edu-level"
                className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                value={form.educationLevel ?? ""}
                onChange={(e) => set("educationLevel", e.target.value || null)}
              >
                <option value="">Select</option>
                {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
              </select>
            </Field>
            <Field label="Degree / Course" htmlFor="edu-degree">
              <Input id="edu-degree" className="h-11 rounded-xl" placeholder="B.Tech, B.Sc, Class 12"
                value={form.degree ?? ""} onChange={(e) => set("degree", e.target.value)} />
            </Field>
            <Field label="Branch / Specialization" htmlFor="edu-branch">
              <Input id="edu-branch" className="h-11 rounded-xl" placeholder="Computer Science"
                value={form.branchSpecialization ?? ""} onChange={(e) => set("branchSpecialization", e.target.value)} />
            </Field>
            <Field label="Status" htmlFor="edu-status">
              <select
                id="edu-status"
                className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                value={form.educationStatus ?? ""}
                onChange={(e) => set("educationStatus", e.target.value || null)}
              >
                {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
            </Field>
            <Field label="Start year" htmlFor="edu-start">
              <Input id="edu-start" type="number" className="h-11 rounded-xl" inputMode="numeric"
                value={form.startYear ?? ""} onChange={(e) => set("startYear", e.target.value ? Number(e.target.value) : null)} />
            </Field>
            <Field label="End / expected year" htmlFor="edu-end">
              <Input id="edu-end" type="number" className="h-11 rounded-xl" inputMode="numeric"
                value={form.endYear ?? ""} onChange={(e) => set("endYear", e.target.value ? Number(e.target.value) : null)} />
            </Field>
          </div>

          <Field label="Description (optional)" htmlFor="edu-desc">
            <Textarea id="edu-desc" rows={2} placeholder="Thesis, activities, notes..."
              value={form.description ?? ""} onChange={(e) => set("description", e.target.value)} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Visibility" htmlFor="edu-visibility">
              <select
                id="edu-visibility"
                className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                value={form.visibility ?? "PRIVATE"}
                onChange={(e) => set("visibility", e.target.value as EducationInput["visibility"])}
              >
                {VISIBILITY_OPTIONS.map((v) => <option key={v.value} value={v.value}>{v.label}</option>)}
              </select>
            </Field>
            <div className="flex flex-col justify-end gap-2 pb-1">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 rounded border-input"
                  checked={form.current ?? false} onChange={(e) => set("current", e.target.checked)} />
                Currently studying here
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 rounded border-input"
                  checked={form.primary ?? false} onChange={(e) => set("primary", e.target.checked)} />
                Mark as primary education
              </label>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="ghost" className="rounded-xl" onClick={close}>Cancel</Button>
            <Button type="button" className="gap-2 rounded-xl" onClick={submit} disabled={save.isPending}>
              {save.isPending && <Loader2 className="size-4 animate-spin" />}
              {editing === "new" ? "Add education" : "Save changes"}
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium" htmlFor={htmlFor}>{label}</label>
      {children}
    </div>
  );
}
