<script setup>
import { computed, reactive, ref } from 'vue';
import { apiFetch, formatDateTime, formatVnd } from '../api/http';

const inventory = ref([]);
const suppliers = ref([]);
const imports = ref([]);
const forecast = ref([]);
const warehouses = ref([]);
const supplierDraft = reactive({ id: null, name: '', phone: '', address: '' });
const warehouseDraft = reactive({ code: '', name: '' });
const thresholds = reactive({});
const notice = ref('');
const saving = ref(false);
const loading = ref(false);
const errorText = ref('');

const importForm = reactive({
  supplierId: null,
  warehouseId: null,
  note: '',
  skuId: null,
  quantity: 20,
  unitCost: 15000,
  lines: [],
});

const importTotal = computed(() =>
  importForm.lines.reduce((s, l) => s + Number(l.unitCost) * l.quantity, 0)
);
const warehouseInventory = computed(() => [...new Map(inventory.value.map(i => [i.skuId, {
  ...i, quantity: inventory.value.find(row => row.skuId === i.skuId && row.warehouseId === importForm.warehouseId)?.quantity || 0,
}])).values()]);

function editSupplier() {
  const selected = suppliers.value.find(s => s.id === importForm.supplierId);
  if (selected) Object.assign(supplierDraft, selected);
}

async function createWarehouse() {
  if (saving.value) return;
  saving.value = true;
  errorText.value = '';
  try {
    const result = await apiFetch('/api/management/warehouses', { method: 'POST', body: warehouseDraft });
    await loadAll();
    importForm.warehouseId = result.id;
    warehouseDraft.code = ''; warehouseDraft.name = '';
    notice.value = 'Đã tạo kho mới.';
  } catch (err) { errorText.value = err.message; }
  finally { saving.value = false; }
}

async function createSupplier() {
  if (saving.value || !supplierDraft.name.trim()) return;
  saving.value = true;
  errorText.value = '';
  try {
    const supplier = await apiFetch(supplierDraft.id ? `/api/management/suppliers/${supplierDraft.id}` : '/api/management/suppliers', {
      method: supplierDraft.id ? 'PUT' : 'POST', body: supplierDraft,
    });
    Object.assign(supplierDraft, { id: null, name: '', phone: '', address: '' });
    await loadAll();
    importForm.supplierId = supplier.id;
    notice.value = 'Đã lưu nhà cung cấp.';
  } catch (err) { errorText.value = err.message; }
  finally { saving.value = false; }
}

async function saveThreshold(item) {
  const minQuantity = Number(thresholds[item.id]);
  if (!Number.isInteger(minQuantity) || minQuantity < 0 || minQuantity > 1000000) {
    errorText.value = 'Ngưỡng cảnh báo phải là số nguyên từ 0 đến 1.000.000.';
    return;
  }
  if (saving.value) return;
  saving.value = true;
  try {
    await apiFetch(`/api/management/inventory/${item.id}/min-quantity`, { method: 'PUT', body: { minQuantity } });
    await loadAll();
    notice.value = 'Đã cập nhật ngưỡng cảnh báo.';
  } catch (err) { errorText.value = err.message; }
  finally { saving.value = false; }
}

async function loadAll() {
  if (loading.value) return;
  loading.value = true;
  errorText.value = '';
  try {
    const [inv, sups, imps, fc, whs] = await Promise.all([
      apiFetch('/api/management/inventory'),
      apiFetch('/api/management/suppliers'),
      apiFetch('/api/management/imports'),
      apiFetch('/api/management/reports/forecast'),
      apiFetch('/api/management/warehouses'),
    ]);
    inventory.value = inv;
    suppliers.value = sups;
    imports.value = imps;
    forecast.value = fc;
    warehouses.value = whs;
    if (!importForm.warehouseId && whs.length) importForm.warehouseId = whs[0].id;
    inv.forEach(i => { thresholds[i.id] = i.minQuantity; });
    if (inv.length && !importForm.skuId) importForm.skuId = inv[0].skuId;
  } catch (err) {
    errorText.value = err.message;
  } finally {
    loading.value = false;
  }
}

function addImportLine() {
  const item = inventory.value.find((i) => i.skuId === Number(importForm.skuId));
  if (!item || !Number.isInteger(Number(importForm.quantity)) || importForm.quantity <= 0 || importForm.quantity > 1000000
      || !Number.isInteger(Number(importForm.unitCost)) || Number(importForm.unitCost) < 0 || importForm.unitCost === '') {
    errorText.value = 'Chọn mặt hàng, số lượng nguyên từ 1–1.000.000 và giá vốn hợp lệ.';
    return;
  }
  importForm.lines.push({
    skuId: item.skuId,
    name: `${item.productName} ${item.variant || ''}`.trim(),
    code: item.code,
    quantity: Number(importForm.quantity),
    unitCost: Number(importForm.unitCost),
  });
}

function quickFillFromForecast(row) {
  if (row.suggestedImport <= 0) return;
  if (importForm.lines.some(l => l.skuId === row.skuId)) {
    notice.value = 'Mặt hàng này đã có trong phiếu. Bạn có thể sửa số lượng bên dưới.';
    return;
  }
  importForm.lines.push({
    skuId: row.skuId,
    name: row.name,
    code: row.code,
    quantity: row.suggestedImport,
    unitCost: '',
  });
  notice.value = `Đã thêm "${row.name}". Nhập giá vốn thực tế trước khi xác nhận.`;
}

async function submitImport() {
  if (!importForm.lines.length) return;
  if (saving.value) return;
  saving.value = true;
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/management/imports', {
      method: 'POST',
      body: {
        supplierId: importForm.supplierId,
        warehouseId: importForm.warehouseId,
        note: importForm.note,
        lines: importForm.lines.map((l) => ({
          skuId: l.skuId,
          quantity: Number(l.quantity),
          unitCost: Number(l.unitCost),
        })),
      },
    });
    importForm.lines = [];
    importForm.note = '';
    notice.value = 'Đã nhập kho thành công và cộng tồn kho.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  } finally {
    saving.value = false;
  }
}

loadAll();
</script>

<template>
  <fieldset class="view-fields" :disabled="saving" :aria-busy="saving || loading">
  <p v-if="loading" class="loading-hint" role="status">Đang tải dữ liệu…</p>
  <div class="pos-grid">
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">TỒN KHO & DỰ BÁO NHẬP HÀNG</span>
          <h3>Dự báo nhu cầu 7 ngày tới (Weighted Moving Average 4 tuần)</h3>
        </div>
        <button type="button" class="btn-outline" :disabled="loading" @click="loadAll">Làm mới</button>
      </div>

      <p v-if="notice" class="alert ok" role="status">{{ notice }}</p>
      <p v-if="errorText" class="alert err" role="alert">{{ errorText }}</p>

      <table class="data-table">
        <thead>
          <tr>
            <th>Mặt hàng</th>
            <th>Tồn / Ngưỡng</th>
            <th>Bán 4 tuần (W1..W4)</th>
            <th>Dự báo 7 ngày</th>
            <th>Gợi ý nhập</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in forecast" :key="r.skuId">
            <td>
              <strong>{{ r.name }}</strong>
              <div class="meta">{{ r.code }} · {{ r.unit }}</div>
            </td>
            <td>
              <span :class="{ 'low-text': r.currentStock <= r.minQuantity }">
                <strong>{{ r.currentStock }}</strong> / {{ r.minQuantity }}
              </span>
            </td>
            <td><code>{{ r.weeklySales.join(' / ') }}</code></td>
            <td>{{ r.forecastNext7Days }} {{ r.unit }}</td>
            <td>
              <span :class="['tag', r.suggestedImport > 0 ? 'warn' : 'ok']">
                {{ r.suggestedImport > 0 ? `+${r.suggestedImport} ${r.unit}` : 'Đủ hàng' }}
              </span>
            </td>
            <td>
              <button
                v-if="r.suggestedImport > 0"
                type="button"
                class="btn-sm"
                @click="quickFillFromForecast(r)"
              >
                + Đưa vào phiếu
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <h4>Tồn kho theo kho & ngưỡng cảnh báo</h4>
      <table class="data-table">
        <thead><tr><th>Mặt hàng / Kho</th><th>Tồn</th><th>Ngưỡng tối thiểu</th></tr></thead>
        <tbody>
          <tr v-for="item in inventory" :key="item.id">
            <td>{{ item.productName }} {{ item.variant }}<div class="meta">{{ item.warehouseName }}</div></td>
            <td>{{ item.quantity }}</td>
            <td><form class="bar-row compact" @submit.prevent="saveThreshold(item)">
              <input v-model="thresholds[item.id]" type="number" min="0" max="1000000" step="1" required :aria-label="`Ngưỡng cảnh báo ${item.productName} tại ${item.warehouseName}`" />
              <button class="btn-sm" type="submit">Lưu</button>
            </form></td>
          </tr>
          <tr v-if="!loading && !inventory.length"><td colspan="3" class="empty-hint">Chưa có tồn kho. Tạo sản phẩm trong Hàng hoá & Giá bán.</td></tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">NHẬP KHO</span>
          <h3>Tạo phiếu nhập hàng</h3>
        </div>
      </div>

      <form class="bar-row compact" @submit.prevent="createSupplier">
        <input v-model="supplierDraft.name" required maxlength="160" aria-label="Tên nhà cung cấp" placeholder="Tên nhà cung cấp" />
        <input v-model="supplierDraft.phone" maxlength="32" aria-label="Điện thoại nhà cung cấp" placeholder="Điện thoại" />
        <input v-model="supplierDraft.address" maxlength="300" aria-label="Địa chỉ nhà cung cấp" placeholder="Địa chỉ" />
        <button type="submit" class="btn-outline">{{ supplierDraft.id ? 'Lưu nhà cung cấp' : '+ Nhà cung cấp' }}</button>
        <button v-if="supplierDraft.id" type="button" class="btn-ghost" @click="Object.assign(supplierDraft, {id: null, name: '', phone: '', address: ''})">Đóng</button>
      </form>
      <details>
        <summary>Thêm kho</summary>
        <form class="bar-row compact" @submit.prevent="createWarehouse">
          <input v-model="warehouseDraft.code" required maxlength="32" pattern="[A-Za-z0-9_\-]{1,32}" aria-label="Mã kho mới" placeholder="Mã kho" />
          <input v-model="warehouseDraft.name" required maxlength="120" aria-label="Tên kho mới" placeholder="Tên kho" />
          <button type="submit" class="btn-outline">+ Kho</button>
        </form>
        <p class="meta">Quầy thu ngân bán từ kho được tạo đầu tiên. Kho khác được theo dõi riêng.</p>
      </details>
      <form @submit.prevent="submitImport">
      <label for="import-warehouse">Kho nhận hàng</label>
      <select id="import-warehouse" v-model="importForm.warehouseId" required>
        <option v-for="w in warehouses" :key="w.id" :value="w.id">{{ w.name }}</option>
      </select>
      <div class="two-col">
        <div>
          <label for="stockview-field-1">Nhà cung cấp</label>
          <select id="stockview-field-1" v-model="importForm.supplierId">
            <option :value="null">Không chọn nhà cung cấp</option>
            <option v-for="s in suppliers" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
          <button type="button" class="btn-ghost" :disabled="!importForm.supplierId" @click="editSupplier">Sửa nhà cung cấp</button>
        </div>
        <div>
          <label for="stockview-field-2">Ghi chú phiếu nhập</label>
          <input id="stockview-field-2" v-model="importForm.note" maxlength="500" placeholder="VD: Nhập bổ sung đầu tuần" />
        </div>
      </div>

      <div class="three-col">
        <div>
          <label for="stockview-field-3">Mặt hàng</label>
          <select id="stockview-field-3" v-model="importForm.skuId">
            <option v-for="i in warehouseInventory" :key="i.skuId" :value="i.skuId">
              {{ i.productName }} {{ i.variant }} (Tồn: {{ i.quantity }})
            </option>
          </select>
        </div>
        <div>
          <label for="stockview-field-4">Số lượng nhập</label>
          <input id="stockview-field-4" v-model="importForm.quantity" type="number" min="1" max="1000000" step="1" />
        </div>
        <div>
          <label for="stockview-field-5">Giá vốn / ĐVT (VNĐ)</label>
          <input id="stockview-field-5" v-model="importForm.unitCost" type="number" min="0" max="999999999999" step="1" />
        </div>
      </div>
      <button type="button" class="btn-outline" style="margin-top: 10px" @click="addImportLine">
        + Thêm dòng vào phiếu nhập
      </button>

      <table v-if="importForm.lines.length" class="data-table" style="margin-top: 14px">
        <thead>
          <tr>
            <th>Mặt hàng</th>
            <th>SL</th>
            <th>Giá vốn</th>
            <th>Thành tiền</th>
            <th><span class="sr-only">Thao tác</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, idx) in importForm.lines" :key="idx">
            <td>{{ l.name }}</td>
            <td><input v-model.number="l.quantity" type="number" required min="1" max="1000000" step="1" :aria-label="`Số lượng ${l.name}`" /></td>
            <td><input v-model.number="l.unitCost" type="number" required min="0" max="999999999999" step="1" :aria-label="`Giá vốn ${l.name}`" /></td>
            <td><strong>{{ formatVnd(l.unitCost * l.quantity) }}</strong></td>
            <td><button type="button" class="btn-ghost" :aria-label="`Xoá ${l.name}`" @click="importForm.lines.splice(idx, 1)">Xoá</button></td>
          </tr>
        </tbody>
      </table>

      <div class="total-bar">
        <span>Tổng phiếu nhập</span>
        <strong>{{ formatVnd(importTotal) }}</strong>
      </div>
      <button
        type="submit"
        class="btn-solid full"
        :disabled="!importForm.lines.length || saving"
      >
        Xác nhận nhập kho & cộng tồn kho
      </button>
      </form>

      <h4 style="margin-top: 24px">Phiếu nhập gần đây</h4>
      <ul class="receipt-lines">
        <li v-for="imp in imports" :key="imp.id">
          <span>
            #{{ imp.id }} · {{ imp.supplierName || 'NCC lẻ' }} · {{ formatDateTime(imp.createdAt) }}
          </span>
          <strong>{{ formatVnd(imp.total) }}</strong>
        </li>
      </ul>
    </section>
  </div>
  </fieldset>
</template>

