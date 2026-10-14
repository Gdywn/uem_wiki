<script setup>
import { computed, ref } from 'vue'

const form = ref({ title: '', course: '', body: '', author: '', contact: '' })
const file = ref(null)
const fileInput = ref(null)
const busy = ref(false)
const result = ref(null)
const error = ref('')
const fileSize = computed(() => file.value ? `${(file.value.size / 1024 / 1024).toFixed(2)} MiB` : '')

function selectFile(event) {
  error.value = ''
  const selected = event.target.files?.[0] ?? null
  if (!selected) { file.value = null; return }
  const extension = selected.name.split('.').pop()?.toLowerCase()
  if (selected.size > 10 * 1024 * 1024) error.value = '附件不能超过 10 MiB。'
  else if (!['pdf', 'docx', 'pptx'].includes(extension)) error.value = '请选择 PDF、DOCX 或 PPTX 文件。'
  if (error.value) { event.target.value = ''; file.value = null; return }
  file.value = selected
}

async function submit() {
  error.value = ''
  result.value = null
  const data = new FormData()
  for (const [key, value] of Object.entries(form.value)) data.append(key, value)
  if (file.value) data.append('file', file.value)
  busy.value = true
  try {
    const response = await fetch('/api/contributions', { method: 'POST', body: data })
    const payload = await response.json()
    if (!response.ok || !payload.success) throw new Error(payload.message || '提交失败，请稍后重试。')
    result.value = payload
    form.value = { title: '', course: '', body: '', author: '', contact: '' }
    file.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '网络暂时不可用，请稍后重试。'
  } finally { busy.value = false }
}
</script>

<template>
  <div class="site-shell">
    <header class="topbar">
      <a class="brand" href="/" aria-label="应大 Wiki 首页"><img class="brand-logo" src="/圆形logo.png" alt="" /><span>应大 Wiki</span></a>
      <a class="admin-link" href="/admin/login">管理员入口 <span aria-hidden="true">↗</span></a>
    </header>
    <main class="page-wrap">
      <section class="intro">
        <p class="eyebrow">CAMPUS KNOWLEDGE · SHARE WITH CARE</p>
        <h1>把有用的资料，<span>分享给下一位同学。</span></h1>
        <p class="intro-copy">无需注册。提交后由管理员审核，通过的内容会整理进应大 Wiki。</p>
      </section>
      <section class="form-card" aria-labelledby="form-title">
        <div class="card-heading"><div><p class="section-kicker">CONTRIBUTE</p><h2 id="form-title">资料投稿</h2></div><span class="review-note"><span class="status-dot"></span>人工审核后发布</span></div>
        <form @submit.prevent="submit">
          <div class="field-grid">
            <label class="field"><span>资料标题 <b>*</b></span><input v-model.trim="form.title" name="title" maxlength="200" placeholder="例如：高等数学期末复习提纲" required /></label>
            <label class="field"><span>课程名称 <b>*</b></span><input v-model.trim="form.course" name="course" maxlength="200" placeholder="填写课程全名" required /></label>
          </div>
          <label class="field"><span>资料说明 <b>*</b></span><textarea v-model="form.body" name="body" rows="7" placeholder="介绍资料内容、适用范围或使用建议……" required></textarea><small>纯文本即可，换行会保留。</small></label>
          <div class="field-grid">
            <label class="field"><span>投稿署名 <b>*</b></span><input v-model.trim="form.author" name="author" maxlength="100" placeholder="可使用昵称" required /></label>
            <label class="field"><span>联系方式 <b>*</b><em>仅管理员可见</em></span><input v-model.trim="form.contact" name="contact" maxlength="255" placeholder="邮箱、QQ 或其他便于联系的方式" required /></label>
          </div>
          <div class="field"><span>上传附件 <em>选填</em></span><label class="file-drop" :class="{ 'has-file': file }">
            <input ref="fileInput" type="file" accept=".pdf,.docx,.pptx,application/pdf" @change="selectFile" />
            <span class="upload-icon" aria-hidden="true">↑</span><span class="file-copy"><strong>{{ file ? file.name : '点击选择文件' }}</strong><small>{{ file ? fileSize : '支持 PDF、DOCX、PPTX，单个文件不超过 10 MiB' }}</small></span><span v-if="file" class="file-change">更换</span>
          </label></div>
          <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
          <div v-if="result" class="alert alert-success" role="status"><strong>投稿提交成功</strong><span>{{ result.message }} · 编号 {{ result.data?.id }}</span></div>
          <div class="form-footer"><p>提交即表示你确认资料适合在校园 Wiki 公开分享。联系方式不会出现在公开页面。</p><button class="submit-button" type="submit" :disabled="busy"><span>{{ busy ? '正在提交…' : '提交资料' }}</span><span aria-hidden="true">→</span></button></div>
        </form>
      </section>
      <footer class="page-footer">应大 Wiki <span>·</span> 学生共建的校园指南</footer>
    </main>
  </div>
</template>
