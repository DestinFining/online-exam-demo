<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Reading, User, Lock, Message, Phone } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { isReported } from '@/api/http'
const auth = useAuthStore()
const router = useRouter()
const formRef = ref()
const busy = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '', email: '', phone: '' })
const validateConfirm = (_: unknown, value: string, callback: (error?: Error) => void) => value === form.password ? callback() : callback(new Error('两次输入的密码不一致'))
const rules = {
  username: [{ required: true, min: 4, max: 50, message: '用户名长度应为 4–50 个字符', trigger: 'blur' }],
  nickname: [{ required: true, max: 50, message: '请输入昵称（最多 50 个字符）', trigger: 'blur' }],
  password: [{ required: true, min: 6, max: 72, message: '密码长度应为 6–72 个字符', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
}
async function submit() {
  await formRef.value?.validate()
  busy.value = true
  try {
    await auth.register(form)
    ElMessage.success('注册成功，请使用新账号登录')
    router.replace('/login')
  } catch (e) { if (!isReported(e)) ElMessage.error(e instanceof Error ? e.message : '注册失败') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="auth-page register-page">
    <section class="auth-visual"><div class="auth-brand"><div class="brand-mark large"><el-icon><Reading /></el-icon></div><div><strong>学知</strong><span>ONLINE LEARNING PLATFORM</span></div></div><div class="auth-hero-copy"><div class="hero-overline">YOUR NEXT CHAPTER STARTS HERE</div><h1>建立习惯，<br />看见成长。</h1><p>记录每一次作答、每一次改进。把知识真正变成自己的能力。</p><div class="join-stats"><div><strong>01</strong><span>持续练习</span></div><div><strong>02</strong><span>及时复盘</span></div><div><strong>03</strong><span>看见进步</span></div></div></div><div class="auth-orbit orbit-one"></div><div class="auth-orbit orbit-two"></div><div class="auth-visual-footer">SMALL STEPS. REAL PROGRESS.</div></section>
    <section class="auth-panel"><div class="auth-form-wrap register-form-wrap"><div class="auth-mobile-brand"><span class="brand-mark"><el-icon><Reading /></el-icon></span><strong>学知</strong></div><div class="auth-eyebrow">加入学知</div><h2>创建学生账号</h2><p class="auth-subtitle">填写基本信息，开启你的练习与考试之旅。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" size="large" placeholder="4–50 个字符" :prefix-icon="User" autocomplete="username" /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" size="large" placeholder="在平台中显示的名称" /></el-form-item>
        <div class="form-two-cols"><el-form-item label="密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="至少 6 位" :prefix-icon="Lock" autocomplete="new-password" /></el-form-item><el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" size="large" type="password" show-password placeholder="再次输入" autocomplete="new-password" /></el-form-item></div>
        <el-form-item label="邮箱（选填）"><el-input v-model="form.email" size="large" placeholder="example@email.com" :prefix-icon="Message" /></el-form-item>
        <el-form-item label="手机号（选填）"><el-input v-model="form.phone" size="large" placeholder="用于个人资料维护" :prefix-icon="Phone" /></el-form-item>
        <el-button type="primary" size="large" class="auth-submit" :loading="busy" @click="submit">创建账号</el-button>
      </el-form><div class="auth-helper-row"><span>已有账号？</span><router-link to="/login">返回登录</router-link></div><div class="auth-legal">新注册用户默认分配学生角色。</div>
    </div></section>
  </div>
</template>
