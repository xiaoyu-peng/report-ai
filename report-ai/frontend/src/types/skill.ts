export interface Skill {
  id: string | number
  name: string
  slug: string
  type: 'SKILL' | 'MCP'
  tenantId?: string | number
  createdBy?: string | number
  platformVersions?: string
  skillFormatVersion?: string
  mcpProtocolVersion?: string
  config?: string
  metadata?: string
  permissionLevel: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE'
  deptId?: string | number
  status: 'draft' | 'published' | 'disabled' | 'archived'
  currentVersionId?: string | number
  usageCount: number
  rating: number
  ratingCount: number
  tags?: string
  description?: string
  icon?: string
  sourceType?: string
  sourceUrl?: string
  createdAt: string
  updatedAt: string
}

export interface SkillVersion {
  id: string
  skillId: string
  version: string
  config: string
  changelog?: string
  createdBy: string
  isCurrent: boolean
  status: string
  createdAt: string
  updatedAt: string
}

export interface SkillAccess {
  id: string
  skillId: string
  userId: string
  deptId?: string
  accessLevel: 'read' | 'write' | 'admin'
  status: 'pending' | 'approved' | 'rejected'
  appliedAt: string
  approvedBy?: string
  approvedAt?: string
  rejectedReason?: string
  expiresAt?: string
  createdAt: string
  updatedAt: string
}
