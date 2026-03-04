<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight, DataAnalysis, TrendCharts } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { consoleModules, recommendedFlow } from './router/consoleModules'

const route = useRoute()
const router = useRouter()

const nowText = ref(new Date().toLocaleString())
let timer = null

const isModuleRoute = (modulePath, currentPath = route.path) =>
  currentPath === modulePath || currentPath.startsWith(`${modulePath}/`)

const activeModule = computed(() => consoleModules.find((item) => isModuleRoute(item.path)) || consoleModules[0])
const activeIndex = computed(() => consoleModules.findIndex((item) => item.path === activeModule.value.path))
const progressPercent = computed(() => Math.round(((activeIndex.value + 1) / consoleModules.length) * 100))

const goTo = (path) => {
  if (route.path !== path) {
    router.push(path)
  }
}

const goPrev = () => {
  if (activeIndex.value <= 0) return
  goTo(consoleModules[activeIndex.value - 1].path)
}

const goNext = () => {
  if (activeIndex.value >= consoleModules.length - 1) return
  goTo(consoleModules[activeIndex.value + 1].path)
}

onMounted(() => {
  timer = window.setInterval(() => {
    nowText.value = new Date().toLocaleString()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="app-shell">
    <div class="bg-halo halo-left"></div>
    <div class="bg-halo halo-right"></div>

    <header class="app-header reveal-up">
      <div class="brand-panel">
        <div class="brand-mark">SE</div>
        <div>
          <p class="brand-kicker">Smart Exam Cloud</p>
          <h1 class="brand-title">运营控制台</h1>
          <p class="brand-sub">页面已拆分为独立路由，桌面端可直接并行处理不同业务模块。</p>
        </div>
      </div>

      <div class="header-metrics">
        <div class="metric-pill">
          <span>当前时间</span>
          <strong>{{ nowText }}</strong>
        </div>
        <div class="metric-pill">
          <span>当前页面</span>
          <strong>{{ activeModule.label }}</strong>
        </div>
        <div class="metric-pill metric-pill-progress">
          <span>流程进度</span>
          <strong>{{ progressPercent }}%</strong>
          <el-progress :percentage="progressPercent" :show-text="false" :stroke-width="6" />
        </div>
      </div>
    </header>

    <main class="app-main reveal-up delay-1">
      <aside class="side-nav card-surface">
        <div class="side-nav-head">
          <p class="side-nav-kicker">Navigation</p>
          <h2>业务页面</h2>
        </div>

        <div class="side-nav-list">
          <button
            v-for="(module, index) in consoleModules"
            :key="module.path"
            type="button"
            class="side-nav-item"
            :class="{ active: isModuleRoute(module.path) }"
            @click="goTo(module.path)"
          >
            <span class="side-nav-order">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="side-nav-main">
              <span class="side-nav-label">{{ module.label }}</span>
              <span class="side-nav-tagline">{{ module.tagline }}</span>
            </span>
            <span class="side-nav-icon">
              <el-icon>
                <component :is="module.icon" />
              </el-icon>
            </span>
          </button>
        </div>
      </aside>

      <section class="workspace card-surface">
        <div class="workspace-head">
          <div>
            <p class="workspace-kicker">Current Page</p>
            <h2>{{ activeModule.label }}</h2>
            <p class="workspace-desc">{{ activeModule.description }}</p>
          </div>

          <div class="workspace-actions">
            <el-button plain :disabled="activeIndex <= 0" @click="goPrev">
              <el-icon><ArrowLeft /></el-icon>
              上一页
            </el-button>
            <el-button type="primary" :disabled="activeIndex >= consoleModules.length - 1" @click="goNext">
              下一页
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="workspace-body">
          <router-view v-slot="{ Component }">
            <transition name="route-fade" mode="out-in">
              <component :is="Component" :key="route.path" />
            </transition>
          </router-view>
        </div>
      </section>

      <aside class="tips-rail">
        <el-card class="guide-card" shadow="never">
          <template #header>
            <div class="guide-head">
              <el-icon><TrendCharts /></el-icon>
              <strong>操作建议</strong>
            </div>
          </template>
          <ol class="guide-list">
            <li v-for="(tip, index) in activeModule.tips" :key="`${activeModule.name}-${index}`">
              {{ tip }}
            </li>
          </ol>
        </el-card>

        <el-card class="guide-card" shadow="never">
          <template #header>
            <div class="guide-head">
              <el-icon><DataAnalysis /></el-icon>
              <strong>推荐路径</strong>
            </div>
          </template>
          <p class="guide-path">{{ recommendedFlow }}</p>
        </el-card>
      </aside>
    </main>
  </div>
</template>
