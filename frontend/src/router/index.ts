import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import Layout from "../components/Layout.vue";
import HomeView from "../views/HomeView.vue";
import LoginView from "../views/LoginView.vue";
import DepartmentsView from "../views/DepartmentsView.vue";
import DoctorsView from "../views/DoctorsView.vue";
import PatientsView from "../views/PatientsView.vue";
import AppointmentsView from "../views/AppointmentsView.vue";
import MedicinesView from "../views/MedicinesView.vue";
import MedicineStockLogsView from "../views/MedicineStockLogsView.vue";
import MedicalRecordsView from "../views/MedicalRecordsView.vue";
import SystemLogsView from "../views/SystemLogsView.vue";
import UserManageView from "../views/UserManageView.vue";
import AiConsultView from "../views/AiConsultView.vue";
import AiChatView from "../views/AiChatView.vue";
import { getToken } from "../utils/authStorage";

const routes: RouteRecordRaw[] = [
  // 登录页（独立路由，不使用 Layout）
  { path: "/login", name: "login", component: LoginView, meta: { title: "登录" } },
  
  // 布局页：侧边栏导航 + 子路由视图
  {
    path: "/",
    component: Layout,
    redirect: "/login",
    children: [
      // 首页：系统概览
      { path: "home", name: "home", component: HomeView, meta: { title: "系统概览" } },
      // 科室管理页：演示 "前端 <-> 后端 <-> 数据库" 完整链路
      { path: "departments", name: "departments", component: DepartmentsView, meta: { title: "科室管理" } },
      // 医生管理页：依赖科室数据，演示 CRUD + 状态切换
      { path: "doctors", name: "doctors", component: DoctorsView, meta: { title: "医生管理" } },
      // 患者档案页：预约/病历模块的基础依赖数据
      { path: "patients", name: "patients", component: PatientsView, meta: { title: "患者管理" } },
      // 预约管理页：依赖患者/医生/科室，用于挂号与预约状态维护
      { path: "appointments", name: "appointments", component: AppointmentsView, meta: { title: "预约管理" } },
      // 药品管理页：对接 /api/medicines，管理药品库存与上下架状态
      { path: "medicines", name: "medicines", component: MedicinesView, meta: { title: "药品管理" } },
      // 库存日志页：记录药品出入库操作历史
      { path: "stock-logs", name: "stock-logs", component: MedicineStockLogsView, meta: { title: "库存日志" } },
      // 病历管理页：依赖患者/医生，记录就诊诊断与处方信息
      { path: "medical-records", name: "medical-records", component: MedicalRecordsView, meta: { title: "病历管理" } },
      // 系统日志页：查看用户操作记录
      { path: "system-logs", name: "system-logs", component: SystemLogsView, meta: { title: "操作日志" } },
      // 账号管理页：管理员维护系统用户
      { path: "user-manage", name: "user-manage", component: UserManageView, meta: { title: "账号管理" } },
      // AI 智能问诊页：调用大模型 API 实现 AI 辅助问诊
      { path: "ai-consult", name: "ai-consult", component: AiConsultView, meta: { title: "AI 智能问诊" } },
      // AI 智能聊天页：多轮对话，支持会话管理
      { path: "ai-chat", name: "ai-chat", component: AiChatView, meta: { title: "AI 智能聊天" } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = getToken();
  if (to.path !== "/login" && !token) {
    next("/login");
  } else if (to.path === "/login" && token) {
    next("/home");
  } else {
    next();
  }
});

export default router;

