import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { X } from "lucide-react";
type Tone = "neutral" | "success" | "error";
type ToastInput = { tone?: Tone; message: string };
type ToastItem = ToastInput & { id: number };
type ToastContextValue = { toast: (input: ToastInput) => void };
const ToastContext = createContext<ToastContextValue | null>(null);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const toast = useCallback((input: ToastInput) => { const id = Date.now() + Math.floor(Math.random() * 1000); setItems((current) => [...current.slice(-2), { ...input, id }]); window.setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), input.tone === "error" ? 7000 : 4500); }, []);
  const value = useMemo(() => ({ toast }), [toast]);
  return <ToastContext.Provider value={value}>{children}<div className="toast-viewport" role="region" aria-label="Notifications">{items.map((item) => <div className={`toast-card toast-${item.tone ?? "neutral"}`} role="status" aria-live={item.tone === "error" ? "assertive" : "polite"} key={item.id}><i className="toast-dot" aria-hidden="true" /><span>{item.message}</span><button aria-label="Dismiss" onClick={() => setItems((current) => current.filter((entry) => entry.id !== item.id))}><X size={15} /></button></div>)}</div></ToastContext.Provider>;
}
export function useToast(): ToastContextValue { const value = useContext(ToastContext); if (!value) throw new Error("ToastProvider is missing."); return value; }
