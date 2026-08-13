export interface WorkspaceActivity {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  occurredAt: string | number;
}
