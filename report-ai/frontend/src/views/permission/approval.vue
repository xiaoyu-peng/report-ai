<template>
  <div class="permission-approval-page">
    <div class="page-header">
      <h1 class="page-title">权限审批</h1>
    </div>

    <div class="card">
      <el-table :data="approvalList" style="width: 100%">
        <el-table-column prop="userName" label="申请人" min-width="100" />
        <el-table-column prop="skillName" label="技能名称" min-width="150" />
        <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="appliedAt" label="申请时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">批准</el-button>
            <el-button size="small" @click="handleReject(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="rejectVisible" title="拒绝原因" width="400px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        placeholder="请输入拒绝原因"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPendingApprovals, approvePermission, rejectPermission } from '@/api/permission'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const rejectVisible = ref(false)
const rejectReason = ref('')
const currentRow = ref<any>(null)
const userStore = useUserStore()
const loading = ref(false)

const approvalList = ref<any[]>([])

async function handleApprove(row: any) {
  loading.value = true
  try {
    await approvePermission(row.id, userStore.userInfo?.id || '')
    approvalList.value = approvalList.value.filter(item => item.id !== row.id)
    ElMessage.success(`已批准 ${row.userName} 的申请`)
  } catch (error) {
    console.error('批准失败:', error)
    ElMessage.error('批准失败')
  } finally {
    loading.value = false
  }
}

function handleReject(row: any) {
  currentRow.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value) {
    ElMessage.warning('请输入拒绝原因')
    return
  }
  
  loading.value = true
  try {
    await rejectPermission(currentRow.value.id, userStore.userInfo?.id || '', rejectReason.value)
    rejectVisible.value = false
    approvalList.value = approvalList.value.filter(item => item.id !== currentRow.value.id)
    ElMessage.warning(`已拒绝 ${currentRow.value.userName} 的申请`)
  } catch (error) {
    console.error('拒绝失败:', error)
    ElMessage.error('拒绝失败')
  } finally {
    loading.value = false
  }
}

async function loadPendingApprovals() {
  loading.value = true
  try {
    const response = await getPendingApprovals(userStore.deptId || '')
    if (response.data) {
      approvalList.value = response.data.map((item: any) => ({
        id: item.id,
        userId: item.userId,
        userName: item.userName || item.userId,
        skillId: item.skillId,
        skillName: item.skillName || item.skillId,
        accessLevel: item.accessLevel,
        reason: item.reason || '申请使用权限',
        appliedAt: dayjs(item.appliedAt).format('YYYY-MM-DD HH:mm')
      }))
    }
  } catch (error) {
    console.error('加载待审批列表失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPendingApprovals()
})
</script>

<style scoped lang="scss">
.permission-approval-page {
  padding: 0;
  
  .card {
    background: #fff;
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  }
  
  :deep(.el-table) {
    width: 100%;
    
    .el-table__header-wrapper th {
      background: #f8fafc;
      font-weight: 600;
      color: #475569;
    }
  }
}
</style>
