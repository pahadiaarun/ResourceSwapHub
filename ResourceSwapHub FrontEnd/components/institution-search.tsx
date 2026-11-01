"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Building2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { apiClient } from "@/lib/api";
import { cn } from "@/lib/utils";

interface InstitutionSearchProps {
  value?: string;
  onSelect: (institution: { id: string; name: string }) => void;
  institutionType?: string;
  placeholder?: string;
}

export function InstitutionSearch({
  value,
  onSelect,
  institutionType,
  placeholder = "Search institution...",
}: InstitutionSearchProps) {
  const [query, setQuery] = useState(value || "");
  const [isOpen, setIsOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["institutions", query, institutionType],
    queryFn: () => apiClient.searchInstitutions({ q: query, type: institutionType, size: 10 }),
    enabled: query.length >= 2,
  });

  const results = data?.content || [];

  return (
    <div className="relative">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          placeholder={placeholder}
          className="h-11 rounded-xl pl-9"
        />
      </div>
      {isOpen && query.length >= 2 && (
        <div className="absolute z-50 mt-2 w-full rounded-xl border border-border bg-card shadow-lg">
          {isLoading ? (
            <div className="p-3 text-sm text-muted-foreground">Searching...</div>
          ) : results.length > 0 ? (
            <ul className="max-h-60 overflow-auto">
              {results.map((inst) => (
                <li key={inst.id}>
                  <button
                    type="button"
                    className="flex w-full items-start gap-2 p-3 text-left hover:bg-muted"
                    onClick={() => {
                      onSelect({ id: inst.id, name: inst.name });
                      setQuery(inst.name);
                      setIsOpen(false);
                    }}
                  >
                    <Building2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{inst.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {[inst.city, inst.state, inst.officialCodeType && inst.officialCode]
                          .filter(Boolean)
                          .join(" • ")}
                      </p>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <div className="p-3 text-sm text-muted-foreground">No results found</div>
          )}
        </div>
      )}
    </div>
  );
}
