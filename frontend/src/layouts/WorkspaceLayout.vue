<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  HomeFilled, Collection, Reading, Notebook, Star, EditPen, Tickets, Calendar,
  DataAnalysis, UserFilled, Files, Operation, Setting, ArrowDown, Monitor,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const role = computed(() => auth.roles.includes('ADMIN') ? 'ADMIN' : auth.roles.includes('TEACHER') ? 'TEACHER' : auth.roles[0] || 'STUDENT')
const roleName = computed(() => ({ STUDENT: '学生空间', TEACHER: '教师工作台', ADMIN: '系统管理' }[role.value] || '在线学习'))
const menus = computed(() => {
  const common = [{ title: '工作台', path: '/dashboard', icon: HomeFilled }]
  if (role.value === 'STUDENT') return [...common,
    { title: '在线刷题', path: '/practice', icon: Reading },
    { title: '我的错题', path: '/wrong-questions', icon: Notebook },
    { title: '我的收藏', path: '/favorites', icon: Star },
    { title: '题目笔记', path: '/notes', icon: EditPen },
    { title: '考试中心', path: '/exams', icon: Calendar },
    { title: '成绩记录', path: '/grades', icon: Tickets },
    { title: '学情分析', path: '/statistics', icon: DataAnalysis },
  ]
  if (role.value === 'TEACHER') return [...common,
    { title: '题库管理', path: '/questions', icon: Collection },
    { title: '知识点管理', path: '/knowledge-points', icon: Files },
    { title: '试卷管理', path: '/papers', icon: Tickets },
    { title: '考试管理', path: '/exams', icon: Calendar },
    { title: '阅卷与成绩', path: '/grades', icon: EditPen },
    { title: '考试分析', path: '/teacher-statistics', icon: DataAnalysis },
  ]
  return [...common,
    { title: '题库管理', path: '/questions', icon: Collection },
    { title: '学科管理', path: '/subjects', icon: Files },
    { title: '知识点管理', path: '/knowledge-points', icon: Notebook },
    { title: '试卷管理', path: '/papers', icon: Tickets },
    { title: '考试管理', path: '/exams', icon: Calendar },
    { title: '阅卷与成绩', path: '/grades', icon: EditPen },
    { title: '考试分析', path: '/teacher-statistics', icon: DataAnalysis },
    { title: '用户管理', path: '/users', icon: UserFilled },
    { title: '系统概览', path: '/system', icon: Setting },
  ]
})
const activeTitle = computed(() => String(route.meta.title || '工作台'))
async function logout() {
  await auth.logout()
  router.replace('/login')
}
function handleCommand(command: string) {
  if (command === 'logout') logout()
  else router.push('/dashboard')
}
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <div class="brand-lockup">
        <div class="brand-mark"><el-icon><Reading /></el-icon></div>
        <div><div class="brand-name">学知</div><div class="brand-caption">ONLINE LEARNING</div></div>
      </div>
      <div class="workspace-label">{{ roleName }}</div>
      <nav class="side-menu">
        <router-link v-for="item in menus" :key="item.path" :to="item.path" class="menu-item" :class="{ active: route.path === item.path || (item.path !== '/dashboard' && route.path.startsWith(item.path)) }">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span>
          <span v-if="item.path === '/exams' && role === 'STUDENT'" class="menu-dot"></span>
        </router-link>
      </nav>
      <div class="sidebar-bottom">
        <div class="support-card"><div class="support-icon"><el-icon><Monitor /></el-icon></div><div><strong>学习无止境</strong><p>每天进步一点点</p></div></div>
        <div class="sidebar-footer">© 2026 学知在线考试平台</div>
      </div>
    </aside>
    <section class="main-shell">
      <header class="topbar">
        <div class="breadcrumb"><span>学知平台</span><span class="crumb-sep">/</span><strong>{{ activeTitle }}</strong></div>
        <div class="topbar-actions">
          <div class="system-status"><i></i> 系统运行正常</div>
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="profile-trigger">
              <div class="avatar">{{ (auth.displayName || '学').slice(0, 1) }}</div>
              <div class="profile-copy"><strong>{{ auth.displayName }}</strong><small>{{ role === 'ADMIN' ? '系统管理员' : role === 'TEACHER' ? '教师 / 出题人' : '学生 / 考生' }}</small></div>
              <el-icon class="profile-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人工作台</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <main class="page-stage"><router-view /></main>
    </section>
  </div>
</template>
