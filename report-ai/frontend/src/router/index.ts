import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        redirect: '/knowledge/list',
        meta: { title: '知识库', icon: 'List' },
        children: [
          {
            path: 'list',
            name: 'KnowledgeList',
            component: () => import('@/views/knowledge/list.vue'),
            meta: { title: '知识库列表', icon: 'List' }
          },
          {
            path: ':id',
            name: 'KnowledgeDetail',
            component: () => import('@/views/knowledge/detail.vue'),
            meta: { title: '知识库详情', icon: 'Document', hidden: true }
          }
        ]
      },
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/workspace/index.vue'),
        meta: { title: '写作工作台', icon: 'EditPen' }
      },
      {
        path: 'templates',
        name: 'Templates',
        component: () => import('@/views/templates/index.vue'),
        meta: { title: '模板中心', icon: 'DocumentCopy' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/reports/index.vue'),
        meta: { title: '报告库', icon: 'Folder' }
      },
      {
        path: 'users',
        name: 'Users',
        redirect: '/users/list',
        meta: { title: '用户管理', icon: 'User' },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/views/users/list.vue'),
            meta: { title: '用户列表', icon: 'List' }
          },
          {
            path: 'roles',
            name: 'Roles',
            component: () => import('@/views/users/roles.vue'),
            meta: { title: '角色管理', icon: 'Avatar' }
          }
        ]
      },
      {
        path: 'logs',
        name: 'Logs',
        redirect: '/logs/operation',
        meta: { title: '日志管理', icon: 'Document' },
        children: [
          {
            path: 'operation',
            name: 'OperationLogs',
            component: () => import('@/views/logs/operation.vue'),
            meta: { title: '操作日志', icon: 'Tickets' }
          }
        ]
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.token || localStorage.getItem('token')

  if (to.meta.title) {
    document.title = `${to.meta.title} - ReportAI 智能报告写作平台`
  }

  if (to.path !== '/login' && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
