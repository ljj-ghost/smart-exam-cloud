<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, getSavedUser } from '../../api/client'
import { useAsyncAction } from '../../composables/useAsyncAction'
import { hasAnyPermission } from '../../composables/accessControl'

const { loading, run } = useAsyncAction()

const activePage = ref('')

const createForm = reactive({
  paperId: '',
  title: '',
  startTime: '',
  endTime: '',
  antiCheatLevel: 1,
  studentIds: [],
})

const createdExam = ref(null)
const startedSession = ref(null)
const submitSessionId = ref('')
const sessionPaper = ref(null)
const answerDrafts = reactive({})
const submitResult = ref(null)
const currentRole = ref('')
const studentOptions = ref([])
const assignedExams = ref([])
const paperOptions = ref([])
const publishedExams = ref([])

const typeLabelMap = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  SHORT: '简答题',
}

const canOpenTeacherPanel = computed(
  () =>
    currentRole.value === 'ADMIN' ||
    (currentRole.value === 'TEACHER' && hasAnyPermission(getSavedUser(), ['EXAM_CREATE']))
)
const canOpenStudentPanel = computed(
  () =>
    currentRole.value === 'ADMIN' ||
    (currentRole.value === 'STUDENT' && hasAnyPermission(getSavedUser(), ['EXAM_SESSION_START']))
)
const availablePages = computed(() => {
  const pages = []
  if (canOpenTeacherPanel.value) pages.push('teacher')
  if (canOpenStudentPanel.value) pages.push('student')
  return pages
})
const pageOptions = computed(() => availablePages.value.map((value) => ({ value, label: value === 'teacher' ? '老师端' : '学生端' })))

const paperQuestions = computed(() => {
  if (!sessionPaper.value?.questions) return []
  return [...sessionPaper.value.questions].sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0))
})

const paperScore = computed(() =>
  paperQuestions.value.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
)

const answeredCount = computed(() =>
  paperQuestions.value.filter((question) => isDraftAnswered(getDraft(question.questionId), question.type)).length
)

const assignedExamCount = computed(() => assignedExams.value.length)
const publishedExamCount = computed(() => publishedExams.value.length)
const canAccessAssignedExams = computed(() => canOpenStudentPanel.value)
const canLoadStudentDirectory = computed(() => canOpenTeacherPanel.value)
const canLoadPaperDirectory = computed(
  () =>
    canOpenTeacherPanel.value &&
    hasAnyPermission(getSavedUser(), ['PAPER_DETAIL'])
)
const canLoadPublishedExams = computed(() => canOpenTeacherPanel.value)
const selectedPaperOption = computed(
  () => paperOptions.value.find((item) => String(item.id) === String(createForm.paperId || '')) || null
)
const antiCheatThrottleMs = 1500
const antiCheatLastSentAt = reactive({})

const formatDateTimeDisplay = (value) => {
  if (!value) return '-'
  const normalized = String(value).replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const judgeStatusType = (status) => {
  const value = String(status || '').toLowerCase()
  if (value.includes('运行') || value.includes('进行') || value.includes('progress') || value.includes('active')) {
    return 'success'
  }
  if (value.includes('结束') || value.includes('finish') || value.includes('closed')) {
    return 'info'
  }
  if (value.includes('异常') || value.includes('cancel') || value.includes('error')) {
    return 'danger'
  }
  return 'warning'
}

const judgeSessionStatusType = (status) => {
  const value = String(status || '').toLowerCase()
  if (value.includes('submitted') || value.includes('已提交') || value.includes('finished')) return 'success'
  if (value.includes('running') || value.includes('进行') || value.includes('started')) return 'warning'
  return 'info'
}

const toStatusText = (status) => {
  const value = String(status || '').toUpperCase()
  if (value === 'NOT_STARTED') return '未开始'
  if (value === 'RUNNING') return '进行中'
  if (value === 'FINISHED') return '已结束'
  return status || '未知'
}

const toSessionStatusText = (status) => {
  const value = String(status || '').toUpperCase()
  if (value === 'IN_PROGRESS') return '作答中'
  if (value === 'SUBMITTED') return '已提交'
  return status || '-'
}

const canStartAssignedExam = (exam) => {
  if (!exam) return false
  const status = String(exam.status || '').toUpperCase()
  if (status !== 'RUNNING') return false
  const sessionStatus = String(exam.sessionStatus || '').toUpperCase()
  return sessionStatus !== 'SUBMITTED'
}

const resolveStartActionText = (exam) => {
  if (!exam) return '开始考试'
  const status = String(exam.status || '').toUpperCase()
  if (status === 'NOT_STARTED') return '未开始'
  if (status === 'FINISHED') return '已结束'
  const sessionStatus = String(exam.sessionStatus || '').toUpperCase()
  if (sessionStatus === 'IN_PROGRESS') return '继续作答'
  if (sessionStatus === 'SUBMITTED') return '已提交'
  return '开始考试'
}

const renderStudentOptionLabel = (user) => {
  if (!user) return '-'
  const name = user.realName || user.username || String(user.id || '')
  return `${name}（${user.id}）`
}

const isUserEnabled = (status) => {
  const text = String(status ?? '').trim().toUpperCase()
  return text === '1' || text === 'ENABLED' || text === 'ACTIVE'
}

const renderPaperOptionLabel = (paper) => {
  if (!paper) return '-'
  const name = paper.name || '-'
  const score = Number(paper.totalScore) || 0
  const duration = Number(paper.timeLimitMinutes) || 0
  return `${name}（总分 ${score}，${duration} 分钟）`
}

const formatDateTimeForBackend = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const resolveCurrentSessionId = () => String(submitSessionId.value || startedSession.value?.sessionId || '').trim()

const canReportAntiCheatEvent = () => {
  if (activePage.value !== 'student') return false
  if (!resolveCurrentSessionId()) return false
  const status = String(startedSession.value?.status || submitResult.value?.status || '').toUpperCase()
  return status !== 'SUBMITTED' && status !== 'FORCE_SUBMITTED'
}

const shouldThrottleAntiCheatEvent = (eventType) => {
  const key = String(eventType || 'OTHER').trim().toUpperCase() || 'OTHER'
  const now = Date.now()
  const lastTs = antiCheatLastSentAt[key] || 0
  if (now - lastTs < antiCheatThrottleMs) {
    return true
  }
  antiCheatLastSentAt[key] = now
  return false
}

const reportAntiCheatEvent = async (eventType, metadata = {}) => {
  if (!canReportAntiCheatEvent()) return
  const normalizedEventType = String(eventType || 'OTHER').trim().toUpperCase() || 'OTHER'
  if (shouldThrottleAntiCheatEvent(normalizedEventType)) return
  const sessionId = resolveCurrentSessionId()
  if (!sessionId) return
  try {
    await api.reportAntiCheatEvent(sessionId, {
      eventType: normalizedEventType,
      metadata,
    })
  } catch (error) {
    // Anti-cheat telemetry failures must not break the answer flow.
    console.warn('anti-cheat report failed', error)
  }
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'hidden') {
    reportAntiCheatEvent('SWITCH_SCREEN', { visibilityState: 'hidden' })
  }
}

const handleWindowBlur = () => {
  reportAntiCheatEvent('WINDOW_BLUR', { source: 'window.blur' })
}

const handleCopy = (event) => {
  reportAntiCheatEvent('COPY_ATTEMPT', {
    targetTag: event?.target?.tagName || '',
  })
}

const handlePaste = (event) => {
  reportAntiCheatEvent('PASTE_ATTEMPT', {
    targetTag: event?.target?.tagName || '',
  })
}

const handleOffline = () => {
  reportAntiCheatEvent('NETWORK_DISCONNECT', { online: false })
}

const bindAntiCheatListeners = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('blur', handleWindowBlur)
  window.addEventListener('copy', handleCopy)
  window.addEventListener('paste', handlePaste)
  window.addEventListener('offline', handleOffline)
}

const unbindAntiCheatListeners = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('blur', handleWindowBlur)
  window.removeEventListener('copy', handleCopy)
  window.removeEventListener('paste', handlePaste)
  window.removeEventListener('offline', handleOffline)
}

const normalizeDraftAnswer = (type, rawValue) => {
  if (type === 'MULTI') {
    if (Array.isArray(rawValue)) return rawValue
    return []
  }
  if (type === 'JUDGE') {
    if (rawValue === true || rawValue === false) return rawValue
    return null
  }
  if (rawValue === null || rawValue === undefined) return ''
  return String(rawValue)
}

const createDefaultDraft = (question) => ({
  questionId: question.questionId,
  type: question.type,
  answerContent: normalizeDraftAnswer(question.type, null),
  markedForReview: false,
})

const getDraft = (questionId) => answerDrafts[questionId] || null

const isDraftAnswered = (draft, type) => {
  if (!draft) return false
  if (type === 'MULTI') {
    return Array.isArray(draft.answerContent) && draft.answerContent.length > 0
  }
  if (type === 'JUDGE') {
    return draft.answerContent === true || draft.answerContent === false
  }
  return String(draft.answerContent ?? '').trim().length > 0
}

const normalizeAnswerForSubmit = (question, draft) => {
  const type = question.type
  const rawValue = draft?.answerContent

  if (type === 'MULTI') {
    if (!Array.isArray(rawValue)) return []
    return rawValue.map((item) => String(item).trim()).filter((item) => item)
  }

  if (type === 'JUDGE') {
    if (rawValue === true || rawValue === false) return rawValue
    return ''
  }

  return String(rawValue ?? '')
}

const syncDraftsByPaper = () => {
  const validIds = new Set(paperQuestions.value.map((item) => item.questionId))

  paperQuestions.value.forEach((question) => {
    const existing = answerDrafts[question.questionId]
    if (!existing) {
      answerDrafts[question.questionId] = createDefaultDraft(question)
      return
    }
    existing.questionId = question.questionId
    existing.type = question.type
    existing.answerContent = normalizeDraftAnswer(question.type, existing.answerContent)
    existing.markedForReview = Boolean(existing.markedForReview)
  })

  Object.keys(answerDrafts).forEach((questionId) => {
    if (!validIds.has(questionId)) {
      delete answerDrafts[questionId]
    }
  })
}

const createExam = async () => {
  const paperId = String(createForm.paperId || '').trim()
  if (!paperId || !createForm.title.trim()) {
    ElMessage.warning('请先选择试卷并填写考试标题')
    return
  }
  if (!createForm.studentIds.length) {
    ElMessage.warning('请先选择发布学生')
    return
  }

  const payload = {
    paperId,
    title: createForm.title.trim(),
    startTime: formatDateTimeForBackend(createForm.startTime),
    endTime: formatDateTimeForBackend(createForm.endTime),
    antiCheatLevel: Number(createForm.antiCheatLevel),
    studentIds: createForm.studentIds.map((id) => String(id)),
  }

  if (!payload.startTime || !payload.endTime) {
    ElMessage.warning('请完整填写开始时间和结束时间')
    return
  }

  const data = await run('createExam', () => api.createExam(payload), {
    successMessage: '考试创建成功',
  })
  if (data) {
    createdExam.value = data
    await loadPublishedExams()
  }
}

const loadPaperOptions = async (keyword = '') => {
  if (!canLoadPaperDirectory.value) {
    paperOptions.value = []
    return
  }
  const params = {
    keyword: String(keyword || '').trim() || undefined,
    page: 1,
    size: 50,
  }
  const data = await run('listPapers', () => api.listPapers(params))
  if (!data) return
  paperOptions.value = Array.isArray(data.records) ? data.records : []
}

const searchPaperOptions = (keyword) => {
  loadPaperOptions(keyword)
}

const refreshPaperOptions = async () => {
  await loadPaperOptions('')
}

const useCreatedExamId = () => {
  if (!createdExam.value?.id) {
    ElMessage.warning('请先创建考试')
    return
  }
  activePage.value = 'student'
  if (canAccessAssignedExams.value) {
    loadAssignedExams()
    ElMessage.success('已切换到学生端，请在我的考试列表开始作答')
    return
  }
  ElMessage.info('考试已创建，请切换学生账号后在“我的考试”中开始作答')
}

const loadStudentOptions = async () => {
  if (!canLoadStudentDirectory.value) {
    studentOptions.value = []
    return
  }
  const data = await run('listUsers', () => api.listUsers())
  if (!Array.isArray(data)) {
    studentOptions.value = []
    return
  }
  studentOptions.value = data
    .filter((item) => String(item?.role || '').toUpperCase() === 'STUDENT' && isUserEnabled(item?.status))
    .map((item) => ({
      id: String(item.id),
      username: item.username || '',
      realName: item.realName || item.real_name || '',
    }))
}

const loadAssignedExams = async () => {
  if (!canAccessAssignedExams.value) {
    assignedExams.value = []
    return
  }
  const data = await run('assignedExams', () => api.listAssignedExams())
  assignedExams.value = Array.isArray(data) ? data : []
}

const loadPublishedExams = async () => {
  if (!canLoadPublishedExams.value) {
    publishedExams.value = []
    return
  }
  const data = await run('publishedExams', () => api.listPublishedExams())
  publishedExams.value = Array.isArray(data) ? data : []
}

const syncPageByCurrentRole = () => {
  const savedUser = getSavedUser()
  currentRole.value = String(savedUser?.role || '').trim().toUpperCase()
  if (!availablePages.value.length) {
    activePage.value = ''
    return
  }
  if (!availablePages.value.includes(activePage.value)) {
    activePage.value = availablePages.value[0]
  }
}

const loadSessionPaper = async (sessionId) => {
  const targetSessionId = String(sessionId || submitSessionId.value || '').trim()
  if (!targetSessionId) {
    ElMessage.warning('未找到可用会话')
    return
  }

  const data = await run('sessionPaper', () => api.getSessionPaper(targetSessionId))
  if (data) {
    submitSessionId.value = targetSessionId
    sessionPaper.value = data
    syncDraftsByPaper()
  }
}

const startExam = async (examId) => {
  const targetExamId = String(examId || '').trim()
  if (!targetExamId) {
    ElMessage.warning('考试 ID 缺失')
    return
  }
  const data = await run('startExam', () => api.startExam(targetExamId), { successMessage: '考试会话已创建' })
  if (!data) return

  startedSession.value = data
  submitSessionId.value = data.sessionId
  submitResult.value = null
  await loadSessionPaper(data.sessionId)
  await loadAssignedExams()
}

const continueSession = async (exam) => {
  if (!exam?.sessionId) {
    ElMessage.warning('该考试暂无可继续会话，请点击开始考试')
    return
  }
  startedSession.value = {
    sessionId: exam.sessionId,
    examId: exam.examId,
    status: exam.sessionStatus || 'IN_PROGRESS',
    startTime: exam.sessionStartTime || null,
    deadlineTime: exam.endTime || null,
    studentId: getSavedUser()?.id || '',
  }
  submitSessionId.value = exam.sessionId
  await loadSessionPaper(exam.sessionId)
}

const startOrContinueExam = async (exam) => {
  if (!exam) return
  const sessionStatus = String(exam.sessionStatus || '').toUpperCase()
  if (sessionStatus === 'IN_PROGRESS') {
    await continueSession(exam)
    return
  }
  if (sessionStatus === 'SUBMITTED') {
    ElMessage.warning('该考试已提交，无法再次作答')
    return
  }
  if (!canStartAssignedExam(exam)) {
    ElMessage.warning(`当前状态为${toStatusText(exam.status)}，暂不可开考`)
    return
  }
  await startExam(exam.examId)
}

const loadLatestSessionPaper = async () => {
  const sessionId = String(submitSessionId.value || startedSession.value?.sessionId || '').trim()
  if (!sessionId) {
    ElMessage.warning('当前没有可加载的会话')
    return
  }
  await loadSessionPaper(sessionId)
}

const saveAnswers = async () => {
  const sessionId = String(submitSessionId.value || startedSession.value?.sessionId || '').trim()
  if (!sessionId) {
    ElMessage.warning('请先从我的考试列表开始考试')
    return
  }
  if (!paperQuestions.value.length) {
    ElMessage.warning('请先加载会话试卷')
    return
  }

  const answers = paperQuestions.value.map((question) => {
    const draft = getDraft(question.questionId)
    return {
      questionId: question.questionId,
      answerContent: normalizeAnswerForSubmit(question, draft),
      markedForReview: Boolean(draft?.markedForReview),
    }
  })

  await run('saveAnswers', () => api.saveAnswers(sessionId, { answers }), { successMessage: '答案已保存' })
}

const submitSession = async () => {
  const sessionId = String(submitSessionId.value || startedSession.value?.sessionId || '').trim()
  if (!sessionId) {
    ElMessage.warning('请先从我的考试列表开始考试')
    return
  }

  const data = await run('submit', () => api.submitSession(sessionId), {
    successMessage: '交卷成功，正在推送阅卷任务，请稍候查看阅卷与报表',
  })
  if (data) {
    submitResult.value = data
    if (startedSession.value?.sessionId === sessionId) {
      startedSession.value = {
        ...startedSession.value,
        status: data.status || 'SUBMITTED',
      }
    }
    await loadAssignedExams()
  }
}

watch(resolveCurrentSessionId, () => {
  Object.keys(antiCheatLastSentAt).forEach((key) => {
    delete antiCheatLastSentAt[key]
  })
})

watch(activePage, (page) => {
  if (page === 'teacher') {
    if (canLoadStudentDirectory.value) {
      loadStudentOptions()
    }
    if (canLoadPaperDirectory.value) {
      loadPaperOptions()
    }
    if (canLoadPublishedExams.value) {
      loadPublishedExams()
    }
  } else if (page === 'student') {
    if (canAccessAssignedExams.value) {
      loadAssignedExams()
    }
  }
})

watch(availablePages, (pages) => {
  if (!pages.length) {
    activePage.value = ''
    return
  }
  if (!pages.includes(activePage.value)) {
    activePage.value = pages[0]
  }
})

watch(canLoadPaperDirectory, (enabled) => {
  if (!enabled) {
    paperOptions.value = []
    createForm.paperId = ''
    return
  }
  if (activePage.value === 'teacher') {
    loadPaperOptions()
  }
})

watch(canLoadPublishedExams, (enabled) => {
  if (!enabled) {
    publishedExams.value = []
    return
  }
  if (activePage.value === 'teacher') {
    loadPublishedExams()
  }
})

onMounted(async () => {
  bindAntiCheatListeners()
  syncPageByCurrentRole()
  if (canLoadStudentDirectory.value) {
    await loadStudentOptions()
  }
  if (canLoadPaperDirectory.value) {
    await loadPaperOptions()
  }
  if (canLoadPublishedExams.value) {
    await loadPublishedExams()
  }
  if (activePage.value === 'student' && canAccessAssignedExams.value) {
    await loadAssignedExams()
  }
})

onUnmounted(() => {
  unbindAntiCheatListeners()
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head mode-head">
        <div>
          <h3 class="block-title">考试业务页</h3>
          <p class="block-sub">按角色切换老师端和学生端流程（当前角色：{{ currentRole || 'UNKNOWN' }}）。</p>
        </div>
        <div class="mode-actions">
          <el-radio-group v-if="pageOptions.length" v-model="activePage" size="small">
            <el-radio-button v-for="item in pageOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <el-button text @click="syncPageByCurrentRole">按角色定位</el-button>
        </div>
      </div>
    </section>

    <section v-if="!availablePages.length" class="console-block">
      <el-empty description="当前账号没有考试模块权限" />
    </section>

    <template v-if="activePage === 'teacher'">
      <section class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">老师端：创建考试</h3>
            <p class="block-sub">绑定试卷、设置时间窗口，并明确发布给哪些学生。</p>
          </div>
        </div>

        <el-form label-position="top">
          <div class="form-grid cols-3">
            <el-form-item label="选择试卷">
              <div class="paper-picker">
                <el-select
                  v-model="createForm.paperId"
                  clearable
                  filterable
                  remote
                  reserve-keyword
                  placeholder="输入试卷名称搜索并选择"
                  :remote-method="searchPaperOptions"
                  :loading="loading.listPapers"
                  style="width: 100%"
                >
                  <el-option
                    v-for="paper in paperOptions"
                    :key="paper.id"
                    :label="renderPaperOptionLabel(paper)"
                    :value="paper.id"
                  />
                </el-select>
                <el-button :loading="loading.listPapers" @click="refreshPaperOptions">刷新</el-button>
              </div>
              <p class="hint-text">
                {{
                  selectedPaperOption
                    ? `已选：${renderPaperOptionLabel(selectedPaperOption)}`
                    : '请先选择一份试卷'
                }}
              </p>
            </el-form-item>
            <el-form-item label="考试标题">
              <el-input v-model="createForm.title" />
            </el-form-item>
            <el-form-item label="防作弊等级">
              <el-input-number v-model="createForm.antiCheatLevel" :min="1" :max="5" />
            </el-form-item>
          </div>

          <div class="form-grid cols-2">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="createForm.startTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="createForm.endTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </div>

          <el-form-item label="发布给学生">
            <el-select
              v-model="createForm.studentIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择学生（可多选）"
              :loading="loading.listUsers"
              style="width: 100%"
            >
              <el-option
                v-for="student in studentOptions"
                :key="student.id"
                :label="renderStudentOptionLabel(student)"
                :value="student.id"
              />
            </el-select>
            <p class="hint-text">已选择 {{ createForm.studentIds.length }} 名学生</p>
          </el-form-item>

          <div class="action-row">
            <el-button type="primary" :loading="loading.createExam" @click="createExam">创建考试</el-button>
            <el-button :disabled="!createdExam" @click="useCreatedExamId">去学生端答卷</el-button>
          </div>
        </el-form>
      </section>

      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">老师端结果预览</h3>
        </div>
        <el-empty v-if="!createdExam" description="暂未创建考试" />
        <div v-else class="stack-gap">
          <div class="metrics-grid cols-3">
            <article class="metric-card">
              <span>考试 ID</span>
              <strong>{{ createdExam.id || '-' }}</strong>
            </article>
            <article class="metric-card">
              <span>考试标题</span>
              <strong>{{ createdExam.title || '-' }}</strong>
            </article>
            <article class="metric-card">
              <span>状态</span>
              <el-tag :type="judgeStatusType(createdExam.status)">{{ createdExam.status || '未知' }}</el-tag>
            </article>
          </div>

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="试卷 ID">{{ createdExam.paperId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="防作弊等级">{{ createdExam.antiCheatLevel ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="发布人数">{{ createdExam.targetStudentCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatDateTimeDisplay(createdExam.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatDateTimeDisplay(createdExam.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ createdExam.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTimeDisplay(createdExam.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="发布学生" :span="2">
              <span v-if="Array.isArray(createdExam.studentIds) && createdExam.studentIds.length">
                {{ createdExam.studentIds.join(', ') }}
              </span>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </section>

      <section class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">老师端：已发布考试</h3>
            <p class="block-sub">展示当前老师已发布的考试，便于回查与复核时间窗口。</p>
          </div>
          <div class="action-row">
            <el-button :loading="loading.publishedExams" @click="loadPublishedExams">刷新列表</el-button>
          </div>
        </div>

        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>已发布考试</span>
            <strong>{{ publishedExamCount }}</strong>
          </article>
          <article class="metric-card">
            <span>最近创建</span>
            <strong>{{ publishedExams[0]?.id || '-' }}</strong>
          </article>
          <article class="metric-card">
            <span>最近状态</span>
            <strong>{{ toStatusText(publishedExams[0]?.status || '-') }}</strong>
          </article>
        </div>

        <el-empty v-if="!publishedExams.length" description="暂未查询到已发布考试" />

        <el-table v-else :data="publishedExams" size="small" max-height="320">
          <el-table-column prop="id" label="考试 ID" min-width="140" />
          <el-table-column prop="title" label="考试标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="paperId" label="试卷 ID" min-width="140" />
          <el-table-column label="时间窗口" min-width="240">
            <template #default="{ row }">
              {{ formatDateTimeDisplay(row.startTime) }} ~ {{ formatDateTimeDisplay(row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="考试状态" width="100">
            <template #default="{ row }">
              <el-tag :type="judgeStatusType(row.status)">{{ toStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetStudentCount" label="发布人数" width="90" />
        </el-table>
      </section>
    </template>

    <template v-else>
      <section class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">学生端：我的考试</h3>
            <p class="block-sub">老师发布给当前学生的考试列表，直接一键开考或继续作答。</p>
          </div>
          <div class="action-row">
            <el-button :loading="loading.assignedExams" @click="loadAssignedExams">刷新列表</el-button>
            <el-button :loading="loading.sessionPaper" @click="loadLatestSessionPaper">加载当前会话试卷</el-button>
          </div>
        </div>

        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>已分配考试</span>
            <strong>{{ assignedExamCount }}</strong>
          </article>
          <article class="metric-card">
            <span>当前会话</span>
            <strong>{{ submitSessionId || '-' }}</strong>
          </article>
          <article class="metric-card">
            <span>当前作答状态</span>
            <strong>{{ toSessionStatusText(startedSession?.status || submitResult?.status || '-') }}</strong>
          </article>
        </div>

        <el-empty v-if="!assignedExams.length" description="暂无已发布考试，请联系老师发布后再进入" />

        <el-table v-else :data="assignedExams" size="small" max-height="320">
          <el-table-column prop="examId" label="考试 ID" min-width="140" />
          <el-table-column prop="title" label="考试标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间窗口" min-width="240">
            <template #default="{ row }">
              {{ formatDateTimeDisplay(row.startTime) }} ~ {{ formatDateTimeDisplay(row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="考试状态" width="100">
            <template #default="{ row }">
              <el-tag :type="judgeStatusType(row.status)">{{ toStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="会话状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.sessionStatus" :type="judgeSessionStatusType(row.sessionStatus)">
                {{ toSessionStatusText(row.sessionStatus) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="!canStartAssignedExam(row) && String(row.sessionStatus || '').toUpperCase() !== 'IN_PROGRESS'"
                :loading="loading.startExam"
                @click="startOrContinueExam(row)"
              >
                {{ resolveStartActionText(row) }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">学生端：作答区</h3>
            <p class="block-sub">题目来自会话试卷，不展示标准答案。</p>
          </div>
        </div>

        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>题目数量</span>
            <strong>{{ paperQuestions.length }}</strong>
          </article>
          <article class="metric-card">
            <span>已作答</span>
            <strong>{{ answeredCount }}</strong>
          </article>
          <article class="metric-card">
            <span>总分</span>
            <strong>{{ paperScore }}</strong>
          </article>
        </div>

        <el-empty v-if="!paperQuestions.length" description="请先加载会话试卷后作答" />

        <div v-else class="answer-sheet">
          <article v-for="question in paperQuestions" :key="question.questionId" class="question-card">
            <header class="question-head">
              <div class="question-title">
                <strong>第 {{ question.orderNo || '-' }} 题</strong>
                <el-tag size="small">{{ typeLabelMap[question.type] || question.type }}</el-tag>
              </div>
              <span class="question-score">{{ question.score || 0 }} 分</span>
            </header>

            <p class="question-stem">{{ question.stem }}</p>

            <div class="question-input">
              <el-radio-group
                v-if="question.type === 'SINGLE'"
                v-model="answerDrafts[question.questionId].answerContent"
              >
                <el-radio v-for="option in question.options || []" :key="option.key" :value="option.key">
                  {{ option.key }}. {{ option.text }}
                </el-radio>
              </el-radio-group>

              <el-checkbox-group
                v-else-if="question.type === 'MULTI'"
                v-model="answerDrafts[question.questionId].answerContent"
              >
                <el-checkbox v-for="option in question.options || []" :key="option.key" :label="option.key">
                  {{ option.key }}. {{ option.text }}
                </el-checkbox>
              </el-checkbox-group>

              <el-radio-group
                v-else-if="question.type === 'JUDGE'"
                v-model="answerDrafts[question.questionId].answerContent"
              >
                <el-radio :value="true">正确</el-radio>
                <el-radio :value="false">错误</el-radio>
              </el-radio-group>

              <el-input
                v-else-if="question.type === 'FILL'"
                v-model="answerDrafts[question.questionId].answerContent"
                placeholder="请输入填空答案"
              />

              <el-input
                v-else
                v-model="answerDrafts[question.questionId].answerContent"
                type="textarea"
                :rows="4"
                placeholder="请输入简答答案"
              />
            </div>

            <div class="question-foot">
              <el-switch v-model="answerDrafts[question.questionId].markedForReview" />
              <span>标记为稍后检查</span>
            </div>
          </article>
        </div>

        <div class="action-row">
          <el-button :loading="loading.saveAnswers" @click="saveAnswers">保存答案</el-button>
          <el-button type="danger" :loading="loading.submit" @click="submitSession">提交试卷</el-button>
        </div>
      </section>

      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">学生端结果预览</h3>
        </div>
        <div class="stack-gap">
          <el-empty
            v-if="!startedSession && !sessionPaper && !submitResult"
            description="尚无会话结果，先开始考试并提交后查看"
          />

          <template v-else>
            <div class="metrics-grid cols-3">
              <article class="metric-card">
                <span>会话 ID</span>
                <strong>{{ startedSession?.sessionId || submitSessionId || '-' }}</strong>
              </article>
              <article class="metric-card">
                <span>会话状态</span>
                <el-tag :type="judgeSessionStatusType(startedSession?.status || submitResult?.status)">
                  {{ startedSession?.status || submitResult?.status || '未知' }}
                </el-tag>
              </article>
              <article class="metric-card">
                <span>试卷题数</span>
                <strong>{{ paperQuestions.length }}</strong>
              </article>
            </div>

            <el-descriptions v-if="startedSession" :column="2" border size="small">
              <el-descriptions-item label="考试 ID">{{ startedSession.examId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="学生 ID">{{ startedSession.studentId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">
                {{ formatDateTimeDisplay(startedSession.startTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="截止时间">
                {{ formatDateTimeDisplay(startedSession.deadlineTime) }}
              </el-descriptions-item>
            </el-descriptions>

            <el-descriptions v-if="submitResult" :column="2" border size="small">
              <el-descriptions-item label="提交状态">{{ submitResult.status || '-' }}</el-descriptions-item>
              <el-descriptions-item label="客观题得分">{{ submitResult.objectiveScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="总分">{{ submitResult.totalScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">
                {{ formatDateTimeDisplay(submitResult.submittedAt) }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.mode-head {
  align-items: center;
}

.mode-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.paper-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}

@media (max-width: 680px) {
  .paper-picker {
    grid-template-columns: 1fr;
  }
}

.answer-sheet {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.question-card {
  border: 1px solid rgba(205, 220, 210, 0.94);
  border-radius: 10px;
  background: rgba(249, 253, 249, 0.9);
  padding: 10px;
}

.question-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.question-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-score {
  font-size: 12px;
  color: var(--ink-soft);
}

.question-stem {
  margin: 0 0 8px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.question-input :deep(.el-radio-group),
.question-input :deep(.el-checkbox-group) {
  display: grid;
  gap: 8px;
}

.question-foot {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
