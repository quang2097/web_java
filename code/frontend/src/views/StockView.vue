<script setup>
import { computed, reactive, ref } from 'vue';
import { apiFetch, formatDateTime, formatVnd } from '../api/http';

const inventory = ref([]);
const suppliers = ref([]);
const imports = ref([]);
const forecast = ref([]);
const notice = ref('');
const errorText = ref('');

const importForm = reactive({
  supplierId: null,
  note: '',
  skuId: null,
  quantity: 20,
  unitCost: 15000,
  lines: [],
});

const importTotal = computed(() =>
  importForm.lines.reduce((s, l) => s + Number(l.unitCost) * l.quantity, 0)
);

async function loadAll() {
  try {
    const [inv, sups, imps, fc] = await Promise.all([
      apiFetch('/api/management/inventory'),
      apiFetch('/api/management/suppliers'),
      apiFetch('/api/management/imports'),
      apiFetch('/api/management/reports/forecast'),
    ]);
    inventory.value = inv;
    suppliers.value = sups;
    imports.value = imps;
    forecast.value = fc;
    if (sups.length && !importForm.supplierId) importForm.supplierId = sups[0].id;
    if (inv.length && !importForm.skuId) importForm.skuId = inv[0].skuId;
  } catch (err) {
    errorText.value = err.message;
  }
}

function addImportLine() {
  const item = inventory.value.find((i) => i.skuId === Number(importForm.skuId));
  if (!item || importForm.quantity <= 0) return;
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
  importForm.lines.push({
    skuId: row.skuId,
    name: row.name,
    code: row.code,
    quantity: row.suggestedImport,
    unitCost: 10000,
  });
  notice.value = `Đã đưa "${row.name}" (SL: ${row.suggestedImport}) vào phiếu nhập bên phải.`;
}

async function submitImport() {
  if (!importForm.lines.length) return;
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/management/imports', {
      method: 'POST',
      body: {
        supplierId: importForm.supplierId,
        note: importForm.note,
        lines: importForm.lines.map((l) => ({
          skuId: l.skuId,
          quantity: l.quantity,
          unitCost: l.unitCost,
        })),
      },
    });
    importForm.lines = [];
    importForm.note = '';
    notice.value = 'Đã nhập kho thành công và cộng tồn kho.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  }
}

loadAll();
</script>

<template>
  <div class="pos-grid">
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">TỒN KHO & DỰ BÁO NHẬP HÀNG (AI-3)</span>
          <h3>Dự báo nhu cầu 7 ngày tới (Weighted Moving Average 4 tuần)</h3>
        </div>
        <button type="button" class="btn-outline" @click="loadAll">Làm mới</button>
      </div>

      <p v-if="notice" class="alert ok">{{ notice }}</p>
      <p v-if="errorText" class="alert err">{{ errorText }}</p>

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
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">NHẬP KHO</span>
          <h3>Tạo phiếu nhập hàng</h3>
        </div>
      </div>

      <div class="two-col">
        <div>
          <label>Nhà cung cấp</label>
          <select v-model="importForm.supplierId">
            <option v-for="s in suppliers" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div>
          <label>Ghi chú phiếu nhập</label>
          <input v-model="importForm.note" placeholder="VD: Nhập bổ sung đầu tuần" />
        </div>
      </div>

      <div class="three-col">
        <div>
          <label>Mặt hàng</label>
          <select v-model="importForm.skuId">
            <option v-for="i in inventory" :key="i.skuId" :value="i.skuId">
              {{ i.productName }} {{ i.variant }} (Tồn: {{ i.quantity }})
            </option>
          </select>
        </div>
        <div>
          <label>Số lượng nhập</label>
          <input v-model="importForm.quantity" type="number" min="1" />
        </div>
        <div>
          <label>Giá vốn / ĐVT (VNĐ)</label>
          <input v-model="importForm.unitCost" type="number" min="0" step="500" />
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
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, idx) in importForm.lines" :key="idx">
            <td>{{ l.name }}</td>
            <td>{{ l.quantity }}</td>
            <td>{{ formatVnd(l.unitCost) }}</td>
            <td><strong>{{ formatVnd(l.unitCost * l.quantity) }}</strong></td>
          </tr>
        </tbody>
      </table>

      <div class="total-bar">
        <span>Tổng phiếu nhập</span>
        <strong>{{ formatVnd(importTotal) }}</strong>
      </div>
      <button
        type="button"
        class="btn-solid full"
        :disabled="!importForm.lines.length"
        @click="submitImport"
      >
        Xác nhận nhập kho & cộng tồn kho
      </button>

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
</template>

