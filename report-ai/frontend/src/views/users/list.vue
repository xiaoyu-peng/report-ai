<template>
  <div class="users-list-page list-page-common">
    <div class="page-header">
      <h1 class="page-title">用户列表</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        添加用户
      </el-button>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchForm.keyword"
        placeholder="搜索用户名、邮箱..."
        prefix-icon="Search"
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="全部" value="" />
        <el-option label="正常" value="active" />
        <el-option label="禁用" value="inactive" />
        <el-option label="锁定" value="locked" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="card">
      <el-table :data="users" v-loading="loading" style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="100">
          <template #default="{ row }">
            <span>{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="部门" min-width="100">
          <template #default="{ row }">
            <span class="resource-name">{{ getDeptName(row.deptId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="light">{{ getRoleName(row.roleId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              <el-icon v-if="row.status === 'active'"><SuccessFilled /></el-icon>
              <el-icon v-else><CircleCloseFilled /></el-icon>
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="160">
          <template #default="{ row }">
            {{ row.lastLoginAt ? formatDate(row.lastLoginAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
              <el-button link type="danger" @click="handleDelete(row)" :disabled="row.username === 'admin'">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            :data="departmentTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            placeholder="请选择部门"
            clearable
            check-strictly
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" clearable style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="状态" prop="status" v-if="isEdit">
          <el-radio-group v-model="form.status">
            <el-radio value="active">正常</el-radio>
            <el-radio value="inactive">禁用</el-radio>
            <el-radio value="locked">锁定</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUserList, createUser, updateUser, deleteUser, resetUserPassword } from '@/api/user'
import { getDepartmentTree, type DepartmentTree } from '@/api/department'
import { getRoleList } from '@/api/role'
import type { Role } from '@/api/role'
import dayjs from 'dayjs'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const searchForm = reactive({
  keyword: '',
  status: ''
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const users = ref<any[]>([])
const departmentTree = ref<DepartmentTree[]>([])
const roles = ref<Role[]>([])

const form = reactive({
  id: '' as string | number,
  username: '',
  email: '',
  password: '',
  deptId: null as string | number | null,
  roleId: null as string | number | null,
  phone: '',
  status: 'active'
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => isEdit.value ? '编辑用户' : '添加用户')

function getStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    active: 'success',
    inactive: 'info',
    locked: 'danger'
  }
  return types[status] || 'info'
}

function getStatusLabel(status: string) {
  const labels: Record<string, string> = {
    active: '正常',
    inactive: '禁用',
    locked: '锁定'
  }
  return labels[status] || status
}

function getDeptName(deptId: string | number | null): string {
  if (!deptId) return '-'
  const dept = findDeptById(departmentTree.value, String(deptId))
  return dept?.name || '-'
}

function getRoleName(roleId: string | number | null): string {
  if (!roleId) return '-'
  const role = roles.value.find(r => String(r.id) === String(roleId))
  return role?.name || '-'
}

function findDeptById(depts: DepartmentTree[], id: string): DepartmentTree | null {
  for (const dept of depts) {
    if (String(dept.id) === id) return dept
    if (dept.children) {
      const found = findDeptById(dept.children, id)
      if (found) return found
    }
  }
  return null
}

function formatDate(date: string) {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

async function loadUsers() {
  loading.value = true
  try {
    const response = await getUserList({
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined,
      current: pagination.current,
      size: pagination.size
    })
    
    if (response.data) {
      users.value = response.data.records
      pagination.total = response.data.total
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const response = await getDepartmentTree()
    if (response.data) {
      departmentTree.value = response.data
    }
  } catch (error) {
    console.error('加载部门树失败:', error)
  }
}

async function loadRoles() {
  try {
    const response = await getRoleList({ current: 1, size: 100 })
    if (response.data) {
      roles.value = response.data.records
    }
  } catch (error) {
    console.error('加载角色列表失败:', error)
  }
}

function handleSearch() {
  pagination.current = 1
  loadUsers()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  pagination.current = 1
  loadUsers()
}

function handleCreate() {
  isEdit.value = false
  form.id = 0
  form.username = ''
  form.email = ''
  form.password = ''
  form.deptId = null
  form.roleId = null
  form.phone = ''
  form.status = 'active'
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  form.id = row.id
  form.username = row.username
  form.email = row.email
  form.deptId = row.deptId
  form.roleId = row.roleId
  form.phone = row.phone || ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.id, {
        email: form.email,
        deptId: form.deptId || undefined,
        roleId: form.roleId || undefined,
        phone: form.phone || undefined,
        status: form.status
      })
      ElMessage.success('更新成功')
    } else {
      await createUser({
        username: form.username,
        email: form.email,
        password: form.password,
        deptId: form.deptId || undefined,
        roleId: form.roleId || undefined,
        phone: form.phone || undefined
      })
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleResetPassword(row: any) {
  try {
    await ElMessageBox.confirm(`确定要重置用户「${row.username}」的密码吗？密码将被重置为: 123456`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await resetUserPassword(String(row.id))
    ElMessage.success('密码已重置为: 123456')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重置密码失败:', error)
      ElMessage.error('重置密码失败')
    }
  }
}

async function handleDelete(row: any) {
  if (row.username === 'admin') {
    ElMessage.warning('不能删除管理员账户')
    return
  }
  
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteUser(String(row.id))
    users.value = users.value.filter(u => u.id !== row.id)
    pagination.total = Math.max(0, pagination.total - 1)
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

function handleSizeChange(size: number) {
  pagination.size = size
  loadUsers()
}

function handleCurrentChange(current: number) {
  pagination.current = current
  loadUsers()
}

onMounted(() => {
  loadUsers()
  loadDepartments()
  loadRoles()
})
</script>

<style scoped lang="scss">
@import '@/styles/list-page.scss';

.users-list-page {
  .user-info {
    display: flex;
    align-items: center;
  }

  .resource-name {
    font-weight: 500;
    color: #0f172a;
  }
}
</style>
