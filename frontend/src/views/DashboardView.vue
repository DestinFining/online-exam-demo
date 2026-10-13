<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Collection, DataAnalysis, Reading, TrendCharts, Trophy, UserFilled, TopRight, CircleCheckFilled, Timer } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { examApi, paperApi, questionApi, statisticsApi } from '@/api/modules'
import type { Exam, PageResult, Paper, Question, StudentStatistics } from '@/types'

const auth = useAuthStore()
const router = useRouter()
const role = computed(() => auth.roles.includes('ADMIN') ? 'ADMIN' : auth.roles.includes('TEACHER') ? 'TEACHER' : auth.roles[0] || 'STUDENT')
const stats = ref<StudentStatistics | null>(null)
const exams = ref<Exam[]>([])
const paperCount = ref(0)
const questionCount = ref(0)
const loading = ref(false)
const isStudent = computed(() => role.value === 'STUDENT')
const quickActions = computed(() => isStudent.value ? [
  { title: '开始刷题', description: '按章节和知识点巩固基础', icon: Reading, path: '/practice', tone: 'blue' },
  { title: '参加考试', description: '查看考试安排并在线作答', icon: Calendar, path: '/exams', tone: 'violet' },
  { title: '复习错题', description: '回顾薄弱知识点与易错题', icon: Collection, path: '/wrong-questions', tone: 'amber' },
] : [
  { title: '题库管理', description: '录入、维护和组织题目', icon: Collection, path: '/questions', tone: 'blue' },
  { title: '创建试卷', description: '固定选题或按规则动态组卷', icon: Reading, path: '/papers', tone: 'violet' },
  { title: '考试与阅卷', description: '发布考试并处理提交答卷', icon: Calendar, path: '/exams', tone: 'amber' },
])
async function load() {
  loading.value = true
  const tasks: Promise<unknown>[] = [
    examApi.page().then((r) => { exams.value = r.records || [] }).catch(() => undefined),
    paperApi.page().then((r) => { paperCount.value = r.total || r.records?.length || 0 }).catch(() => undefined),
    questionApi.page({ size: 1 }).then((r) => { questionCount.value = r.total || 0 }).catch(() => undefined),
  ]
  if (isStudent.value) {
    tasks.push(statisticsApi.student().then((r) => { stats.value = r }).catch(() => undefined))
  }
  await Promise.all(tasks)
  loading.value = false
}
const greeting = computed(() => { const hour = new Date().getHours(); return hour < 11 ? '早上好' : hour < 18 ? '下午好' : '晚上好' })
const upcomingExams = computed(() => exams.value.filter((e) => e.status === 'PUBLISHED').slice(0, 3))
onMounted(load)
</script>

<template>
  <div class="page-content" v-loading="loading">
    <div class="welcome-banner">
      <div class="welcome-copy"><div class="eyebrow-light">LEARN WITH PURPOSE</div><h1>{{ greeting }}，{{ auth.displayName }} <span class="wave">✦</span></h1><p>{{ isStudent ? '每一道题都是一次进步的机会。准备好继续今天的学习了吗？' : role === 'TEACHER' ? '课程内容、试卷与考试都在这里有序管理。' : '欢迎进入平台管理中心，查看系统模块与运行状态。' }}</p><div class="welcome-actions"><el-button type="primary" @click="router.push(isStudent ? '/practice' : '/questions')">{{ isStudent ? '继续学习' : '进入管理' }} <el-icon><TopRight /></el-icon></el-button><el-button class="banner-ghost" @click="router.push('/exams')">查看考试安排</el-button></div></div>
      <div class="welcome-illustration"><div class="illus-orbit orbit-a"></div><div class="illus-orbit orbit-b"></div><div class="illus-paper"><div class="illus-paper-top"><span></span><span></span><span></span></div><div class="illus-check"><el-icon><CircleCheckFilled /></el-icon></div><div class="illus-line long"></div><div class="illus-line"></div><div class="illus-line short"></div><div class="illus-progress"><span></span></div></div><div class="illus-bubble bubble-one"><el-icon><Trophy /></el-icon></div><div class="illus-bubble bubble-two"><el-icon><TrendCharts /></el-icon></div></div>
    </div>

    <div class="section-head"><div><h2>{{ isStudent ? '学习概览' : '平台概览' }}</h2><p>{{ isStudent ? '用数据看见自己的每一步进展' : '从核心业务入口开始今天的工作' }}</p></div><span class="updated-hint"><i></i> 数据按需加载</span></div>
    <div class="stats-grid" v-if="isStudent">
      <div class="metric-card"><div class="metric-top"><span>累计刷题</span><span class="metric-icon blue"><el-icon><Reading /></el-icon></span></div><div class="metric-value">{{ stats?.totalPractice ?? '—' }}<small>题</small></div><div class="metric-foot">已记录的练习次数</div><div class="metric-decoration blue-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>练习正确率</span><span class="metric-icon green"><el-icon><CircleCheckFilled /></el-icon></span></div><div class="metric-value">{{ Number(stats?.practiceAccuracy || 0).toFixed(1) }}<small>%</small></div><div class="metric-foot">基于已提交练习统计</div><div class="metric-decoration green-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>待复习错题</span><span class="metric-icon orange"><el-icon><Collection /></el-icon></span></div><div class="metric-value">{{ stats?.wrongQuestionCount ?? '—' }}<small>题</small></div><div class="metric-foot">从薄弱项开始复盘</div><div class="metric-decoration orange-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>已完成考试</span><span class="metric-icon purple"><el-icon><Trophy /></el-icon></span></div><div class="metric-value">{{ stats?.examCount ?? '—' }}<small>场</small></div><div class="metric-foot">最高成绩 {{ Number(stats?.highestScore || 0).toFixed(1) }} 分</div><div class="metric-decoration purple-line"></div></div>
    </div>
    <div class="stats-grid" v-else>
      <div class="metric-card"><div class="metric-top"><span>题库题目</span><span class="metric-icon blue"><el-icon><Collection /></el-icon></span></div><div class="metric-value">{{ questionCount }}<small>题</small></div><div class="metric-foot">已接入题库查询接口</div><div class="metric-decoration blue-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>试卷总数</span><span class="metric-icon purple"><el-icon><Reading /></el-icon></span></div><div class="metric-value">{{ paperCount }}<small>份</small></div><div class="metric-foot">包含固定试卷与规则试卷</div><div class="metric-decoration purple-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>考试记录</span><span class="metric-icon green"><el-icon><Calendar /></el-icon></span></div><div class="metric-value">{{ exams.length }}<small>场</small></div><div class="metric-foot">当前可访问的考试记录</div><div class="metric-decoration green-line"></div></div>
      <div class="metric-card"><div class="metric-top"><span>当前角色</span><span class="metric-icon orange"><el-icon><UserFilled /></el-icon></span></div><div class="metric-value role-value">{{ role === 'ADMIN' ? '管理员' : '教师' }}</div><div class="metric-foot">页面权限由后端 RBAC 决定</div><div class="metric-decoration orange-line"></div></div>
    </div>

    <div class="dashboard-columns">
      <section class="surface-card action-card"><div class="card-heading"><div><h3>快捷入口</h3><p>从常用任务开始</p></div><span class="card-heading-icon"><el-icon><TopRight /></el-icon></span></div><div class="quick-action-list"><button v-for="(item, index) in quickActions" :key="item.path" class="quick-action" @click="router.push(item.path)"><span class="action-index">0{{ index + 1 }}</span><span class="quick-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span><span class="quick-text"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span><el-icon class="quick-arrow"><TopRight /></el-icon></button></div></section>
      <section class="surface-card exam-preview"><div class="card-heading"><div><h3>{{ isStudent ? '近期考试' : '考试动态' }}</h3><p>{{ isStudent ? '留意考试时间，提前做好准备' : '最近可访问的考试项目' }}</p></div><el-button text type="primary" @click="router.push('/exams')">全部考试 <el-icon><TopRight /></el-icon></el-button></div><div v-if="upcomingExams.length" class="exam-preview-list"><div v-for="exam in upcomingExams" :key="exam.id" class="exam-preview-row"><div class="exam-date-block"><span>{{ new Date(exam.startTime).getDate() }}</span><small>{{ new Date(exam.startTime).toLocaleDateString('zh-CN', { month: 'short' }) }}</small></div><div class="exam-preview-info"><strong>{{ exam.name }}</strong><small>{{ exam.duration }} 分钟 · 最多 {{ exam.maxAttempts }} 次</small></div><el-tag size="small" type="success" effect="light">已发布</el-tag></div></div><el-empty v-else description="暂时没有可显示的已发布考试" :image-size="72" /><div class="exam-preview-footer"><el-icon><Timer /></el-icon><span>考试时间、状态与权限均以服务端返回为准</span></div></section>
    </div>
    <div class="dashboard-note"><el-icon><DataAnalysis /></el-icon><span>提示：学生学情数据来自实际练习和已发布成绩；教师与管理员统计仅展示当前后端已提供的业务计数，不使用模拟数据。</span></div>
  </div>
</template>
