<template>
  <div class="permission-access-page">
    <div class="page-header">
      <h1 class="page-title">权限申请</h1>
      <div class="page-subtitle">查看可用技能并申请访问权限</div>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon public">
          <el-icon><Unlock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ publicSkills.length }}</div>
          <div class="stat-label">公共技能</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon department">
          <el-icon><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ departmentSkills.length }}</div>
          <div class="stat-label">部门技能</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon personal">
          <el-icon><User /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ personalSkills.length }}</div>
          <div class="stat-label">个人技能</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon applications">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ myApplications.length }}</div>
          <div class="stat-label">我的申请</div>
        </div>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索技能名称、描述..."
            prefix-icon="Search"
            clearable
            style="width: 300px"
          />
          <el-select v-model="filterType" placeholder="类型" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="SKILL" value="SKILL" />
            <el-option label="MCP" value="MCP" />
          </el-select>
          <el-select v-model="filterPermission" placeholder="权限级别" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="公共" value="PUBLIC" />
            <el-option label="部门" value="DEPARTMENT" />
            <el-option label="个人" value="PRIVATE" />
          </el-select>
        </div>

        <div class="skills-section">
          <div v-if="publicSkillsFiltered.length > 0" class="permission-group">
            <div class="group-header">
              <div class="group-title">
                <el-icon class="group-icon public"><Unlock /></el-icon>
                公共技能
              </div>
            </div>
            <el-row :gutter="16">
              <el-col v-for="skill in publicSkillsFiltered" :key="skill.id" :xs="24" :sm="12" :lg="12">
                <div class="skill-card public">
                  <div class="skill-header">
                    <div class="skill-icon">
                      <el-icon v-if="skill.type === 'SKILL'"><Document /></el-icon>
                      <el-icon v-else><Connection /></el-icon>
                    </div>
                    <span class="skill-type" :class="skill.type.toLowerCase()">{{ skill.type }}</span>
                  </div>
                  <div class="skill-name">{{ skill.name }}</div>
                  <div class="skill-desc">{{ skill.description }}</div>
                  <div class="skill-footer">
                    <div class="skill-meta">
                      <span class="skill-dept">{{ skill.department }}</span>
                    </div>
                    <el-tag type="success" size="small" effect="plain">
                      <el-icon><Check /></el-icon>
                      可直接使用
                    </el-tag>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>

          <div v-if="departmentSkillsFiltered.length > 0" class="permission-group">
            <div class="group-header">
              <div class="group-title">
                <el-icon class="group-icon department"><OfficeBuilding /></el-icon>
                部门技能
              </div>
            </div>
            <el-row :gutter="16">
              <el-col v-for="skill in departmentSkillsFiltered" :key="skill.id" :xs="24" :sm="12" :lg="12">
                <div class="skill-card department">
                  <div class="skill-header">
                    <div class="skill-icon">
                      <el-icon v-if="skill.type === 'SKILL'"><Document /></el-icon>
                      <el-icon v-else><Connection /></el-icon>
                    </div>
                    <span class="skill-type" :class="skill.type.toLowerCase()">{{ skill.type }}</span>
                  </div>
                  <div class="skill-name">{{ skill.name }}</div>
                  <div class="skill-desc">{{ skill.description }}</div>
                  <div class="skill-footer">
                    <div class="skill-meta">
                      <span class="skill-dept">{{ skill.department }}</span>
                    </div>
                    <el-tag type="warning" size="small" effect="plain">
                      <el-icon><Lock /></el-icon>
                      需要申请
                    </el-tag>
                    <el-button
                      v-if="!skill.applied"
                      type="primary"
                      size="small"
                      @click="handleApply(skill)"
                      style="margin-left: 8px"
                    >
                      申请权限
                    </el-button>
                    <el-tag v-else type="info" size="small" style="margin-left: 8px">
                      <el-icon><Clock /></el-icon>
                      已申请
                    </el-tag>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>

          <div v-if="personalSkillsFiltered.length > 0" class="permission-group">
            <div class="group-header">
              <div class="group-title">
                <el-icon class="group-icon personal"><User /></el-icon>
                个人技能
              </div>
            </div>
            <el-row :gutter="16">
              <el-col v-for="skill in personalSkillsFiltered" :key="skill.id" :xs="24" :sm="12" :lg="12">
                <div class="skill-card personal">
                  <div class="skill-header">
                    <div class="skill-icon">
                      <el-icon v-if="skill.type === 'SKILL'"><Document /></el-icon>
                      <el-icon v-else><Connection /></el-icon>
                    </div>
                    <span class="skill-type" :class="skill.type.toLowerCase()">{{ skill.type }}</span>
                  </div>
                  <div class="skill-name">{{ skill.name }}</div>
                  <div class="skill-desc">{{ skill.description }}</div>
                  <div class="skill-footer">
                    <div class="skill-meta">
                      <span class="skill-dept">{{ skill.department }}</span>
                    </div>
                    <el-tag type="primary" size="small" effect="plain">
                      <el-icon><User /></el-icon>
                      我的技能
                    </el-tag>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>

          <el-empty v-if="filteredSkills.length === 0" description="暂无匹配的技能" />
        </div>
      </el-col>

      <el-col :xs="24" :lg="8">
        <div class="applications-card">
          <div class="card-header">
            <h3>我的申请记录</h3>
            <el-badge :value="pendingCount" :hidden="pendingCount === 0" type="warning">
              <el-button text type="primary" size="small">查看全部</el-button>
            </el-badge>
          </div>
          
          <div class="applications-list" v-if="myApplications.length > 0">
            <div v-for="item in myApplications" :key="item.id" class="application-item">
              <div class="application-header">
                <div class="application-skill">{{ item.skillName }}</div>
                <el-tag :type="getStatusType(item.status)" size="small">
                  {{ getStatusLabel(item.status) }}
                </el-tag>
              </div>
              <div class="application-time">
                <el-icon><Clock /></el-icon>
                {{ item.time }}
              </div>
              <div v-if="item.rejectedReason" class="application-reason">
                <el-icon><Warning /></el-icon>
                {{ item.rejectedReason }}
              </div>
            </div>
          </div>
          
          <el-empty v-else description="暂无申请记录" :image-size="100" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="applyVisible" title="申请权限" width="500px" class="apply-dialog">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="技能名称">
          <el-input :value="applyForm.skillName" disabled />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-input :value="applyForm.department" disabled />
        </el-form-item>
        <el-form-item label="申请理由" required>
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请详细说明申请该技能权限的理由..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitApply" :loading="loading">
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { applyPermission, getMyApplications } from '@/api/permission'
import { getSkillList } from '@/api/skill'
import { getDepartmentList } from '@/api/department'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const searchKeyword = ref('')
const filterType = ref('')
const filterPermission = ref('')
const applyVisible = ref(false)
const userStore = useUserStore()

const availableSkills = ref<any[]>([])
const myApplications = ref<any[]>([])
const loading = ref(false)
const departmentMap = ref<Map<number, string>>(new Map())

const applyForm = reactive({
  skillId: '',
  skillName: '',
  department: '',
  reason: ''
})

const publicSkills = computed(() => 
  availableSkills.value.filter(s => s.permissionLevel === 'PUBLIC')
)

const departmentSkills = computed(() => 
  availableSkills.value.filter(s => s.permissionLevel === 'DEPARTMENT')
)

const personalSkills = computed(() => 
  availableSkills.value.filter(s => s.permissionLevel === 'PRIVATE')
)

const filteredSkills = computed(() => {
  let skills = availableSkills.value
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    skills = skills.filter(skill => 
      skill.name.toLowerCase().includes(keyword) ||
      skill.description?.toLowerCase().includes(keyword)
    )
  }
  
  if (filterType.value) {
    skills = skills.filter(skill => skill.type === filterType.value)
  }
  
  if (filterPermission.value) {
    skills = skills.filter(skill => skill.permissionLevel === filterPermission.value)
  }
  
  return skills
})

const publicSkillsFiltered = computed(() => 
  filteredSkills.value.filter(s => s.permissionLevel === 'PUBLIC')
)

const departmentSkillsFiltered = computed(() => 
  filteredSkills.value.filter(s => s.permissionLevel === 'DEPARTMENT')
)

const personalSkillsFiltered = computed(() => 
  filteredSkills.value.filter(s => s.permissionLevel === 'PRIVATE')
)

const pendingCount = computed(() => 
  myApplications.value.filter(app => app.status === 'pending').length
)

function getStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    approved: 'success',
    pending: 'warning',
    rejected: 'danger'
  }
  return types[status] || 'info'
}

function getStatusLabel(status: string) {
  const labels: Record<string, string> = {
    approved: '已批准',
    pending: '待审批',
    rejected: '已拒绝'
  }
  return labels[status] || status
}

function handleApply(skill: any) {
  applyForm.skillId = skill.id
  applyForm.skillName = skill.name
  applyForm.department = skill.department
  applyForm.reason = ''
  applyVisible.value = true
}

async function handleSubmitApply() {
  if (!applyForm.reason) {
    ElMessage.warning('请填写申请理由')
    return
  }
  
  loading.value = true
  try {
    await applyPermission({
      skillId: applyForm.skillId,
      userId: userStore.userInfo?.id || '',
      accessLevel: 'read',
      reason: applyForm.reason
    })
    
    applyVisible.value = false
    ElMessage.success('申请已提交，请等待审批')
    
    const skill = availableSkills.value.find(s => s.id === applyForm.skillId)
    if (skill) {
      skill.applied = true
    }
    
    loadMyApplications()
  } catch (error) {
    console.error('申请提交失败:', error)
    ElMessage.error('申请提交失败')
  } finally {
    loading.value = false
  }
}

async function loadAvailableSkills() {
  loading.value = true
  try {
    const [publicRes, deptRes, personalRes] = await Promise.all([
      getSkillList({ permissionLevel: 'PUBLIC', current: 1, size: 100 }),
      getSkillList({ permissionLevel: 'DEPARTMENT', current: 1, size: 100 }),
      getSkillList({ permissionLevel: 'PRIVATE', current: 1, size: 100 })
    ])
    
    const appliedSkillIds = new Set(
      myApplications.value
        .filter(app => app.status === 'pending' || app.status === 'approved')
        .map(app => app.skillId)
    )
    
    const mapSkills = (records: any[], level: string) => 
      (records || []).map((skill: any) => ({
        id: skill.id,
        name: skill.name,
        type: skill.type,
        permissionLevel: level,
        department: departmentMap.value.get(skill.deptId) || (skill.deptId ? `部门${skill.deptId}` : '公共部门'),
        description: skill.description,
        applied: appliedSkillIds.has(skill.id),
        needsApproval: level === 'DEPARTMENT'
      }))
    
    const publicSkills = mapSkills(publicRes.data?.records, 'PUBLIC')
    const deptSkills = mapSkills(deptRes.data?.records, 'DEPARTMENT')
    const personalSkills = mapSkills(personalRes.data?.records, 'PRIVATE')
    
    availableSkills.value = [...publicSkills, ...deptSkills, ...personalSkills]
  } catch (error) {
    console.error('加载可申请技能失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const response = await getDepartmentList({ size: 100 })
    if (response.data?.records) {
      response.data.records.forEach((dept: any) => {
        departmentMap.value.set(dept.id, dept.name)
      })
    }
  } catch (error) {
    console.error('加载部门列表失败:', error)
  }
}

async function loadMyApplications() {
  try {
    const response = await getMyApplications(userStore.userInfo?.id || '')
    if (response.data) {
      myApplications.value = response.data.map((item: any) => ({
        id: item.id,
        skillId: item.skillId,
        skillName: item.skillName || item.skillId,
        status: item.status,
        time: dayjs(item.appliedAt).format('YYYY-MM-DD HH:mm'),
        reason: item.reason,
        rejectedReason: item.rejectedReason
      }))
    }
  } catch (error) {
    console.error('加载我的申请失败:', error)
  }
}

onMounted(async () => {
  await loadDepartments()
  await loadMyApplications()
  await loadAvailableSkills()
})
</script>

<style scoped lang="scss">
.permission-access-page {
  .page-header {
    margin-bottom: 24px;
    
    .page-title {
      font-size: 28px;
      font-weight: 700;
      color: #1f2d3d;
      margin: 0 0 8px;
      letter-spacing: -0.5px;
    }
    
    .page-subtitle {
      font-size: 14px;
      color: #8c929a;
      font-weight: 500;
    }
  }

  .stats-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
    
    .stat-card {
      background: #fff;
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      }
      
      .stat-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        flex-shrink: 0;
        
        &.public {
          background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
          color: #fff;
          box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
        }
        
        &.department {
          background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
          color: #fff;
          box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
        }
        
        &.personal {
          background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
          color: #fff;
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        }
        
        &.applications {
          background: linear-gradient(135deg, #909399 0%, #b4b4b4 100%);
          color: #fff;
          box-shadow: 0 4px 12px rgba(144, 147, 153, 0.3);
        }
      }
      
      .stat-content {
        flex: 1;
        
        .stat-value {
          font-size: 28px;
          font-weight: 700;
          color: #1f2d3d;
          line-height: 1.2;
        }
        
        .stat-label {
          font-size: 13px;
          color: #8c929a;
          font-weight: 500;
          margin-top: 4px;
        }
      }
    }
  }

  .search-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;
    flex-wrap: wrap;
    align-items: center;

    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
      }

      &.is-focus {
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }
    }

    :deep(.el-select) {
      .el-input__wrapper {
        border-radius: 8px;
      }
    }
  }

  .skills-section {
    .permission-group {
      margin-bottom: 32px;
      
      .group-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
        padding-bottom: 12px;
        border-bottom: 2px solid rgba(0, 0, 0, 0.06);
        
        .group-title {
          font-size: 18px;
          font-weight: 700;
          color: #1f2d3d;
          display: flex;
          align-items: center;
          gap: 10px;
          
          .group-icon {
            font-size: 20px;
            
            &.public {
              color: #67c23a;
            }
            
            &.department {
              color: #e6a23c;
            }
            
            &.personal {
              color: #409eff;
            }
          }
        }
      }
    }
  }

  .skill-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    margin-bottom: 16px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    border: 2px solid transparent;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
    cursor: pointer;

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
    }
    
    &.public {
      border-color: rgba(103, 194, 58, 0.2);
      
      &:hover {
        border-color: rgba(103, 194, 58, 0.4);
      }
    }
    
    &.department {
      border-color: rgba(230, 162, 60, 0.2);
      
      &:hover {
        border-color: rgba(230, 162, 60, 0.4);
      }
    }
    
    &.personal {
      border-color: rgba(64, 158, 255, 0.2);
      
      &:hover {
        border-color: rgba(64, 158, 255, 0.4);
      }
    }

    .skill-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 12px;

      .skill-icon {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-size: 20px;
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
      }

      .skill-type {
        padding: 4px 12px;
        border-radius: 20px;
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.3px;

        &.skill {
          background: rgba(64, 158, 255, 0.12);
          color: #409eff;
        }

        &.mcp {
          background: rgba(103, 194, 58, 0.12);
          color: #67c23a;
        }
      }
    }

    .skill-name {
      font-size: 16px;
      font-weight: 700;
      color: #1f2d3d;
      margin-bottom: 8px;
      letter-spacing: -0.2px;
    }

    .skill-desc {
      font-size: 13px;
      color: #5a5e66;
      margin-bottom: 16px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      min-height: 40px;
      line-height: 1.5;
    }

    .skill-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .skill-meta {
        .skill-dept {
          font-size: 12px;
          color: #8c929a;
          font-weight: 500;
        }
      }

      :deep(.el-button) {
        border-radius: 8px;
        font-weight: 600;
        
        .el-icon {
          margin-right: 4px;
        }
      }

      :deep(.el-tag) {
        .el-icon {
          margin-right: 4px;
        }
      }
    }
  }

  .applications-card {
    background: #fff;
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
    position: sticky;
    top: 20px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding-bottom: 16px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);

      h3 {
        font-size: 18px;
        font-weight: 700;
        margin: 0;
        color: #1f2d3d;
        letter-spacing: -0.3px;
      }
    }

    .applications-list {
      max-height: 600px;
      overflow-y: auto;
      
      &::-webkit-scrollbar {
        width: 6px;
      }
      
      &::-webkit-scrollbar-thumb {
        background: rgba(0, 0, 0, 0.1);
        border-radius: 3px;
        
        &:hover {
          background: rgba(0, 0, 0, 0.15);
        }
      }

      .application-item {
        padding: 16px;
        margin-bottom: 12px;
        border-radius: 10px;
        background: #f5f7fa;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

        &:hover {
          background: #eef1f6;
        }

        .application-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 8px;

          .application-skill {
            font-size: 14px;
            font-weight: 600;
            color: #1f2d3d;
          }
        }

        .application-time {
          font-size: 12px;
          color: #8c929a;
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .application-reason {
          font-size: 12px;
          color: #f56c6c;
          margin-top: 8px;
          padding: 8px 12px;
          background: rgba(245, 108, 108, 0.08);
          border-radius: 6px;
          display: flex;
          align-items: flex-start;
          gap: 6px;
          line-height: 1.5;
          
          .el-icon {
            flex-shrink: 0;
            margin-top: 2px;
          }
        }
      }
    }
  }

  :deep(.apply-dialog) {
    .el-dialog {
      border-radius: 16px;
      overflow: hidden;

      .el-dialog__header {
        padding: 20px 24px;
        border-bottom: 1px solid rgba(0, 0, 0, 0.06);

        .el-dialog__title {
          font-size: 18px;
          font-weight: 700;
          color: #1f2d3d;
        }
      }

      .el-dialog__body {
        padding: 24px;
      }

      .el-dialog__footer {
        padding: 16px 24px;
        border-top: 1px solid rgba(0, 0, 0, 0.06);
      }
    }

    .el-form-item__label {
      font-weight: 600;
      color: #5a5e66;
    }

    .el-textarea__inner {
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
      }

      &:focus {
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }
    }
  }

  :deep(.el-tag) {
    border-radius: 20px;
    font-weight: 600;
    
    .el-icon {
      font-size: 12px;
    }
  }

  :deep(.el-empty) {
    padding: 40px 0;
  }
}
</style>
