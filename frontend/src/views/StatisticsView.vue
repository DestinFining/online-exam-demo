<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import echarts, { type EChartsInstance } from '@/plugins/echarts'
import { ElMessage } from 'element-plus'
import { statisticsApi } from '@/api/modules'
import { isReported } from '@/api/http'
import type { StudentStatistics } from '@/types'
const data = ref<StudentStatistics | null>(null)
const loading = ref(false)
const radarEl = ref<HTMLDivElement | null>(null)
const trendEl = ref<HTMLDivElement | null>(null)
let radarChart: EChartsInstance | undefined
let trendChart: EChartsInstance | undefined
async function renderCharts() {
  await nextTick()
  if (!data.value) return
  if (radarEl.value) {
    radarChart?.dispose(); radarChart = echarts.init(radarEl.value)
    const kps = [...(data.value.knowledgePoints || [])].slice(0, 6).reverse()
    radarChart.setOption({ tooltip: {}, radar: { indicator: kps.length ? kps.map(k => ({ name: k.knowledgePointName.length > 8 ? `${k.knowledgePointName.slice(0,8)}…` : k.knowledgePointName, max: 100 })) : [{ name: '暂无数据', max: 100 }], radius: '62%', splitNumber: 4, axisName: { color: '#7b879d', fontSize: 11 }, splitArea: { areaStyle: { color: ['#f8faff', '#f4f7ff'] } }, axisLine: { lineStyle: { color: '#e0e7f3' } }, splitLine: { lineStyle: { color: '#e0e7f3' } } }, series: [{ type: 'radar', data: [{ value: kps.length ? kps.map(k => Number(k.accuracy) || 0) : [0], name: '知识点正确率', areaStyle: { color: 'rgba(76, 110, 245, 0.18)' }, lineStyle: { color: '#4c6ef5', width: 2 }, itemStyle: { color: '#4c6ef5' } }] }] })
  }
  if (trendEl.value) {
    trendChart?.dispose(); trendChart = echarts.init(trendEl.value)
    const trend = [...(data.value.scoreTrend || [])].reverse()
    trendChart.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 34, right: 18, top: 28, bottom: 30 }, xAxis: { type: 'category', data: trend.map((t, i) => `第 ${i + 1} 次`), axisLine: { lineStyle: { color: '#d8dfed' } }, axisLabel: { color: '#7b879d' } }, yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: '#edf1f7', type: 'dashed' } }, axisLabel: { color: '#7b879d' } }, series: [{ name: '成绩', type: 'line', smooth: true, data: trend.map(t => Number(t.score) || 0), symbolSize: 7, lineStyle: { color: '#13a985', width: 3 }, itemStyle: { color: '#13a985' }, areaStyle: { color: 'rgba(19,169,133,.10)' } }] })
  }
}
async function load() { loading.value = true; try { data.value = await statisticsApi.student(); await renderCharts() } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '学情数据加载失败') } finally { loading.value = false } }
function resize() { radarChart?.resize(); trendChart?.resize() }
onMounted(() => { void load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); radarChart?.dispose(); trendChart?.dispose() })
</script>
<template><div class="page-content" v-loading="loading"><div class="page-title-row"><div><div class="eyebrow">LEARNING INSIGHTS</div><h1>学情分析</h1><p>用真实刷题与已发布考试数据，识别掌握情况并安排后续复习。</p></div><el-button @click="load">刷新数据</el-button></div><div class="stats-grid"><div class="metric-card"><div class="metric-top"><span>累计练习</span><span class="metric-icon blue">01</span></div><div class="metric-value">{{ data?.totalPractice ?? '—' }}<small>次</small></div><div class="metric-foot">提交并记录的练习</div></div><div class="metric-card"><div class="metric-top"><span>正确率</span><span class="metric-icon green">02</span></div><div class="metric-value">{{ Number(data?.practiceAccuracy || 0).toFixed(1) }}<small>%</small></div><div class="metric-foot">{{ data?.correctPractice ?? 0 }} 次正确 / {{ data?.wrongPractice ?? 0 }} 次错误</div></div><div class="metric-card"><div class="metric-top"><span>平均考试成绩</span><span class="metric-icon purple">03</span></div><div class="metric-value">{{ Number(data?.averageScore || 0).toFixed(1) }}<small>分</small></div><div class="metric-foot">仅统计已发布成绩</div></div><div class="metric-card"><div class="metric-top"><span>最高成绩</span><span class="metric-icon orange">04</span></div><div class="metric-value">{{ Number(data?.highestScore || 0).toFixed(1) }}<small>分</small></div><div class="metric-foot">已发布考试的最高分</div></div></div><div class="analytics-grid"><section class="surface-card chart-card"><div class="card-heading"><div><h3>知识点掌握度</h3><p>正确率较低的知识点会优先展示</p></div><span class="chart-pill">TOP 6 薄弱项</span></div><div ref="radarEl" class="chart-canvas"></div><el-empty v-if="!data?.knowledgePoints?.length" description="完成几次练习后，这里会展示知识点数据" :image-size="72"/></section><section class="surface-card chart-card"><div class="card-heading"><div><h3>考试成绩趋势</h3><p>按已发布的考试记录展示变化</p></div><span class="chart-pill green-pill">SCORE TREND</span></div><div ref="trendEl" class="chart-canvas"></div><el-empty v-if="!data?.scoreTrend?.length" description="暂时没有已发布的考试成绩" :image-size="72"/></section></div><section class="surface-card data-card"><div class="table-toolbar"><div><h3>知识点明细</h3><p>根据实际答题结果聚合，不使用估算值</p></div></div><el-table :data="data?.knowledgePoints || []" stripe empty-text="暂无知识点答题数据"><el-table-column prop="knowledgePointName" label="知识点" min-width="240"/><el-table-column prop="attempts" label="作答次数" width="140"/><el-table-column prop="correct" label="正确次数" width="140"/><el-table-column label="正确率" width="240"><template #default="scope"><div class="accuracy-cell"><el-progress :percentage="Number(scope.row.accuracy) || 0" :show-text="false" :stroke-width="7"/><strong>{{ Number(scope.row.accuracy || 0).toFixed(1) }}%</strong></div></template></el-table-column><el-table-column label="学习建议" min-width="180"><template #default="scope"><el-tag :type="Number(scope.row.accuracy) < 60 ? 'danger' : Number(scope.row.accuracy) < 80 ? 'warning' : 'success'">{{ Number(scope.row.accuracy) < 60 ? '优先复习' : Number(scope.row.accuracy) < 80 ? '继续巩固' : '保持练习' }}</el-tag></template></el-table-column></el-table></section><div class="dashboard-note"><span>分析边界说明：雷达图和成绩趋势均依据当前后端返回的数据；教师班级统计与试卷难度统计需要后续新增对应聚合接口。</span></div></div></template>
