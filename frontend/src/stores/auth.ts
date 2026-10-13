import { defineStore } from 'pinia'
import { authApi } from '@/api/modules'
import type { Role, UserProfile } from '@/types'

interface LoginPayload { username: string; password: string }

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('oe_access_token') || '',
    user: (JSON.parse(sessionStorage.getItem('oe_profile') || 'null') as UserProfile | null),
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    roles: (state): Role[] => (state.user?.roles || []) as Role[],
    displayName: (state) => state.user?.nickname || state.user?.username || '用户',
  },
  actions: {
    async login(payload: LoginPayload) {
      const result = await authApi.login(payload)
      this.token = result.accessToken
      sessionStorage.setItem('oe_access_token', result.accessToken)
      this.user = {
        userId: result.userId,
        username: result.username,
        nickname: result.nickname,
        roles: result.roles as Role[],
      }
      sessionStorage.setItem('oe_profile', JSON.stringify(this.user))
      return this.user
    },
    async refreshProfile() {
      const profile = await authApi.me()
      this.user = profile
      sessionStorage.setItem('oe_profile', JSON.stringify(profile))
      return profile
    },
    async register(payload: { username: string; password: string; confirmPassword: string; nickname: string; email?: string; phone?: string }) {
      return authApi.register(payload)
    },
    async logout() {
      // Revoke the token server-side first, otherwise a copied token keeps working
      // until it expires. A failure here must not trap the user in a logged-in
      // shell, so the local session is cleared either way.
      try {
        if (this.token) await authApi.logout()
      } catch {
        // Reported by the API layer; the local session is cleared regardless.
      }
      this.token = ''
      this.user = null
      sessionStorage.removeItem('oe_access_token')
      sessionStorage.removeItem('oe_profile')
      sessionStorage.removeItem('oe_exam_cache')
    },
  },
})
