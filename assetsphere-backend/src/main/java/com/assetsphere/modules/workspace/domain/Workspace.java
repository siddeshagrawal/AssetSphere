package com.assetsphere.modules.workspace.domain;

import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "workspaces")
public class Workspace extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceStatus status;

    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;

    protected Workspace() {
    }

    public Workspace(String name, String slug, String description, UUID creatorUserId) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.creatorUserId = creatorUserId;
        this.status = WorkspaceStatus.ACTIVE;
    }

    public void update(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
