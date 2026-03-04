<script setup>
import { inject } from 'vue'
import { prettyJson } from '../../../composables/useAsyncAction'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">角色权限矩阵</h3>
        <p class="block-sub">按角色配置权限点，支持细粒度授权。</p>
      </div>
      <el-button :loading="admin.loading.roles || admin.loading.permissions" @click="admin.loadRolesAndPermissions">
        刷新权限
      </el-button>
    </div>

    <div class="query-row">
      <el-select v-model="admin.selectedRoleCode" placeholder="选择角色">
        <el-option v-for="role in admin.roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
      </el-select>
      <el-button type="primary" :loading="admin.loading.rolePermissions" @click="admin.applyRolePermissions">
        保存权限
      </el-button>
    </div>

    <div class="permission-panel">
      <section v-for="group in admin.permissionGroups" :key="group.moduleKey" class="permission-group">
        <p class="permission-group-title">{{ group.moduleKey }}</p>
        <el-checkbox-group v-model="admin.rolePermissionSelection">
          <el-checkbox v-for="item in group.items" :key="item.permissionCode" :label="item.permissionCode">
            {{ item.permissionName }}
          </el-checkbox>
        </el-checkbox-group>
      </section>
    </div>

    <pre class="json-block">{{ prettyJson(admin.selectedRoleDetail) }}</pre>
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.permission-panel {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  margin-bottom: 8px;
  max-height: 360px;
  overflow: auto;
  padding-right: 6px;
}

.permission-group {
  border: 1px solid rgba(199, 216, 204, 0.92);
  border-radius: 10px;
  background: rgba(249, 253, 250, 0.95);
  padding: 10px;
}

.permission-group-title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #3b5547;
}
</style>
