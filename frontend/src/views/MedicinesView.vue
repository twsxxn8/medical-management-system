<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Plus, Edit } from "@element-plus/icons-vue";
import { medicinesApi, type CreateMedicinePayload, type Medicine, type UpdateMedicinePayload } from "../api/medicines";
import { getRole } from "../utils/authStorage";

// 仅管理员可维护药品；医生只读列表（与后端 POST/PUT 仅 ADMIN 一致）
const isAdmin = computed(() => getRole() === "ADMIN");

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<Medicine[]>([]);

// 弹窗与提交状态
const dialogOpen = ref(false);
const submitting = ref(false);

// 编辑模式：null 表示新增；有值表示编辑该 id
const editingId = ref<number | null>(null);

// 表单模型：新增与更新共用；提交时根据 editingId 走不同接口
const form = reactive<{
  name: string;
  category: string;
  specification: string;
  unit: string;
  price: number | null;
  stock: number | null;
  manufacturer: string;
  approvalNumber: string;
  storageCondition: string;
}>({
  name: "",
  category: "",
  specification: "",
  unit: "",
  price: null,
  stock: null,
  manufacturer: "",
  approvalNumber: "",
  storageCondition: ""
});

// 拉取药品列表（初次进入页面 & 新增/更新后刷新）
async function refresh() {
  loading.value = true;
  try {
    items.value = await medicinesApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!isAdmin.value) {
    ElMessage.warning("仅管理员可新增药品");
    return;
  }
  // 新增：清空表单
  editingId.value = null;
  form.name = "";
  form.category = "";
  form.specification = "";
  form.unit = "";
  form.price = null;
  form.stock = null;
  form.manufacturer = "";
  form.approvalNumber = "";
  form.storageCondition = "";
  dialogOpen.value = true;
}

function openEdit(row: Medicine) {
  if (!isAdmin.value) {
    ElMessage.warning("仅管理员可编辑药品");
    return;
  }
  // 编辑：回填表单
  editingId.value = row.id;
  form.name = row.name;
  form.category = row.category ?? "";
  form.specification = row.specification ?? "";
  form.unit = row.unit ?? "";
  form.price = row.price ?? null;
  form.stock = row.stock ?? null;
  form.manufacturer = row.manufacturer ?? "";
  form.approvalNumber = row.approvalNumber ?? "";
  form.storageCondition = row.storageCondition ?? "";
  dialogOpen.value = true;
}

async function submit() {
  if (!isAdmin.value) {
    ElMessage.warning("仅管理员可提交变更");
    return;
  }
  // 最小校验：关键必填项
  if (!form.name?.trim()) {
    ElMessage.warning("请输入药品名称");
    return;
  }
  if (!form.price || form.price <= 0) {
    ElMessage.warning("请输入正确的价格");
    return;
  }
  if (!form.stock && form.stock !== 0) {
    ElMessage.warning("请输入库存数量");
    return;
  }

  submitting.value = true;
  try {
    if (!editingId.value) {
      // 新增（POST /api/medicines）
      const payload: CreateMedicinePayload = {
        name: form.name.trim(),
        category: form.category?.trim() || undefined,
        specification: form.specification?.trim() || undefined,
        unit: form.unit?.trim() || undefined,
        price: form.price,
        stock: form.stock,
        manufacturer: form.manufacturer?.trim() || undefined,
        approvalNumber: form.approvalNumber?.trim() || undefined,
        storageCondition: form.storageCondition?.trim() || undefined
      };
      await medicinesApi.create(payload);
      ElMessage.success("新增成功");
    } else {
      // 更新（PUT /api/medicines/{id}）
      const payload: UpdateMedicinePayload = {
        name: form.name?.trim() || undefined,
        category: form.category?.trim() || undefined,
        specification: form.specification?.trim() || undefined,
        unit: form.unit?.trim() || undefined,
        price: form.price ?? undefined,
        stock: form.stock ?? undefined,
        manufacturer: form.manufacturer?.trim() || undefined,
        approvalNumber: form.approvalNumber?.trim() || undefined,
        storageCondition: form.storageCondition?.trim() || undefined
      };
      await medicinesApi.update(editingId.value, payload);
      ElMessage.success("保存成功");
    }

    dialogOpen.value = false;
    await refresh();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function toggleStatus(row: Medicine, next: boolean) {
  if (!isAdmin.value) {
    ElMessage.warning("仅管理员可修改上下架状态");
    return;
  }
  // 状态切换：与后端对齐（0=下架，1=上架）
  const status = next ? 1 : 0;
  const old = row.status;
  row.status = status; // 乐观更新：界面先变，失败再回滚
  try {
    await medicinesApi.updateStatus(row.id, status);
    ElMessage.success("状态已更新");
  } catch (e: any) {
    row.status = old;
    ElMessage.error(e?.message ?? "状态更新失败");
  }
}

function formatStatusText(status: number) {
  return status === 1 ? "上架" : "下架";
}

// 进入页面自动加载列表
onMounted(refresh);
</script>

<template>
  <section>
    <!-- 顶部栏：标题 + 新增（仅管理员） -->
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <div>
        <h2 style="margin: 0">药品管理</h2>
        <p v-if="!isAdmin" style="margin: 6px 0 0; font-size: 13px; color: #909399">当前账号为查看模式，新增与编辑仅管理员可操作</p>
      </div>
      <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新增药品</el-button>
    </div>

    <!-- 列表：对应后端 medicine 表 -->
    <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="name" label="药品名称" min-width="160" />
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="specification" label="规格" width="140" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="price" label="价格" width="110" />
      <el-table-column prop="stock" label="库存" width="100" />
      <el-table-column label="状态" width="160">
        <!-- 状态：管理员可切换；医生只读展示 -->
        <template #default="{ row }">
          <el-switch
            v-if="isAdmin"
            :model-value="row.status === 1"
            inline-prompt
            active-text="上架"
            inactive-text="下架"
            @change="(val: any) => toggleStatus(row, Boolean(val))"
          />
          <span v-else>{{ formatStatusText(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态：列表无数据时显示 -->
    <el-empty v-if="!loading && items.length === 0" description="暂无药品数据" />

    <!-- 弹窗：新增/编辑药品（提交时根据 editingId 走 create/update） -->
    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑药品' : '新增药品'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="药品名称" required>
          <el-input v-model="form.name" placeholder="例如：阿莫西林胶囊" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="form.category" placeholder="例如：抗生素" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.specification" placeholder="例如：500mg*24粒" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="例如：盒" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="form.stock" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生产厂家">
          <el-input v-model="form.manufacturer" placeholder="例如：XX制药" />
        </el-form-item>
        <el-form-item label="批准文号">
          <el-input v-model="form.approvalNumber" placeholder="例如：国药准字H12345678" />
        </el-form-item>
        <el-form-item label="储存条件">
          <el-input v-model="form.storageCondition" type="textarea" :rows="2" placeholder="例如：密封，置阴凉干燥处" />
        </el-form-item>
      </el-form>

      <template #footer>
        <!-- footer：取消/提交（提交时 loading 防止重复点击） -->
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>
