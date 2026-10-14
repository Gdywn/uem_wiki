<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const path = ref(window.location.pathname)
const authenticated = ref(false)
const csrfToken = ref('')
const username = ref('')
const password = ref('')
const status = ref('')
const rows = ref([])
const entry = ref(null)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const isLogin = computed(() => path.value === '/admin/login')
const entryId = computed(() => path.value.match(/^\/admin\/entry\/(\d+)\/?$/)?.[1] || '')

function navigate(next) {
  history.pushState({}, '', next)
  path.value = location.pathname
  error.value = ''
  notice.value = ''
  if (authenticated.value) loadPage()
}
function onPopState() { path.value = location.pathname; if (authenticated.value) loadPage() }
async function api(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) } })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok || payload.success === false) throw new Error(payload.message || `请求失败（${response.status}）`)
  return payload
}
async function checkSession() {
  try {
    const payload = await api('/api/admin/session')
    authenticated.value = !!payload.data?.authenticated
    csrfToken.value = payload.data?.csrfToken || ''
    if (authenticated.value && isLogin.value) navigate('/admin')
    else if (authenticated.value) await loadPage()
    else if (!isLogin.value) navigate('/admin/login')
  } catch (cause) { error.value = cause.message }
}
async function login() {
  error.value = ''; busy.value = true
  try {
    const payload = await api('/api/admin/login', { method: 'POST', body: JSON.stringify({ username: username.value, password: password.value }) })
    authenticated.value = true
    csrfToken.value = payload.data?.csrfToken || ''
    password.value = ''
    navigate('/admin')
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
async function logout() {
  try { await api('/api/admin/logout', { method: 'POST', body: JSON.stringify({ csrfToken: csrfToken.value }) }) }
  catch (cause) { error.value = cause.message }
  authenticated.value = false
  navigate('/admin/login')
}
async function loadPage() {
  if (!authenticated.value) { if (!isLogin.value) navigate('/admin/login'); return }
  error.value = ''
  try {
    if (entryId.value) {
      const payload = await api(`/api/admin/contributions/${entryId.value}`)
      entry.value = payload.data
    } else {
      entry.value = null
      const query = status.value ? `?status=${encodeURIComponent(status.value)}` : ''
      const payload = await api(`/api/admin/contributions${query}`)
      rows.value = payload.data || []
    }
  } catch (cause) {
    if (cause.message.includes('请先登录')) { authenticated.value = false; navigate('/admin/login') }
    else error.value = cause.message
  }
}
async function review(decision) {
  await mutate(`/api/admin/contributions/${entryId.value}/review`, { decision, csrfToken: csrfToken.value }, decision === 'APPROVED' ? '审核完成，正在发布。' : '已标记为不通过。')
}
async function publish() {
  await mutate(`/api/admin/contributions/${entryId.value}/publish`, { csrfToken: csrfToken.value }, '发布已完成。')
}
async function mutate(url, body, successMessage) {
  busy.value = true; error.value = ''; notice.value = ''
  try { await api(url, { method: 'POST', body: JSON.stringify(body) }); notice.value = successMessage }
  catch (cause) { error.value = cause.message }
  finally { busy.value = false; await loadPage() }
}
function bytes(value) { return value == null ? '' : `${(value / 1024).toFixed(1)} KiB` }
function statusText(value) { return ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '不通过', NOT_PUBLISHED: '未发布', PUBLISHING: '发布中', FAILED: '发布失败', PUBLISHED: '已发布' })[value] || value || '—' }
onMounted(() => { addEventListener('popstate', onPopState); checkSession() })
onUnmounted(() => removeEventListener('popstate', onPopState))
</script>

<template>
  <div class="site-shell admin-shell">
    <header class="topbar"><a class="brand" href="/"><img class="brand-logo" src="/圆形logo.png" alt="" /><span>应大 Wiki · 投稿管理</span></a><button v-if="authenticated" class="admin-link link-button" @click="logout">退出登录</button></header>
    <main class="page-wrap admin-wrap">
      <section v-if="isLogin && !authenticated" class="form-card admin-login">
        <p class="section-kicker">ADMINISTRATION</p><h1>管理员登录</h1><p class="intro-copy">登录后可审核投稿、下载私有附件并管理静态发布。</p>
        <form @submit.prevent="login"><label class="field"><span>账号</span><input v-model.trim="username" autocomplete="username" required /></label><label class="field"><span>密码</span><input v-model="password" type="password" autocomplete="current-password" required /></label>
          <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p><button class="submit-button" :disabled="busy"><span>{{ busy ? '正在登录…' : '登录管理后台' }}</span><span>→</span></button>
        </form>
      </section>
      <section v-else-if="authenticated" class="admin-content">
        <div class="admin-heading"><div><p class="section-kicker">REVIEW DESK</p><h1>{{ entry ? '投稿详情' : '投稿审核' }}</h1></div><button v-if="entry" class="quiet-button" @click="navigate('/admin')">← 返回列表</button></div>
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p><p v-if="notice" class="alert alert-success" role="status">{{ notice }}</p>
        <section v-if="!entry" class="form-card">
          <div class="list-toolbar"><div><h2>投稿列表</h2><p class="intro-copy">联系方式和待审资料仅在管理员登录后可见。</p></div><label class="filter-field">状态筛选<select v-model="status" @change="loadPage"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="REJECTED">不通过</option><option value="APPROVED">已通过</option></select></label></div>
          <div v-if="!rows.length" class="empty-state">当前筛选下暂无投稿。</div>
          <div v-else class="table-scroll"><table class="admin-table"><thead><tr><th>ID</th><th>标题 / 课程</th><th>投稿时间</th><th>审核状态</th><th>发布状态</th><th></th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>#{{ row.id }}</td><td><strong>{{ row.title }}</strong><small>{{ row.courseName }} · {{ row.authorName }}</small></td><td>{{ row.createdAt || '—' }}</td><td><span class="state-pill">{{ statusText(row.reviewStatus) }}</span></td><td>{{ statusText(row.publishStatus) }}</td><td><button class="text-button" @click="navigate(`/admin/entry/${row.id}`)">查看详情 →</button></td></tr></tbody></table></div>
        </section>
        <section v-else class="form-card detail-card">
          <div class="detail-title"><div><p class="section-kicker">CONTRIBUTION #{{ entry.id }}</p><h2>{{ entry.title }}</h2><p class="intro-copy">{{ entry.courseName }} · {{ entry.authorName }} · {{ entry.createdAt }}</p></div><span class="state-pill">{{ statusText(entry.reviewStatus) }}</span></div>
          <div class="detail-grid"><div><span>联系方式（仅管理员可见）</span><strong>{{ entry.contact }}</strong></div><div><span>发布状态</span><strong>{{ statusText(entry.publishStatus) }}</strong></div></div>
          <div class="body-preview"><h3>资料说明</h3><p>{{ entry.body }}</p></div>
          <div class="attachment-panel"><div><strong>{{ entry.hasAttachment ? entry.originalFilename : '未上传附件' }}</strong><small v-if="entry.hasAttachment">{{ bytes(entry.fileSize) }} · 私有附件</small></div><a v-if="entry.hasAttachment" class="quiet-button" :href="`/api/admin/contributions/${entry.id}/attachment`">下载附件</a></div>
          <p v-if="entry.publishError" class="alert alert-error">发布失败：{{ entry.publishError }}</p>
          <div class="action-row"><template v-if="entry.reviewStatus === 'PENDING'"><button class="submit-button" :disabled="busy" @click="review('APPROVED')">通过并发布 <span>→</span></button><button class="danger-button" :disabled="busy" @click="review('REJECTED')">不通过</button></template><button v-if="entry.reviewStatus === 'APPROVED' && entry.publishStatus !== 'PUBLISHED'" class="submit-button" :disabled="busy" @click="publish">{{ busy ? '正在发布…' : '导出 / 重试发布' }} <span>→</span></button><p v-if="entry.publishStatus === 'PUBLISHED'" class="published-note">静态页面已发布：export/docs/submissions/{{ entry.id }}/index.html</p></div>
        </section>
      </section>
    </main>
  </div>
</template>
