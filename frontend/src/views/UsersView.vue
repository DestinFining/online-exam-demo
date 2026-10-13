<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, EditPen, CircleClose, UserFilled } from '@element-plus/icons-vue'
import { userApi, type AdminUser, type UserRoleOption } from '@/api/modules'
import { isReported } from '@/api/http'
import type { PageResult } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminUser[]>([])
const roles = ref<UserRoleOption[]>([])
const total = ref(0)
const filters = reactive({ keyword: '', roleCode: '', status: '' as '' | '0' | '1', page: 1, size: 10 })
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ username: '', nickname: '', email: '', phone: '', password: '', roleCodes: ['STUDENT'] as string[], status: 1 })
const roleName = (code: string) => roles.value.find(role => role.code === code)?.name || code
async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: filters.page, size: filters.size }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.roleCode) params.roleCode = filters.roleCode
    if (filters.status !== '') params.status = Number(filters.status)
    const result = await userApi.page(params)
    rows.value = result.records || []; total.value = result.total || 0
  } catch { /* API layer reports errors */ } finally { loading.value = false }
}
function resetForm() {
  editingId.value = null
  Object.assign(form, { username: '', nickname: '', email: '', phone: '', password: '', roleCodes: ['STUDENT'], status: 1 })
}
function createUser() { resetForm(); dialogVisible.value = true }
function editUser(row: AdminUser) {
  editingId.value = row.id
  Object.assign(form, { username: row.username, nickname: row.nickname || '', email: row.email || '', phone: row.phone || '', password: '', roleCodes: [...(row.roles || ['STUDENT'])], status: row.status ?? 1 })
  dialogVisible.value = true
}
async function save() {
  if (!form.nickname.trim()) return ElMessage.warning('请填写姓名/昵称')
  if (!form.roleCodes.length) return ElMessage.warning('请至少分配一个角色')
  if (!editingId.value && (!/^[a-zA-Z0-9_.-]{3,50}$/.test(form.username) || form.password.length < 8 || form.password.length > 72)) return ElMessage.warning('用户名须为 3-50 位字母/数字/._-，初始密码须为 8-72 位')
  saving.value = true
  try {
    if (editingId.value) {
      await userApi.update(editingId.value, { nickname: form.nickname.trim(), email: form.email.trim() || null, phone: form.phone.trim() || null, password: form.password || undefined, roleCodes: form.roleCodes, status: form.status })
      ElMessage.success('用户信息已更新')
    } else {
      await userApi.create({ username: form.username.trim(), password: form.password, nickname: form.nickname.trim(), email: form.email.trim() || null, phone: form.phone.trim() || null, roleCodes: form.roleCodes, status: form.status })
      ElMessage.success('用户创建成功')
    }
    dialogVisible.value = false; await load()
  } catch { /* API layer reports errors */ } finally { saving.value = false }
}
async function disableUser(row: AdminUser) {
  try {
    await ElMessageBox.confirm(`确定停用账号“${row.username}”吗？停用后该账号将不能登录，但历史答卷和成绩会保留。`, '停用用户', { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' })
    await userApi.disable(row.id); ElMessage.success('用户已停用'); await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close' && !isReported(error)) ElMessage.error(error instanceof Error ? error.message : '停用失败') }
}
function search() { filters.page = 1; void load() }
onMounted(async () => { try { roles.value = await userApi.roleOptions() } catch { /* handled in API layer */ } await load() })
</script>

<template>
  <div class="page-content" v-loading="loading">
    <div class="page-title-row"><div><div class="eyebrow">SYSTEM ACCESS CONTROL</div><h1>用户管理</h1><p>维护账号、角色和启停状态。停用操作会保留用户的历史考试数据。</p></div><div class="title-actions"><el-button :icon="Refresh" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="createUser">新增用户</el-button></div></div>
    <section class="surface-card data-card">
      <div class="table-toolbar"><div><h3>用户列表</h3><p>基于账号、昵称、邮箱和手机号检索</p></div><el-tag type="info" effect="plain">{{ total }} 个账号</el-tag></div>
      <div class="filter-row"><el-input v-model="filters.keyword" clearable placeholder="搜索用户名 / 昵称 / 邮箱 / 手机号" :prefix-icon="Search" @keyup.enter="search"/><el-select v-model="filters.roleCode" clearable placeholder="全部角色" @change="search"><el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code"/></el-select><el-select v-model="filters.status" clearable placeholder="全部状态" @change="search"><el-option label="正常" value="1"/><el-option label="已停用" value="0"/></el-select><el-button type="primary" @click="search">查询</el-button></div>
      <el-table :data="rows" stripe empty-text="暂无用户数据"><el-table-column label="账号" min-width="170"><template #default="scope"><div class="question-cell"><span class="question-id"><el-icon><UserFilled/></el-icon></span><div><strong>{{ scope.row.username }}</strong><small>ID：{{ scope.row.id }}</small></div></div></template></el-table-column><el-table-column prop="nickname" label="姓名/昵称" min-width="150"/><el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip/><el-table-column prop="phone" label="手机号" min-width="140"/><el-table-column label="角色" min-width="160"><template #default="scope"><el-tag v-for="role in scope.row.roles" :key="role" size="small" effect="plain" class="role-tag">{{ roleName(role) }}</el-tag></template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '正常' : '已停用' }}</el-tag></template></el-table-column><el-table-column label="最后登录" min-width="170"><template #default="scope">{{ scope.row.lastLoginAt ? new Date(scope.row.lastLoginAt).toLocaleString() : '尚未登录' }}</template></el-table-column><el-table-column label="操作" fixed="right" width="160"><template #default="scope"><el-button text type="primary" :icon="EditPen" @click="editUser(scope.row)">编辑</el-button><el-button v-if="scope.row.status === 1" text type="danger" :icon="CircleClose" @click="disableUser(scope.row)">停用</el-button></template></el-table-column></el-table>
      <div class="table-pagination"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[10,20,50,100]" layout="sizes, prev, pager, next, jumper" @change="load"/></div>
    </section>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="620px" destroy-on-close>
      <el-form label-position="top" class="dialog-form"><div class="form-two-cols"><el-form-item label="用户名" required><el-input v-model="form.username" :disabled="Boolean(editingId)" placeholder="3-50 位字母、数字、点、下划线或连字符"/></el-form-item><el-form-item :label="editingId ? '重置密码（留空不修改）' : '初始密码'" :required="!editingId"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少 8 位"/></el-form-item></div><div class="form-two-cols"><el-form-item label="姓名/昵称" required><el-input v-model="form.nickname" placeholder="显示名称"/></el-form-item><el-form-item label="手机号"><el-input v-model="form.phone" placeholder="可选"/></el-form-item></div><el-form-item label="邮箱"><el-input v-model="form.email" placeholder="可选"/></el-form-item><el-form-item label="角色分配" required><el-checkbox-group v-model="form.roleCodes"><el-checkbox v-for="role in roles" :key="role.code" :label="role.code">{{ role.name }}</el-checkbox></el-checkbox-group></el-form-item><el-form-item label="账号状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item><el-alert v-if="editingId" title="角色修改会立即影响账号权限；密码留空表示不重置。" type="info" :closable="false"/></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
