"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Search, Sparkles, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Proficiency, Topic, TopicRelationType, UserTopic, UserTopicInput } from "@/lib/types";
import { VISIBILITY_OPTIONS } from "./visibility";

const SECTIONS: {
  relation: TopicRelationType;
  label: string;
  hint: string;
  defaultType: string;
  showProficiency: boolean;
}[] = [
  { relation: "CAN_HELP_WITH", label: "I can help with", hint: "Things you can teach or lend a hand with.", defaultType: "SKILL", showProficiency: true },
  { relation: "EXPERIENCED_IN", label: "Experienced in", hint: "Areas where you have real depth.", defaultType: "EXPERTISE", showProficiency: true },
  { relation: "LEARNING", label: "Currently learning", hint: "What you're picking up right now.", defaultType: "LEARNING_GOAL", showProficiency: false },
  { relation: "INTERESTED_IN", label: "Interested in", hint: "Subjects and topics you follow.", defaultType: "INTEREST", showProficiency: false },
];

const PROFICIENCY_ORDER: Proficiency[] = ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"];

export function TopicSelector() {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);

  const { data: topics = [], isLoading } = useQuery({
    queryKey: ["my-topics"],
    queryFn: apiClient.myTopics,
  });

  const replace = useMutation({
    mutationFn: (items: UserTopicInput[]) => apiClient.replaceTopics(items),
    onMutate: () => setPending(true),
    onSettled: () => setPending(false),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["my-topics"] }),
    onError: (e) => toast.error(errorMessage(e)),
  });

  function toInput(list: UserTopic[]): UserTopicInput[] {
    return list.map((t) => ({
      topicId: t.topicId,
      topicType: t.topicType,
      relationType: t.relationType,
      proficiency: t.proficiency ?? null,
      visibility: t.visibility ?? "PRIVATE",
    }));
  }

  function addTopic(relation: TopicRelationType, defaultType: string, topic: { id?: string; name: string }, visibility: string) {
    const exists = topics.some(
      (t) => t.relationType === relation && (topic.id ? t.topicId === topic.id : t.canonicalName.toLowerCase() === topic.name.toLowerCase())
    );
    if (exists) return;
    const item: UserTopicInput = {
      topicId: topic.id ?? null,
      topicName: topic.id ? null : topic.name,
      topicType: defaultType,
      relationType: relation,
      visibility: visibility as UserTopicInput["visibility"],
    };
    replace.mutate([...toInput(topics), item]);
  }

  function removeTopic(entry: UserTopic) {
    replace.mutate(toInput(topics.filter((t) => t.id !== entry.id)));
  }

  function cycleProficiency(entry: UserTopic) {
    const current = PROFICIENCY_ORDER.indexOf((entry.proficiency ?? "BEGINNER") as Proficiency);
    const next = PROFICIENCY_ORDER[(current + 1) % PROFICIENCY_ORDER.length];
    replace.mutate(
      toInput(topics).map((t) =>
        t.topicId === entry.topicId && t.relationType === entry.relationType ? { ...t, proficiency: next } : t
      )
    );
  }

  return (
    <section className="rounded-2xl border border-border bg-card p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Sparkles className="size-5 text-primary" /> Skills & interests
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Build your knowledge profile. Nothing here is independently verified — it reflects what you tell us.
          </p>
        </div>
        {pending && <Loader2 className="size-5 shrink-0 animate-spin text-muted-foreground" />}
      </div>

      {isLoading ? (
        <div className="flex justify-center p-6"><Loader2 className="size-5 animate-spin" /></div>
      ) : (
        <div className="space-y-5">
          {SECTIONS.map((section) => {
            const sectionTopics = topics.filter((t) => t.relationType === section.relation);
            return (
              <TopicSection
                key={section.relation}
                label={section.label}
                hint={section.hint}
                showProficiency={section.showProficiency}
                topics={sectionTopics}
                onAdd={(topic, visibility) => addTopic(section.relation, section.defaultType, topic, visibility)}
                onRemove={removeTopic}
                onCycleProficiency={cycleProficiency}
              />
            );
          })}
        </div>
      )}
    </section>
  );
}

function TopicSection({
  label,
  hint,
  showProficiency,
  topics,
  onAdd,
  onRemove,
  onCycleProficiency,
}: {
  label: string;
  hint: string;
  showProficiency: boolean;
  topics: UserTopic[];
  onAdd: (topic: { id?: string; name: string }, visibility: string) => void;
  onRemove: (entry: UserTopic) => void;
  onCycleProficiency: (entry: UserTopic) => void;
}) {
  const [visibility, setVisibility] = useState("PRIVATE");

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-medium">{label}</h3>
          <p className="text-xs text-muted-foreground">{hint}</p>
        </div>
        <select
          aria-label={`Visibility for new ${label} entries`}
          className="h-9 rounded-lg border border-input bg-transparent px-2 text-xs"
          value={visibility}
          onChange={(e) => setVisibility(e.target.value)}
        >
          {VISIBILITY_OPTIONS.map((v) => <option key={v.value} value={v.value}>{v.label}</option>)}
        </select>
      </div>

      {topics.length > 0 && (
        <ul className="mb-2 flex flex-wrap gap-2">
          {topics.map((entry) => (
            <li key={entry.id}>
              <Badge variant="secondary" className="gap-1.5 py-1 pl-3 pr-1.5 text-sm font-normal">
                {entry.canonicalName}
                {showProficiency && entry.proficiency && (
                  <button
                    type="button"
                    onClick={() => onCycleProficiency(entry)}
                    title="Change proficiency"
                    className="rounded bg-background/60 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide"
                  >
                    {entry.proficiency}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => onRemove(entry)}
                  aria-label={`Remove ${entry.canonicalName}`}
                  className="rounded-full p-0.5 hover:bg-background/60"
                >
                  <X className="size-3.5" />
                </button>
              </Badge>
            </li>
          ))}
        </ul>
      )}

      <TopicAutocomplete onAdd={(topic) => onAdd(topic, visibility)} />
    </div>
  );
}

function TopicAutocomplete({ onAdd }: { onAdd: (topic: { id?: string; name: string }) => void }) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);

  const enabled = query.trim().length >= 2;
  const { data: results = [], isLoading } = useQuery({
    queryKey: ["topic-search", query],
    queryFn: () => apiClient.searchTopics({ q: query, limit: 8 }),
    enabled,
  });

  // Row count = directory matches + one "create new" row.
  const canCreate = query.trim().length > 0 && !results.some((r) => r.canonicalName.toLowerCase() === query.trim().toLowerCase());
  const rowCount = results.length + (canCreate ? 1 : 0);

  useEffect(() => {
    function onPointerDown(e: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  function pick(topic: Topic) {
    onAdd({ id: topic.id, name: topic.canonicalName });
    setQuery("");
    setOpen(false);
  }

  function create() {
    onAdd({ name: query.trim() });
    setQuery("");
    setOpen(false);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (!open || rowCount === 0) {
      if (e.key === "Enter" && canCreate) { e.preventDefault(); create(); }
      return;
    }
    switch (e.key) {
      case "ArrowDown": e.preventDefault(); setActive((i) => (i + 1) % rowCount); break;
      case "ArrowUp": e.preventDefault(); setActive((i) => (i - 1 + rowCount) % rowCount); break;
      case "Enter":
        e.preventDefault();
        if (active < results.length) pick(results[active]);
        else if (canCreate) create();
        break;
      case "Escape": setOpen(false); break;
    }
  }

  return (
    <div className="relative" ref={boxRef}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          role="combobox"
          aria-expanded={open}
          aria-autocomplete="list"
          autoComplete="off"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setActive(0); setOpen(true); }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="Search or type to add..."
          className="h-10 rounded-xl pl-9 text-sm"
        />
      </div>

      {open && enabled && (
        <div className="absolute z-50 mt-2 w-full overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          {isLoading ? (
            <div className="flex items-center gap-2 p-3 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> Searching...
            </div>
          ) : rowCount === 0 ? (
            <div className="p-3 text-sm text-muted-foreground">Keep typing to add a topic.</div>
          ) : (
            <ul role="listbox" className="max-h-56 overflow-auto py-1">
              {results.map((topic, index) => (
                <li key={topic.id} role="option" aria-selected={active === index}>
                  <button
                    type="button"
                    tabIndex={-1}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => pick(topic)}
                    className={cn(
                      "flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm",
                      active === index ? "bg-muted" : "hover:bg-muted/60"
                    )}
                  >
                    <span className="truncate">{topic.canonicalName}</span>
                    <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">{topic.topicType}</span>
                  </button>
                </li>
              ))}
              {canCreate && (
                <li role="option" aria-selected={active === results.length}>
                  <button
                    type="button"
                    tabIndex={-1}
                    onMouseEnter={() => setActive(results.length)}
                    onClick={create}
                    className={cn(
                      "flex w-full items-center gap-2 border-t border-border px-3 py-2 text-left text-sm",
                      active === results.length ? "bg-muted" : "hover:bg-muted/60"
                    )}
                  >
                    <span className="text-muted-foreground">Add new topic</span>
                    <span className="font-medium">&ldquo;{query.trim()}&rdquo;</span>
                  </button>
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
