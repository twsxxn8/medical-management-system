<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Delete } from "@element-plus/icons-vue";
import { departmentsApi, type CreateDepartmentPayload, type Department } from "../api/departments";

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<Department[]>([]);

// 分页参数：current 从 1 开始，size 默认为 10
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
});

// 弹窗与提交状态
const dialogOpen = ref(false);
const submitting = ref(false);

// 表单模型
const form = reactive<CreateDepartmentPayload>({
  name: "",
  parentId: null,
  description: "",
  location: ""
});

// 拉取列表数据（支持分页）
async function fetchData() {
  loading.value = true;
  try {
    // 后端 page 从 0 开始，所以传入 current - 1
    const data = await departmentsApi.list({
      page: pagination.current - 1,
      size: pagination.size
    });
    items.value = data.content;
    pagination.total = data.totalElements;
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

// 打开新增弹窗
function openCreate() {
  form.name = "";
  form.parentId = null;
  form.description = "";
  form.location = "";
  dialogOpen.value = true;
}

// 提交新增
async function submitCreate() {
  if (!form.name?.trim()) {
    ElMessage.warning("请输入科室名称");
    return;
  }

  submitting.value = true;
  try {
    await departmentsApi.create({
      name: form.name.trim(),
      parentId: form.parentId ?? null,
      description: form.description?.trim() || undefined,
      location: form.location?.trim() || undefined
    });
    ElMessage.success("新增成功");
    dialogOpen.value = false;
    // 新增后回到第一页并刷新
    pagination.current = 1;
    await fetchData();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "新增失败");
  } finally {
    submitting.value = false;
  }
}

/**
 * 删除科室：二次确认后调用删除接口。
 * 备注：若该科室下有医生关联，后端会因外键约束返回错误。
 */
async function handleDelete(row: Department) {
  try {
    await ElMessageBox.confirm(`确认删除科室「${row.name}」吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    await departmentsApi.delete(row.id);
    ElMessage.success("删除成功");
    await fetchData();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.message ?? "删除失败");
    }
  }
}

// 进入页面自动加载列表
onMounted(fetchData);
</script>

<template>
  <section>
    <!-- 顶部栏 -->
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">科室管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增科室</el-button>
    </div>

    <!-- 列表 -->
    <el-table
        v-loading="loading"
        :data="items"
        style="width: 100%; margin-top: 14px"
        border
    >
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="parentId" label="父级ID" width="110" />
      <el-table-column prop="location" label="位置" width="140" />
      <el-table-column prop="description" label="描述" min-width="220" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
      <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          background
          @size-change="fetchData"
          @current-change="fetchData"
      />
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && items.length === 0" description="暂无科室数据" />

    <!-- 弹窗 -->
    <el-dialog v-model="dialogOpen" title="新增科室" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：内科" />
        </el-form-item>
        <el-form-item label="父级ID">
          <el-input-number v-model="form.parentId" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="例如：1F" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>
