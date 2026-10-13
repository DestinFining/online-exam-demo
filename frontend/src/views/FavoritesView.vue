<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Star } from '@element-plus/icons-vue'
import { favoriteApi } from '@/api/modules'
const rows = ref<any[]>([])
const loading = ref(false)
async function load() { loading.value = true; try { const page = await favoriteApi.page(); rows.value = page.records || [] } finally { loading.value = false } }
async function remove(row: any) { try { await ElMessageBox.confirm('确定从收藏夹移除该题目吗？', '取消收藏', { type: 'warning' }); await favoriteApi.remove(row.questionId); ElMessage.success('已取消收藏'); await load() } catch { /* cancelled */ } }
onMounted(load)
</script>
<template><div class="page-content"><div class="page-title-row"><div><div class="eyebrow">YOUR COLLECTION</div><h1>我的收藏</h1><p>把重要题目收集起来，建立自己的复习清单。</p></div><el-button :icon="Refresh" @click="load">刷新</el-button></div><section class="surface-card data-card"><div class="table-toolbar"><div><h3>收藏题目</h3><p>共 {{ rows.length }} 道已收藏题目</p></div><div class="favorites-summary"><el-icon><Star /></el-icon><strong>{{ rows.length }}</strong></div></div><el-table :data="rows" v-loading="loading" stripe empty-text="收藏夹还是空的，可以在刷题时收藏重要题目"><el-table-column label="题目" min-width="420"><template #default="scope"><div class="question-cell"><span class="question-id">Q{{ scope.row.questionId }}</span><div><strong>{{ scope.row.content || `题目 ${scope.row.questionId}` }}</strong><small>{{ scope.row.type }} · {{ scope.row.difficulty }}</small></div></div></template></el-table-column><el-table-column label="收藏时间" width="200"><template #default="scope">{{ scope.row.createdAt ? new Date(scope.row.createdAt).toLocaleString() : '—' }}</template></el-table-column><el-table-column label="操作" width="150"><template #default="scope"><el-button text type="danger" :icon="Delete" @click="remove(scope.row)">取消收藏</el-button></template></el-table-column></el-table></section></div></template>
