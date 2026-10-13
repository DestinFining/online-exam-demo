<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Reading, Lock, User, ArrowRight, CircleCheck } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { isReported } from '@/api/http'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const busy = ref(false)
const formRef = ref()
// Demo credentials only exist in a development build: they are rendered/prefilled
// solely when Vite serves the app, and never in a production bundle.
const isDev = import.meta.env.DEV
const form = reactive({ username: isDev ? 'student' : '', password: isDev ? 'student123456' : '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
async function submit() {
  await formRef.value?.validate()
  busy.value = true
  try {
    await auth.login(form)
    ElMessage.success('登录成功，欢迎回来')
    router.replace(String(route.query.redirect || '/dashboard'))
  } catch (e) {
    if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '登录失败，请检查账号密码')
  } finally { busy.value = false }
}
function quickLogin(username: string, password: string) { form.username = username; form.password = password; void submit() }
</script>

<template>
  <div class="auth-page">
    <section class="auth-visual">
      <div class="auth-brand"><div class="brand-mark large"><el-icon><Reading /></el-icon></div><div><strong>学知</strong><span>ONLINE LEARNING PLATFORM</span></div></div>
      <div class="auth-hero-copy"><div class="hero-overline">LEARN SMARTER · GROW FURTHER</div><h1>让每一次练习<br />都更有价值。</h1><p>从系统刷题到在线测评，从错题复盘到学情洞察，建立自己的学习成长闭环。</p>
        <div class="auth-highlights"><div><span class="highlight-icon"><el-icon><CircleCheck /></el-icon></span><div><strong>智能题库与动态组卷</strong><small>按知识点与难度，精准练习</small></div></div><div><span class="highlight-icon"><el-icon><Lock /></el-icon></span><div><strong>安全可靠的在线考试</strong><small>自动保存与服务端考试校验</small></div></div></div>
      </div>
      <div class="auth-orbit orbit-one"></div><div class="auth-orbit orbit-two"></div><div class="auth-visual-footer">BUILD KNOWLEDGE · MEASURE PROGRESS · ACHIEVE MORE</div>
    </section>
    <section class="auth-panel"><div class="auth-form-wrap"><div class="auth-mobile-brand"><span class="brand-mark"><el-icon><Reading /></el-icon></span><strong>学知</strong></div><div class="auth-eyebrow">欢迎回来</div><h2>登录你的账号</h2><p class="auth-subtitle">继续你的学习旅程，今天也向前一步。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" size="large" placeholder="请输入用户名" :prefix-icon="User" autocomplete="username" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" autocomplete="current-password" /></el-form-item>
        <el-button type="primary" size="large" class="auth-submit" :loading="busy" @click="submit">登录平台 <el-icon class="button-arrow"><ArrowRight /></el-icon></el-button>
      </el-form>
      <div class="auth-helper-row"><span>还没有账号？</span><router-link to="/register">创建学生账号</router-link></div>
      <div v-if="isDev" class="demo-box"><div class="demo-heading"><span>演示环境快速体验</span><small>首次运行后端自动初始化</small></div><div class="demo-accounts"><button @click="quickLogin('student','student123456')"><span class="demo-avatar student">学</span><span><b>学生账号</b><small>student</small></span><el-icon><ArrowRight /></el-icon></button><button @click="quickLogin('teacher','teacher123456')"><span class="demo-avatar teacher">师</span><span><b>教师账号</b><small>teacher</small></span><el-icon><ArrowRight /></el-icon></button><button @click="quickLogin('admin','admin123456')"><span class="demo-avatar admin">管</span><span><b>管理员</b><small>admin</small></span><el-icon><ArrowRight /></el-icon></button></div></div>
      <div class="auth-legal">登录即表示你同意平台的使用规范与数据保护规则。</div>
    </div></section>
  </div>
</template>
