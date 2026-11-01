"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Plus, Pencil, Trash2, Briefcase } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { apiClient, errorMessage } from "@/lib/api";
import type { Employment, EmploymentInput } from "@/lib/types";
import { VISIBILITY_OPTIONS, VisibilityBadge } from "./visibility";

const EMPLOYMENT_TYPES = [
  { value: "FULL_TIME", label: "Full-time" },
  { value: "PART_TIME", label: "Part-time" },
  { value: "INTERNSHIP", label: "Internship" },
  { value: "CONTRACT", label: "Contract" },
  { value: "FREELANCE", label: "Freelance" },
  { value: "SELF_EMPLOYED", label: "Self-employed" },
  { value: "OTHER", label: "Other" },
];

const EMPTY: EmploymentInput = { visibility: "PRIVATE", current: false };

export function EmploymentEditor() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const [form, setForm] = useState<EmploymentInput>(EMPTY);

  const { data: entries = [], isLoading } = useQuery({
    queryKey: ["my-employment"],
    queryFn: apiClient.myEmployment,
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["my-employment"] });
  }

  const save = useMutation({
    mutationFn: (payload: { id?: string; body: EmploymentInput }) =>
      payload.id
        ? apiClient.updateEmployment(payload.id, payload.body)
        : apiClient.createEmployment(payload.body),
    onSuccess: () => {
      invalidate();
      toast.success("Employment saved");
      close();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.deleteEmployment(id),
    onSuccess: () => {
      invalidate();
      toast.success("Employment removed");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  function openNew() {
    setForm(EMPTY);
    setEditing("new");
  }

  function openEdit(entry: Employment) {
    setForm({ ...entry });
    setEditing(entry.id);
  }

  function close() {
    setEditing(null);
    setForm(EMPTY);
  }

  function set<K extends keyof EmploymentInput>(key: K, value: EmploymentInput[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function submit() {
    if (!form.designation?.trim()) {
      toast.error("Job title / designation is required");
      return;
    }
    save.mutate({ id: editing === "new" ? undefined : editing ?? undefined, body: form });
  }

  return (
    <section className="rounded-2xl border border-border bg-card p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Briefcase className="size-5 text-primary" /> Work
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Add current and past roles. Each entry has its own visibility.
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
                No work history added yet.
              </li>
            )}
            {entries.map((entry) => (
              <li
                key={entry.id}
                className="flex items-start justify-between gap-3 rounded-xl border border-border p-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    {entry.designation ?? "Role"}
                    {entry.companyName ? <span className="text-muted-foreground"> · {entry.companyName}</span> : null}
                  </p>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">
                    {[
                      EMPLOYMENT_TYPES.find((t) => t.value === entry.employmentType)?.label ?? entry.employmentType,
                      entry.industry,
                      [entry.workCity, entry.workState].filter(Boolean).join(", "),
                      entry.startDate || entry.endDate
                        ? `${entry.startDate ?? ""} – ${entry.current ? "present" : entry.endDate ?? ""}`
                        : null,
                    ]
                      .filter(Boolean)
                      .join(" • ")}
                  </p>
                  <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                    {entry.current && <Badge variant="secondary">Current</Badge>}
                    <VisibilityBadge value={entry.visibility} />
                  </div>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button type="button" size="icon" variant="ghost" aria-label="Edit employment" onClick={() => openEdit(entry)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="Delete employment"
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
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Job title / Designation" htmlFor="emp-designation" required>
              <Input id="emp-designation" className="h-11 rounded-xl" placeholder="Software Engineer"
                value={form.designation ?? ""} onChange={(e) => set("designation", e.target.value)} />
            </Field>
            <Field label="Company / Organization" htmlFor="emp-company">
              <Input id="emp-company" className="h-11 rounded-xl"
                value={form.companyName ?? ""} onChange={(e) => set("companyName", e.target.value)} />
            </Field>
            <Field label="Employment type" htmlFor="emp-type">
              <select
                id="emp-type"
                className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                value={form.employmentType ?? ""}
                onChange={(e) => set("employmentType", e.target.value || null)}
              >
                <option value="">Select</option>
                {EMPLOYMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </Field>
            <Field label="Industry" htmlFor="emp-industry">
              <Input id="emp-industry" className="h-11 rounded-xl"
                value={form.industry ?? ""} onChange={(e) => set("industry", e.target.value)} />
            </Field>
            <Field label="Work city" htmlFor="emp-city">
              <Input id="emp-city" className="h-11 rounded-xl"
                value={form.workCity ?? ""} onChange={(e) => set("workCity", e.target.value)} />
            </Field>
            <Field label="Work state" htmlFor="emp-state">
              <Input id="emp-state" className="h-11 rounded-xl"
                value={form.workState ?? ""} onChange={(e) => set("workState", e.target.value)} />
            </Field>
            <Field label="Start date" htmlFor="emp-start">
              <Input id="emp-start" type="date" className="h-11 rounded-xl"
                value={form.startDate ?? ""} onChange={(e) => set("startDate", e.target.value || null)} />
            </Field>
            <Field label="End date" htmlFor="emp-end">
              <Input id="emp-end" type="date" className="h-11 rounded-xl" disabled={form.current ?? false}
                value={form.endDate ?? ""} onChange={(e) => set("endDate", e.target.value || null)} />
            </Field>
          </div>

          <Field label="Skills used (optional)" htmlFor="emp-skills">
            <Input id="emp-skills" className="h-11 rounded-xl" placeholder="React, PostgreSQL, ..."
              value={form.skillsUsed ?? ""} onChange={(e) => set("skillsUsed", e.target.value)} />
          </Field>

          <Field label="Description (optional)" htmlFor="emp-desc">
            <Textarea id="emp-desc" rows={2}
              value={form.description ?? ""} onChange={(e) => set("description", e.target.value)} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Visibility" htmlFor="emp-visibility">
              <select
                id="emp-visibility"
                className="h-11 w-full rounded-xl border border-input bg-transparent px-3 text-sm"
                value={form.visibility ?? "PRIVATE"}
                onChange={(e) => set("visibility", e.target.value as EmploymentInput["visibility"])}
              >
                {VISIBILITY_OPTIONS.map((v) => <option key={v.value} value={v.value}>{v.label}</option>)}
              </select>
            </Field>
            <div className="flex items-end pb-1">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 rounded border-input"
                  checked={form.current ?? false} onChange={(e) => set("current", e.target.checked)} />
                I currently work here
              </label>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="ghost" className="rounded-xl" onClick={close}>Cancel</Button>
            <Button type="button" className="gap-2 rounded-xl" onClick={submit} disabled={save.isPending}>
              {save.isPending && <Loader2 className="size-4 animate-spin" />}
              {editing === "new" ? "Add work" : "Save changes"}
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
  required,
  children,
}: {
  label: string;
  htmlFor: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium" htmlFor={htmlFor}>
        {label}{required && <span className="text-destructive"> *</span>}
      </label>
      {children}
    </div>
  );
}
