<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Lock } from "@element-plus/icons-vue";
import { authApi, type LoginPayload } from "../api/auth";
import { setAuth } from "../utils/authStorage";

const router = useRouter();
const loading = ref(false);

// 表单模型
const form = reactive<LoginPayload>({
  username: "",
  password: ""
});

// 提交登录
async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }

  loading.value = true;
  try {
    const res = await authApi.login(form);
    setAuth(res.token, res.userId, res.role);
    ElMessage.success("登录成功");
    // 跳转至首页
    router.push("/home");
  } catch (e: any) {
    ElMessage.error(e?.message ?? "网络错误");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-container">
    <!-- 背景装饰层 -->
    <div class="login-bg"></div>

    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <h2 class="title">医疗管理系统</h2>
        <p class="subtitle">Medical Management System</p>
      </div>

      <el-form :model="form" label-width="0" class="login-form">
        <el-form-item>
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            placeholder="请输入用户名"
            size="large"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          class="login-btn"
          size="large"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f5f7fa;
  position: relative;
  overflow: hidden;
}

/* 医疗风格渐变背景 */
.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  z-index: 0;
}

.login-card {
  width: 420px;
  padding: 40px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  z-index: 1;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  animation: fadeInUp 0.6s ease-out;
}

.login-header {
  text-align: center;
  margin-bottom: 35px;
}

.title {
  font-size: 26px;
  color: #303133;
  margin: 0;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
  letter-spacing: 1px;
}

.login-form {
  margin-top: 20px;
}

.login-btn {
  width: 100%;
  margin-top: 15px;
  font-size: 16px;
  letter-spacing: 2px;
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
