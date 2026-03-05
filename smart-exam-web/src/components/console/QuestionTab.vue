<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AUTH_CHANGED_EVENT, getSavedUser } from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'

const route = useRoute()
const router = useRouter()
const authVersion = ref(0)

const tabs = [
  {
    path: '/questions/create',
    label: '创建题目',
    desc: '录入题目、答案与解析',
    permissionsAny: ['QUESTION_CREATE'],
  },
  {
    path: '/questions/library',
    label: '题目检索',
    desc: '按类型检索并查看详情',
    permissionsAny: ['QUESTION_LIST', 'QUESTION_DETAIL'],
  },
]
const currentUser = computed(() => {
  authVersion.value
  return getSavedUser()
})
const visibleTabs = computed(() => tabs.filter((item) => hasAnyPermission(currentUser.value, item.permissionsAny)))

const activeTab = computed({
  get: () => {
    const defaultPath = visibleTabs.value[0]?.path || ''
    if (!defaultPath) return ''
    const current = visibleTabs.value.find((item) => route.path.startsWith(item.path))
    return current?.path || defaultPath
  },
  set: (path) => {
    if (route.path !== path) {
      router.push(path)
    }
  },
})

const syncVisibleRoute = () => {
  if (!visibleTabs.value.length) return
  const matched = visibleTabs.value.some((item) => route.path.startsWith(item.path))
  if (matched) return
  const fallbackPath = visibleTabs.value[0].path
  if (route.path !== fallbackPath) {
    router.replace(fallbackPath)
  }
}

const onAuthChanged = () => {
  authVersion.value += 1
}

onMounted(() => {
  window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})

watch([visibleTabs, () => route.path], syncVisibleRoute, { immediate: true })
</script>

<template>
  <div class="stack-gap">
    <section class="console-block">
      <div class="block-head question-shell-head">
        <div>
          <h3 class="block-title">题库中心分流页</h3>
          <p class="block-sub">将录题与检索拆分为独立页面，降低单页状态和渲染压力。</p>
        </div>
        <el-radio-group v-if="visibleTabs.length" v-model="activeTab" size="small">
          <el-radio-button v-for="item in visibleTabs" :key="item.path" :value="item.path">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="visibleTabs.length" class="question-tab-hints">
        <span v-for="item in visibleTabs" :key="item.path" class="hint-chip">
          {{ item.label }}: {{ item.desc }}
        </span>
      </div>
      <el-empty v-else description="当前账号没有题库页面权限" />
    </section>

    <router-view v-if="visibleTabs.length" />
  </div>
</template>

<style scoped>
.question-shell-head {
  align-items: center;
}

.question-tab-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hint-chip {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--ink-soft);
  border: 1px solid rgba(204, 220, 208, 0.95);
  background: rgba(247, 252, 248, 0.92);
}

@media (max-width: 920px) {
  .question-shell-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
