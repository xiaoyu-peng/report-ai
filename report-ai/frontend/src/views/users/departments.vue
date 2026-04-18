<template>
  <div class="dept-page list-page-common">
    <div class="page-header">
      <h1 class="page-title">部门管理</h1>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加部门
      </el-button>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索部门名称、描述..."
        prefix-icon="Search"
        clearable
        style="width: 300px"
        @keyup.enter="filterDepartments"
      />
      <el-button type="primary" @click="filterDepartments">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <div class="card">
      <el-table
        :data="flatDepartments"
        v-loading="loading"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
        style="width: 100%"
      >
        <el-table-column prop="name" label="部门名称" min-width="180">
          <template #default="{ row }">
            <div class="dept-name-cell" :style="{ paddingLeft: (row.level || 0) * 24 + 'px' }">
              <el-icon class="dept-icon" :class="{ 'is-parent': row.children && row.children.length > 0 }">
                <OfficeBuilding />
              </el-icon>
              <span class="dept-name" :class="{ 'is-parent': row.children && row.children.length > 0 }">{{ row.name }}</span>
              <el-tag v-if="row.level === 0" size="small" type="primary" effect="plain" class="level-tag">顶级</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="dept-desc">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" min-width="100">
          <template #default="{ row }">
            <span class="resource-name">{{ getManagerName(row.managerId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成员数" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ getMemberCount(row.id) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ row.createdAt ? formatDate(row.createdAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleAddChild(row)">添加子部门</el-button>
              <el-button link type="primary" @click="handleViewMembers(row)">成员</el-button>
              <el-button link type="danger" @click="handleDelete(row)" :disabled="row.children && row.children.length > 0">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      class="dept-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="部门名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="50"
            placeholder="请输入部门名称"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="departmentTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            placeholder="请选择上级部门（不选则为顶级部门）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="form.managerId"
            placeholder="请选择负责人"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="user.username"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="200"
            placeholder="请输入部门描述"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="membersDialogVisible"
      :title="currentDept?.name + ' - 成员列表'"
      width="600px"
    >
      <el-table :data="deptMembers" style="width: 100%">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, OfficeBuilding } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getDepartmentTree, createDepartment, updateDepartment, deleteDepartment, type DepartmentTree } from '@/api/department'
import { getUserList } from '@/api/user'
import dayjs from 'dayjs'

interface User {
  id: string | number
  username: string
  email: string
  status: string
  deptId?: string | number
}

const loading = ref(false)
const searchKeyword = ref('')
const departments = ref<DepartmentTree[]>([])
const users = ref<User[]>([])
const dialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentId = ref<string | number | null>(null)
const currentDept = ref<DepartmentTree | null>(null)
const deptMembers = ref<User[]>([])
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  parentId: null as string | null,
  managerId: null as string | null,
  description: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const dialogTitle = computed(() => currentId.value ? '编辑部门' : '添加部门')

const departmentTree = computed(() => departments.value)

const flatDepartments = computed(() => {
  const addLevel = (depts: DepartmentTree[], level: number = 0): DepartmentTree[] => {
    return depts.map(dept => ({
      ...dept,
      level,
      children: dept.children ? addLevel(dept.children, level + 1) : undefined
    }))
  }
  
  if (!searchKeyword.value) return addLevel(departments.value)
  
  const keyword = searchKeyword.value.toLowerCase()
  const filterDepts = (depts: DepartmentTree[], level: number = 0): DepartmentTree[] => {
    return depts.reduce((acc: DepartmentTree[], dept) => {
      const matchName = dept.name.toLowerCase().includes(keyword)
      const matchDesc = dept.description?.toLowerCase().includes(keyword)
      const filteredChildren = dept.children ? filterDepts(dept.children, level + 1) : []
      
      if (matchName || matchDesc || filteredChildren.length > 0) {
        acc.push({ ...dept, level, children: filteredChildren })
      }
      return acc
    }, [])
  }
  return filterDepts(departments.value)
})

function getManagerName(managerId: string | number | null): string {
  if (!managerId) return '-'
  const user = users.value.find(u => String(u.id) === String(managerId))
  return user?.username || '-'
}

function getMemberCount(deptId: string | number): number {
  return users.value.filter(u => String(u.deptId) === String(deptId)).length
}

function formatDate(date: string) {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const loadDepartments = async () => {
  loading.value = true
  try {
    const res = await getDepartmentTree()
    if (res.data) {
      departments.value = res.data
    }
  } catch (error) {
    console.error('加载部门树失败:', error)
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  try {
    const res = await getUserList({ current: 1, size: 100 })
    if (res.data?.records) {
      users.value = res.data.records
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
  }
}

const filterDepartments = () => {
}

const resetSearch = () => {
  searchKeyword.value = ''
}

const handleAdd = () => {
  currentId.value = null
  Object.assign(form, {
    name: '',
    parentId: null,
    managerId: null,
    description: ''
  })
  dialogVisible.value = true
}

const handleEdit = (dept: DepartmentTree) => {
  currentId.value = dept.id
  Object.assign(form, {
    name: dept.name,
    parentId: dept.parentId || null,
    managerId: dept.managerId || null,
    description: dept.description || ''
  })
  dialogVisible.value = true
}

const handleAddChild = (dept: DepartmentTree) => {
  currentId.value = null
  Object.assign(form, {
    name: '',
    parentId: dept.id,
    managerId: null,
    description: ''
  })
  dialogVisible.value = true
}

const handleDelete = async (dept: DepartmentTree) => {
  if (dept.children && dept.children.length > 0) {
    ElMessage.warning('请先删除子部门')
    return
  }
  
  try {
    await ElMessageBox.confirm('确定要删除该部门吗？', '提示', {
      type: 'warning'
    })
    await deleteDepartment(dept.id)
    ElMessage.success('删除成功')
    loadDepartments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleViewMembers = (dept: DepartmentTree) => {
  currentDept.value = dept
  deptMembers.value = users.value.filter(u => String(u.deptId) === String(dept.id))
  membersDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const data = {
          name: form.name,
          parentId: form.parentId || undefined,
          managerId: form.managerId || undefined,
          description: form.description || undefined
        }
        if (currentId.value) {
          await updateDepartment(String(currentId.value), data)
          ElMessage.success('更新成功')
        } else {
          await createDepartment(data)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadDepartments()
      } catch (error) {
        ElMessage.error(currentId.value ? '更新失败' : '创建失败')
      }
    }
  })
}

onMounted(() => {
  loadDepartments()
  loadUsers()
})
</script>

<style scoped lang="scss">
@import '@/styles/list-page.scss';

.dept-page {
  .dept-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    transition: all 0.2s ease;
  }

  .dept-icon {
    color: #909399;
    font-size: 16px;
    transition: all 0.2s ease;

    &.is-parent {
      color: #409eff;
      font-size: 18px;
    }
  }

  .dept-name {
    font-weight: 500;
    color: #606266;
    transition: all 0.2s ease;

    &.is-parent {
      font-weight: 600;
      color: #303133;
    }
  }

  .level-tag {
    margin-left: 8px;
    font-size: 11px;
  }

  .dept-desc {
    color: #909399;
  }

  .resource-name {
    font-weight: 500;
    color: #303133;
  }

  :deep(.el-table) {
    .el-table__row {
      transition: background-color 0.2s ease;
      
      &:hover {
        background-color: #f5f7fa !important;
      }
    }

    .el-table__row--level-0 {
      background-color: #fafafa;
      
      .dept-name-cell {
        padding-left: 0 !important;
      }
    }

    .el-table__row--level-1 {
      background-color: #ffffff;
    }

    .el-table__row--level-2 {
      background-color: #fafafa;
    }
  }
}

.dept-dialog {
  :deep(.el-dialog__body) {
    padding: 20px 24px;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
