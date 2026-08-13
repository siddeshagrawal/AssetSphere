import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { format, formatDistanceToNow } from "date-fns";

/** Merge Tailwind classes without specificity conflicts. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

export function createClientRequestId(): string {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0"));
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`;
}

export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const field = document.createElement("textarea");
  field.value = text;
  field.setAttribute("readonly", "");
  field.style.position = "fixed";
  field.style.opacity = "0";
  document.body.appendChild(field);
  field.select();
  const copied = document.execCommand("copy");
  field.remove();
  if (!copied) throw new Error("Clipboard is unavailable");
}

/** Format bytes into a human-readable string (e.g. "2.4 MB"). */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export type BackendTimestamp = string | number;

export function backendDate(value: BackendTimestamp): Date {
  const numeric = typeof value === "number"
    ? value
    : /^-?\d+(\.\d+)?$/.test(value.trim())
      ? Number(value)
      : null;
  if (numeric !== null) {
    return new Date(Math.abs(numeric) < 1_000_000_000_000 ? numeric * 1_000 : numeric);
  }
  return new Date(value);
}

export function formatBackendDate(value: BackendTimestamp, pattern = "MMM d, yyyy"): string {
  return format(backendDate(value), pattern);
}

/** Format a backend timestamp into a short date string. */
export function formatDate(value: BackendTimestamp): string {
  return formatBackendDate(value);
}

/** Format a backend timestamp as a relative time (e.g. "3 hours ago"). */
export function formatRelative(value: BackendTimestamp): string {
  return formatDistanceToNow(backendDate(value), { addSuffix: true });
}

/**
 * Derive a slug from a workspace name.
 * Lowercase, spaces → hyphens, remove non-alphanumeric except hyphens.
 */
export function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}
