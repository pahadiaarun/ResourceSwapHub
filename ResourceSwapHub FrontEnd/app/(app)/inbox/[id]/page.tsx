import { InboxWorkspace } from "@/components/inbox-workspace";

// AppShell gates this page behind client-side auth, so the page never renders
// during prerender and instant-navigation validation cannot run.
export const instant = false;

export const metadata = {
  title: "Conversation",
};

export default async function ConversationPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <InboxWorkspace activeId={id} />;
}
