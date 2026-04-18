<template>
  <div class="admin-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapse }">
      <div class="sidebar-header">
        <div class="logo" @click="router.push('/dashboard')">
          <div class="logo-icon">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="url(#gradient)" />
              <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="gradient" x1="0" y1="0" x2="32" y2="32">
                  <stop stop-color="#6366f1"/>
                  <stop offset="1" stop-color="#8b5cf6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <transition name="fade-slide">
            <span v-if="!isCollapse" class="logo-text">ReportAI</span>
          </transition>
        </div>
        <button class="collapse-btn" @click="toggleCollapse">
          <el-icon :size="18">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
        </button>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section">
          <transition name="fade-slide">
            <span v-if="!isCollapse" class="nav-section-title">工作台</span>
          </transition>
          <ul class="nav-list">
            <li>
              <router-link to="/dashboard" class="nav-item" :class="{ active: activeMenu === '/dashboard' }">
                <el-icon :size="20"><Odometer /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">仪表盘</span>
                </transition>
              </router-link>
            </li>
            <li>
              <router-link to="/workspace" class="nav-item" :class="{ active: activeMenu === '/workspace' }">
                <el-icon :size="20"><EditPen /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">写作工作台</span>
                </transition>
              </router-link>
            </li>
          </ul>
        </div>

        <div class="nav-section">
          <transition name="fade-slide">
            <span v-if="!isCollapse" class="nav-section-title">内容中心</span>
          </transition>
          <ul class="nav-list">
            <li>
              <router-link to="/knowledge/list" class="nav-item" :class="{ active: activeMenu.startsWith('/knowledge') }">
                <el-icon :size="20"><List /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">知识库</span>
                </transition>
              </router-link>
            </li>
            <li>
              <router-link to="/templates" class="nav-item" :class="{ active: activeMenu === '/templates' }">
                <el-icon :size="20"><DocumentCopy /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">模板中心</span>
                </transition>
              </router-link>
            </li>
            <li>
              <router-link to="/reports" class="nav-item" :class="{ active: activeMenu === '/reports' }">
                <el-icon :size="20"><Folder /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">报告库</span>
                </transition>
              </router-link>
            </li>
          </ul>
        </div>

        <div class="nav-section">
          <transition name="fade-slide">
            <span v-if="!isCollapse" class="nav-section-title">用户管理</span>
          </transition>
          <ul class="nav-list">
            <li>
              <router-link to="/users/list" class="nav-item" :class="{ active: activeMenu === '/users/list' }">
                <el-icon :size="20"><User /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">用户列表</span>
                </transition>
              </router-link>
            </li>
            <li>
              <router-link to="/users/roles" class="nav-item" :class="{ active: activeMenu === '/users/roles' }">
                <el-icon :size="20"><Avatar /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">角色管理</span>
                </transition>
              </router-link>
            </li>
          </ul>
        </div>

        <div class="nav-section">
          <transition name="fade-slide">
            <span v-if="!isCollapse" class="nav-section-title">日志审计</span>
          </transition>
          <ul class="nav-list">
            <li>
              <router-link to="/logs/operation" class="nav-item" :class="{ active: activeMenu === '/logs/operation' }">
                <el-icon :size="20"><Tickets /></el-icon>
                <transition name="fade-slide">
                  <span v-if="!isCollapse">操作日志</span>
                </transition>
              </router-link>
            </li>
          </ul>
        </div>
      </nav>
    </aside>

    <div class="main-container">
      <header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">
              <el-icon :size="14"><HomeFilled /></el-icon>
            </el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-center">
          <div class="search-box">
            <el-icon :size="16" class="search-icon"><Search /></el-icon>
            <input 
              type="text" 
              v-model="searchKeyword"
              placeholder="搜索报告、模板、知识库..."
              class="search-input"
              @focus="searchFocused = true"
              @blur="searchFocused = false"
              @keyup.enter="handleSearch"
            />
            <kbd class="search-shortcut" v-show="!searchFocused">⌘K</kbd>
          </div>
        </div>
        <div class="header-right">
          <el-tooltip content="帮助文档" placement="bottom">
            <button class="header-btn">
              <el-icon :size="18"><QuestionFilled /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip :content="`通知 (${notifications})`" placement="bottom">
            <button class="header-btn notification-btn">
              <el-icon :size="18"><Bell /></el-icon>
              <span v-if="notifications" class="notification-dot">{{ notifications > 9 ? '9+' : notifications }}</span>
            </button>
          </el-tooltip>
          <el-dropdown trigger="click" placement="bottom-end">
            <div class="user-avatar-wrapper">
              <el-avatar :size="32" :src="userStore.avatar" class="header-avatar">
                {{ userStore.username?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="user-dropdown-header">
                  <el-avatar :size="40" :src="userStore.avatar">
                    {{ userStore.username?.charAt(0)?.toUpperCase() }}
                  </el-avatar>
                  <div class="user-dropdown-info">
                    <span class="user-dropdown-name">{{ userStore.username }}</span>
                    <span class="user-dropdown-role">{{ getRoleName() }}</span>
                  </div>
                </div>
                <el-dropdown-item divided command="profile" @click="handleCommand('profile')">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="settings" @click="handleCommand('settings')">
                  <el-icon><Setting /></el-icon>
                  账户设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" @click="handleCommand('logout')">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main">
        <router-view v-slot="{ Component, route }">
          <component :is="Component" :key="route.fullPath" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox, ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const notifications = ref(0)
const searchFocused = ref(false)
const searchKeyword = ref('')

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  return route.matched.filter(item => item.meta?.title && !item.meta?.hidden)
})

function toggleCollapse() {
  isCollapse.value = !isCollapse.value
}

function handleSearch() {
  router.push({
    path: '/reports',
    query: searchKeyword.value.trim() ? { keyword: searchKeyword.value.trim() } : {}
  })
  searchKeyword.value = ''
}

async function loadNotifications() {
  // ReportAI 暂无待办通知接口，保持占位
  notifications.value = 0
}

function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      ElMessage.info('个人中心开发中...')
      break
    case 'settings':
      ElMessage.info('账户设置开发中...')
      break
    case 'logout':
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        userStore.logout()
        router.push('/login')
        ElMessage.success('退出成功')
      }).catch(() => {})
      break
  }
}

function getRoleName(): string {
  const roles = userStore.roles
  if (roles.includes('ADMIN')) return '系统管理员'
  if (roles.includes('EDITOR')) return '报告编辑'
  if (roles.includes('VIEWER')) return '浏览者'
  return '普通用户'
}

onMounted(() => {
  loadNotifications()
})

onUnmounted(() => {
  // no-op
})
</script>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100vh;
  background: #f8fafc;
}

.sidebar {
  width: 220px;
  height: 100vh;
  background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 100;

  &.collapsed {
    width: 72px;

    .sidebar-header {
      padding: 16px 14px;
    }

    .logo {
      justify-content: center;
    }

    .collapse-btn {
      margin-left: 0;
    }

    .nav-item {
      justify-content: center;
      padding: 0;
    }

    .user-card {
      padding: 12px;
      justify-content: center;
    }

    .user-info {
      justify-content: center;
    }
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.9;
  }

  .logo-icon {
    width: 36px;
    height: 36px;
    flex-shrink: 0;

    svg {
      width: 100%;
      height: 100%;
    }
  }

  .logo-text {
    font-size: 18px;
    font-weight: 700;
    color: #fff;
    letter-spacing: -0.5px;
    white-space: nowrap;
  }
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  margin-left: auto;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    color: #fff;
  }
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 0;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 2px;
  }
}

.nav-section {
  margin-bottom: 8px;
}

.nav-section-title {
  display: block;
  padding: 8px 20px;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.nav-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  margin: 2px 8px;
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.06);
    color: rgba(255, 255, 255, 0.9);
  }

  &.active {
    background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
    color: #fff;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);

    &:hover {
      background: linear-gradient(135deg, #5558e3 0%, #7c4ff6 100%);
    }
  }

  span {
    white-space: nowrap;
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: visible;
}

.header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;

  :deep(.el-breadcrumb) {
    font-size: 14px;

    .el-breadcrumb__item {
      .el-breadcrumb__inner {
        color: #64748b;
        font-weight: 500;

        &.is-link:hover {
          color: #6366f1;
        }
      }

      &:last-child .el-breadcrumb__inner {
        color: #1e293b;
      }
    }
  }
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  max-width: 480px;
  margin: 0 24px;
}

.search-box {
  width: 100%;
  max-width: 400px;
  height: 40px;
  background: #f1f5f9;
  border: 1px solid transparent;
  border-radius: 10px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  transition: all 0.2s;

  &:focus-within {
    background: #fff;
    border-color: #6366f1;
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  }

  .search-icon {
    color: #94a3b8;
    flex-shrink: 0;
  }

  .search-input {
    flex: 1;
    border: none;
    background: transparent;
    outline: none;
    font-size: 14px;
    color: #1e293b;
    padding: 0 12px;

    &::placeholder {
      color: #94a3b8;
    }
  }

  .search-shortcut {
    padding: 4px 8px;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    font-size: 12px;
    color: #94a3b8;
    font-family: inherit;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: all 0.2s;
  
  &:hover {
    background: #f1f5f9;
  }
}

.header-avatar {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  font-weight: 600;
}

.dropdown-arrow {
  color: #64748b;
  font-size: 12px;
  transition: transform 0.2s;
}

.user-dropdown-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 8px;
  
  .el-avatar {
    background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
    color: #fff;
    font-weight: 600;
  }
}

.user-dropdown-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-dropdown-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.user-dropdown-role {
  font-size: 12px;
  color: #64748b;
}

.header-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: 10px;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  position: relative;

  &:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
}

.notification-btn {
  .notification-dot {
    position: absolute;
    top: 6px;
    right: 6px;
    min-width: 16px;
    height: 16px;
    background: #ef4444;
    color: #fff;
    font-size: 10px;
    font-weight: 600;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 4px;
  }
}

.main {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: #f8fafc;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.2s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

.page-enter-active,
.page-leave-active {
  transition: all 0.2s ease;
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
