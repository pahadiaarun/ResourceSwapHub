import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";

export const metadata = {
  title: "Sign in",
  description: "Sign in to manage your ResourceSwapHub listings and conversations.",
};

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="min-h-svh bg-background" />}>
      <AuthForm mode="login" />
    </Suspense>
  );
}
