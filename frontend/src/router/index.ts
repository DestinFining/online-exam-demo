import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '@/layouts/WorkspaceLayout.vue'

/**
 * Route level code splitting: every view is a dynamic import, so the initial
 * bundle carries only the shell, the router and the auth/login path. The chart
 * heavy statistics pages (ECharts) and the table heavy admin pages are fetched
 * on first visit instead of on every page load.
 */
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const DashboardView = () => import('@/views/DashboardView.vue')
const SystemOverviewView = () => import('@/views/SystemOverviewView.vue')
const QuestionBankView = () => import('@/views/QuestionBankView.vue')
const PracticeView = () => import('@/views/PracticeView.vue')
const WrongQuestionsView = () => import('@/views/WrongQuestionsView.vue')
const FavoritesView = () => import('@/views/FavoritesView.vue')
const NotesView = () => import('@/views/NotesView.vue')
const PapersView = () => import('@/views/PapersView.vue')
const ExamsView = () => import('@/views/ExamsView.vue')
const ExamTakeView = () => import('@/views/ExamTakeView.vue')
const GradesView = () => import('@/views/GradesView.vue')
const StatisticsView = () => import('@/views/StatisticsView.vue')
const SubjectsView = () => import('@/views/SubjectsView.vue')
const KnowledgeView = () => import('@/views/KnowledgeView.vue')
const UsersView = () => import('@/views/UsersView.vue')
const TeacherStatisticsView = () => import('@/views/TeacherStatisticsView.vue')
const NotFoundView = () => import('@/views/NotFoundView.vue')

const routes = [
  { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
  { path: '/register', component: RegisterView, meta: { public: true, title: '注册' } },
  {
    path: '/', component: WorkspaceLayout, redirect: '/dashboard', children: [
      { path: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
      { path: 'questions', component: QuestionBankView, meta: { title: '题库管理', roles: ['TEACHER', 'ADMIN'] } },
      { path: 'practice', component: PracticeView, meta: { title: '在线刷题', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'wrong-questions', component: WrongQuestionsView, meta: { title: '我的错题', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'favorites', component: FavoritesView, meta: { title: '我的收藏', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'notes', component: NotesView, meta: { title: '题目笔记', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'papers', component: PapersView, meta: { title: '试卷管理', roles: ['TEACHER', 'ADMIN'] } },
      { path: 'exams', component: ExamsView, meta: { title: '考试中心', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'exams/:id/take', component: ExamTakeView, meta: { title: '在线考试', roles: ['STUDENT'] } },
      { path: 'grades', component: GradesView, meta: { title: '成绩与阅卷', roles: ['STUDENT', 'TEACHER', 'ADMIN'] } },
      { path: 'statistics', component: StatisticsView, meta: { title: '学情分析', roles: ['STUDENT'] } },
      { path: 'teacher-statistics', component: TeacherStatisticsView, meta: { title: '教师统计分析', roles: ['TEACHER', 'ADMIN'] } },
      { path: 'users', component: UsersView, meta: { title: '用户管理', roles: ['ADMIN'] } },
      { path: 'subjects', component: SubjectsView, meta: { title: '学科管理', roles: ['ADMIN'] } },
      { path: 'knowledge-points', component: KnowledgeView, meta: { title: '知识点管理', roles: ['ADMIN', 'TEACHER'] } },
      { path: 'system', component: SystemOverviewView, meta: { title: '系统概览', roles: ['ADMIN'] } },
    ],
  },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { public: true, title: '页面不存在' } },
]

const router = createRouter({ history: createWebHistory(), routes })

/** The stored profile is written by the auth store; a corrupted value must not break routing. */
function storedProfile(): { roles?: string[] } | null {
  const raw = sessionStorage.getItem('oe_profile')
  if (!raw) return null
  try {
    return JSON.parse(raw) as { roles?: string[] }
  } catch {
    sessionStorage.removeItem('oe_profile')
    return null
  }
}

router.beforeEach((to) => {
  const token = sessionStorage.getItem('oe_access_token')
  const user = storedProfile()
  if (to.meta.public) {
    if (token && (to.path === '/login' || to.path === '/register')) return '/dashboard'
    return true
  }
  if (!token) return { path: '/login', query: { redirect: to.fullPath } }
  const required = (to.meta.roles as string[] | undefined)
  if (required?.length && !required.some((role) => user?.roles?.includes(role))) {
    // Redirecting silently made a forbidden page look like a dead link, so say why.
    ElMessage.warning('当前账号无权访问该页面，已返回工作台')
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '工作台')} · 学知在线考试`
})

export default router
