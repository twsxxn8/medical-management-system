<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { usersApi, type User } from "../api/users";

const loading = ref(false);
const items = ref<User[]>([]);
const dialogOpen = ref(false);
const submitting = ref(false);

// 表单模型
const form = reactive({
  username: "",
  password: "",
  realName: "",
  role: "DOCTOR",
  phone: ""
});

// 拉取列表
async function refresh() {
  loading.value = true;
  try {
    items.value = await usersApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

// 提交新增
async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning("用户名和密码必填");
    return;
  }
  submitting.value = true;
  try {
    await usersApi.create(form);
    ElMessage.success("账号创建成功");
    dialogOpen.value = false;
    // 重置表单
    form.username = "";
    form.password = "";
    form.realName = "";
    form.phone = "";
    await refresh();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "提交失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <section>
    <!-- 顶部栏 -->
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">医生账号管理</h2>
      <el-button type="primary" :icon="Plus" @click="dialogOpen = true">新增账号</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="items" border style="width: 100%; margin-top: 14px">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="role" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'success'">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-popconfirm title="确定删除该账号吗？" @confirm="usersApi.delete(row.id).then(refresh)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogOpen" title="新增医生账号" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="医生" value="DOCTOR" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>
