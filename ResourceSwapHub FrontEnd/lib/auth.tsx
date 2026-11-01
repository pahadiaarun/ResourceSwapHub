"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { apiClient, getAccessToken, refreshSession, setAccessToken } from "./api";
import type { AuthResponse, User } from "./types";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  signIn: (result: AuthResponse) => void;
  login: (input: { email: string; password: string }) => Promise<User>;
  register: (input: {
    name: string;
    email: string;
    password: string;
  }) => Promise<User>;
  loginWithGoogle: (credential: string) => Promise<User>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<User | null>;
  setUser: (user: User | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function bootstrap() {
      try {
        if (!getAccessToken()) {
          const refreshed = await refreshSession();
          if (!refreshed) return;
        }
        const me = await apiClient.me();
        if (active) setUser(me);
      } catch {
        setAccessToken(null);
      } finally {
        if (active) setLoading(false);
      }
    }
    void bootstrap();
    return () => {
      active = false;
    };
  }, []);

  const signIn = useCallback((result: AuthResponse) => {
    setAccessToken(result.accessToken);
    setUser(result.user);
  }, []);

  const login = useCallback(
    async (input: { email: string; password: string }) => {
      const result = await apiClient.login(input);
      signIn(result);
      return result.user;
    },
    [signIn]
  );

  const register = useCallback(
    async (input: { name: string; email: string; password: string }) => {
      const result = await apiClient.register(input);
      signIn(result);
      return result.user;
    },
    [signIn]
  );

  const loginWithGoogle = useCallback(
    async (credential: string) => {
      const result = await apiClient.loginWithGoogle(credential);
      signIn(result);
      return result.user;
    },
    [signIn]
  );

  const logout = useCallback(async () => {
    try {
      await apiClient.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const me = await apiClient.me();
      setUser(me);
      return me;
    } catch {
      return null;
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      signIn,
      login,
      register,
      loginWithGoogle,
      logout,
      refreshUser,
      setUser,
    }),
    [
      user,
      loading,
      signIn,
      login,
      register,
      loginWithGoogle,
      logout,
      refreshUser,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
