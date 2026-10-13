<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Calendar, Collection, DataAnalysis, Files, Refresh, Setting, Tickets,
  UserFilled, Reading, Notebook, ArrowRight, TrendCharts,
} from '@element-plus/icons-vue'
import {
  examApi, knowledgeApi, paperApi, questionApi, subjectApi, userApi,
} from '@/api/modules'

const router = useRouter()
const loading = ref(false)
const refreshedAt = ref('尚未刷新')
const summary = ref({
  users: null as number | null,
  questions: null as number | null,
  papers: null as number | null,
  exams: null as number | null,
  subjects: null as number | null,
  knowledgePoints: null as number | null,
})

async function safe<T>(request: Promise<T>): Promise<T | null> {
  try { return await request } catch { return null }
}

async function load() {
  loading.value = true
  const [users, questions, papers, exams, subjects, knowledgePoints] = await Promise.all([
    safe(userApi.page({ page: 1, size: 1 })),
    safe(questionApi.page({ page: 1, size: 1 })),
    safe(paperApi.page({ page: 1, size: 1 })),
    safe(examApi.page({ page: 1, size: 1 })),
    safe(subjectApi.list()),
    safe(knowledgeApi.list()),
  ])
  summary.value = {
    users: users?.total ?? null,
    questions: questions?.total ?? null,
    papers: papers?.total ?? null,
    exams: exams?.total ?? null,
    subjects: subjects?.length ?? null,
    knowledgePoints: knowledgePoints?.length ?? null,
  }
  refreshedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  loading.value = false
}

const metrics = [
  { key: 'users', title: '用户总量', unit: '人', description: '平台账号记录', icon: UserFilled, tone: 'blue' },
  { key: 'questions', title: '题库题目', unit: '题', description: '题库管理接口统计', icon: Collection, tone: 'purple' },
  { key: 'papers', title: '试卷总量', unit: '份', description: '包含固定与规则试卷', icon: Reading, tone: 'green' },
  { key: 'exams', title: '考试总量', unit: '场', description: '考试管理接口统计', icon: Calendar, tone: 'orange' },
  { key: 'subjects', title: '学科数量', unit: '个', description: '平台学科目录', icon: Files, tone: 'blue' },
  { key: 'knowledgePoints', title: '知识点数量', unit: '个', description: '平台知识点目录', icon: Notebook, tone: 'purple' },
] as const

const modules = [
  { title: '用户与权限', description: '管理账号、角色和可用状态', path: '/users', icon: UserFilled, badge: '账户安全' },
  { title: '题库维护', description: '维护题目、题型与导入导出', path: '/questions', icon: Collection, badge: '内容资源' },
  { title: '学科与知识点', description: '维护课程目录及章节知识结构', path: '/subjects', icon: Files, badge: '课程结构' },
  { title: '试卷管理', description: '维护试卷、组卷规则和题目配置', path: '/papers', icon: Tickets, badge: '考试资源' },
  { title: '考试管理', description: '管理考试安排、发布与归档', path: '/exams', icon: Calendar, badge: '考试安排' },
  { title: '教师统计分析', description: '查看成绩分布和试卷难度分析', path: '/teacher-statistics', icon: DataAnalysis, badge: '业务分析' },
]

onMounted(() => { void load() })
</script>

<template>
  <div class="page-content system-overview-page" v-loading="loading">
    <div class="page-title-row">
      <div>
        <div class="eyebrow">SYSTEM OVERVIEW</div>
        <h1>系统概览</h1>
        <p>集中查看平台资源规模与管理模块。这里用于系统管理汇总，不重复展示工作台的欢迎信息、快捷任务和考试动态。</p>
      </div>
      <div class="title-actions">
        <el-tag type="info" effect="plain"><el-icon><Setting /></el-icon> 管理员视图</el-tag>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新数据</el-button>
      </div>
    </div>

    <section class="system-overview-hero">
      <div class="system-overview-hero-copy">
        <span class="system-overview-kicker">PLATFORM RESOURCES</span>
        <h2>平台资源总览</h2>
        <p>以下数字来自当前系统管理接口。接口暂不可用时显示“—”，不会用演示数据填充。</p>
      </div>
      <div class="system-overview-hero-side">
        <div class="system-overview-hero-icon"><el-icon><TrendCharts /></el-icon></div>
        <div><strong>数据更新时间</strong><span>{{ refreshedAt }}</span></div>
      </div>
    </section>

    <div class="system-overview-metrics">
      <div v-for="item in metrics" :key="item.key" class="metric-card system-overview-metric">
        <div class="metric-top">
          <span>{{ item.title }}</span>
          <span class="metric-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        </div>
        <div class="metric-value">{{ summary[item.key] == null ? '—' : summary[item.key] }}<small v-if="summary[item.key] != null">{{ item.unit }}</small></div>
        <div class="metric-foot">{{ item.description }}</div>
      </div>
    </div>

    <div class="system-overview-section-title">
      <div><h2>管理模块</h2><p>选择模块进入对应的系统管理页面。</p></div>
      <el-tag effect="plain">{{ modules.length }} 个入口</el-tag>
    </div>
    <div class="system-overview-modules">
      <button v-for="item in modules" :key="item.path" class="system-module-card" @click="router.push(item.path)">
        <span class="system-module-icon"><el-icon><component :is="item.icon" /></el-icon></span>
        <span class="system-module-copy"><small>{{ item.badge }}</small><strong>{{ item.title }}</strong><span>{{ item.description }}</span></span>
        <el-icon class="system-module-arrow"><ArrowRight /></el-icon>
      </button>
    </div>

    <section class="surface-card system-overview-note">
      <div class="system-overview-note-icon"><el-icon><DataAnalysis /></el-icon></div>
      <div>
        <strong>统计口径说明</strong>
        <p>用户、题目、试卷和考试总量来自对应管理 API；学科与知识点数量来自目录 API。该页面展示的是业务数据概览，不等同于服务器、数据库或 Redis 的实时健康监控。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.system-overview-page { gap: 22px; }
.system-overview-hero { display:flex; align-items:center; justify-content:space-between; gap:24px; padding:25px 28px; border:1px solid #e4eaff; border-radius:18px; background:linear-gradient(120deg,#f5f7ff 0%,#fbfcff 58%,#eef8f7 100%); }
.system-overview-hero-copy { min-width:0; }
.system-overview-kicker { color:#5973d8; font-size:10px; font-weight:800; letter-spacing:1.6px; }
.system-overview-hero h2 { margin:8px 0 7px; color:#202d48; font-size:21px; }
.system-overview-hero p { margin:0; max-width:670px; color:#78859b; font-size:12px; line-height:1.8; }
.system-overview-hero-side { display:flex; align-items:center; gap:12px; flex-shrink:0; padding:12px 15px; border:1px solid rgba(210,220,244,.85); border-radius:13px; background:rgba(255,255,255,.75); }
.system-overview-hero-icon { display:grid; place-items:center; width:39px; height:39px; border-radius:11px; background:#e9eeff; color:#5871dc; font-size:19px; }
.system-overview-hero-side strong,.system-overview-hero-side span { display:block; }
.system-overview-hero-side strong { margin-bottom:5px; color:#53617b; font-size:11px; }
.system-overview-hero-side span { color:#8793a7; font-size:11px; }
.system-overview-metrics { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
.system-overview-metric { min-width:0; }
.system-overview-section-title { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-top:2px; }
.system-overview-section-title h2 { margin:0 0 5px; color:#25324b; font-size:17px; }
.system-overview-section-title p { margin:0; color:#8a96aa; font-size:11px; }
.system-overview-modules { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:13px; }
.system-module-card { display:flex; align-items:flex-start; gap:13px; width:100%; min-width:0; padding:18px; border:1px solid #e9edf5; border-radius:15px; background:#fff; text-align:left; cursor:pointer; transition:transform .18s ease,border-color .18s ease,box-shadow .18s ease; }
.system-module-card:hover { transform:translateY(-2px); border-color:#cfd9ff; box-shadow:0 9px 24px rgba(48,69,119,.07); }
.system-module-icon { display:grid; place-items:center; width:40px; height:40px; flex:0 0 40px; border-radius:12px; background:#eef2ff; color:#536fdf; font-size:18px; }
.system-module-copy { display:flex; flex:1; flex-direction:column; gap:6px; min-width:0; }
.system-module-copy small { color:#7c8ee0; font-size:9px; font-weight:800; letter-spacing:.5px; }
.system-module-copy strong { color:#2b3852; font-size:13px; }
.system-module-copy>span { color:#8c97aa; font-size:11px; line-height:1.7; }
.system-module-arrow { margin:2px 0 0 auto; color:#a1abc0; font-size:14px; }
.system-overview-note { display:flex; align-items:flex-start; gap:13px; padding:18px 20px; }
.system-overview-note-icon { display:grid; place-items:center; width:36px; height:36px; flex:0 0 36px; border-radius:11px; background:#edf8f5; color:#219b7e; font-size:17px; }
.system-overview-note strong { color:#43516b; font-size:12px; }
.system-overview-note p { margin:6px 0 0; color:#8994a7; font-size:11px; line-height:1.8; }
@media(max-width:1050px) { .system-overview-metrics,.system-overview-modules { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:700px) { .system-overview-page { gap:16px; } .system-overview-hero { align-items:flex-start; flex-direction:column; padding:20px; } .system-overview-hero-side { width:100%; box-sizing:border-box; } .system-overview-metrics,.system-overview-modules { grid-template-columns:1fr; } .system-overview-section-title { align-items:flex-start; } .system-overview-note { padding:15px; } }
</style>
