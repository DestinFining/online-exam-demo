<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, EditPen, Delete, Download, Filter, Upload } from '@element-plus/icons-vue'
import { knowledgeApi, questionApi, subjectApi } from '@/api/modules'
import { http, apiErrorMessage, isReported } from '@/api/http'
import type { KnowledgePoint, Question, Subject } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Question[]>([])
const total = ref(0)
const subjects = ref<Subject[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const filters = reactive({ keyword: '', subjectId: undefined as number | undefined, type: '', difficulty: '', page: 1, size: 10 })
const selectedRows = ref<Question[]>([])
const batchDialogVisible = ref(false)
const batchSaving = ref(false)
const batchJson = ref('')
const batchDefaultSubjectId = ref<number | undefined>()
const batchDefaultKnowledgePointId = ref<number | undefined>()
const batchKnowledgePoints = ref<KnowledgePoint[]>([])
const batchFileInput = ref<HTMLInputElement | null>(null)
const excelFileInput = ref<HTMLInputElement | null>(null)
const excelBusy = ref(false)
const batchSample = `[
  {
    "type": "SINGLE_CHOICE",
    "content": "以下哪一个是关系型数据库？",
    "difficulty": "EASY",
    "difficultyValue": 0.3,
    "defaultScore": 2,
    "analysis": "MySQL 是关系型数据库。",
    "options": [
      { "optionKey": "A", "optionContent": "MySQL", "correct": true },
      { "optionKey": "B", "optionContent": "Redis", "correct": false },
      { "optionKey": "C", "optionContent": "Vue", "correct": false },
      { "optionKey": "D", "optionContent": "Nginx", "correct": false }
    ]
  },
  {
    "type": "SHORT_ANSWER",
    "content": "请简述索引的作用。",
    "difficulty": "MEDIUM",
    "difficultyValue": 0.6,
    "defaultScore": 5,
    "referenceAnswer": "索引可以加快数据检索，但会增加存储和维护成本。",
    "gradingRubric": "说明检索优化、存储成本两点。"
  }
]`
const form = reactive<any>({ subjectId: undefined, knowledgePointId: undefined, type: 'SINGLE_CHOICE', content: '', analysis: '', fillAnswers: '', referenceAnswer: '', gradingRubric: '', difficulty: 'EASY', difficultyValue: 0.3, defaultScore: 2, tags: '', options: [ { optionKey: 'A', optionContent: '', sort: 1, correct: true }, { optionKey: 'B', optionContent: '', sort: 2, correct: false }, { optionKey: 'C', optionContent: '', sort: 3, correct: false }, { optionKey: 'D', optionContent: '', sort: 4, correct: false } ] })
const filteredRows = computed(() => rows.value.filter((r) => !filters.keyword || r.content?.toLowerCase().includes(filters.keyword.toLowerCase())))
const needsOptions = computed(() => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(form.type))
const questionTypeName = (value: string) => (({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', PROGRAMMING: '编程题' } as Record<string, string>)[value] || value)
const difficultyName = (value: string) => (({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[value] || value)
async function onSubjectChange(value: number) { knowledgePoints.value = await knowledgeApi.list(value); form.knowledgePointId = knowledgePoints.value[0]?.id }
async function load() {
  loading.value = true
  try {
    const [page, subs, kps] = await Promise.all([questionApi.page({ page: filters.page, size: filters.size, subjectId: filters.subjectId, type: filters.type || undefined, difficulty: filters.difficulty || undefined }), subjectApi.list(), knowledgeApi.list(filters.subjectId)])
    rows.value = page.records || []; total.value = page.total || 0; subjects.value = subs || []; knowledgePoints.value = kps || []
  } catch { /* API layer displays server errors */ }
  finally { loading.value = false }
}
function resetForm() {
  editingId.value = null
  Object.assign(form, { subjectId: subjects.value[0]?.id, knowledgePointId: knowledgePoints.value[0]?.id, type: 'SINGLE_CHOICE', content: '', analysis: '', fillAnswers: '', referenceAnswer: '', gradingRubric: '', difficulty: 'EASY', difficultyValue: 0.3, defaultScore: 2, tags: '', options: [ { optionKey: 'A', optionContent: '', sort: 1, correct: true }, { optionKey: 'B', optionContent: '', sort: 2, correct: false }, { optionKey: 'C', optionContent: '', sort: 3, correct: false }, { optionKey: 'D', optionContent: '', sort: 4, correct: false } ] })
}
async function openCreate() {
  if (!subjects.value.length) await load()
  resetForm(); dialogVisible.value = true
}
function parseAnswerConfig(value?: string): Record<string, any> {
  try {
    const parsed = JSON.parse(value || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    // 兼容早期题库中直接存储的文本型参考答案。
    return value ? { referenceAnswer: value } : {}
  }
}
function parseFillAnswers(value?: string) {
  const parsed = parseAnswerConfig(value)
  return Array.isArray(parsed.answers) ? parsed.answers.join('|') : ''
}
function parseReferenceAnswer(value?: string) {
  return String(parseAnswerConfig(value).referenceAnswer || '')
}
function parseGradingRubric(value?: string) {
  return String(parseAnswerConfig(value).gradingRubric || '')
}
async function openEdit(row: Question) {
  try {
    const detail = await questionApi.detail(row.id)
    editingId.value = row.id
    Object.assign(form, { subjectId: detail.subjectId, knowledgePointId: detail.knowledgePointId, type: detail.type, content: detail.content, analysis: detail.analysis || '', fillAnswers: parseFillAnswers(detail.answerConfig), referenceAnswer: parseReferenceAnswer(detail.answerConfig), gradingRubric: parseGradingRubric(detail.answerConfig), difficulty: detail.difficulty, difficultyValue: detail.difficultyValue ?? 0.5, defaultScore: detail.defaultScore ?? 2, tags: detail.tags || '', options: (detail.options || []).map((o: any, i: number) => ({ optionKey: o.optionKey || o.key || String.fromCharCode(65 + i), optionContent: o.optionContent || o.content || '', sort: o.sort ?? i + 1, correct: Boolean(o.correct ?? o.isCorrect) })) })
    if (!form.options.length) form.options = [{ optionKey: 'A', optionContent: '', sort: 1, correct: true }, { optionKey: 'B', optionContent: '', sort: 2, correct: false }]
    dialogVisible.value = true
  } catch { /* displayed by API layer */ }
}
function changeType(value: string) {
  form.type = value
  if (value === 'TRUE_FALSE') form.options = [{ optionKey: 'T', optionContent: '正确', sort: 1, correct: true }, { optionKey: 'F', optionContent: '错误', sort: 2, correct: false }]
  else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(value) && form.options.length < 2) form.options = [{ optionKey: 'A', optionContent: '', sort: 1, correct: true }, { optionKey: 'B', optionContent: '', sort: 2, correct: false }]
  else if (!['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(value)) form.options = []
}
function addOption() { const key = String.fromCharCode(65 + form.options.length); form.options.push({ optionKey: key, optionContent: '', sort: form.options.length + 1, correct: false }) }
function removeOption(index: number) { form.options.splice(index, 1); form.options.forEach((o: any, i: number) => { o.sort = i + 1; o.optionKey = String.fromCharCode(65 + i) }) }
async function saveQuestion() {
  if (!form.subjectId || !form.knowledgePointId || !form.content.trim()) return ElMessage.warning('请填写学科、知识点和题干')
  if (needsOptions.value && (form.options.length < 2 || form.options.some((o: any) => !o.optionContent.trim()))) return ElMessage.warning('选择题至少需要两个填写完整的选项')
  if (needsOptions.value && !form.options.some((o: any) => o.correct)) return ElMessage.warning('请设置正确答案')
  if (form.type === 'SINGLE_CHOICE' && form.options.filter((o: any) => o.correct).length !== 1) return ElMessage.warning('单选题只能有一个正确选项')
  if (form.type === 'TRUE_FALSE' && form.options.length !== 2) return ElMessage.warning('判断题需要两个选项')
  if (form.type === 'FILL_BLANK' && !form.fillAnswers.split('|').some((s: string) => s.trim())) return ElMessage.warning('请至少填写一个标准答案')
  if (['SHORT_ANSWER', 'PROGRAMMING'].includes(form.type) && !form.referenceAnswer.trim()) return ElMessage.warning('请填写参考答案或解题思路，便于教师阅卷')
  saving.value = true
  try {
    const payload = {
      subjectId: form.subjectId,
      knowledgePointId: form.knowledgePointId,
      type: form.type,
      content: form.content,
      answerConfig: form.type === 'FILL_BLANK'
        ? JSON.stringify({ answers: form.fillAnswers.split('|').map((s: string) => s.trim()).filter(Boolean) })
        : ['SHORT_ANSWER', 'PROGRAMMING'].includes(form.type)
          ? JSON.stringify({ referenceAnswer: form.referenceAnswer.trim(), gradingRubric: form.gradingRubric.trim() || null })
          : undefined,
      analysis: form.analysis,
      difficulty: form.difficulty,
      difficultyValue: form.difficultyValue,
      defaultScore: form.defaultScore,
      tags: form.tags,
      options: needsOptions.value ? form.options : [],
    }
    if (editingId.value) await questionApi.update(editingId.value, payload); else await questionApi.create(payload)
    ElMessage.success(editingId.value ? '题目已更新' : '题目已创建')
    dialogVisible.value = false; await load()
  } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '保存失败') }
  finally { saving.value = false }
}
async function remove(row: Question) {
  try {
    await ElMessageBox.confirm('该题将从可用题库中移除，不再用于新刷题和组卷；历史试卷和答卷记录会保留。确定继续吗？', '移除题目', { type: 'warning' })
    await questionApi.remove(row.id)
    ElMessage.success('题目已从可用题库中移除')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close' && !isReported(e)) ElMessage.error(e instanceof Error ? e.message : '移除题目失败')
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename; document.body.appendChild(anchor); anchor.click(); anchor.remove()
  URL.revokeObjectURL(url)
}
async function downloadQuestionTemplate() {
  excelBusy.value = true
  try {
    const response = await http.get('/questions/excel/template', { responseType: 'blob' })
    downloadBlob(new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), '题目导入模板.xlsx')
    ElMessage.success('已下载 Excel 导入模板')
  } catch { /* API layer handles errors */ } finally { excelBusy.value = false }
}
async function exportQuestionsExcel() {
  excelBusy.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filters.subjectId) params.subjectId = filters.subjectId
    if (filters.type) params.type = filters.type
    if (filters.difficulty) params.difficulty = filters.difficulty
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    const response = await http.get('/questions/excel/export', { params, responseType: 'blob' })
    downloadBlob(new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), '题库导出.xlsx')
    ElMessage.success('题目 Excel 导出成功')
  } catch { /* API layer handles errors */ } finally { excelBusy.value = false }
}
function triggerExcelImport() { excelFileInput.value?.click() }
async function importQuestionsExcel(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) { ElMessage.warning('请选择 .xlsx 格式的 Excel 文件'); input.value = ''; return }
  const formData = new FormData(); formData.append('file', file)
  excelBusy.value = true
  try {
    const response = await http.post('/questions/excel/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    const result = response.data?.data as { totalRows: number; importedCount: number; failedCount: number; errors: string[] }
    ElMessage.success(`Excel 处理完成：成功导入 ${result.importedCount} 条，失败 ${result.failedCount} 条`)
    if (result.errors?.length) await ElMessageBox.alert(result.errors.slice(0, 100).join('\n') + (result.errors.length > 100 ? `\n……其余 ${result.errors.length - 100} 条错误未展示` : ''), '导入失败明细', { confirmButtonText: '我知道了', type: 'warning', dangerouslyUseHTMLString: false })
    await load()
  } catch { /* API layer handles errors */ } finally { excelBusy.value = false; input.value = '' }
}

function onSelectionChange(selection: Question[]) { selectedRows.value = selection }
async function removeSelected() {
  const ids = [...new Set(selectedRows.value.map(row => Number(row.id)).filter(Number.isFinite))]
  if (!ids.length) return ElMessage.warning('请先勾选要移除的题目')
  try {
    await ElMessageBox.confirm(`确定将选中的 ${ids.length} 道题目从可用题库中移除吗？历史试卷快照不会删除。`, '批量移除题目', { type: 'warning' })
    const result = await questionApi.removeBatch(ids)
    selectedRows.value = []
    ElMessage.success(`已移除 ${result?.deleted ?? ids.length} 道题目`)
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close' && !isReported(e)) ElMessage.error(apiErrorMessage(e, '批量移除失败'))
  }
}
async function openBatchAdd() {
  if (!subjects.value.length) await load()
  batchDefaultSubjectId.value = filters.subjectId ?? subjects.value[0]?.id
  await onBatchSubjectChange(batchDefaultSubjectId.value)
  batchJson.value = batchSample
  batchDialogVisible.value = true
}
async function onBatchSubjectChange(subjectId?: number) {
  batchKnowledgePoints.value = subjectId ? await knowledgeApi.list(subjectId) : []
  if (!batchKnowledgePoints.value.some(k => k.id === batchDefaultKnowledgePointId.value)) {
    batchDefaultKnowledgePointId.value = batchKnowledgePoints.value[0]?.id
  }
}
function triggerBatchFile() { batchFileInput.value?.click() }
async function onBatchFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    batchJson.value = await file.text()
    ElMessage.success(`已读取文件：${file.name}`)
  } catch {
    ElMessage.error('无法读取文件，请确认是 UTF-8 编码的 JSON 文件')
  } finally {
    input.value = ''
  }
}
function normalizeBatchQuestion(item: Record<string, any>, index: number): Record<string, unknown> {
  const type = String(item.type || 'SINGLE_CHOICE').trim().toUpperCase()
  const difficulty = String(item.difficulty || 'MEDIUM').trim().toUpperCase()
  const subjectId = Number(item.subjectId ?? batchDefaultSubjectId.value)
  const knowledgePointId = Number(item.knowledgePointId ?? batchDefaultKnowledgePointId.value)
  const difficultyValue = Number(item.difficultyValue ?? ({ EASY: 0.3, MEDIUM: 0.6, HARD: 0.9 } as Record<string, number>)[difficulty] ?? 0.6)
  const defaultScore = Number(item.defaultScore ?? 2)
  const content = String(item.content || '').trim()
  if (!Number.isFinite(subjectId) || subjectId <= 0) throw new Error(`第 ${index + 1} 道题缺少学科，请先选择默认学科或在题目中填写 subjectId`)
  if (!Number.isFinite(knowledgePointId) || knowledgePointId <= 0) throw new Error(`第 ${index + 1} 道题缺少知识点，请先选择默认知识点或填写 knowledgePointId`)
  if (!content) throw new Error(`第 ${index + 1} 道题缺少题干 content`)
  if (!['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER', 'PROGRAMMING'].includes(type)) throw new Error(`第 ${index + 1} 道题题型不正确：${type}`)
  if (!['EASY', 'MEDIUM', 'HARD'].includes(difficulty)) throw new Error(`第 ${index + 1} 道题难度不正确：${difficulty}`)

  const options = Array.isArray(item.options) ? item.options.map((option: Record<string, any>, optionIndex: number) => ({
    optionKey: String(option.optionKey ?? option.key ?? String.fromCharCode(65 + optionIndex)).trim().toUpperCase(),
    optionContent: String(option.optionContent ?? option.content ?? '').trim(),
    sort: Number(option.sort ?? optionIndex + 1),
    correct: Boolean(option.correct ?? option.isCorrect),
  })) : []
  let answerConfig = item.answerConfig
  if (answerConfig && typeof answerConfig !== 'string') answerConfig = JSON.stringify(answerConfig)
  if (!answerConfig && type === 'FILL_BLANK') {
    const answers = Array.isArray(item.answers) ? item.answers.map((x: unknown) => String(x).trim()).filter(Boolean) : String(item.fillAnswers || '').split('|').map((x: string) => x.trim()).filter(Boolean)
    if (!answers.length) throw new Error(`第 ${index + 1} 道填空题缺少答案，请填写 answers 或 fillAnswers`)
    answerConfig = JSON.stringify({ answers })
  }
  if (!answerConfig && ['SHORT_ANSWER', 'PROGRAMMING'].includes(type)) {
    const referenceAnswer = String(item.referenceAnswer || '').trim()
    if (!referenceAnswer) throw new Error(`第 ${index + 1} 道题是简答/编程题，请填写 referenceAnswer`)
    answerConfig = JSON.stringify({ referenceAnswer, gradingRubric: String(item.gradingRubric || '').trim() || null })
  }
  if (type === 'FILL_BLANK' && !answerConfig) throw new Error(`第 ${index + 1} 道填空题缺少答案，可填写 answers 或 fillAnswers`)
  if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type)) {
    if (options.length < 2) throw new Error(`第 ${index + 1} 道选择/判断题至少需要两个 options`)
    if (options.some((option: { optionContent: string }) => !option.optionContent)) throw new Error(`第 ${index + 1} 道题存在空选项`)
    const correctCount = options.filter((option: { correct: boolean }) => option.correct).length
    if (type !== 'MULTIPLE_CHOICE' && correctCount !== 1) throw new Error(`第 ${index + 1} 道单选/判断题必须且只能有一个正确选项`)
    if (type === 'MULTIPLE_CHOICE' && correctCount < 1) throw new Error(`第 ${index + 1} 道多选题至少需要一个正确选项`)
  }

  return {
    subjectId, knowledgePointId, type, content, answerConfig: answerConfig || undefined,
    analysis: item.analysis == null ? null : String(item.analysis), difficulty, difficultyValue,
    defaultScore, tags: item.tags == null ? null : (typeof item.tags === 'string' ? item.tags : JSON.stringify(item.tags)),
    options: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type) ? options : [],
  }
}
async function submitBatchAdd() {
  let parsed: unknown
  try { parsed = JSON.parse(batchJson.value) } catch { return ElMessage.warning('JSON 格式有误，请检查逗号、引号和括号') }
  if (!Array.isArray(parsed) || parsed.length === 0) return ElMessage.warning('请提供至少一道题目组成的 JSON 数组')
  if (parsed.length > 200) return ElMessage.warning('单次最多批量新增 200 道题目')
  let payload: Record<string, unknown>[]
  try { payload = parsed.map((item, index) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error(`第 ${index + 1} 项必须是题目对象`)
    return normalizeBatchQuestion(item as Record<string, any>, index)
  }) } catch (e) { return ElMessage.warning(apiErrorMessage(e, '请检查批量题目数据')) }
  batchSaving.value = true
  try {
    const result = await questionApi.createBatch(payload)
    ElMessage.success(`批量新增成功，共添加 ${result?.count ?? payload.length} 道题目`)
    batchDialogVisible.value = false
    selectedRows.value = []
    filters.page = 1
    await load()
  } catch (e) { if (!isReported(e)) ElMessage.error(apiErrorMessage(e, '批量新增失败')) }
  finally { batchSaving.value = false }
}
async function filtersChanged() { filters.page = 1; selectedRows.value = []; await load() }
onMounted(load)
</script>

<template>
  <div class="page-content">
    <div class="page-title-row"><div><div class="eyebrow">CONTENT MANAGEMENT</div><h1>题库管理</h1><p>维护题目、题型、知识点与难度，为刷题和考试提供可靠内容。</p></div><div class="title-actions"><input ref="excelFileInput" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" hidden @change="importQuestionsExcel"/><el-button :loading="excelBusy" :icon="Download" @click="downloadQuestionTemplate">Excel模板</el-button><el-button :loading="excelBusy" :icon="Upload" @click="triggerExcelImport">导入Excel</el-button><el-button :loading="excelBusy" :icon="Download" @click="exportQuestionsExcel">导出Excel</el-button><el-button :icon="Refresh" @click="load">刷新</el-button><el-button :icon="Upload" @click="openBatchAdd">批量添加</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新增题目</el-button></div></div>
    <div class="surface-card filter-card"><div class="filter-head"><div><el-icon><Filter /></el-icon><strong>筛选题目</strong></div><span>共 {{ total }} 道题目</span></div><div class="filter-row"><el-input v-model="filters.keyword" placeholder="搜索题干关键词" clearable :prefix-icon="Search" @keyup.enter="filtersChanged" @clear="filtersChanged" /><el-select v-model="filters.subjectId" placeholder="全部学科" clearable @change="filtersChanged"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select><el-select v-model="filters.type" placeholder="全部题型" clearable @change="filtersChanged"><el-option label="单选题" value="SINGLE_CHOICE"/><el-option label="多选题" value="MULTIPLE_CHOICE"/><el-option label="判断题" value="TRUE_FALSE"/><el-option label="填空题" value="FILL_BLANK"/><el-option label="简答题" value="SHORT_ANSWER"/><el-option label="编程题" value="PROGRAMMING"/></el-select><el-select v-model="filters.difficulty" placeholder="全部难度" clearable @change="filtersChanged"><el-option label="简单" value="EASY"/><el-option label="中等" value="MEDIUM"/><el-option label="困难" value="HARD"/></el-select><el-button type="primary" plain @click="filtersChanged">查询</el-button></div></div>
    <section class="surface-card data-card"><div class="table-toolbar"><div><h3>题目列表</h3><p>教师与管理员可创建和维护题目；批量操作仅作用于当前页已勾选的题目</p></div><div class="title-actions"><el-button type="danger" plain :icon="Delete" :disabled="!selectedRows.length" @click="removeSelected">批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span></el-button><el-tag type="info" effect="plain">{{ total }} 条记录</el-tag></div></div><el-table :data="filteredRows" v-loading="loading" stripe style="width: 100%" empty-text="暂无题目，请尝试调整筛选条件或新增题目" @selection-change="onSelectionChange"><el-table-column type="selection" width="48" /><el-table-column label="题目信息" min-width="360"><template #default="scope"><div class="question-cell"><span class="question-id">Q{{ scope.row.id }}</span><div><strong>{{ scope.row.content }}</strong><small>{{ scope.row.hasAnalysis ? '已配置解析' : '尚未填写解析' }} · {{ scope.row.createdAt ? new Date(scope.row.createdAt).toLocaleDateString() : '题库题目' }}</small></div></div></template></el-table-column><el-table-column label="题型" width="120"><template #default="scope"><el-tag effect="plain" round>{{ questionTypeName(scope.row.type) }}</el-tag></template></el-table-column><el-table-column label="难度" width="100"><template #default="scope"><span class="difficulty" :class="scope.row.difficulty?.toLowerCase()">{{ difficultyName(scope.row.difficulty) }}</span></template></el-table-column><el-table-column prop="defaultScore" label="分值" width="80"><template #default="scope">{{ scope.row.defaultScore ?? '—' }} 分</template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="scope"><div class="row-actions"><el-button text type="primary" :icon="EditPen" @click="openEdit(scope.row)">编辑</el-button><el-button text type="danger" :icon="Delete" @click="remove(scope.row)">删除</el-button></div></template></el-table-column></el-table><div class="table-pagination"><span>当前显示 {{ filteredRows.length }} 条</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next" @change="load" /></div></section>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题目' : '新增题目'" width="760px" top="5vh" destroy-on-close><el-form label-position="top" class="dialog-form"><div class="form-two-cols"><el-form-item label="学科" required><el-select v-model="form.subjectId" placeholder="选择学科" @change="onSubjectChange"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item><el-form-item label="知识点" required><el-select v-model="form.knowledgePointId" placeholder="选择知识点"><el-option v-for="k in knowledgePoints" :key="k.id" :label="k.name" :value="k.id"/></el-select></el-form-item></div><div class="form-three-cols"><el-form-item label="题型"><el-select v-model="form.type" @change="changeType"><el-option label="单选题" value="SINGLE_CHOICE"/><el-option label="多选题" value="MULTIPLE_CHOICE"/><el-option label="判断题" value="TRUE_FALSE"/><el-option label="填空题" value="FILL_BLANK"/><el-option label="简答题" value="SHORT_ANSWER"/><el-option label="编程题" value="PROGRAMMING"/></el-select></el-form-item><el-form-item label="难度"><el-select v-model="form.difficulty" @change="form.difficultyValue = form.difficulty === 'EASY' ? 0.3 : form.difficulty === 'MEDIUM' ? 0.6 : 0.9"><el-option label="简单" value="EASY"/><el-option label="中等" value="MEDIUM"/><el-option label="困难" value="HARD"/></el-select></el-form-item><el-form-item label="默认分值"><el-input-number v-model="form.defaultScore" :min="0.5" :max="100" :step="0.5"/></el-form-item></div><el-form-item label="题目内容" required><el-input v-model="form.content" type="textarea" :rows="3" placeholder="输入清晰、完整的题干"/></el-form-item><div v-if="needsOptions" class="option-editor"><div class="option-editor-head"><strong>题目选项</strong><el-button v-if="form.type !== 'TRUE_FALSE'" text type="primary" :icon="Plus" @click="addOption">添加选项</el-button></div><div v-for="(opt,index) in form.options" :key="index" class="option-edit-row"><el-tag>{{ opt.optionKey }}</el-tag><el-input v-model="opt.optionContent" placeholder="填写选项内容"/><el-checkbox v-model="opt.correct">正确答案</el-checkbox><el-button v-if="form.type !== 'TRUE_FALSE' && form.options.length > 2" text type="danger" :icon="Delete" @click="removeOption(Number(index))"/></div><p class="form-hint">{{ form.type === 'MULTIPLE_CHOICE' ? '多选题可以勾选多个正确选项。' : form.type === 'TRUE_FALSE' ? '请确保正确项与题目实际答案一致。' : '单选题必须且只能设置一个正确选项。' }}</p></div><el-form-item v-if="form.type === 'FILL_BLANK'" label="标准答案（多个答案用 | 分隔）"><el-input v-model="form.fillAnswers" placeholder="例如：Spring Boot|SpringBoot"/></el-form-item><template v-if="['SHORT_ANSWER', 'PROGRAMMING'].includes(form.type)"><el-form-item :label="form.type === 'PROGRAMMING' ? '参考代码 / 参考答案' : '参考答案'" required><el-input v-model="form.referenceAnswer" type="textarea" :rows="4" :placeholder="form.type === 'PROGRAMMING' ? '填写参考代码、关键思路或预期结果' : '填写参考答案，供教师阅卷时参考'"/></el-form-item><el-form-item label="评分要点（可选）"><el-input v-model="form.gradingRubric" type="textarea" :rows="2" placeholder="例如：核心概念 2 分，分析过程 3 分，结论 1 分"/></el-form-item></template><el-form-item label="题目解析"><el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="解释答案思路，帮助学生复盘"/></el-form-item><el-form-item label="标签（可选）"><el-input v-model="form.tags" placeholder="例如 java,集合,基础"/></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveQuestion">保存题目</el-button></template></el-dialog>

    <el-dialog v-model="batchDialogVisible" title="批量添加题目" width="900px" top="4vh" destroy-on-close>
      <el-alert title="支持一次添加最多 200 道题。请粘贴 JSON 数组或导入 .json 文件；本次操作全部成功才会写入，任意一题格式错误则整批回滚。" type="info" :closable="false" show-icon />
      <el-form label-position="top" class="dialog-form">
        <div class="form-two-cols batch-defaults">
        <el-form-item label="默认学科" required>
          <el-select v-model="batchDefaultSubjectId" placeholder="选择默认学科" @change="onBatchSubjectChange">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认知识点" required>
          <el-select v-model="batchDefaultKnowledgePointId" placeholder="选择默认知识点">
            <el-option v-for="k in batchKnowledgePoints" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
        </el-form-item>
        </div>
      </el-form>
      <div class="batch-json-toolbar">
        <span class="form-hint">每题需提供 type、content；可单独覆盖 subjectId / knowledgePointId。题型支持 SINGLE_CHOICE、MULTIPLE_CHOICE、TRUE_FALSE、FILL_BLANK、SHORT_ANSWER、PROGRAMMING。</span>
        <div class="title-actions">
          <input ref="batchFileInput" type="file" accept=".json,application/json" hidden @change="onBatchFileChange" />
          <el-button :icon="Upload" @click="triggerBatchFile">导入 JSON</el-button>
          <el-button @click="batchJson = batchSample">恢复示例</el-button>
        </div>
      </div>
      <el-input v-model="batchJson" type="textarea" :rows="17" resize="vertical" placeholder="粘贴 JSON 数组……" />
      <p class="form-hint">选择题示例使用 options 数组，每个选项包含 optionKey、optionContent、correct。填空题可填写 answers 数组或用 | 分隔的 fillAnswers；简答题和编程题填写 referenceAnswer，可选 gradingRubric。</p>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="submitBatchAdd">校验并批量添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
