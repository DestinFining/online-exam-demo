<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Reading, Check, CircleClose, Refresh, Star } from '@element-plus/icons-vue'
import { favoriteApi, knowledgeApi, practiceApi, subjectApi } from '@/api/modules'
import { isReported } from '@/api/http'
import type { KnowledgePoint, PracticeQuestion, Subject } from '@/types'
const subjects = ref<Subject[]>([])
const points = ref<KnowledgePoint[]>([])
const settings = reactive({ subjectId: undefined as number | undefined, knowledgePointId: undefined as number | undefined, type: '', difficulty: '', count: 10, mode: 'RANDOM' })
const questions = ref<PracticeQuestion[]>([])
const index = ref(0)
const answer = ref<any>('')
const result = ref<{ correct: boolean; message: string } | null>(null)
const loading = ref(false)
const answered = ref(false)
const current = computed(() => questions.value[index.value])
const progress = computed(() => questions.value.length ? Math.round((index.value + (answered.value ? 1 : 0)) / questions.value.length * 100) : 0)
const correctCount = ref(0)
const questionTypeName = (t: string) => (({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', PROGRAMMING: '编程题' } as Record<string, string>)[t] || t)
const difficultyName = (d: string) => (({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[d] || d)
const availableQuestionCount = ref(0)
const maxSelectableCount = computed(() => Math.min(availableQuestionCount.value, 5000))
const countLoading = ref(false)
let countRequestSequence = 0
function countFilters() {
  return {
    subjectId: settings.subjectId,
    knowledgePointId: settings.knowledgePointId,
    type: settings.type || undefined,
    difficulty: settings.difficulty || undefined,
  }
}
async function refreshAvailableQuestionCount() {
  const requestSequence = ++countRequestSequence
  countLoading.value = true
  try {
    const total = await practiceApi.count(countFilters())
    if (requestSequence !== countRequestSequence) return
    availableQuestionCount.value = Math.max(0, Number(total) || 0)
    if (availableQuestionCount.value > 0) {
      settings.count = Math.max(1, Math.min(Number(settings.count) || 1, maxSelectableCount.value))
    } else {
      settings.count = 0
    }
  } catch {
    if (requestSequence === countRequestSequence) {
      availableQuestionCount.value = 0
      settings.count = 0
    }
  } finally {
    if (requestSequence === countRequestSequence) countLoading.value = false
  }
}
watch(
  () => [settings.subjectId, settings.knowledgePointId, settings.type, settings.difficulty],
  () => { void refreshAvailableQuestionCount() },
  { immediate: true },
)
onMounted(async () => { try { subjects.value = await subjectApi.list(); points.value = await knowledgeApi.list() } catch { /* api error already shown */ } })
async function changeSubject() { settings.knowledgePointId = undefined; points.value = await knowledgeApi.list(settings.subjectId) }
async function start() {
  if (countLoading.value) return ElMessage.info('正在统计当前条件下的可练习题目，请稍候')
  if (availableQuestionCount.value <= 0) return ElMessage.warning('当前筛选条件下没有可刷题目，请调整学科、知识点、题型或难度')
  settings.count = Math.max(1, Math.min(Number(settings.count) || 1, maxSelectableCount.value))
  loading.value = true
  try { questions.value = await practiceApi.start({ ...settings, count: Number(settings.count) }); index.value = 0; answer.value = current.value?.type === 'MULTIPLE_CHOICE' ? [] : ''; result.value = null; answered.value = false; correctCount.value = 0; ElMessage.success(`已生成 ${questions.value.length} 道练习题`) } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '暂时无法开始练习') } finally { loading.value = false }
}
async function submitAnswer() { if (!current.value) return; if (answer.value === '' || (Array.isArray(answer.value) && !answer.value.length)) return ElMessage.warning('请先选择或填写答案'); loading.value = true; try { result.value = await practiceApi.answer({ questionId: current.value.id, answerContent: answer.value, mode: settings.mode }); answered.value = true; if (result.value.correct) correctCount.value++; } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '提交答案失败') } finally { loading.value = false } }
function nextQuestion() { if (index.value < questions.value.length - 1) { index.value++; answer.value = current.value?.type === 'MULTIPLE_CHOICE' ? [] : ''; result.value = null; answered.value = false } else { ElMessage.success(`本轮练习完成，正确 ${correctCount.value}/${questions.value.length} 题`); questions.value = [] } }
async function favorite() { if (!current.value) return; try { const exists = await favoriteApi.exists(current.value.id); if (exists.favorite) { await favoriteApi.remove(current.value.id); ElMessage.success('已取消收藏') } else { await favoriteApi.add(current.value.id); ElMessage.success('已加入收藏') } } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '收藏操作失败') } }
</script>
<template>
  <div class="page-content"><div class="page-title-row"><div><div class="eyebrow">DAILY PRACTICE</div><h1>在线刷题</h1><p>按学科、章节和难度生成练习，完成后系统会自动记录答题结果与错题。</p></div><el-tag effect="plain" type="success" round><el-icon><Reading /></el-icon> 自主学习模式</el-tag></div>
    <section v-if="!questions.length" class="practice-start-layout"><div class="surface-card practice-config"><div class="card-heading"><div><h3>配置本轮练习</h3><p>可以先从一个知识点开始，也可以进行随机抽题</p></div><span class="card-heading-icon"><el-icon><Refresh /></el-icon></span></div><div class="practice-mode-switch"><button :class="{ selected: settings.mode === 'RANDOM' }" @click="settings.mode = 'RANDOM'"><strong>随机刷题</strong><small>打乱顺序，综合检验</small></button><button :class="{ selected: settings.mode === 'CHAPTER' }" @click="settings.mode = 'CHAPTER'"><strong>章节刷题</strong><small>围绕特定知识点</small></button></div><el-form label-position="top" class="practice-config-form"><el-form-item label="学科"><el-select v-model="settings.subjectId" clearable placeholder="全部学科" @change="changeSubject"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item><el-form-item label="知识点"><el-select v-model="settings.knowledgePointId" clearable placeholder="全部知识点"><el-option v-for="k in points" :key="k.id" :label="k.name" :value="k.id"/></el-select></el-form-item><div class="form-two-cols"><el-form-item label="题型"><el-select v-model="settings.type" clearable placeholder="全部题型"><el-option label="单选题" value="SINGLE_CHOICE"/><el-option label="多选题" value="MULTIPLE_CHOICE"/><el-option label="判断题" value="TRUE_FALSE"/><el-option label="填空题" value="FILL_BLANK"/><el-option label="简答题" value="SHORT_ANSWER"/><el-option label="编程题" value="PROGRAMMING"/></el-select></el-form-item><el-form-item label="难度"><el-select v-model="settings.difficulty" clearable placeholder="全部难度"><el-option label="简单" value="EASY"/><el-option label="中等" value="MEDIUM"/><el-option label="困难" value="HARD"/></el-select></el-form-item></div><el-form-item label="题目数量"><div class="practice-count-control"><el-input-number v-model="settings.count" :min="availableQuestionCount > 0 ? 1 : 0" :max="maxSelectableCount" :step="1" controls-position="right" :disabled="countLoading || availableQuestionCount === 0"/><div class="practice-count-info"><span v-if="countLoading">正在统计符合条件的题目…</span><template v-else><strong>{{ settings.mode === 'CHAPTER' ? '当前筛选条件可刷题总数' : '当前筛选条件可刷题总数' }}：{{ availableQuestionCount }} 道</strong><small>题目数量最大值：{{ maxSelectableCount }} 道</small><small v-if="availableQuestionCount > 5000">单次练习最多支持 5000 道题</small><small v-if="settings.type === 'SHORT_ANSWER' || settings.type === 'PROGRAMMING'">简答题和编程题暂不支持自动判分，请在考试中作答并由教师阅卷。</small></template></div></div></el-form-item><el-button type="primary" size="large" class="start-practice-btn" :loading="loading" :disabled="countLoading || availableQuestionCount === 0" @click="start">开始本轮练习 <el-icon><ArrowRight /></el-icon></el-button></el-form></div><div class="practice-aside"><div class="practice-aside-art"><div class="art-circle"></div><div class="art-book"><span></span><span></span><span></span><b>?</b></div><div class="art-star star-a">✦</div><div class="art-star star-b">✧</div></div><div class="surface-card practice-guide"><h3>练习小建议</h3><div class="guide-step"><span>01</span><div><strong>先独立思考</strong><p>尽量不查资料，记录真实掌握情况。</p></div></div><div class="guide-step"><span>02</span><div><strong>关注错题反馈</strong><p>每次答错都会进入错题记录，方便复习。</p></div></div><div class="guide-step"><span>03</span><div><strong>固定频率复盘</strong><p>每天少量练习，比考前突击更稳定。</p></div></div></div></div></section>
    <section v-else class="practice-session"><div class="practice-session-main surface-card"><div class="session-top"><div><div class="session-eyebrow">{{ settings.mode === 'CHAPTER' ? 'CHAPTER PRACTICE' : 'RANDOM PRACTICE' }}</div><h2>第 {{ index + 1 }} 题 <span>/ {{ questions.length }}</span></h2></div><el-button text :icon="Star" @click="favorite">收藏题目</el-button></div><el-progress :percentage="progress" :show-text="false" :stroke-width="6"/><div class="question-meta"><el-tag effect="plain">{{ questionTypeName(current.type) }}</el-tag><el-tag effect="plain" :type="current.difficulty === 'HARD' ? 'danger' : current.difficulty === 'EASY' ? 'success' : 'warning'">{{ difficultyName(current.difficulty) }}</el-tag><span>{{ current.defaultScore }} 分</span></div><h3 class="practice-question-text">{{ current.content }}</h3><div class="practice-answer-area"><el-radio-group v-if="current.type === 'SINGLE_CHOICE' || current.type === 'TRUE_FALSE'" v-model="answer" class="practice-options"><el-radio v-for="option in current.options" :key="option.id" :value="option.key" border><b>{{ option.key }}</b><span>{{ option.content }}</span></el-radio></el-radio-group><el-checkbox-group v-else-if="current.type === 'MULTIPLE_CHOICE'" v-model="answer" class="practice-options"><el-checkbox v-for="option in current.options" :key="option.id" :value="option.key" border><b>{{ option.key }}</b><span>{{ option.content }}</span></el-checkbox></el-checkbox-group><el-input v-else v-model="answer" type="textarea" :rows="current.type === 'PROGRAMMING' ? 8 : 4" :placeholder="current.type === 'FILL_BLANK' ? '输入你的答案' : '写下你的思路或答案'" /></div><div v-if="result" class="answer-feedback" :class="result.correct ? 'correct' : 'incorrect'"><el-icon><component :is="result.correct ? Check : CircleClose" /></el-icon><div><strong>{{ result.correct ? '回答正确' : '需要再复习一下' }}</strong><p>{{ result.message }}。系统已记录本次练习。</p></div></div><div class="practice-session-footer"><span>进度 {{ index + 1 }}/{{ questions.length }} · 本轮正确 {{ correctCount }} 题</span><div><el-button @click="questions = []">结束练习</el-button><el-button v-if="!answered" type="primary" :loading="loading" @click="submitAnswer">提交答案</el-button><el-button v-else type="primary" @click="nextQuestion">{{ index === questions.length - 1 ? '查看结果' : '下一题' }} <el-icon><ArrowRight /></el-icon></el-button></div></div></div><aside class="practice-session-aside"><div class="surface-card answer-sheet"><h3>答题卡</h3><p>点击序号可查看当前进度</p><div class="answer-sheet-grid"><button v-for="(_,i) in questions" :key="i" :class="{ current: i === index, done: i < index || (i === index && answered) }" @click="index = i; answer = ''; result = null; answered = false">{{ i + 1 }}</button></div><div class="answer-legend"><span><i class="current-dot"></i>当前题</span><span><i class="done-dot"></i>已完成</span><span><i class="todo-dot"></i>未作答</span></div></div><div class="mini-tip"><el-icon><Reading /></el-icon><div><strong>错题会自动收录</strong><p>提交后系统自动更新错题记录，无需手动整理。</p></div></div></aside></section>
  </div>
</template>

<style scoped>
.practice-count-control { display: flex; width: 100%; align-items: center; gap: 14px; flex-wrap: wrap; }
.practice-count-control :deep(.el-input-number) { width: 150px; }
.practice-count-info { display: flex; flex-direction: column; gap: 5px; color: #6f7d94; font-size: 12px; line-height: 1.5; }
.practice-count-info strong { color: #34425c; font-size: 12px; }
.practice-count-info small { color: #909bb0; font-size: 11px; }
</style>
