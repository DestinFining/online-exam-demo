<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Clock, WarningFilled, DocumentChecked, Upload } from '@element-plus/icons-vue'
import { examApi } from '@/api/modules'
import { isReported } from '@/api/http'
import type { ExamQuestion } from '@/types'

const route = useRoute()
const router = useRouter()
const examId = Number(route.params.id)
const loading = ref(true)
const submitting = ref(false)
const saving = ref(false)
const online = ref(navigator.onLine)
const exam = ref<any>(null)
const attempt = ref<any>(null)
const questions = ref<ExamQuestion[]>([])
const effectiveEndTime = ref('')
const answers = reactive<Record<number, any>>({})
const activeIndex = ref(0)
const secondsRemaining = ref(0)
const startedAt = ref<number>(Date.now())
let timerId: number | undefined
let autoSaveId: number | undefined
let localRestore: Record<string, unknown> = {}
let lastSavePromise: Promise<void> = Promise.resolve()
const current = computed(() => questions.value[activeIndex.value])
const answeredCount = computed(() => questions.value.filter(q => { const a = answers[q.paperQuestionId]; return Array.isArray(a) ? a.length > 0 : String(a ?? '').trim().length > 0 }).length)
const timerText = computed(() => { const seconds = Math.max(0, secondsRemaining.value); return `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` })
const formattedType = (t: string) => (({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', PROGRAMMING: '编程题' } as Record<string, string>)[t] || t)
function parseAnswer(value?: string) { if (value == null || value === '') return ''; try { const parsed = JSON.parse(value); return parsed } catch { return value } }
function defaultAnswer(q: ExamQuestion): string | string[] { return q.type === 'MULTIPLE_CHOICE' ? [] : '' }
function questionHasAnswer(q: ExamQuestion) { const value = answers[q.paperQuestionId]; return Array.isArray(value) ? value.length > 0 : String(value ?? '').trim() !== '' }
function remaining() { const end = new Date(effectiveEndTime.value).getTime(); return Math.max(0, Math.floor((end - Date.now()) / 1000)) }
function tick() { secondsRemaining.value = remaining(); if (secondsRemaining.value <= 0) void submitExam(true) }
async function load() {
  loading.value = true
  try {
    let data: any
    try {
      data = await examApi.resume(examId)
    } catch (error) {
      // Only create a new attempt when resume says there is no in-progress
      // attempt. Do not mask 403/409/500 errors by blindly retrying start().
      const e = error as { response?: { status?: number; data?: { message?: string } } }
      const message = e?.response?.data?.message || ''
      if (e?.response?.status === 404 && message.includes('没有正在进行的考试')) {
        data = await examApi.start(examId)
      } else {
        throw error
      }
    }
    exam.value = data.exam || null
    attempt.value = data.attempt || null
    questions.value = data.questions || []
    effectiveEndTime.value = data.effectiveEndTime || data.attempt?.submitTime || ''
    if (!attempt.value?.id || !questions.value.length) throw new Error('未能加载有效考试记录或试卷为空')
    startedAt.value = new Date(attempt.value.startTime || Date.now()).getTime()
    const cacheKey = `oe_exam_cache_${attempt.value.id}`
    try { localRestore = JSON.parse(localStorage.getItem(cacheKey) || '{}') as Record<string, unknown> } catch { localRestore = {} }
    for (const q of questions.value) {
      const serverValue = parseAnswer(q.savedAnswer)
      const localValue = localRestore[String(q.paperQuestionId)]
      const serverHasAnswer = serverValue !== '' && serverValue != null && !(Array.isArray(serverValue) && !serverValue.length)
      answers[q.paperQuestionId] = (serverHasAnswer ? serverValue : localValue ?? defaultAnswer(q)) as string | string[]
    }
    tick()
    timerId = window.setInterval(tick, 1000)
    autoSaveId = window.setInterval(() => { void saveAnswers(true) }, 8000)
    if (Object.keys(localRestore).length) { await saveAnswers(true); ElMessage.info('已检查本地缓存，并尝试同步到服务器') }
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
  } catch (e) {
    const error = e as { response?: { data?: { message?: string } }; message?: string }
    if (!isReported(error)) ElMessage.error(error?.response?.data?.message || error?.message || '进入考试失败，请检查考试状态、开放时间和考生范围')
    router.replace('/exams')
  } finally { loading.value = false }
}
function cacheAnswers() { if (!attempt.value?.id) return; localStorage.setItem(`oe_exam_cache_${attempt.value.id}`, JSON.stringify(answers)) }
function items() { return questions.value.map(q => ({ paperQuestionId: q.paperQuestionId, answerContent: answers[q.paperQuestionId] ?? null })) }
async function saveAnswers(silent = false) {
  if (!attempt.value?.id || !exam.value || submitting.value || attempt.value.status !== 'IN_PROGRESS') return
  cacheAnswers()
  if (!navigator.onLine) { if (!silent) ElMessage.warning('当前离线，答案已暂存在本地，网络恢复后会尝试同步'); return }
  if (saving.value) return lastSavePromise
  saving.value = true
  lastSavePromise = (async () => {
    try {
      const response: any = await examApi.save(examId, { attemptId: attempt.value.id, answers: items(), clientVersion: attempt.value.versionNo ?? 0 })
      if (response?.attempt) attempt.value = response.attempt
      else if (attempt.value.versionNo != null) attempt.value.versionNo += 1
      if (!silent) ElMessage.success('作答进度已保存')
    } catch (e) {
      if (!silent && !isReported(e)) ElMessage.error(e instanceof Error ? e.message : '保存失败，答案仍保存在当前浏览器')
    } finally { saving.value = false }
  })()
  return lastSavePromise
}
async function submitExam(auto = false) {
  if (submitting.value || !attempt.value?.id || !exam.value) return
  if (!auto) {
    try { await ElMessageBox.confirm(`已作答 ${answeredCount.value}/${questions.value.length} 题。提交后将不能继续修改答案，确定交卷吗？`, '确认交卷', { confirmButtonText: '确认交卷', cancelButtonText: '继续作答', type: 'warning' }) }
    catch { return }
  }
  submitting.value = true
  try {
    cacheAnswers()
    if (navigator.onLine) {
      try { await examApi.save(examId, { attemptId: attempt.value.id, answers: items(), clientVersion: attempt.value.versionNo ?? 0 }) } catch { /* final submit below remains the source of truth */ }
      const result: any = await examApi.submit(examId, attempt.value.id, items())
      localStorage.removeItem(`oe_exam_cache_${attempt.value.id}`)
      if (result?.manualGradingRequired || result?.status === 'SUBMITTED') {
        ElMessage.success(auto
          ? '考试时间已结束，答卷已提交；简答题和编程题将由教师阅卷，发布后可查看成绩'
          : '答卷提交成功；简答题和编程题将由教师阅卷，成绩发布后可在成绩记录中查看')
      } else {
        ElMessage.success(auto
          ? '考试时间已结束，客观题自动评分完成，成绩已发布，可在成绩记录中查看'
          : '答卷提交成功；客观题自动评分完成，成绩已发布，可在成绩记录中查看')
      }
      router.replace('/grades')
    } else {
      ElMessage.error('当前处于离线状态，暂时无法向服务端交卷。答案已保存在本地，请恢复网络后重试。')
      submitting.value = false
    }
  } catch (e) {
    if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '交卷失败，请检查网络后重试')
    submitting.value = false
  }
}
function onVisibilityChange() { if (!attempt.value?.id) return; void examApi.event(examId, { attemptId: attempt.value.id, eventType: document.hidden ? 'PAGE_HIDDEN' : 'PAGE_VISIBLE', clientInfo: navigator.userAgent, metadata: { hidden: document.hidden, elapsedSeconds: Math.floor((Date.now() - startedAt.value) / 1000) } }).catch(() => undefined) }
function onOnline() { online.value = true; if (attempt.value?.id) void examApi.event(examId, { attemptId: attempt.value.id, eventType: 'NETWORK_ONLINE', clientInfo: navigator.userAgent }).catch(() => undefined); ElMessage.success('网络已恢复，正在同步作答进度'); void saveAnswers(true) }
function onOffline() { online.value = false; cacheAnswers(); ElMessage.warning('网络已断开，作答会暂存在本地，请尽快恢复连接') }
watch(answers, () => { cacheAnswers() }, { deep: true })
onMounted(load)
onBeforeUnmount(() => { if (timerId) clearInterval(timerId); if (autoSaveId) clearInterval(autoSaveId); document.removeEventListener('visibilitychange', onVisibilityChange); window.removeEventListener('online', onOnline); window.removeEventListener('offline', onOffline); cacheAnswers() })
</script>
<template>
  <div class="exam-take-page" v-loading="loading"><header class="exam-take-header"><button class="exam-brand" @click="router.push('/exams')"><span class="brand-mark"><el-icon><DocumentChecked /></el-icon></span><span><strong>{{ exam?.name || '在线考试' }}</strong><small>答卷编号：{{ attempt?.id || '—' }}</small></span></button><div class="exam-save-state"><span :class="{ online: online }"></span>{{ online ? saving ? '正在保存' : '自动保存已开启' : '离线缓存中' }}</div><div class="exam-countdown" :class="{ urgent: secondsRemaining < 300 }"><el-icon><Clock /></el-icon><div><small>剩余时间</small><strong>{{ timerText }}</strong></div></div><el-button type="danger" :loading="submitting" @click="submitExam(false)">提交试卷</el-button></header>
    <div class="exam-take-body"><main class="exam-question-column"><div v-if="current" class="surface-card exam-question-card"><div class="exam-question-heading"><div><span class="section-eyebrow">QUESTION {{ current.questionNo }}</span><h2>第 {{ current.questionNo }} 题</h2></div><div class="question-score">{{ current.score }} <small>分</small></div></div><div class="question-meta"><el-tag effect="plain">{{ formattedType(current.type) }}</el-tag><span>题目 {{ activeIndex + 1 }} / {{ questions.length }}</span></div><div class="exam-question-content">{{ current.content }}</div><div class="exam-answer-area"><el-radio-group v-if="current.type === 'SINGLE_CHOICE' || current.type === 'TRUE_FALSE'" v-model="answers[current.paperQuestionId]" class="exam-options"><el-radio v-for="o in current.options" :key="o.optionKey" :value="o.optionKey" border><b>{{ o.optionKey }}</b><span>{{ o.optionContent }}</span></el-radio></el-radio-group><el-checkbox-group v-else-if="current.type === 'MULTIPLE_CHOICE'" v-model="answers[current.paperQuestionId]" class="exam-options"><el-checkbox v-for="o in current.options" :key="o.optionKey" :value="o.optionKey" border><b>{{ o.optionKey }}</b><span>{{ o.optionContent }}</span></el-checkbox></el-checkbox-group><el-input v-else v-model="answers[current.paperQuestionId]" type="textarea" :rows="current.type === 'PROGRAMMING' ? 12 : 6" :placeholder="current.type === 'FILL_BLANK' ? '请输入答案' : current.type === 'PROGRAMMING' ? '在此输入代码（本版本不提供在线执行环境）' : '请在此输入你的回答'" /></div><div class="exam-question-navigation"><el-button :disabled="activeIndex === 0" @click="activeIndex--">上一题</el-button><span>已作答 {{ answeredCount }} / {{ questions.length }}</span><el-button type="primary" :disabled="activeIndex >= questions.length - 1" @click="activeIndex++">下一题</el-button></div></div><div v-else class="surface-card"><el-empty description="试卷内容为空，请联系教师检查试卷配置"/></div><div class="exam-bottom-note"><el-icon><WarningFilled /></el-icon><span>请保持网络连接。系统会定期保存答案，考试结束时间由服务器最终判定。</span></div></main>
      <aside class="exam-answer-aside"><section class="surface-card exam-card-meta"><div class="exam-card-meta-row"><span>考试时长</span><strong>{{ exam?.duration || '—' }} 分钟</strong></div><div class="exam-card-meta-row"><span>开始时间</span><strong>{{ attempt?.startTime ? new Date(attempt.startTime).toLocaleTimeString() : '—' }}</strong></div><div class="exam-card-meta-row"><span>答题进度</span><strong>{{ Math.round(answeredCount / Math.max(questions.length,1) * 100) }}%</strong></div><el-progress :percentage="Math.round(answeredCount / Math.max(questions.length,1) * 100)" :show-text="false" :stroke-width="7"/></section><section class="surface-card exam-answer-sheet"><div class="answer-sheet-title"><h3>答题卡</h3><span>{{ answeredCount }}/{{ questions.length }}</span></div><p>点击题号可快速跳转</p><div class="answer-sheet-grid exam-grid"><button v-for="(q,i) in questions" :key="q.paperQuestionId" :class="{ current: i === activeIndex, done: questionHasAnswer(q) }" @click="activeIndex = i">{{ q.questionNo }}</button></div><div class="answer-legend"><span><i class="current-dot"></i>当前题</span><span><i class="done-dot"></i>已作答</span><span><i class="todo-dot"></i>未作答</span></div><el-button type="primary" class="submit-full" :loading="submitting" @click="submitExam(false)">确认交卷</el-button></section><div class="exam-security-note"><el-icon><WarningFilled /></el-icon><p>系统会记录页面切换等可检测事件。该机制用于异常行为记录，不代表浏览器能够阻止所有外部作弊行为。</p></div></aside></div>
  </div>
</template>
