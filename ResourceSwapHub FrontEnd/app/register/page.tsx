import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";

export const metadata = {
  title: "Create an account",
  description:
    "Join ResourceSwapHub to give away, sell and discover resources near you.",
};

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="min-h-svh bg-background" />}>
      <AuthForm mode="register" />
    </Suspense>
  );
}
