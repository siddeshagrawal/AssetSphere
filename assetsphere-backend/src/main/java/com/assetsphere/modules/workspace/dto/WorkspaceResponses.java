package com.assetsphere.modules.workspace.dto;
import java.time.Instant; import java.util.UUID; import com.assetsphere.modules.workspace.domain.*;
public final class WorkspaceResponses { private WorkspaceResponses(){} public record Detail(UUID id,String name,String slug,String description,String status){} public record Member(UUID id,UUID userId,String role,String status,Instant joinedAt){} public record Invitation(UUID id,String inviteeEmail,String role,Instant expiresAt,String invitationToken){} }
