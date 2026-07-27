<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" class="aside">
      <div class="logo">医疗管理系统</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <!-- 首页：所有人可见 -->
        <el-menu-item index="/home">
          <el-icon><OfficeBuilding /></el-icon>
          <span>系统概览</span>
        </el-menu-item>

        <!-- 科室管理：仅管理员 (ADMIN) 可见 -->
        <el-menu-item index="/departments" v-if="userRole === 'ADMIN'">
          <el-icon><OfficeBuilding /></el-icon>
          <span>科室管理</span>
        </el-menu-item>

        <!-- 医生管理：所有人可见（或根据需要限制） -->
        <el-menu-item index="/doctors">
          <el-icon><User /></el-icon>
          <span>医生管理</span>
        </el-menu-item>

        <!-- 患者管理：所有人可见 -->
        <el-menu-item index="/patients">
          <el-icon><UserFilled /></el-icon>
          <span>患者管理</span>
        </el-menu-item>

        <!-- 预约管理：所有人可见 -->
        <el-menu-item index="/appointments">
          <el-icon><Calendar /></el-icon>
          <span>预约管理</span>
        </el-menu-item>

        <!-- 药品管理：所有人可见 -->
        <el-menu-item index="/medicines">
          <el-icon><FirstAidKit /></el-icon>
          <span>药品管理</span>
        </el-menu-item>

        <!-- 库存日志：仅管理员 (ADMIN) 可见 -->
        <el-menu-item index="/stock-logs" v-if="userRole === 'ADMIN'">
          <el-icon><Tickets /></el-icon>
          <span>库存日志</span>
        </el-menu-item>

        <!-- 病历管理：仅医生 (DOCTOR) 和管理员可见 -->
        <el-menu-item index="/medical-records" v-if="userRole === 'ADMIN' || userRole === 'DOCTOR'">
          <el-icon><Notebook /></el-icon>
          <span>病历管理</span>
        </el-menu-item>

        <!-- 操作日志：仅管理员 (ADMIN) 可见 -->
        <el-menu-item index="/system-logs" v-if="userRole === 'ADMIN'">
          <el-icon><Document /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
        <!-- 账号管理：仅管理员 (ADMIN) 可见 -->
        <el-menu-item index="/user-manage" v-if="userRole === 'ADMIN'">
          <el-icon><User /></el-icon>
          <span>账号管理</span>
        </el-menu-item>
        <!-- AI 智能问诊：所有人可见 -->
        <el-menu-item index="/ai-consult">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 智能问诊</span>
        </el-menu-item>
        <!-- AI 智能聊天：所有人可见 -->
        <el-menu-item index="/ai-chat">
          <el-icon><ChatLineSquare /></el-icon>
          <span>AI 智能聊天</span>
        </el-menu-item>

        <!-- 系统监控：仅管理员 (ADMIN) 可见 -->
        <el-menu-item index="/monitor" v-if="userRole === 'ADMIN'">
          <el-icon><Monitor /></el-icon>
          <span>系统监控</span>
        </el-menu-item>

      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div style="margin-left: auto;">
          <el-button type="danger" :icon="SwitchButton" plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { OfficeBuilding, User, UserFilled, Calendar, FirstAidKit, Notebook, Tickets, Document, SwitchButton, ChatDotRound, ChatLineSquare } from '@element-plus/icons-vue';
import { clearAuth, getRole } from "../utils/authStorage";

const router = useRouter();
const route = useRoute();

// 从 sessionStorage 读取角色（与登录、请求拦截器一致；每标签页独立）
const userRole = ref(getRole() || "DOCTOR");

// 退出登录：清除本标签页登录态并跳转
function handleLogout() {
  clearAuth();
  router.push("/login");
}
</script>

<style scoped>/* 侧边栏样式 */
.aside {
  background-color: #304156;
}
/* 顶部 Logo 区域 */
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: #263445;
}
/* 顶部栏样式：白色背景 + 底边框 */
.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
}
/* 主内容区背景 */
.main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
