<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Warning } from '@element-plus/icons-vue'
const router = useRouter()
</script>
<template><div class="not-found-page"><div class="not-found-illustration"><el-icon><Warning /></el-icon></div><div class="eyebrow">PAGE NOT FOUND</div><h1>这页暂时不在这里</h1><p>链接可能已失效，或者页面尚未纳入当前版本。你可以返回工作台继续操作。</p><el-button type="primary" @click="router.replace('/dashboard')">返回工作台</el-button></div></template>
