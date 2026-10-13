<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Reading, CircleCheck } from '@element-plus/icons-vue'
import { questionApi, wrongApi } from '@/api/modules'
const router = useRouter()
const rows = ref<Array<any>>([])
const loading = ref(false)
async function load() { loading.value = true; try { const list = await wrongApi.list(); rows.value = await Promise.all((list || []).map(async item => { try { const q = await questionApi.detail(item.questionId); return { ...item, question: q } } catch { return { ...item, question: null } } })) } finally { loading.value = false } }
async function markCorrect(row: any) { try { await wrongApi.markCorrect(row.questionId); ElMessage.success('已更新掌握状态'); await load() } catch { /* API layer displays errors */ } }
function practice(row: any) { router.push({ path: '/practice', query: { questionId: row.questionId } }); ElMessage.info('刷题模式会根据当前筛选条件随机抽题；该题可在题库详情中查看。') }
const masteryLabel = (v: number) => (({ 0: '未掌握', 1: '练习中', 2: '基本掌握', 3: '已掌握' } as Record<number, string>)[v] || '未掌握')
onMounted(load)
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">REVIEW YOUR MISTAKES</div><h1>我的错题</h1><p>集中查看答错记录，反复练习，把薄弱知识点变成稳定得分点。</p></div><el-button :icon="Refresh" @click="load">刷新错题</el-button></div><div class="wrong-summary"><div class="wrong-summary-icon"><el-icon><Reading /></el-icon></div><div><strong>复习比重新做一遍更重要</strong><p>错题会在刷题与考试判分后自动收录。请结合题目 ID 和答题记录定位需要复盘的内容。</p></div><el-tag type="warning" effect="light">{{ rows.length }} 条记录</el-tag></div><section class="surface-card data-card"><div class="table-toolbar"><div><h3>错题记录</h3><p>按最近答错时间排序</p></div></div><el-table :data="rows" v-loading="loading" stripe empty-text="还没有错题记录，开始一次练习吧"><el-table-column label="题目" min-width="360"><template #default="scope"><div class="question-cell"><span class="question-id">Q{{ scope.row.questionId }}</span><div><strong>{{ scope.row.question?.content || `题目 ${scope.row.questionId}` }}</strong><small>{{ scope.row.question?.type || '题型信息暂不可用' }} · 最近错误 {{ scope.row.lastWrongAt ? new Date(scope.row.lastWrongAt).toLocaleString() : '—' }}</small></div></div></template></el-table-column><el-table-column prop="wrongCount" label="错误次数" width="110"/><el-table-column prop="redoCount" label="重做次数" width="110"/><el-table-column label="掌握状态" width="140"><template #default="scope"><el-tag :type="scope.row.masteryLevel >= 3 ? 'success' : scope.row.masteryLevel >= 2 ? 'warning' : 'danger'">{{ masteryLabel(scope.row.masteryLevel) }}</el-tag></template></el-table-column><el-table-column label="操作" width="220"><template #default="scope"><el-button text type="primary" @click="practice(scope.row)">去复习</el-button><el-button text type="success" :icon="CircleCheck" @click="markCorrect(scope.row)">标记已掌握</el-button></template></el-table-column></el-table></section></div></template>
