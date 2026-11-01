"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { Client, type IMessage } from "@stomp/stompjs";
import { toast } from "sonner";
import {
  ArrowLeft,
  ArrowUp,
  Inbox,
  Loader2,
  MessageCircle,
  PackageOpen,
  Send,
} from "lucide-react";
import { Avatar } from "@/components/avatar";
import { BookCover } from "@/components/book-cover";
import { EmptyState } from "@/components/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage, getAccessToken } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { clockTime, formatPrice, relativeTime } from "@/lib/format";
import type { Conversation, Message } from "@/lib/types";
import { cn } from "@/lib/utils";

function wsUrl() {
  if (process.env.NEXT_PUBLIC_WS_URL) return process.env.NEXT_PUBLIC_WS_URL;
  if (typeof window === "undefined") return "ws://localhost:8080/ws";
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.hostname}:8080/ws`;
}

function ConversationRow({
  conversation,
  active,
  onSelect,
}: {
  conversation: Conversation;
  active: boolean;
  onSelect?: () => void;
}) {
  const other = conversation.otherParticipant;
  return (
    <Link
      href={`/inbox/${conversation.id}`}
      onClick={onSelect}
      className={cn(
        "flex gap-3 border-b border-border/60 px-3 py-3.5 transition last:border-0",
        active ? "bg-secondary/70" : "hover:bg-muted/60"
      )}
    >
      <div className="relative shrink-0">
        <Avatar name={other?.name} src={other?.avatarUrl} className="size-10" />
        {conversation.unreadCount > 0 ? (
          <span className="absolute -right-0.5 -top-0.5 flex size-4 items-center justify-center rounded-full bg-primary text-[9px] font-semibold text-primary-foreground ring-2 ring-card">
            {conversation.unreadCount}
          </span>
        ) : null}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <p
            className={cn(
              "truncate text-sm",
              conversation.unreadCount > 0 ? "font-semibold" : "font-medium"
            )}
          >
            {other?.name ?? "ResourceSwapHub reader"}
          </p>
          <span className="shrink-0 text-[10px] text-muted-foreground">
            {relativeTime(conversation.lastMessageAt)}
          </span>
        </div>
        <p className="mt-0.5 truncate text-[11px] font-medium text-primary/80">
          {conversation.listingTitle}
        </p>
        <p
          className={cn(
            "mt-0.5 truncate text-xs",
            conversation.unreadCount > 0
              ? "font-medium text-foreground"
              : "text-muted-foreground"
          )}
        >
          {conversation.lastMessage?.body ?? "Say hello and ask about the book."}
        </p>
      </div>
    </Link>
  );
}

function MessageBubble({ message, mine }: { message: Message; mine: boolean }) {
  return (
    <div className={cn("flex", mine ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[85%] rounded-2xl px-3.5 py-2 text-sm leading-relaxed sm:max-w-[72%]",
          mine
            ? "rounded-br-md bg-primary text-primary-foreground"
            : "rounded-bl-md bg-card text-card-foreground ring-1 ring-border"
        )}
      >
        <p className="whitespace-pre-wrap break-words">{message.body}</p>
        <p
          className={cn(
            "mt-1 text-right text-[9px]",
            mine ? "text-primary-foreground/60" : "text-muted-foreground"
          )}
        >
          {clockTime(message.createdAt)}
        </p>
      </div>
    </div>
  );
}

export function InboxWorkspace({ activeId }: { activeId?: string }) {
  const { user } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");
  const listRef = useRef<HTMLDivElement>(null);
  const composerRef = useRef<HTMLTextAreaElement>(null);

  const conversations = useQuery({
    queryKey: ["conversations"],
    queryFn: () => apiClient.conversations(0, 30),
  });

  const conversation = useQuery({
    queryKey: ["conversation", activeId],
    queryFn: () => apiClient.conversation(activeId!),
    enabled: Boolean(activeId),
  });

  const messages = useInfiniteQuery({
    queryKey: ["messages", activeId],
    queryFn: ({ pageParam }) =>
      apiClient.messages(activeId!, pageParam as number | undefined),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) =>
      last.hasMore && last.nextBefore ? last.nextBefore : undefined,
    enabled: Boolean(activeId),
  });

  const allMessages = useMemo(
    () =>
      (messages.data?.pages ?? [])
        .flatMap((page) => page.messages)
        .sort((a, b) => a.id - b.id),
    [messages.data]
  );

  const send = useMutation({
    mutationFn: (body: string) => apiClient.sendMessage(activeId!, body),
    onSuccess: () => {
      setDraft("");
      void queryClient.invalidateQueries({ queryKey: ["messages", activeId] });
      void queryClient.invalidateQueries({ queryKey: ["conversations"] });
      void queryClient.invalidateQueries({ queryKey: ["unread-count"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const markRead = useMutation({
    mutationFn: (lastMessageId: number) => apiClient.markRead(activeId!, lastMessageId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["conversations"] });
      void queryClient.invalidateQueries({ queryKey: ["unread-count"] });
    },
  });

  useEffect(() => {
    if (!user) return;
    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      beforeConnect: () => {
        const token = getAccessToken();
        client.connectHeaders = token
          ? { Authorization: `Bearer ${token}` }
          : {};
      },
      onConnect: () => {
        client.subscribe("/user/queue/chat-events", (frame: IMessage) => {
          try {
            const event = JSON.parse(frame.body) as {
              type: string;
              conversationId: string;
              message: Message;
            };
            if (event.type !== "MESSAGE") return;
            void queryClient.invalidateQueries({ queryKey: ["conversations"] });
            void queryClient.invalidateQueries({ queryKey: ["unread-count"] });
            if (event.conversationId === activeId) {
              void queryClient.invalidateQueries({
                queryKey: ["messages", activeId],
              });
            }
          } catch {
            // Ignore malformed frames; REST remains the source of truth.
          }
        });
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [user, activeId, queryClient]);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [allMessages.length, activeId]);

  useEffect(() => {
    const last = allMessages[allMessages.length - 1];
    if (!last || !user || last.sender.id === user.id) return;
    if (!activeId) return;
    markRead.mutate(last.id);
    // markRead.mutate is stable; only react to the newest message changing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId, allMessages, user]);

  useEffect(() => {
    if (activeId) composerRef.current?.focus();
  }, [activeId]);

  function submit() {
    const body = draft.trim();
    if (!body || !activeId || send.isPending) return;
    send.mutate(body);
  }

  const activeConversation = conversation.data;

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-card soft-shadow">
      <div className="grid h-[calc(100svh-11.5rem)] min-h-[500px] lg:h-[calc(100svh-8.5rem)] lg:grid-cols-[320px_1fr]">
        <aside
          className={cn(
            "flex min-h-0 flex-col border-r border-border",
            activeId ? "hidden lg:flex" : "flex"
          )}
        >
          <div className="flex items-center justify-between border-b border-border px-4 py-3.5">
            <div>
              <h1 className="font-display text-xl font-semibold">Inbox</h1>
              <p className="text-[11px] text-muted-foreground">
                {conversations.data?.totalElements ?? 0} conversation
                {conversations.data?.totalElements === 1 ? "" : "s"}
              </p>
            </div>
            <Link
              href="/discover"
              className="rounded-lg p-2 text-muted-foreground transition hover:bg-muted hover:text-foreground"
              aria-label="Find more books"
            >
              <PackageOpen className="size-4" />
            </Link>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto">
            {conversations.isLoading ? (
              <div className="space-y-4 p-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={index} className="flex gap-3">
                    <div className="size-10 animate-pulse rounded-full bg-muted" />
                    <div className="flex-1 space-y-2">
                      <div className="h-3 w-1/2 animate-pulse rounded bg-muted" />
                      <div className="h-3 w-4/5 animate-pulse rounded bg-muted" />
                    </div>
                  </div>
                ))}
              </div>
            ) : conversations.data?.content.length ? (
              conversations.data.content.map((item) => (
                <ConversationRow
                  key={item.id}
                  conversation={item}
                  active={item.id === activeId}
                />
              ))
            ) : (
              <div className="p-4">
                <EmptyState
                  icon={Inbox}
                  title="No conversations yet"
                  description="When you message a book owner, the thread will appear here."
                  action={
                    <Button variant="outline" size="sm" render={<Link href="/discover" />}>
                      Find a book
                    </Button>
                  }
                  className="border-0 bg-transparent px-2 py-8"
                />
              </div>
            )}
          </div>
        </aside>

        <section
          className={cn(
            "flex min-h-0 flex-col bg-background/40",
            activeId ? "flex" : "hidden lg:flex"
          )}
        >
          {activeId ? (
            <>
              <header className="flex items-center gap-3 border-b border-border bg-card px-3 py-2.5 sm:px-4">
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="lg:hidden"
                  onClick={() => router.push("/inbox")}
                  aria-label="Back to inbox"
                >
                  <ArrowLeft className="size-4" />
                </Button>
                <Avatar
                  name={activeConversation?.otherParticipant?.name}
                  src={activeConversation?.otherParticipant?.avatarUrl}
                  className="size-9"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold">
                    {activeConversation?.otherParticipant?.name ?? "Conversation"}
                  </p>
                  <p className="truncate text-[11px] text-muted-foreground">
                    {activeConversation?.otherParticipant?.city
                      ? `${activeConversation.otherParticipant.city} · `
                      : ""}
                    {activeConversation?.listingTitle ?? "Book enquiry"}
                  </p>
                </div>
                {activeConversation?.listingId ? (
                  <Button
                    variant="outline"
                    size="sm"
                    render={<Link href={`/books/${activeConversation.listingId}`} />}
                    className="hidden sm:inline-flex"
                  >
                    View book
                  </Button>
                ) : null}
              </header>

              {activeConversation ? (
                <div className="flex items-center gap-2.5 border-b border-border bg-secondary/30 px-3 py-2 sm:px-4">
                  <BookCover
                    title={activeConversation.listingTitle}
                    author={activeConversation.listingAuthor}
                    imageUrl={activeConversation.listingCoverUrl}
                    className="h-10 w-8 shrink-0 rounded"
                    compact
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium">
                      {activeConversation.listingTitle}
                    </p>
                    <p className="truncate text-[10px] text-muted-foreground">
                      {activeConversation.listingAuthor}
                    </p>
                  </div>
                  <Badge variant="outline">
                    {formatPrice({
                      priceType:
                        activeConversation.listingPriceType ?? "NEGOTIABLE",
                      price: activeConversation.listingPrice,
                    })}
                  </Badge>
                </div>
              ) : null}

              <div
                ref={listRef}
                className="min-h-0 flex-1 space-y-3 overflow-y-auto px-3 py-4 sm:px-5"
              >
                {messages.isLoading ? (
                  <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="size-4 animate-spin" /> Opening the thread…
                  </div>
                ) : messages.isError ? (
                  <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
                    <p className="text-sm font-medium">Couldn&apos;t load messages.</p>
                    <p className="text-xs text-muted-foreground">
                      {errorMessage(messages.error)}
                    </p>
                    <Button variant="outline" size="sm" onClick={() => void messages.refetch()}>
                      Try again
                    </Button>
                  </div>
                ) : (
                  <>
                    {messages.hasNextPage ? (
                      <div className="flex justify-center pb-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={messages.isFetchingNextPage}
                          onClick={() => void messages.fetchNextPage()}
                          className="gap-1 text-[11px] text-muted-foreground"
                        >
                          <ArrowUp className="size-3" />
                          {messages.isFetchingNextPage ? "Loading…" : "Earlier messages"}
                        </Button>
                      </div>
                    ) : null}

                    {allMessages.length === 0 ? (
                      <div className="flex h-full flex-col items-center justify-center px-4 text-center">
                        <span className="flex size-11 items-center justify-center rounded-2xl bg-secondary">
                          <MessageCircle className="size-5 text-primary" />
                        </span>
                        <p className="mt-3 text-sm font-medium">Start the conversation</p>
                        <p className="mt-1 max-w-xs text-xs leading-relaxed text-muted-foreground">
                          Ask whether the book is still available, about its
                          condition, or if the price has some room.
                        </p>
                      </div>
                    ) : (
                      allMessages.map((message) => (
                        <MessageBubble
                          key={message.id}
                          message={message}
                          mine={message.sender.id === user?.id}
                        />
                      ))
                    )}
                  </>
                )}
              </div>

              <div className="border-t border-border bg-card p-2.5 sm:p-3">
                <div className="flex items-end gap-2 rounded-xl border border-input bg-background p-1.5 focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/20">
                  <textarea
                    ref={composerRef}
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" && !event.shiftKey) {
                        event.preventDefault();
                        submit();
                      }
                    }}
                    rows={1}
                    maxLength={4000}
                    placeholder="Write a message…"
                    className="max-h-32 min-h-9 flex-1 resize-none bg-transparent px-2 py-2 text-sm outline-none placeholder:text-muted-foreground"
                  />
                  <Button
                    size="icon"
                    className="size-9 shrink-0 rounded-lg"
                    disabled={!draft.trim() || send.isPending}
                    onClick={submit}
                    aria-label="Send message"
                  >
                    {send.isPending ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Send className="size-4" />
                    )}
                  </Button>
                </div>
                <p className="mt-1.5 px-1 text-[10px] text-muted-foreground">
                  Enter to send · Shift + Enter for a new line. Meet in a public
                  place and inspect the book before paying.
                </p>
              </div>
            </>
          ) : (
            <div className="flex h-full flex-col items-center justify-center px-6 text-center">
              <span className="flex size-14 items-center justify-center rounded-2xl bg-secondary">
                <Inbox className="size-6 text-primary" />
              </span>
              <h2 className="mt-4 font-display text-2xl font-semibold">
                Pick a conversation
              </h2>
              <p className="mt-1 max-w-xs text-sm text-muted-foreground">
                Choose a thread on the left to see the book, the owner and your
                messages. New messages arrive here in real time.
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
