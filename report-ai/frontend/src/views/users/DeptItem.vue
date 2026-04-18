<template>
  <div class="dept-item-wrapper">
    <div 
      class="dept-item" 
      :class="{ 'has-children': hasChildren }"
      :style="{ paddingLeft: level * 24 + 'px' }"
    >
      <div class="dept-main">
        <div class="dept-icon">
          <div class="icon-box">
            <span class="icon-emoji">🏢</span>
          </div>
        </div>
        
        <div class="dept-content">
          <div class="dept-header">
            <h3 class="dept-name">{{ department.name }}</h3>
            <button 
              v-if="hasChildren"
              class="expand-btn" 
              :class="{ expanded: expanded }"
              @click.stop="toggleExpand"
            >
              <el-icon>
                <ArrowUp v-if="expanded" />
                <ArrowDown v-else />
              </el-icon>
            </button>
          </div>
          
          <p class="dept-desc">{{ department.description || '暂无描述' }}</p>
          
          <div class="dept-meta">
            <div class="meta-item">
              <el-icon class="meta-icon"><User /></el-icon>
              <span>负责人：{{ department.managerName || '未指定' }}</span>
            </div>
            <div class="meta-item">
              <el-icon class="meta-icon"><UserFilled /></el-icon>
              <span>{{ department.memberCount || 0 }} 人</span>
            </div>
            <div v-if="hasChildren" class="meta-item">
              <el-icon class="meta-icon"><OfficeBuilding /></el-icon>
              <span>{{ department.children?.length || 0 }} 个子部门</span>
            </div>
          </div>
        </div>
        
        <div class="dept-actions">
          <button class="action-btn primary" @click.stop="$emit('edit', department)">
            编辑
          </button>
          <button class="action-btn" @click.stop="$emit('add-child', department)">
            添加子部门
          </button>
          <button class="action-btn" @click.stop="$emit('view-members', department)">
            查看成员
          </button>
          <button 
            class="action-btn danger" 
            @click.stop="$emit('delete', department)"
            :disabled="hasChildren"
          >
            删除
          </button>
        </div>
      </div>
    </div>
    
    <div v-if="hasChildren && expanded" class="dept-children">
      <DeptItem
        v-for="child in department.children"
        :key="child.id"
        :department="child"
        :level="level + 1"
        @edit="(d: any) => $emit('edit', d)"
        @delete="(d: any) => $emit('delete', d)"
        @add-child="(d: any) => $emit('add-child', d)"
        @view-members="(d: any) => $emit('view-members', d)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowUp, ArrowDown, User, UserFilled, OfficeBuilding } from '@element-plus/icons-vue'

interface Department {
  id: string | number
  name: string
  parentId?: string | number
  managerId?: string | number
  managerName?: string
  memberCount?: number
  description?: string
  children?: Department[]
}

const props = defineProps<{
  department: Department
  level: number
}>()

defineEmits<{
  (e: 'edit', department: Department): void
  (e: 'delete', department: Department): void
  (e: 'add-child', department: Department): void
  (e: 'view-members', department: Department): void
}>()

const expanded = ref(props.level === 0)
const hasChildren = computed(() => props.department.children && props.department.children.length > 0)

const toggleExpand = () => {
  if (hasChildren.value) {
    expanded.value = !expanded.value
  }
}
</script>

<style scoped>
.dept-item-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dept-item {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  transition: all 0.2s ease;
}

.dept-item:hover {
  border-color: #d1d5db;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.dept-item:hover .dept-actions {
  opacity: 1;
}

.dept-item.has-children {
  cursor: pointer;
}

.dept-main {
  display: flex;
  align-items: flex-start;
  padding: 20px;
  gap: 16px;
}

.dept-icon {
  flex-shrink: 0;
}

.icon-box {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.icon-emoji {
  font-size: 24px;
}

.dept-content {
  flex: 1;
  min-width: 0;
}

.dept-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.dept-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.expand-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: #f3f4f6;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  transition: all 0.2s ease;
}

.expand-btn:hover {
  background: #e5e7eb;
  color: #1a1a1a;
}

.expand-btn.expanded {
  background: #1a1a1a;
  color: #fff;
}

.dept-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 12px;
  line-height: 1.5;
}

.dept-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}

.meta-icon {
  font-size: 14px;
  color: #9ca3af;
}

.dept-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.action-btn {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
}

.action-btn:hover:not(:disabled) {
  background: #f9fafb;
  border-color: #d1d5db;
}

.action-btn.primary {
  background: #1a1a1a;
  border-color: #1a1a1a;
  color: #fff;
}

.action-btn.primary:hover {
  background: #333;
  border-color: #333;
}

.action-btn.danger {
  color: #ef4444;
}

.action-btn.danger:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #fecaca;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dept-children {
  padding-left: 24px;
  border-left: 2px solid #e5e7eb;
  margin-left: 44px;
}

@media (max-width: 768px) {
  .dept-main {
    flex-direction: column;
  }

  .dept-actions {
    width: 100%;
    justify-content: flex-start;
    opacity: 1;
  }
}
</style>
