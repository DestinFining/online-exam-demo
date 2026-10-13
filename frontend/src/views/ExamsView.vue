<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Plus, Refresh, Search, Timer, Edit, Delete } from '@element-plus/icons-vue'
import { examApi, paperApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage, isReported } from '@/api/http'
import type { Exam, Paper } from '@/types'
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const role = computed(() => auth.roles[0] || 'STUDENT')
const isStudent = computed(() => role.value === 'STUDENT')
const rows = ref<Exam[]>([])
const papers = ref<Paper[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const filters = reactive({ keyword: '', status: '' })
const form = reactive({ name: '', paperId: undefined as number | undefined, startTime: '', endTime: '', duration: 60, maxAttempts: 1, candidateScope: 'ALL', candidateUserIds: '' })
const visibleRows = computed(() => rows.value.filter((r) => (!filters.keyword || r.name.toLowerCase().includes(filters.keyword.toLowerCase())) && (filters.status === 'ENDED' ? (r.status === 'ENDED' || (r.status === 'PUBLISHED' && examTimeState(r) === 'ENDED')) : filters.status ? r.status === filters.status : r.status !== 'ARCHIVED')))
function dt(offsetHours: number) { const d = new Date(Date.now() + offsetHours * 3600000); const pad = (n: number) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00` }
async function load() { loading.value = true; try { const ex = await examApi.page(); rows.value = ex.records || []; if (!isStudent.value) { const p = await paperApi.page(); papers.value = (p.records || []).filter((x) => x.status === 'PUBLISHED') } } finally { loading.value = false } }
function openCreate() { editingId.value = null; Object.assign(form, { name: '', paperId: papers.value[0]?.id, startTime: dt(0), endTime: dt(2), duration: 60, maxAttempts: 1, candidateScope: 'ALL', candidateUserIds: '' }); dialog.value = true }
function canEditExam(row: Exam) { return row.status === 'DRAFT' || (row.status === 'PUBLISHED' && new Date(row.startTime).getTime() > Date.now()) }
async function openEdit(row: Exam) { if (!canEditExam(row)) return ElMessage.warning('只有草稿或尚未开始的考试可以修改'); saving.value = true; try { const [detail, candidates] = await Promise.all([examApi.detail(row.id), examApi.candidates(row.id)]); const info = detail as { exam?: Exam }; const exam = info.exam || row; editingId.value = row.id; Object.assign(form, { name: exam.name, paperId: exam.paperId, startTime: exam.startTime, endTime: exam.endTime, duration: exam.duration, maxAttempts: exam.maxAttempts, candidateScope: exam.candidateScope || 'ALL', candidateUserIds: candidates.join(',') }); dialog.value = true } catch (error) { if (!isReported(error)) ElMessage.error(apiErrorMessage(error, '操作失败，请稍后重试')) } finally { saving.value = false } }
async function saveExam() {
  if (!form.name.trim() || !form.paperId || !form.startTime || !form.endTime) return ElMessage.warning('请填写考试名称、试卷和时间')
  if (new Date(form.endTime) <= new Date(form.startTime)) return ElMessage.warning('结束时间必须晚于开始时间')
  const ids = form.candidateUserIds.split(/[，,\s]+/).map(x => Number(x)).filter(x => Number.isInteger(x) && x > 0)
  if (form.candidateScope === 'ASSIGNED' && !ids.length) return ElMessage.warning('指定考生模式需要至少一个用户 ID')
  saving.value = true
  try {
    const payload = { name: form.name, paperId: form.paperId, startTime: form.startTime, endTime: form.endTime, duration: form.duration, maxAttempts: form.maxAttempts, candidateScope: form.candidateScope, candidateUserIds: form.candidateScope === 'ASSIGNED' ? ids : [] }
    if (editingId.value !== null) {
      await examApi.update(editingId.value, payload)
      ElMessage.success('考试信息修改成功')
    } else {
      await examApi.create(payload)
      ElMessage.success('考试已创建为草稿。请在考试列表点击“发布考试”，发布后学生才能在考试中心看到。')
    }
    dialog.value = false
    editingId.value = null
    await load()
  } catch (error) { if (!isReported(error)) ElMessage.error(apiErrorMessage(error, '操作失败，请稍后重试')) } finally { saving.value = false }
}
async function deleteEnded(row: Exam) {
  if (!canDeleteEnded(row)) return ElMessage.warning('只有已经结束的已发布考试可以删除')
  try {
    await ElMessageBox.confirm(`“${row.name}”的考试时间已经结束。删除操作会将考试归档并从默认列表隐藏，但会保留答卷、成绩和阅卷记录。是否继续？`, '删除已结束考试', { type: 'warning', confirmButtonText: '归档删除', cancelButtonText: '取消' })
    await examApi.removeEnded(row.id)
    ElMessage.success('考试已归档，历史答卷和成绩已保留')
    await load()
  } catch { /* 用户取消或服务端拒绝 */ }
}
async function publish(row: Exam) { try { await ElMessageBox.confirm(`发布考试“${row.name}”？发布后学生可在允许的时间窗口内参加。`, '发布考试', { type: 'warning' }); await examApi.publish(row.id); ElMessage.success('考试已发布'); await load() } catch { /* cancelled */ } }
function examTimeState(row: Exam): 'NOT_STARTED' | 'OPEN' | 'ENDED' {
  const now = Date.now()
  const startAt = new Date(row.startTime).getTime()
  const endAt = new Date(row.endTime).getTime()
  if (Number.isFinite(startAt) && now < startAt) return 'NOT_STARTED'
  if (Number.isFinite(endAt) && now >= endAt) return 'ENDED'
  return 'OPEN'
}
function canDeleteEnded(row: Exam) { return ['PUBLISHED', 'ENDED'].includes(row.status) && examTimeState(row) === 'ENDED' }
function examTimeLabel(row: Exam) {
  const state = examTimeState(row)
  if (state === 'NOT_STARTED') return `尚未开始（${new Date(row.startTime).toLocaleString()} 开始）`
  if (state === 'ENDED') return '已截止'
  return '可进入'
}
async function start(row: Exam) {
  const timeState = examTimeState(row)
  if (timeState === 'NOT_STARTED') {
    ElMessage.warning(`考试尚未开始，请在 ${new Date(row.startTime).toLocaleString()} 后进入。`)
    return
  }
  if (timeState === 'ENDED') {
    ElMessage.warning('该考试已超过结束时间，无法进入。请联系教师确认考试安排。')
    return
  }
  try {
    await ElMessageBox.confirm(`即将进入“${row.name}”，考试计时将由服务端控制。确认开始？`, '开始考试', { confirmButtonText: '开始考试', cancelButtonText: '稍后再说', type: 'info' })
  } catch {
    return // The user cancelled the confirmation dialog.
  }
  try {
    await examApi.start(row.id)
    await router.push(`/exams/${row.id}/take`)
  } catch (error) {
    // The API layer now reports every failure, 403 included, so this never stays silent.
    if (!isReported(error)) ElMessage.error(apiErrorMessage(error, '进入考试失败，请稍后重试'))
  }
}
const statusName = (s: string) => (({ DRAFT: '草稿', PUBLISHED: '已发布', IN_PROGRESS: '进行中', ENDED: '已结束', ARCHIVED: '已归档' } as Record<string, string>)[s] || s)
const statusType = (s: string) => s === 'PUBLISHED' || s === 'IN_PROGRESS' ? 'success' : s === 'ENDED' || s === 'ARCHIVED' ? 'info' : 'warning'
async function initPage() {
  await load()
  if (isStudent.value || route.query.action !== 'create') return
  const paperId = Number(route.query.paperId)
  if (!Number.isInteger(paperId) || paperId <= 0) return
  const paper = papers.value.find((item) => item.id === paperId)
  if (!paper) {
    ElMessage.warning('没有找到这份已发布试卷。请先在试卷管理中发布试卷，再点击“安排考试”。')
    return
  }
  editingId.value = null
  Object.assign(form, {
    name: `${paper.name}考试`,
    paperId: paper.id,
    startTime: dt(0),
    endTime: dt(2),
    duration: paper.duration || 60,
    maxAttempts: 1,
    candidateScope: 'ALL',
    candidateUserIds: '',
  })
  dialog.value = true
}
onMounted(initPage)
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">EXAM CENTER</div><h1>{{ isStudent ? '考试中心' : '考试管理' }}</h1><p>{{ isStudent ? '查看已发布考试安排，按要求进入考场并完成答卷。' : '基于已发布试卷创建考试，设置开放时间、时长及考生范围。' }}</p></div><div class="title-actions"><el-button :icon="Refresh" @click="load">刷新</el-button><el-button v-if="!isStudent" type="primary" :icon="Plus" @click="openCreate">创建考试</el-button></div></div><section class="surface-card filter-card"><div class="filter-row"><el-input v-model="filters.keyword" :prefix-icon="Search" placeholder="搜索考试名称" clearable/><el-select v-model="filters.status" placeholder="全部状态" clearable><el-option label="草稿" value="DRAFT"/><el-option label="已发布" value="PUBLISHED"/><el-option label="进行中" value="IN_PROGRESS"/><el-option label="已结束" value="ENDED"/><el-option label="已归档" value="ARCHIVED"/></el-select><span class="filter-help">服务端负责判断考试资格、考试时间和交卷状态。</span></div></section><section class="surface-card data-card"><div class="table-toolbar"><div><h3>考试列表</h3><p>{{ isStudent ? '只显示你有权访问的考试记录' : '发布考试前请确认试卷已发布且时间配置准确' }}</p></div><span class="total-counter">{{ visibleRows.length }} 场考试</span></div><el-table :data="visibleRows" v-loading="loading" stripe empty-text="暂时没有考试记录"><el-table-column label="考试名称" min-width="270"><template #default="scope"><div class="question-cell"><span class="exam-icon"><el-icon><Calendar /></el-icon></span><div><strong>{{ scope.row.name }}</strong><small>考试 ID：{{ scope.row.id }} · 试卷 ID：{{ scope.row.paperId }}</small></div></div></template></el-table-column><el-table-column label="考试时间" min-width="260"><template #default="scope"><div class="date-range"><strong>{{ new Date(scope.row.startTime).toLocaleString() }}</strong><small>至 {{ new Date(scope.row.endTime).toLocaleString() }}</small></div></template></el-table-column><el-table-column label="时长" width="100"><template #default="scope"><span><el-icon><Timer /></el-icon> {{ scope.row.duration }} 分钟</span></template></el-table-column><el-table-column label="考试次数" width="110"><template #default="scope">最多 {{ scope.row.maxAttempts }} 次</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusType(canDeleteEnded(scope.row) ? 'ENDED' : scope.row.status)">{{ canDeleteEnded(scope.row) ? '已结束' : statusName(scope.row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" width="240" fixed="right"><template #default="scope"><el-button v-if="isStudent && scope.row.status === 'PUBLISHED' && examTimeState(scope.row) === 'OPEN'" type="primary" size="small" @click="start(scope.row)">进入考试</el-button><el-tooltip v-else-if="isStudent && scope.row.status === 'PUBLISHED'" :content="examTimeLabel(scope.row)" placement="top"><el-button size="small" disabled>{{ examTimeState(scope.row) === 'NOT_STARTED' ? '尚未开始' : '已截止' }}</el-button></el-tooltip><template v-else-if="!isStudent && canEditExam(scope.row)"><el-button type="primary" text :icon="Edit" @click="openEdit(scope.row)">编辑</el-button><el-button v-if="scope.row.status === 'DRAFT'" type="primary" text @click="publish(scope.row)">发布考试</el-button></template><el-button v-else-if="!isStudent && canDeleteEnded(scope.row)" type="danger" text :icon="Delete" @click="deleteEnded(scope.row)">删除</el-button><el-tag v-else-if="scope.row.status === 'ARCHIVED'" type="info">已归档</el-tag><el-button v-else text disabled>{{ scope.row.status === 'ENDED' || examTimeState(scope.row) === 'ENDED' ? '已结束' : '—' }}</el-button></template></el-table-column></el-table></section><el-dialog v-model="dialog" :title="editingId !== null ? '编辑考试信息' : '创建考试'" width="650px" top="5vh"><el-form label-position="top"><el-form-item label="考试名称" required><el-input v-model="form.name" placeholder="例如：2026 级 Java 课程阶段测验"/></el-form-item><div class="form-two-cols"><el-form-item label="已发布试卷" required><el-select v-model="form.paperId" placeholder="选择试卷"><el-option v-for="p in papers" :key="p.id" :label="`${p.name}（${p.totalScore}分 / ${p.duration}分钟）`" :value="p.id"/></el-select></el-form-item><el-form-item label="考试时长（分钟）"><el-input-number v-model="form.duration" :min="1" :max="1440"/></el-form-item></div><div class="form-two-cols"><el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm:ss"/></el-form-item><el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm:ss"/></el-form-item></div><div class="form-two-cols"><el-form-item label="每位学生最大考试次数"><el-input-number v-model="form.maxAttempts" :min="1" :max="10"/></el-form-item><el-form-item label="考生范围"><el-select v-model="form.candidateScope"><el-option label="所有有资格学生" value="ALL"/><el-option label="指定学生 ID" value="ASSIGNED"/></el-select></el-form-item></div><el-form-item v-if="form.candidateScope === 'ASSIGNED'" label="学生用户 ID（逗号分隔）"><el-input v-model="form.candidateUserIds" placeholder="例如：3,4,5"/><p class="form-hint">此版本接受后端用户 ID；后续可扩展为姓名搜索和班级选择。</p></el-form-item><el-alert :title="editingId !== null ? '草稿和未开始的已发布考试可以修改；开始后将锁定配置，避免影响作答和成绩。' : '创建后考试为草稿。请在列表确认信息无误后手动发布。'" type="info" :closable="false" show-icon/></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveExam">{{ editingId !== null ? '保存修改' : '创建考试' }}</el-button></template></el-dialog></div></template>
