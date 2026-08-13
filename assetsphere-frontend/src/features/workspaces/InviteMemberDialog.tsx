import { useState, type FormEvent } from "react";
import { Check, Copy, ExternalLink, Loader2, Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useInviteWorkspaceMember } from "@/features/workspaces/hooks";
import { copyText, formatBackendDate } from "@/lib/utils";
import { ApiError } from "@/types/api";
import type { WorkspaceRole } from "@/types/workspace";

export function InviteMemberDialog({ workspaceId, open, onOpenChange }: { workspaceId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<WorkspaceRole>("MEMBER");
  const [copied, setCopied] = useState(false);
  const invite = useInviteWorkspaceMember(workspaceId);
  async function submit(event: FormEvent) { event.preventDefault(); try { await invite.mutateAsync({ email: email.trim(), role }); } catch { return; } }
  const error = invite.error instanceof ApiError ? invite.error.message : invite.error ? "Invitation could not be sent." : null;
  const invitationUrl = invite.data?.invitationUrl ?? null;
  function close() { invite.reset(); setEmail(""); setRole("MEMBER"); setCopied(false); onOpenChange(false); }
  async function copyLink() { if (!invitationUrl) return; await copyText(invitationUrl); setCopied(true); window.setTimeout(() => setCopied(false), 2400); }
  const deliveryMessage = invite.data?.emailDeliveryStatus === "SENT" ? "Invitation email sent. You can also copy the secure link." : invite.data?.emailDeliveryStatus === "FAILED" ? "Email delivery failed. Share this secure link manually." : "Email delivery is not configured. Share this secure link manually.";

  return <Dialog open={open} onOpenChange={(nextOpen) => nextOpen ? onOpenChange(true) : close()}><DialogContent><DialogHeader><DialogTitle>{invitationUrl ? "Invitation created" : "Invite member"}</DialogTitle><DialogDescription>{invitationUrl ? deliveryMessage : "Invite someone by email and assign their workspace role."}</DialogDescription></DialogHeader>{invitationUrl ? <div className="space-y-4"><div className="flex items-center gap-3 rounded-md bg-emerald-500/10 p-3 text-sm text-emerald-700"><Check className="h-4 w-4" />{invite.data?.emailDeliveryStatus === "SENT" ? "Invitation email sent to" : "Invitation created for"} {invite.data?.inviteeEmail}</div><div className="space-y-2"><Label htmlFor="invitation-link">Single-use invitation link</Label><div className="flex gap-2"><Input id="invitation-link" readOnly value={invitationUrl} /><Button type="button" variant="outline" aria-label="Copy invitation link" onClick={copyLink}>{copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}{copied ? "Copied" : "Copy"}</Button><Button asChild type="button" variant="outline" size="icon"><a href={invitationUrl} target="_blank" rel="noreferrer" aria-label="Open invitation link"><ExternalLink className="h-4 w-4" /></a></Button></div><p className="text-xs text-muted-foreground">The link expires at {formatBackendDate(invite.data!.expiresAt, "MMM d, yyyy 'at' h:mm a")} and only the invited email can accept it.</p><span className="sr-only" aria-live="polite">{copied ? "Invitation link copied" : ""}</span></div><DialogFooter><Button type="button" onClick={close}>Done</Button></DialogFooter></div> : <form onSubmit={submit} className="space-y-4"><div className="space-y-2"><Label htmlFor="invite-email">Email</Label><Input id="invite-email" type="email" value={email} maxLength={320} required autoComplete="email" placeholder="colleague@example.com" onChange={(event) => setEmail(event.target.value)} /></div><div className="space-y-2"><Label htmlFor="invite-role">Role</Label><select id="invite-role" value={role} onChange={(event) => setRole(event.target.value as WorkspaceRole)} className="flex h-9 w-full rounded-md border border-input bg-background px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="ADMIN">Admin</option><option value="MEMBER">Member</option><option value="VIEWER">Viewer</option><option value="AUDITOR">Auditor</option></select></div>{error && <p className="text-sm text-destructive" role="alert">{error}</p>}<DialogFooter><Button type="button" variant="outline" onClick={close}>Cancel</Button><Button type="submit" disabled={!email.trim() || invite.isPending}>{invite.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}Create invitation</Button></DialogFooter></form>}</DialogContent></Dialog>;
}
