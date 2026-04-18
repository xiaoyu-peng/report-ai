import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const permissions = ref<string[]>(JSON.parse(localStorage.getItem('permissions') || '[]'))
  const roles = ref<string[]>(JSON.parse(localStorage.getItem('roles') || '[]'))

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const tenantId = computed(() => userInfo.value?.tenantId || '')
  const deptId = computed(() => userInfo.value?.deptId || '')
  const userId = computed(() => userInfo.value?.id || '')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setPermissions(perms: string[]) {
    permissions.value = perms
    localStorage.setItem('permissions', JSON.stringify(perms))
  }

  function setRoles(r: string[]) {
    roles.value = r
    localStorage.setItem('roles', JSON.stringify(r))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    roles.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
    localStorage.removeItem('roles')
  }

  function hasPermission(permission: string): boolean {
    return permissions.value.includes('*') || permissions.value.includes(permission)
  }

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  return {
    token,
    userInfo,
    permissions,
    roles,
    isLoggedIn,
    username,
    avatar,
    tenantId,
    deptId,
    userId,
    setToken,
    setUserInfo,
    setPermissions,
    setRoles,
    logout,
    hasPermission,
    hasRole
  }
})
