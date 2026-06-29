<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { stockLogsApi, type CreateStockLogPayload, type MedicineStockLog } from "../api/medicineStockLogs";
import { medicinesApi, type Medicine } from "../api/medicines";

// 页面状态
const loading = ref(false);
const items = ref<MedicineStockLog[]>([]);
const medicineLoading = ref(false);
const medicines = ref<Medicine[]>([]);

// 筛选参数
const filter = reactive<{ medicineId: number | null }>({ medicineId: null });

// 弹窗与表单
const dialogOpen = ref(false);
const submitting = ref(false);
const form = reactive<CreateStockLogPayload>({
  medicineId: 0,
  type: 1, // 默认入库
  quantity: 0,
  operatorId: 1, // 暂时写死 1，后续由登录用户决定
  remark: ""
});

// 药品名称映射
const medicineNameById = computed(() => {
  const map = new Map<number, string>();
  for (const m of medicines.value) map.set(m.id, m.name);
  return map;
});

// 加载下拉数据
async function loadMedicines() {
  medicineLoading.value = true;
  try {
    medicines.value = await medicinesApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "药品数据加载失败");
  } finally {
    medicineLoading.value = false;
  }
}

// 获取日志列表
async function refresh() {
  loading.value = true;
  try {
    items.value = await stockLogsApi.list(filter.medicineId ? { medicineId: filter.medicineId } : undefined);
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

// 提交新增
async function submit() {
  if (!form.medicineId || !form.quantity) {
    ElMessage.warning("请选择药品并输入数量");
    return;
  }
  submitting.value = true;
  try {
    await stockLogsApi.create({ ...form });
    ElMessage.success("操作成功，库存已同步更新");
    dialogOpen.value = false;
    form.quantity = 0;
    form.remark = "";
    await refresh();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "提交失败");
  } finally {
    submitting.value = false;
  }
}

function getTypeTag(type: number) {
  return type === 1 ? "success" : "danger";
}
function getTypeText(type: number) {
  return type === 1 ? "入库" : "出库";
}

onMounted(async () => {
  await loadMedicines();
  await refresh();
});
</script>

<template>
  <section>
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">药品库存日志</h2>
      <div style="display: flex; gap: 10px; align-items: center">
        <el-select v-model="filter.medicineId" placeholder="按药品筛选" clearable filterable :loading="medicineLoading" style="width: 200px" @change="refresh" @clear="refresh">
          <el-option v-for="m in medicines" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="dialogOpen = true">新增变动</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="药品名称" min-width="160">
        <template #default="{ row }">{{ medicineNameById.get(row.medicineId) ?? row.medicineId }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }"><el-tag :type="getTypeTag(row.type)">{{ getTypeText(row.type) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="100" />
      <el-table-column prop="operatorId" label="操作人 ID" width="110" />
      <el-table-column prop="remark" label="备注" min-width="180" />
      <el-table-column prop="createTime" label="操作时间" width="180" />
    </el-table>

    <el-empty v-if="!loading && items.length === 0" description="暂无库存变动记录" />

    <el-dialog v-model="dialogOpen" title="药品出入库操作" width="500px">
      <el-form label-width="100px">
        <el-form-item label="药品" required>
          <el-select v-model="form.medicineId" filterable placeholder="请选择药品" style="width: 100%">
            <el-option v-for="m in medicines" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio :label="1">入库</el-radio>
            <el-radio :label="2">出库</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="form.quantity" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="操作人 ID">
          <el-input-number v-model="form.operatorId" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>
