<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { School, User, Lock, ArrowRight, Check, Collection, FolderOpened, DataAnalysis, Avatar, UserFilled, Reading, CircleCheck } from '@element-plus/icons-vue'
import { type FormInstance, ElMessage } from 'element-plus'
import { useAuth } from '../stores/auth'
const route = useRoute(); const router = useRouter(); const auth = useAuth()
const form = reactive({ username: '', password: '' }); const formRef = ref<FormInstance>(); const loading = ref(false); const error = ref(''); const errorRegion = ref<HTMLElement>()
const demos = [
  { label: '管理员', username: 'admin', password: 'Admin@12345', icon: UserFilled, description: '审核与专业管理' },
  { label: '教师', username: 'teacher', password: 'Teacher@12345', icon: Reading, description: '教学科研与指导' },
  { label: '学生', username: 'student', password: 'Student@12345', icon: Avatar, description: '个人成果与成长' },
]
const selectedDemo = computed(() => demos.find(item => item.username === form.username && item.password === form.password))
watch([() => form.username, () => form.password], () => { error.value = '' })
async function selectDemo(demo: typeof demos[number]) { if (loading.value) return; Object.assign(form, { username: demo.username, password: demo.password }); await nextTick(); formRef.value?.clearValidate() }
async function login() {
  if (loading.value || !await formRef.value?.validate().catch(() => false)) return
  loading.value = true; error.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.user?.name}`)
    const redirect = String(route.query.redirect || '/')
    await router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
  } catch (e) { error.value = (e as Error).message; await nextTick(); errorRegion.value?.focus() } finally { loading.value = false }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="login-brand"><span class="brand-mark"><el-icon :size="29"><School /></el-icon></span><span>知绩<span class="brand-dot">.</span><small>师生业绩管理平台</small></span></div>
      <div class="story-main"><div class="story-kicker"><span></span> ACADEMIC ACHIEVEMENTS</div><h1>把每一份努力，<br />写进专业的成长。</h1><p>汇聚教学、科研与学生成果。<br />让积累看得见，让专业建设有据可循。</p>
        <div class="login-product-visual" aria-label="统一记录、审核确认、材料归档的成果管理流程"><div class="visual-grid" aria-hidden="true"></div><div class="visual-orbit orbit-one" aria-hidden="true"></div><div class="visual-orbit orbit-two" aria-hidden="true"></div><div class="visual-card visual-card-back"><div class="visual-card-icon teal"><el-icon :size="22"><FolderOpened /></el-icon></div><span>材料归档</span><small>支撑材料 · 分类可追溯</small><div class="visual-mini-lines"><i></i><i></i></div></div><div class="visual-card visual-card-front"><div class="visual-card-header"><div class="visual-card-icon"><el-icon :size="24"><Collection /></el-icon></div><span class="visual-approved"><el-icon><CircleCheck /></el-icon> 成果档案</span></div><h3>每一项成果，都值得记录</h3><p>教学 · 科研 · 竞赛 · 论文 · 专利</p><div class="visual-process"><span><i></i> 统一录入</span><span><i></i> 审核确认</span><span><i></i> 数据沉淀</span></div></div><div class="visual-float"><el-icon :size="20"><DataAnalysis /></el-icon><span>从成果积累，看见专业发展</span></div></div>
      </div>
      <div class="story-bottom"><span><el-icon><Check /></el-icon> 统一成果档案</span><span><el-icon><Check /></el-icon> 私有材料存储</span><span><el-icon><Check /></el-icon> 专业认证支撑</span></div>
    </section>
    <section class="login-form-section"><div class="login-form-wrap"><div class="login-security-label"><el-icon :size="15"><Lock /></el-icon> 个人账号 · 授权访问</div><div class="login-heading"><div class="eyebrow">WELCOME TO ZHIJI</div><h2>欢迎回到工作台</h2><p>登录账号，继续记录您的每一份成果。</p></div>
      <div v-if="error" ref="errorRegion" tabindex="-1" class="login-error" role="alert"><el-alert :title="error" type="error" show-icon :closable="false" /></div>
      <el-form ref="formRef" :model="form" label-position="top" size="large" :disabled="loading" @submit.prevent="login">
        <el-form-item label="登录账号" prop="username" :rules="[{ required: true, message: '请输入您的登录账号', trigger: 'blur' }]"><el-input v-model="form.username" :prefix-icon="User" placeholder="请输入账号" autocomplete="username" maxlength="80" /></el-form-item>
        <el-form-item label="登录密码" prop="password" :rules="[{ required: true, message: '请输入您的登录密码', trigger: 'blur' }]"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入密码" autocomplete="current-password" maxlength="72" /></el-form-item>
        <div class="login-form-helper"><span v-if="selectedDemo" class="demo-filled"><el-icon><Check /></el-icon> 已填入{{ selectedDemo.label }}演示账号</span><span v-else>使用管理员分配的个人账号</span><span>忘记密码请联系管理员</span></div>
        <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">{{ loading ? '正在验证账号…' : '登录工作台' }}<el-icon v-if="!loading"><ArrowRight /></el-icon></el-button>
      </el-form>
      <div class="demo-block"><div class="demo-label"><span>本地演示体验</span><small>选择身份，自动填入账号</small></div><div class="demo-buttons" aria-label="选择本地演示身份"><button v-for="demo in demos" :key="demo.username" :class="{ selected: selectedDemo?.username === demo.username }" :aria-pressed="selectedDemo?.username === demo.username" :disabled="loading" @click="selectDemo(demo)"><el-icon :size="20"><component :is="demo.icon" /></el-icon><strong>{{ demo.label }}</strong><small>{{ demo.description }}</small><el-icon v-if="selectedDemo?.username === demo.username" class="demo-selected-icon" :size="13"><CircleCheck /></el-icon></button></div><p class="demo-note">演示账号仅用于本地体验。选择身份后点击登录，所有操作均使用真实系统数据。</p></div>
    </div><div class="login-copyright"><strong>知绩</strong><span>教学 · 科研 · 成长</span></div></section>
  </div>
</template>
