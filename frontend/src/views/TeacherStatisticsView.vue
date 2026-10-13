<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import echarts, { type EChartsInstance } from '@/plugins/echarts'
import { ElMessage } from 'element-plus'
import { statisticsApi, userApi, type AdminUser, type TeacherStatistics } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isAdmin = auth.roles.includes('ADMIN')
const loading = ref(false)
const data = ref<TeacherStatistics | null>(null)
const teachers = ref<AdminUser[]>([])
const selectedTeacherId = ref<number | undefined>()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsInstance | undefined
async function renderChart() {
  await nextTick()
  if (!chartEl.value || !data.value) return
  chart?.dispose(); chart = echarts.init(chartEl.value)
  chart.setOption({ tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (items: any[]) => { const item = items?.[0]; const band = data.value?.scoreDistribution?.[item?.dataIndex]; return `${band?.label || ''}<br/>人数：${band?.count ?? 0}<br/>占比：${Number(band?.percentage || 0).toFixed(1)}%` } }, grid: { left: 28, right: 20, top: 24, bottom: 30, containLabel: true }, xAxis: { type: 'category', data: data.value.scoreDistribution.map(item => item.label), axisLabel: { color: '#7b879d' }, axisLine: { lineStyle: { color: '#d8dfed' } } }, yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#7b879d' }, splitLine: { lineStyle: { color: '#edf1f7', type: 'dashed' } } }, series: [{ type: 'bar', data: data.value.scoreDistribution.map(item => item.count), barMaxWidth: 46, itemStyle: { borderRadius: [7, 7, 0, 0], color: '#4c6ef5' }, label: { show: true, position: 'top', color: '#53627e' } }] })
}
async function load() {
  loading.value = true
  try { data.value = await statisticsApi.teacher(selectedTeacherId.value); await renderChart() } catch { /* API layer reports errors */ } finally { loading.value = false }
}
async function init() {
  if (isAdmin) { try { const page = await userApi.page({ page: 1, size: 100, roleCode: 'TEACHER', status: 1 }); teachers.value = page.records || [] } catch { /* API layer reports errors */ } }
  await load()
}
function resize() { chart?.resize() }
onMounted(() => { void init(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template>
  <div class="page-content" v-loading="loading">
    <div class="page-title-row"><div><div class="eyebrow">TEACHING ANALYTICS</div><h1>考试统计分析</h1><p>查看成绩分布和各试卷难度，帮助教师评估题目质量与考试效果。</p></div><div class="title-actions"><el-select v-if="isAdmin" v-model="selectedTeacherId" clearable placeholder="全部教师" style="width: 220px" @change="load"><el-option v-for="teacher in teachers" :key="teacher.id" :label="`${teacher.nickname || teacher.username}（${teacher.username}）`" :value="teacher.id"/></el-select><el-button @click="load">刷新统计</el-button></div></div>
    <div class="stats-grid"><div class="metric-card"><div class="metric-top"><span>统计范围</span><span class="metric-icon blue">01</span></div><div class="metric-value metric-text">{{ data?.teacherScope || '—' }}</div><div class="metric-foot">{{ isAdmin && !selectedTeacherId ? '聚合全部教师创建的数据' : '当前教师创建的数据' }}</div></div><div class="metric-card"><div class="metric-top"><span>试卷数量</span><span class="metric-icon purple">02</span></div><div class="metric-value">{{ data?.paperCount ?? 0 }}<small>份</small></div><div class="metric-foot">含未发布试卷</div></div><div class="metric-card"><div class="metric-top"><span>考试场次</span><span class="metric-icon orange">03</span></div><div class="metric-value">{{ data?.examCount ?? 0 }}<small>场</small></div><div class="metric-foot">教师创建的考试</div></div><div class="metric-card"><div class="metric-top"><span>已发布成绩</span><span class="metric-icon green">04</span></div><div class="metric-value">{{ data?.publishedAttemptCount ?? 0 }}<small>份</small></div><div class="metric-foot">平均得分率 {{ Number(data?.averageScoreRate || 0).toFixed(1) }}%</div></div></div>
    <div class="analytics-grid"><section class="surface-card chart-card"><div class="card-heading"><div><h3>成绩分布</h3><p>按每份答卷的得分率归一化分组，便于比较不同满分的试卷。</p></div><span class="chart-pill">SCORE DISTRIBUTION</span></div><div ref="chartEl" class="chart-canvas"></div><el-empty v-if="!data?.publishedAttemptCount" description="暂无已发布成绩，发布成绩后此处将展示分布" :image-size="72"/></section><section class="surface-card chart-card"><div class="card-heading"><div><h3>统计口径</h3><p>防止将估算指标误认为真实成绩。</p></div></div><div class="stat-explain-list"><div><span class="explain-dot actual"></span><div><strong>实际考试难度</strong><p>有已发布成绩时，按平均得分率估计：≥80% 较易，60%–79% 中等，低于 60% 较难。</p></div></div><div><span class="explain-dot estimated"></span><div><strong>组卷预估难度</strong><p>尚无已发布成绩时，依据试卷题目 EASY / MEDIUM / HARD 等级，并按题目分值加权估算。</p></div></div></div></section></div>
    <section class="surface-card data-card"><div class="table-toolbar"><div><h3>试卷难度分析</h3><p>难度指数越高表示越难；“依据”列说明指标来源。</p></div><el-tag type="info" effect="plain">{{ data?.paperDifficulties?.length || 0 }} 份试卷</el-tag></div><el-table :data="data?.paperDifficulties || []" stripe empty-text="暂无试卷，请先创建试卷"><el-table-column prop="paperName" label="试卷名称" min-width="220" show-overflow-tooltip/><el-table-column prop="questionCount" label="题目数" width="90"/><el-table-column prop="totalScore" label="满分" width="90"><template #default="scope">{{ Number(scope.row.totalScore || 0).toFixed(1) }}</template></el-table-column><el-table-column label="难度指数" width="220"><template #default="scope"><div class="accuracy-cell"><el-progress :percentage="Math.max(0, Math.min(100, Number(scope.row.difficultyIndex) || 0))" :show-text="false" :stroke-width="7" :status="scope.row.difficultyLabel === '较难' ? 'exception' : scope.row.difficultyLabel === '中等' ? 'warning' : 'success'"/><strong>{{ Number(scope.row.difficultyIndex || 0).toFixed(1) }}</strong></div></template></el-table-column><el-table-column label="难度判断" width="120"><template #default="scope"><el-tag :type="scope.row.difficultyLabel === '较难' ? 'danger' : scope.row.difficultyLabel === '中等' ? 'warning' : 'success'">{{ scope.row.difficultyLabel }}</el-tag></template></el-table-column><el-table-column label="平均得分率" width="130"><template #default="scope">{{ scope.row.averageScoreRate == null ? '暂无成绩' : `${Number(scope.row.averageScoreRate).toFixed(1)}%` }}</template></el-table-column><el-table-column prop="basis" label="依据" min-width="230" show-overflow-tooltip/></el-table></section>
  </div>
</template>

<style scoped>
.metric-text { font-size: 24px !important; overflow-wrap: anywhere; }
.stat-explain-list { display: grid; gap: 24px; padding: 22px 6px; }
.stat-explain-list > div { display: flex; gap: 12px; align-items: flex-start; }
.stat-explain-list strong { color: #27344d; font-size: 14px; }
.stat-explain-list p { margin: 7px 0 0; color: #7b879d; font-size: 13px; line-height: 1.8; }
.explain-dot { width: 10px; height: 10px; flex: 0 0 10px; margin-top: 5px; border-radius: 50%; }
.explain-dot.actual { background: #13a985; } .explain-dot.estimated { background: #f0a33a; }
</style>
