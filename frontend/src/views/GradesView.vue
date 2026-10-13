<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { examApi, gradeApi } from '@/api/modules'
import { apiErrorMessage, isReported } from '@/api/http'
import type { Exam } from '@/types'
const auth = useAuthStore()
function safeParse(value: unknown) { if (typeof value !== 'string') return value; try { return JSON.parse(value) } catch { return value } }
const isStudent = computed(() => !auth.roles.includes('TEACHER') && !auth.roles.includes('ADMIN'))
const loading = ref(false)
const rows = ref<any[]>([])
const exams = ref<Exam[]>([])
const selectedExamId = ref<number | undefined>()
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)
const gradingBusy = ref<number | null>(null)
const publishingAll = ref(false)
const canPublishAll = computed(() => rows.value.some(row => ['SUBMITTED', 'GRADED'].includes(String(row.status))))
const grades = reactive<Record<number, { score: number; comment: string }>>({})
const statusLabel = (s: string) => (({ SUBMITTED: '待阅卷', GRADED: '待发布', PUBLISHED: '已发布', IN_PROGRESS: '进行中' } as Record<string, string>)[s] || s)
const statusType = (s: string) => s === 'PUBLISHED' ? 'success' : s === 'GRADED' ? 'warning' : 'info'
const typeName = (s: string) => (({ SHORT_ANSWER: '简答题', PROGRAMMING: '编程题', SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', FILL_BLANK: '填空题' } as Record<string, string>)[s] || s)
async function load() {
  loading.value = true
  try {
    if (isStudent.value) {
      rows.value = await gradeApi.my() || []
    } else {
      exams.value = (await examApi.page()).records || []
      if (!selectedExamId.value) {
        const preferred = exams.value.find(e => ['PUBLISHED', 'ENDED', 'ARCHIVED'].includes(String(e.status))) || exams.value[0]
        selectedExamId.value = preferred?.id
      }
      rows.value = selectedExamId.value ? await gradeApi.forExam(selectedExamId.value) || [] : []
    }
  } catch (e) {
    if (!isReported(e)) ElMessage.error(apiErrorMessage(e, '加载成绩/阅卷列表失败'))
  } finally {
    loading.value = false
  }
}
async function selectExam() {
  if (!selectedExamId.value) { rows.value = []; return }
  loading.value = true
  try { rows.value = await gradeApi.forExam(selectedExamId.value) || [] }
  catch (e) { if (!isReported(e)) ElMessage.error(apiErrorMessage(e, '加载考试提交记录失败')) }
  finally { loading.value = false }
}
async function openDetail(row: any) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await gradeApi.answers(row.attemptId)
    for (const a of (detail.value?.answers || [])) grades[a.answerId] = { score: Number(a.manualScore ?? 0), comment: a.teacherComment || '' }
  } catch (e) {
    detail.value = null
    if (!isReported(e)) ElMessage.error(apiErrorMessage(e, '加载答卷详情失败'))
  } finally { detailLoading.value = false }
}
async function saveGrade(answer: any) { const input = grades[answer.answerId] || { score: 0, comment: '' }; if (input.score < 0 || input.score > Number(answer.maxScore)) return ElMessage.warning(`第 ${answer.questionNo} 题得分应在 0 到 ${answer.maxScore} 之间`); gradingBusy.value = answer.answerId; try { await gradeApi.score(answer.answerId, input.score, input.comment); ElMessage.success(`第 ${answer.questionNo} 题评分已保存`); if (detail.value?.summary?.attemptId) await openDetail(detail.value.summary) } catch (e) { if (!isReported(e)) ElMessage.error(apiErrorMessage(e, '保存评分失败')) } finally { gradingBusy.value = null } }
async function finalize() {
  const id = detail.value?.summary?.attemptId
  if (!id) return
  try {
    await ElMessageBox.confirm('结算后将汇总客观题与主观题成绩。请先确认所有简答题和编程题都已评分。', '结算成绩', { type: 'warning' })
    await gradeApi.finalize(id)
    ElMessage.success('成绩结算完成')
    await openDetail({ attemptId: id })
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close' && !isReported(e)) ElMessage.error(apiErrorMessage(e, '成绩结算失败'))
  }
}
async function publish() {
  const id = detail.value?.summary?.attemptId
  if (!id) return
  try {
    await ElMessageBox.confirm('发布后该考生即可查看成绩与答卷详情，确认发布？', '发布成绩', { type: 'warning' })
    await gradeApi.publish(id)
    ElMessage.success('该考生成绩已发布')
    await openDetail({ attemptId: id })
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close' && !isReported(e)) ElMessage.error(apiErrorMessage(e, '成绩发布失败'))
  }
}
async function publishAll() {
  const examId = selectedExamId.value
  if (!examId) return ElMessage.warning('请先选择一场考试')
  if (!canPublishAll.value) return ElMessage.info('当前没有需要发布的新成绩')
  const pendingCount = rows.value.filter(row => row.status === 'SUBMITTED').length
  const gradedCount = rows.value.filter(row => row.status === 'GRADED').length
  try {
    await ElMessageBox.confirm(
      `将批量结算并发布本场考试的已提交答卷。当前待阅卷 ${pendingCount} 份、已结算待发布 ${gradedCount} 份。若有任何简答题/编程题未评分，本次操作将整体取消，不会只发布一部分。确认继续？`,
      '一键发布本场全部成绩',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '再检查一下' },
    )
    publishingAll.value = true
    const result = await gradeApi.publishAll(examId)
    ElMessage.success(`发布完成：新发布 ${result.publishedCount} 份，之前已发布 ${result.alreadyPublishedCount} 份`)
    detailVisible.value = false
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close' && !isReported(e)) ElMessage.error(apiErrorMessage(e, '批量发布失败'))
  } finally {
    publishingAll.value = false
  }
}
onMounted(load)
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">RESULTS & REVIEW</div><h1>{{ isStudent ? '成绩记录' : '阅卷与成绩' }}</h1><p>{{ isStudent ? '查看考试进度；成绩发布后可查看分数与答卷。' : '使用匿名考生编号阅卷，完成主观题评分、成绩结算与批量发布。' }}</p></div><el-button @click="load">刷新</el-button></div><section v-if="!isStudent" class="surface-card filter-card"><div class="filter-row"><el-select v-model="selectedExamId" placeholder="选择你有权限管理的考试" clearable @change="selectExam"><el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id"/></el-select><span class="filter-help">教师只能阅改自己创建的考试；列表不展示学生姓名。一键发布要求所有待阅卷主观题已评分。</span></div></section><section class="surface-card data-card"><div class="table-toolbar"><div><h3>{{ isStudent ? '考试成绩与发布状态' : selectedExamId ? '考试提交记录' : '选择考试以查看答卷' }}</h3><p>{{ isStudent ? `共 ${rows.length} 条考试记录；待阅卷或待发布时不显示分数` : `${rows.length} 份匿名答卷记录` }}</p></div><el-button v-if="!isStudent" type="success" :disabled="!selectedExamId || !canPublishAll" :loading="publishingAll" @click="publishAll">一键发布本场全部成绩</el-button></div><el-table :data="rows" v-loading="loading" stripe empty-text="暂无成绩记录"><el-table-column v-if="!isStudent" prop="candidateLabel" label="匿名考生编号" width="170"><template #default="scope"><strong>{{ scope.row.candidateLabel || `匿名考生-${String(scope.row.attemptId).padStart(6, '0')}` }}</strong></template></el-table-column><el-table-column prop="examId" label="考试 ID" width="110"/><el-table-column label="考试次数" width="110"><template #default="scope">第 {{ scope.row.attemptNo || 1 }} 次</template></el-table-column><el-table-column label="客观题" width="110"><template #default="scope">{{ isStudent && scope.row.status !== 'PUBLISHED' ? '—' : Number(scope.row.objectiveScore || 0).toFixed(1) }}</template></el-table-column><el-table-column label="主观题" width="110"><template #default="scope">{{ isStudent && scope.row.status !== 'PUBLISHED' ? '—' : Number(scope.row.subjectiveScore || 0).toFixed(1) }}</template></el-table-column><el-table-column label="总分" width="120"><template #default="scope"><strong class="score-strong">{{ isStudent && scope.row.status !== 'PUBLISHED' ? '待发布' : Number(scope.row.totalScore || 0).toFixed(1) }}</strong></template></el-table-column><el-table-column label="提交时间" min-width="190"><template #default="scope">{{ scope.row.submitTime ? new Date(scope.row.submitTime).toLocaleString() : '—' }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" width="140" fixed="right"><template #default="scope"><el-button v-if="isStudent ? scope.row.status === 'PUBLISHED' : true" text type="primary" @click="openDetail(scope.row)">{{ isStudent ? '查看答卷' : '阅卷详情' }}</el-button><span v-else class="muted-text">等待发布</span></template></el-table-column></el-table></section>
    <el-dialog v-model="detailVisible" :title="isStudent ? '答卷详情' : '阅卷详情与成绩处理'" width="940px" top="4vh" class="grade-detail-dialog"><div v-loading="detailLoading"><div v-if="detail?.summary" class="grade-detail-summary"><div><small>{{ isStudent ? '考试编号' : '考生' }}</small><strong>{{ isStudent ? detail.summary.examId : (detail.summary.candidateLabel || `匿名考生-${String(detail.summary.attemptId).padStart(6, '0')}`) }}</strong></div><div><small>答卷状态</small><el-tag :type="statusType(detail.summary.status)">{{ statusLabel(detail.summary.status) }}</el-tag></div><div><small>客观题</small><strong>{{ Number(detail.summary.objectiveScore || 0).toFixed(1) }}</strong></div><div><small>主观题</small><strong>{{ Number(detail.summary.subjectiveScore || 0).toFixed(1) }}</strong></div><div><small>当前总分</small><strong class="score-strong">{{ Number(detail.summary.totalScore || 0).toFixed(1) }}</strong></div></div><div v-for="a in detail?.answers || []" :key="a.answerId" class="grade-answer-card"><div class="grade-answer-head"><div><span class="section-eyebrow">QUESTION {{ a.questionNo }}</span><h4>第 {{ a.questionNo }} 题 <el-tag effect="plain">{{ typeName(a.type) }}</el-tag></h4></div><div class="grade-answer-score">{{ a.finalScore ?? a.autoScore ?? 0 }} <small>/ {{ a.maxScore }} 分</small></div></div><p class="grade-question-content">{{ a.content }}</p><div class="answer-detail-grid"><div><small>学生答案</small><p>{{ typeof a.answerContent === 'string' ? (JSON.stringify(safeParse(a.answerContent)) || a.answerContent) : JSON.stringify(a.answerContent) || '未作答' }}</p></div><div v-if="!isStudent"><small>参考答案</small><p>{{ JSON.stringify(safeParse(a.referenceAnswer)) || a.referenceAnswer || '—' }}</p></div></div><div v-if="!isStudent && ['SHORT_ANSWER','PROGRAMMING'].includes(a.type)" class="manual-grade-form"><el-input-number v-model="grades[a.answerId].score" :min="0" :max="Number(a.maxScore)" :precision="2" :step="0.5"/><el-input v-model="grades[a.answerId].comment" placeholder="教师评语（可选）"/><el-button type="primary" size="small" @click="saveGrade(a)" :loading="gradingBusy === a.answerId">保存评分</el-button><el-tag v-if="a.gradedAt" type="success">已批改</el-tag><el-tag v-else type="warning">待批改</el-tag></div><div v-else class="grade-answer-foot"><span>{{ a.type === 'SHORT_ANSWER' || a.type === 'PROGRAMMING' ? `教师评语：${a.teacherComment || '暂无'}` : `自动判分：${a.isCorrect === 1 ? '正确' : '错误'}` }}</span><span>得分 {{ a.finalScore ?? a.autoScore ?? 0 }} / {{ a.maxScore }}</span></div></div><el-empty v-if="!detail?.answers?.length && !detailLoading" description="没有可显示的答题记录"/><div v-if="!isStudent && detail?.summary" class="grade-footer-actions"><el-alert v-if="detail.summary.status === 'SUBMITTED' && !detail.manualGradingComplete" title="仍有主观题未完成评分，结算操作可能被服务端拒绝。" type="warning" :closable="false" show-icon/><el-button v-if="['SUBMITTED','GRADED'].includes(detail.summary.status)" @click="finalize">结算成绩</el-button><el-button v-if="detail.summary.status === 'GRADED'" type="primary" @click="publish">发布成绩</el-button></div></div></el-dialog>
  </div></template>
