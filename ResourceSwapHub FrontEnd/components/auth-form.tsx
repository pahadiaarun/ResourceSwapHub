"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowRight, Eye, EyeOff, Loader2, Mail } from "lucide-react";
import { BookCover } from "@/components/book-cover";
import { Logo } from "@/components/brand";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

const loginSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

const registerSchema = loginSchema.extend({
  name: z
    .string()
    .trim()
    .min(2, "Name must be at least 2 characters")
    .max(80, "Name must be 80 characters or fewer"),
  password: z
    .string()
    .min(8, "Password must be at least 8 characters")
    .max(72, "Password must be 72 characters or fewer"),
});

type LoginValues = z.infer<typeof loginSchema>;
type RegisterValues = z.infer<typeof registerSchema>;

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (
            element: HTMLElement,
            options: Record<string, string | number | boolean>
          ) => void;
        };
      };
    };
  }
}

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const isRegister = mode === "register";
  const router = useRouter();
  const params = useSearchParams();
  const { user, loading, login, register, loginWithGoogle } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const googleRef = useRef<HTMLDivElement>(null);
  const nextParam = params.get("next");
  const next = nextParam && nextParam.startsWith("/") ? nextParam : null;

  const providers = useQuery({
    queryKey: ["providers"],
    queryFn: apiClient.providers,
    staleTime: 5 * 60_000,
  });

  const form = useForm<LoginValues | RegisterValues>({
    resolver: zodResolver(
      (isRegister ? registerSchema : loginSchema) as typeof loginSchema
    ),
    defaultValues: isRegister
      ? { name: "", email: "", password: "" }
      : { email: "", password: "" },
  });

  const fieldErrors = form.formState.errors as unknown as Record<
    string,
    { message?: string } | undefined
  >;

  useEffect(() => {
    if (!loading && user) {
      router.replace(next ?? (user.profileComplete ? "/discover" : "/profile"));
    }
  }, [loading, user, next, router]);

  useEffect(() => {
    const enabled = providers.data?.googleEnabled;
    const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
    if (!enabled || !clientId || !googleRef.current) return;

    const initialize = () => {
      if (!window.google || !googleRef.current) return;
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: async (response) => {
          try {
            const account = await loginWithGoogle(response.credential);
            toast.success(`Welcome, ${account.name.split(" ")[0]}`);
            router.replace(next ?? (account.profileComplete ? "/discover" : "/profile"));
          } catch (error) {
            toast.error(errorMessage(error));
          }
        },
      });
      window.google.accounts.id.renderButton(googleRef.current, {
        theme: "outline",
        size: "large",
        text: "continue_with",
        shape: "pill",
        width: 320,
      });
    };

    if (window.google) {
      initialize();
      return;
    }

    const existing = document.getElementById("google-identity");
    if (existing) {
      existing.addEventListener("load", initialize);
      return () => existing.removeEventListener("load", initialize);
    }

    const script = document.createElement("script");
    script.id = "google-identity";
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.addEventListener("load", initialize);
    document.head.appendChild(script);
  }, [providers.data?.googleEnabled, loginWithGoogle, router, next]);

  async function onSubmit(values: LoginValues | RegisterValues) {
    try {
      const account = await (isRegister
        ? register(values as RegisterValues)
        : login(values as LoginValues));
      toast.success(
        isRegister
          ? `Welcome to ResourceSwapHub, ${account.name.split(" ")[0]}`
          : `Welcome back, ${account.name.split(" ")[0]}`
      );
      router.replace(
        next ?? (account.profileComplete ? "/discover" : "/profile")
      );
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div className="grid min-h-svh lg:grid-cols-[1.05fr_0.95fr]">
      <div className="flex flex-col px-4 py-6 sm:px-8 lg:px-12">
        <Logo />
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-10">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            {isRegister ? "Join the community" : "Welcome back"}
          </p>
          <h1 className="mt-2 font-display text-4xl font-semibold sm:text-5xl">
            {isRegister ? "Give your books a second life." : "Back to your shelf."}
          </h1>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
            {isRegister
              ? "Create an account to list books, message owners and keep track of every conversation."
              : "Sign in to manage your listings, pick up conversations and find your next book."}
          </p>

          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="mt-8 flex flex-col gap-4"
            noValidate
          >
            {isRegister ? (
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">Your name</span>
                <Input
                  {...form.register("name")}
                  autoComplete="name"
                  placeholder="Aisha Kapoor"
                  className="h-11 rounded-xl"
                  aria-invalid={Boolean(fieldErrors.name)}
                />
                {fieldErrors.name ? (
                  <span className="text-xs text-destructive">
                    {fieldErrors.name.message}
                  </span>
                ) : null}
              </label>
            ) : null}

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Email</span>
              <Input
                {...form.register("email")}
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                className="h-11 rounded-xl"
                aria-invalid={Boolean(fieldErrors.email)}
              />
              {fieldErrors.email ? (
                <span className="text-xs text-destructive">
                  {fieldErrors.email.message}
                </span>
              ) : null}
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Password</span>
              <div className="relative">
                <Input
                  {...form.register("password")}
                  type={showPassword ? "text" : "password"}
                  autoComplete={isRegister ? "new-password" : "current-password"}
                  placeholder={isRegister ? "At least 8 characters" : "Your password"}
                  className="h-11 rounded-xl pr-11"
                  aria-invalid={Boolean(fieldErrors.password)}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute right-2 top-1/2 flex size-8 -translate-y-1/2 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              {fieldErrors.password ? (
                <span className="text-xs text-destructive">
                  {fieldErrors.password.message}
                </span>
              ) : null}
            </label>

            <Button
              type="submit"
              disabled={form.formState.isSubmitting}
              className="mt-1 h-11 gap-2 rounded-xl"
            >
              {form.formState.isSubmitting ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Mail className="size-4" />
              )}
              {isRegister ? "Create account" : "Sign in"}
              <ArrowRight className="size-4" />
            </Button>
          </form>

          {providers.data?.googleEnabled ? (
            <>
              <div className="my-6 flex items-center gap-3 text-[11px] uppercase tracking-wider text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                or
                <span className="h-px flex-1 bg-border" />
              </div>
              <div ref={googleRef} className="flex min-h-10 justify-center" />
            </>
          ) : null}

          <p className="mt-8 text-center text-sm text-muted-foreground">
            {isRegister ? "Already have an account?" : "New to ResourceSwapHub?"}{" "}
            <Link
              href={`${isRegister ? "/login" : "/register"}${
                next ? `?next=${encodeURIComponent(next)}` : ""
              }`}
              className="font-medium text-primary underline-offset-4 hover:underline"
            >
              {isRegister ? "Sign in" : "Create an account"}
            </Link>
          </p>

          <Link
            href="/discover"
            className="mt-2 text-center text-xs text-muted-foreground underline-offset-4 hover:underline"
          >
            Keep browsing without an account
          </Link>
        </div>
      </div>

      <div className="relative hidden overflow-hidden border-l border-border bg-secondary/50 lg:flex lg:flex-col lg:justify-between">
        <div className="paper-grid absolute inset-0 opacity-50" />
        <div className="absolute -right-16 top-1/4 size-80 rounded-full bg-accent/60 blur-3xl" />
        <div className="relative flex flex-1 items-center justify-center p-12">
          <div className="relative h-96 w-80">
            <div className="absolute left-0 top-10 w-40 -rotate-9 overflow-hidden rounded-lg shadow-2xl">
              <BookCover
                title="NCERT Mathematics"
                author="Class 10"
                className="aspect-[3/4.2]"
                compact
              />
            </div>
            <div className="absolute right-0 top-0 w-44 rotate-6 overflow-hidden rounded-lg shadow-2xl">
              <BookCover
                title="Concise Physics"
                author="Class 9 · ICSE"
                className="aspect-[3/4.2]"
                compact
              />
            </div>
            <div className="absolute bottom-0 left-1/2 z-10 w-44 -translate-x-1/2 rotate-1 overflow-hidden rounded-lg shadow-2xl">
              <BookCover
                title="First Flight"
                author="English reader"
                className="aspect-[3/4.2]"
                compact
              />
            </div>
          </div>
        </div>
        <div className="relative border-t border-border/60 bg-card/60 p-10 backdrop-blur">
          <blockquote className="font-display text-2xl font-medium leading-snug">
            “A book is a gift you can open again and again — and then pass on.”
          </blockquote>
          <p className="mt-3 text-sm text-muted-foreground">
            ResourceSwapHub is built around one simple idea: the resource
            you need is probably already within a few kilometres of you.
          </p>
        </div>
      </div>
    </div>
  );
}
