<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, Search } from '@element-plus/icons-vue'
import { noteApi, questionApi } from '@/api/modules'
import { isReported } from '@/api/http'
const route = useRoute()
const questionId = ref<number | undefined>(route.query.qid ? Number(route.query.qid) : undefined)
const question = ref<any>(null)
const rows = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ content: '' })
async function load() { if (!questionId.value || !Number.isFinite(questionId.value)) return ElMessage.warning('请输入题目 ID'); loading.value = true; try { const [q,n] = await Promise.all([questionApi.detail(questionId.value), noteApi.list(questionId.value)]); question.value = q; rows.value = n || [] } catch { rows.value = [] } finally { loading.value = false } }
function create() { editingId.value = null; form.content = ''; dialog.value = true }
function edit(row: any) { editingId.value = row.id; form.content = row.content; dialog.value = true }
async function save() { if (!questionId.value || !form.content.trim()) return ElMessage.warning('笔记内容不能为空'); try { if (editingId.value) await noteApi.update(editingId.value, form.content); else await noteApi.create(questionId.value, form.content); ElMessage.success('笔记已保存'); dialog.value = false; await load() } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '保存失败') } }
async function remove(row: any) { try { await ElMessageBox.confirm('确定删除这条笔记吗？', '删除笔记', { type: 'warning' }); await noteApi.remove(row.id); ElMessage.success('已删除'); await load() } catch { /* cancelled */ } }
onMounted(() => { if (questionId.value) void load() })
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">PERSONAL NOTES</div><h1>题目笔记</h1><p>为题目写下自己的理解、易错原因和复习提示，积累个人知识库。</p></div></div><section class="surface-card note-search-card"><div class="note-search-icon"><el-icon><EditPen /></el-icon></div><div class="note-search-main"><h3>按题目 ID 管理笔记</h3><p>笔记关联到题目 ID。可以从题库题目详情或收藏记录中获取题目 ID。</p><div class="note-search-row"><el-input v-model.number="questionId" type="number" placeholder="输入题目 ID" :prefix-icon="Search" @keyup.enter="load"/><el-button type="primary" @click="load">查询笔记</el-button><el-button :disabled="!questionId" @click="create"><el-icon><Plus /></el-icon> 新建笔记</el-button></div></div></section><section v-if="question" class="surface-card note-question-card"><div class="question-id">Q{{ question.id }}</div><div><span class="section-eyebrow">CURRENT QUESTION</span><h3>{{ question.content }}</h3><p>{{ question.type }} · {{ question.difficulty }}</p></div></section><section class="note-list" v-loading="loading"><article v-for="row in rows" :key="row.id" class="surface-card note-item"><div class="note-item-top"><span class="note-date">{{ row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '个人笔记' }}</span><div><el-button text type="primary" :icon="EditPen" @click="edit(row)">编辑</el-button><el-button text type="danger" :icon="Delete" @click="remove(row)">删除</el-button></div></div><p>{{ row.content }}</p></article><el-empty v-if="!rows.length && !loading" description="选择一道题目后，在这里记录自己的解题思路"/></section><el-dialog v-model="dialog" :title="editingId ? '编辑笔记' : '新建笔记'" width="560px"><el-input v-model="form.content" type="textarea" :rows="6" maxlength="5000" show-word-limit placeholder="写下关键思路、易错点或便于复习的提示……"/><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存笔记</el-button></template></el-dialog></div></template>
