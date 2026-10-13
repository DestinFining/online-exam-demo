<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Collection, Plus, Refresh, MagicStick, Delete, Edit } from '@element-plus/icons-vue'
import { paperApi, subjectApi } from '@/api/modules'
import { isReported } from '@/api/http'
import type { Paper, Subject } from '@/types'
const router = useRouter()
const rows = ref<Paper[]>([])
const subjects = ref<Subject[]>([])
const loading = ref(false)
const dialog = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const editingStatus = ref<string>('DRAFT')
const selectedRows = ref<Paper[]>([])
const kind = ref<'FIXED' | 'RULE'>('FIXED')
const filters = reactive({ subjectId: undefined as number | undefined, status: '' })
const form = reactive({ name: '', subjectId: undefined as number | undefined, duration: 60, questionIds: '', questionScores: '', totalScore: 100, singleCount: 20, singleScore: 2, multipleCount: 5, multipleScore: 2, judgeCount: 10, judgeScore: 1, fillCount: 5, fillScore: 2, shortCount: 0, shortScore: 10, programCount: 0, programScore: 10, easyRatio: 0.3, mediumRatio: 0.5, hardRatio: 0.2, knowledgePointIds: '' })
const publishedCount = computed(() => rows.value.filter(r => r.status === 'PUBLISHED').length)
const calculatedScore = computed(() => form.singleCount * form.singleScore + form.multipleCount * form.multipleScore + form.judgeCount * form.judgeScore + form.fillCount * form.fillScore + form.shortCount * form.shortScore + form.programCount * form.programScore)
async function load() { loading.value = true; try { const [p,s] = await Promise.all([paperApi.page({ subjectId: filters.subjectId, status: filters.status || undefined }), subjectApi.list()]); rows.value = p.records || []; subjects.value = s || [] } finally { loading.value = false } }
function openCreate(type: 'FIXED' | 'RULE') {
  editingId.value = null
  editingStatus.value = 'DRAFT'
  kind.value = type
  Object.assign(form, { name: '', subjectId: subjects.value[0]?.id, duration: 60, questionIds: '', questionScores: '', totalScore: 100, singleCount: 20, singleScore: 2, multipleCount: 5, multipleScore: 2, judgeCount: 10, judgeScore: 1, fillCount: 5, fillScore: 2, shortCount: 0, shortScore: 10, programCount: 0, programScore: 10, easyRatio: 0.3, mediumRatio: 0.5, hardRatio: 0.2, knowledgePointIds: '' })
  dialog.value = true
}
async function openEdit(row: Paper) {
  if (row.status === 'ARCHIVED') return ElMessage.warning('已归档试卷不可修改')
  if (row.status === 'PUBLISHED') {
    try {
      await ElMessageBox.confirm(
        '已发布试卷现在也可以修改。修改会同步影响尚未开始且没有答卷的关联考试；如果关联考试已经开始、结束或已有答卷，系统会阻止保存，以保护考试历史。确定继续吗？',
        '编辑已发布试卷',
        { type: 'warning', confirmButtonText: '继续编辑', cancelButtonText: '暂不修改' },
      )
    } catch { return }
  }
  submitting.value = true
  try {
    const detail = await paperApi.detail(row.id)
    const paper = detail.paper || row
    editingId.value = row.id
    editingStatus.value = row.status
    kind.value = paper.type === 'RULE' ? 'RULE' : 'FIXED'
    Object.assign(form, { name: paper.name, subjectId: paper.subjectId, duration: paper.duration, questionIds: (detail.questions || []).map(q => q.questionId).join(','), questionScores: (detail.questions || []).map(q => q.score).join(','), totalScore: Number(paper.totalScore || 0), singleCount: 20, singleScore: 2, multipleCount: 5, multipleScore: 2, judgeCount: 10, judgeScore: 1, fillCount: 5, fillScore: 2, shortCount: 0, shortScore: 10, programCount: 0, programScore: 10, easyRatio: 0.3, mediumRatio: 0.5, hardRatio: 0.2, knowledgePointIds: '' })
    if (kind.value === 'RULE') {
      const raw = paper.ruleConfig
      const config = (typeof raw === 'string' ? JSON.parse(raw || '{}') : raw || {}) as { totalScore?: number; types?: Array<{ type: string; count: number; score: number }>; difficulty?: Record<string, number>; knowledgePointIds?: number[] }
      for (const item of config.types || []) {
        const count = Number(item.count || 0)
        const score = Number(item.score || 1)
        if (item.type === 'SINGLE_CHOICE') { form.singleCount = count; form.singleScore = score }
        else if (item.type === 'MULTIPLE_CHOICE') { form.multipleCount = count; form.multipleScore = score }
        else if (item.type === 'TRUE_FALSE') { form.judgeCount = count; form.judgeScore = score }
        else if (item.type === 'FILL_BLANK') { form.fillCount = count; form.fillScore = score }
        else if (item.type === 'SHORT_ANSWER') { form.shortCount = count; form.shortScore = score }
        else if (item.type === 'PROGRAMMING') { form.programCount = count; form.programScore = score }
      }
      form.totalScore = Number(config.totalScore || paper.totalScore || 0)
      form.easyRatio = Number(config.difficulty?.EASY ?? 0.3)
      form.mediumRatio = Number(config.difficulty?.MEDIUM ?? 0.5)
      form.hardRatio = Number(config.difficulty?.HARD ?? 0.2)
      form.knowledgePointIds = (config.knowledgePointIds || []).join(',')
    }
    dialog.value = true
  } catch (error) {
    const e = error as { response?: { data?: { message?: string } }; message?: string }
    if (!isReported(e)) ElMessage.error(e.response?.data?.message || e.message || '加载试卷详情失败')
  } finally { submitting.value = false }
}
function typeRule(type: string, count: number, score: number) { return { type, count, score } }
function readableGroupError(message: string) {
  const match = message.match(/(?:题型|Type)\s*[=:：]\s*([A-Z_]+)[\s\S]*?难度\s*[=:：]\s*([A-Z]+)[\s\S]*?需要\s*[=:：]\s*(\d+)[\s\S]*?可用\s*[=:：]\s*(\d+)/i)
  if (!match) {
    const detail = message && !message.includes('SINGLE_CHOICE') && !message.includes('MULTIPLE_CHOICE') && !message.includes('TRUE_FALSE')
      ? `\n\n详细信息：${message}`
      : ''
    return [
      '这次没有组卷成功，因为符合你当前设置的题目不够。',
      detail,
      '',
      '你可以尝试：',
      '1. 减少题目数量，或调整简单、中等、困难题的比例；',
      '2. 为当前学科补充题目，或确认相关题目已启用；',
      '3. 清空“知识点 ID”限制，让系统从更多知识点中选题。',
      '',
      '系统不会自动减少题目，以确保试卷符合你的设置。',
    ].filter(Boolean).join('\n')
  }

  const typeNames: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
    PROGRAMMING: '编程题',
  }
  const difficultyNames: Record<string, string> = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难',
  }
  const required = Number(match[3])
  const available = Number(match[4])
  const shortage = Math.max(required - available, 0)
  return [
    '这次组卷没有成功，因为题库里符合条件的题目不够。',
    '',
    '目前的情况：',
    `题目类型：${typeNames[match[1]] || match[1]}`,
    `题目难度：${difficultyNames[match[2]] || match[2]}`,
    `需要：${required} 道`,
    `目前可用：${available} 道`,
    `还差：${shortage} 道`,
    '',
    '你可以这样处理：',
    `1. 把${typeNames[match[1]] || '对应题型'}的数量调少一些，或降低“${difficultyNames[match[2]] || '对应难度'}题”的比例；`,
    '2. 在当前学科中添加更多符合条件的题目，并确认题目状态为“启用”；',
    '3. 清空“知识点 ID”限制，让系统可以从更多知识点中选题。',
    '',
    '为保证试卷符合你的设置，系统不会自动减少题目数量。',
  ].join('\n')
}
async function save() {
  if (!form.name.trim() || !form.subjectId) return ElMessage.warning('请填写试卷名称并选择学科')
  submitting.value = true
  try {
    let payload: Record<string, unknown>
    if (kind.value === 'FIXED') {
      const ids = form.questionIds.split(/[，,\s]+/).map(s => Number(s.trim())).filter(n => Number.isInteger(n) && n > 0)
      const scores = form.questionScores.trim() ? form.questionScores.split(/[，,\s]+/).map(s => Number(s.trim())) : ids.map(() => 2)
      if (!ids.length) return ElMessage.warning('请输入至少一个题目 ID')
      if (scores.length !== ids.length || scores.some(n => !Number.isFinite(n) || n <= 0)) return ElMessage.warning('每题分值数量必须与题目 ID 数量一致，且分值必须大于 0')
      payload = { name: form.name, subjectId: form.subjectId, type: 'FIXED', duration: form.duration, questions: ids.map((questionId, index) => ({ questionId, score: scores[index] })) }
    } else {
      const types = [
        typeRule('SINGLE_CHOICE', form.singleCount, form.singleScore),
        typeRule('MULTIPLE_CHOICE', form.multipleCount, form.multipleScore),
        typeRule('TRUE_FALSE', form.judgeCount, form.judgeScore),
        typeRule('FILL_BLANK', form.fillCount, form.fillScore),
        typeRule('SHORT_ANSWER', form.shortCount, form.shortScore),
        typeRule('PROGRAMMING', form.programCount, form.programScore),
      ].filter(r => r.count > 0)
      const ids = form.knowledgePointIds.split(/[，,\s]+/).map(s => Number(s.trim())).filter(n => Number.isInteger(n) && n > 0)
      const ratios = form.easyRatio + form.mediumRatio + form.hardRatio
      if (!types.length) return ElMessage.warning('至少配置一种题型')
      if (types.some(r => r.score <= 0)) return ElMessage.warning('各题型每题分值必须大于 0')
      if (Math.abs(ratios - 1) > 0.01) return ElMessage.warning('难度比例之和必须为 1（例如 0.3 + 0.5 + 0.2）')
      form.totalScore = calculatedScore.value
      payload = { name: form.name, subjectId: form.subjectId, type: 'RULE', duration: form.duration, ruleConfig: { totalScore: form.totalScore, types, difficulty: { EASY: form.easyRatio, MEDIUM: form.mediumRatio, HARD: form.hardRatio }, knowledgePointIds: ids } }
    }
    if (editingId.value !== null) await paperApi.update(editingId.value, payload)
    else if (kind.value === 'FIXED') await paperApi.create(payload)
    else await paperApi.generate(payload)
    ElMessage.success(editingId.value !== null ? '试卷修改成功' : kind.value === 'FIXED' ? '固定试卷创建成功' : '动态组卷成功')
    dialog.value = false
    editingId.value = null
    selectedRows.value = []
    await load()
  } catch (e: unknown) {
    const error = e as { response?: { status?: number; data?: { message?: unknown } }; message?: string }
    const status = error.response?.status
    const serverMessage = typeof error.response?.data?.message === 'string' ? error.response.data.message : ''
    if (kind.value === 'RULE' && editingId.value === null && status === 409) {
      await ElMessageBox.alert(readableGroupError(serverMessage || ''), '题目数量不够，暂时无法组卷', { type: 'warning', confirmButtonText: '知道了' })
    } else if (!isReported(e)) ElMessage.error(serverMessage || (e instanceof Error ? e.message : '保存试卷失败'))
  } finally { submitting.value = false }
}
async function deleteSelected() {
  if (!selectedRows.value.length) return ElMessage.warning('请先选择要删除的试卷')
  const ids = selectedRows.value.map(row => row.id)
  try {
    await ElMessageBox.confirm(`确定批量删除选中的 ${ids.length} 份试卷吗？如果其中任何试卷已被考试引用，本次操作会整体取消，不会部分删除。`, '批量删除试卷', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    const result = await paperApi.removeBatch(ids)
    ElMessage.success(`成功删除 ${result.deleted} 份试卷`)
    selectedRows.value = []
    await load()
  } catch { /* 用户取消或服务端拦截 */ }
}
function selectionChanged(rows: Paper[]) { selectedRows.value = rows }
async function publish(row: Paper) { try { await ElMessageBox.confirm(`确定发布试卷“${row.name}”吗？注意：发布试卷不等于发布考试。发布后还需要安排考试并再次发布，学生才能在考试中心看到。`, '发布试卷', { type: 'info', confirmButtonText: '发布试卷', cancelButtonText: '取消' }); await paperApi.publish(row.id); ElMessage.success('试卷已发布。下一步请点击“安排考试”，创建并发布考试后学生才能看到。'); await load() } catch { /* cancelled */ } }
function arrangeExam(row: Paper) { router.push({ path: '/exams', query: { action: 'create', paperId: String(row.id) } }) }
const typeLabel = (type: string) => type === 'RULE' ? '动态组卷' : '固定试卷'
const statusLabel = (status: string) => (({ DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用', ARCHIVED: '已归档' } as Record<string, string>)[status] || status)
onMounted(load)
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">ASSESSMENT DESIGN</div><h1>试卷管理</h1><p>创建并发布试卷后，还需要到“考试管理”安排考试并发布考试，学生才会在“考试中心”看到。</p></div><div class="title-actions"><el-button :icon="Refresh" @click="load">刷新</el-button><el-button type="danger" plain :icon="Delete" :disabled="!selectedRows.length" @click="deleteSelected">批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span></el-button><el-button :icon="Plus" @click="openCreate('FIXED')">固定试卷</el-button><el-button type="primary" :icon="MagicStick" @click="openCreate('RULE')">动态组卷</el-button></div></div><div class="stats-grid compact-stats"><div class="metric-card"><div class="metric-top"><span>试卷总数</span><span class="metric-icon blue"><el-icon><Collection /></el-icon></span></div><div class="metric-value">{{ rows.length }}<small>份</small></div><div class="metric-foot">固定试卷和规则试卷</div></div><div class="metric-card"><div class="metric-top"><span>已发布</span><span class="metric-icon green">02</span></div><div class="metric-value">{{ publishedCount }}<small>份</small></div><div class="metric-foot">可供考试引用</div></div><div class="metric-card"><div class="metric-top"><span>组卷方式</span><span class="metric-icon purple">03</span></div><div class="metric-value">双模式</div><div class="metric-foot">固定选择 + 规则抽题</div></div></div><section class="surface-card filter-card"><div class="filter-row"><el-select v-model="filters.subjectId" placeholder="全部学科" clearable @change="load"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id"/></el-select><el-select v-model="filters.status" placeholder="全部状态" clearable @change="load"><el-option label="草稿" value="DRAFT"/><el-option label="已发布" value="PUBLISHED"/><el-option label="已归档" value="ARCHIVED"/></el-select><span class="filter-help">流程：创建试卷 → 发布试卷 → 安排考试 → 发布考试 → 学生考试中心可见。</span></div></section><section class="surface-card data-card"><div class="table-toolbar"><div><h3>试卷列表</h3><p>发布试卷后，才能将其用于考试</p></div></div><el-table :data="rows" v-loading="loading" stripe empty-text="还没有试卷，请创建固定试卷或尝试动态组卷" @selection-change="selectionChanged"><el-table-column type="selection" width="48"/><el-table-column label="试卷名称" min-width="280"><template #default="scope"><div class="question-cell"><span class="paper-symbol"><el-icon><Collection /></el-icon></span><div><strong>{{ scope.row.name }}</strong><small>试卷 ID：{{ scope.row.id }} · {{ scope.row.createdAt ? new Date(scope.row.createdAt).toLocaleDateString() : '—' }}</small></div></div></template></el-table-column><el-table-column label="组卷类型" width="130"><template #default="scope"><el-tag :type="scope.row.type === 'RULE' ? 'warning' : 'info'" effect="light">{{ typeLabel(scope.row.type) }}</el-tag></template></el-table-column><el-table-column label="总分" width="100"><template #default="scope"><strong>{{ scope.row.totalScore ?? '—' }}</strong> 分</template></el-table-column><el-table-column label="时长" width="100"><template #default="scope">{{ scope.row.duration }} 分钟</template></el-table-column><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" width="310" fixed="right"><template #default="scope"><template v-if="scope.row.status === 'DRAFT'"><el-button text type="primary" :icon="Edit" @click="openEdit(scope.row)">编辑</el-button><el-button text type="primary" @click="publish(scope.row)">发布试卷</el-button></template><template v-else-if="scope.row.status === 'PUBLISHED'"><el-button text type="primary" :icon="Edit" @click="openEdit(scope.row)">编辑</el-button><el-button type="primary" size="small" @click="arrangeExam(scope.row)">安排考试</el-button><el-tag type="success" effect="plain">已发布</el-tag></template><el-tag v-else type="info">已归档</el-tag></template></el-table-column></el-table></section><el-dialog v-model="dialog" :title="editingId !== null ? (kind === 'RULE' ? '编辑动态试卷' : '编辑固定试卷') : (kind === 'RULE' ? '动态组卷' : '创建固定试卷')" width="680px" top="6vh"><el-form label-position="top"><el-alert v-if="editingId !== null && editingStatus === 'PUBLISHED'" title="已发布试卷可以修改；关联考试已开始、已结束或已有答卷时，后端将阻止保存。修改尚未开始且没有答卷的关联考试时，会影响后续考试内容。" type="warning" :closable="false" show-icon class="mb-16"/><el-form-item label="试卷名称" required><el-input v-model="form.name" placeholder="例如：Java 程序设计阶段测试"/></el-form-item><div class="form-two-cols"><el-form-item label="学科" required><el-select v-model="form.subjectId"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item><el-form-item label="考试时长（分钟）"><el-input-number v-model="form.duration" :min="1" :max="1440"/></el-form-item></div><template v-if="kind === 'FIXED'"><el-form-item label="题目 ID（逗号或空格分隔）" required><el-input v-model="form.questionIds" placeholder="例如：1001,1002,1003"/></el-form-item><el-form-item label="每题分值（与题目 ID 一一对应，可选）"><el-input v-model="form.questionScores" placeholder="例如：2,2,5；留空时每题默认 2 分"/></el-form-item><el-alert title="编辑固定试卷时，题目 ID 和每题分值按顺序一一对应。" type="info" :closable="false" show-icon/></template><template v-else><el-alert title="组卷会严格按题型、难度和知识点筛选已启用题目。若返回 409，提示会指出需要数量和可用数量；知识点 ID 是精确筛选，留空表示不限知识点。" type="info" :closable="false" show-icon/><el-form-item label="预计总分"><div class="estimated-score"><strong>{{ calculatedScore }}</strong><span>分 · 根据题型数量与每题分值自动计算</span></div></el-form-item><div class="rule-count-grid"><label>单选题<el-input-number v-model="form.singleCount" :min="0" :max="100"/></label><label>多选题<el-input-number v-model="form.multipleCount" :min="0" :max="100"/></label><label>判断题<el-input-number v-model="form.judgeCount" :min="0" :max="100"/></label><label>填空题<el-input-number v-model="form.fillCount" :min="0" :max="100"/></label><label>简答题<el-input-number v-model="form.shortCount" :min="0" :max="50"/></label><label>编程题<el-input-number v-model="form.programCount" :min="0" :max="50"/></label></div><div class="rule-score-grid"><label>单选每题分值<el-input-number v-model="form.singleScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label><label>多选每题分值<el-input-number v-model="form.multipleScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label><label>判断每题分值<el-input-number v-model="form.judgeScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label><label>填空每题分值<el-input-number v-model="form.fillScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label><label>简答每题分值<el-input-number v-model="form.shortScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label><label>编程每题分值<el-input-number v-model="form.programScore" :min="0.5" :max="100" :step="0.5" :precision="1"/></label></div><div class="rule-count-grid difficulty-ratios"><label>简单比例<el-input-number v-model="form.easyRatio" :min="0" :max="1" :step="0.1" :precision="1"/></label><label>中等比例<el-input-number v-model="form.mediumRatio" :min="0" :max="1" :step="0.1" :precision="1"/></label><label>困难比例<el-input-number v-model="form.hardRatio" :min="0" :max="1" :step="0.1" :precision="1"/></label></div><el-form-item label="知识点 ID（可选，逗号分隔）"><el-input v-model="form.knowledgePointIds" placeholder="留空表示不限制知识点"/></el-form-item><p class="form-hint">默认每题分值：单选 2、多选 2、判断 1、填空 2、简答 10。正式组卷前请核对总分与题目数量规则。</p></template></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="submitting" @click="save">{{ editingId !== null ? '保存修改' : kind === 'RULE' ? '生成试卷' : '创建试卷' }}</el-button></template></el-dialog></div></template>
