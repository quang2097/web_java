<script setup>
import { ref } from 'vue';
import { apiFetch, formatDateTime, formatVnd } from '../api/http';
import InvoicePayment from '../components/InvoicePayment.vue';

const invoices = ref([]);
const selected = ref(null);
const errorText = ref('');
const loading = ref(false);

async function onInvoiceUpdated(invoice) {
  selected.value = invoice;
  await load();
}
function printReceipt() { window.print(); }

async function load() {
  if (loading.value) return;
  loading.value = true;
  errorText.value = '';
  try {
    invoices.value = await apiFetch('/api/pos/invoices?limit=50');
    const currentId = selected.value?.id;
    selected.value = invoices.value.find(i => i.id === currentId) || invoices.value[0] || null;
  } catch (err) {
    errorText.value = err.message;
  } finally { loading.value = false; }
}

load();
</script>

<template>
  <div class="pos-grid">
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">LỊCH SỬ BÁN HÀNG</span>
          <h3>50 hoá đơn gần nhất</h3>
        </div>
        <button type="button" class="btn-outline" :disabled="loading" @click="load">Làm mới</button>
      </div>
      <p v-if="errorText" role="alert" class="alert err">{{ errorText }}</p>
      <p v-if="loading" role="status" class="loading-hint">Đang tải hoá đơn…</p>

      <table class="data-table">
        <thead>
          <tr>
            <th>Mã HĐ</th>
            <th>Thời gian</th>
            <th>Thu ngân</th>
            <th>Tổng tiền</th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="inv in invoices"
            :key="inv.id"
            :class="{ 'row-active': selected?.id === inv.id }"
          >
            <td><button type="button" class="text-button" :aria-pressed="selected?.id === inv.id" @click="selected = inv">{{ inv.code }}</button></td>
            <td>{{ formatDateTime(inv.paidAt || inv.createdAt) }}</td>
            <td>{{ inv.cashier }}</td>
            <td><strong>{{ formatVnd(inv.total) }}</strong></td>
            <td>
              <span :class="['tag', inv.status === 'PAID' ? 'ok' : inv.status === 'DRAFT' ? 'warn' : 'muted-tag']">
                {{ inv.status === 'PAID' ? 'Đã TT' : inv.status === 'DRAFT' ? 'Nháp' : 'Đã huỷ' }}
              </span>
            </td>
          </tr>
          <tr v-if="!loading && !invoices.length"><td colspan="5" class="empty-hint">Chưa có hoá đơn. Bắt đầu bán hàng tại quầy thu ngân.</td></tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">CHI TIẾT HOÁ ĐƠN</span>
          <h3>{{ selected ? selected.code : 'Chọn một hoá đơn' }}</h3>
        </div>
      </div>

      <div v-if="selected" :class="{ 'receipt-print': selected.status === 'PAID' }">
        <h3>{{ selected.code }} · {{ selected.status === 'PAID' ? 'Đã thanh toán' : selected.status === 'DRAFT' ? 'Chờ thanh toán' : 'Đã huỷ' }}</h3>
        <p class="meta">{{ formatDateTime(selected.paidAt || selected.createdAt) }}</p>
        <button v-if="selected.status === 'PAID'" type="button" class="btn-outline no-print" @click="printReceipt">In hoá đơn</button>
        <p class="meta">
          Thu ngân: <strong>{{ selected.cashier }}</strong> · Khách hàng:
          <strong>{{ selected.customerName || 'Khách lẻ' }}</strong>
          <span v-if="selected.customerPhone">({{ selected.customerPhone }})</span>
        </p>
        <p v-if="selected.payment" class="meta">
          Thanh toán:
          <strong>{{ selected.payment.method === 'TRANSFER' ? 'Chuyển khoản QR' : 'Tiền mặt' }}</strong>
          · Khách đưa: {{ formatVnd(selected.payment.received) }}
          · Tiền thừa: {{ formatVnd(selected.payment.changeAmount) }}
        </p>

        <table class="data-table">
          <thead>
            <tr>
              <th>Mã SKU</th>
              <th>Tên mặt hàng</th>
              <th>SL</th>
              <th>Đơn giá</th>
              <th>Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in selected.lines" :key="l.skuId">
              <td>{{ l.code }}</td>
              <td>{{ l.name }}</td>
              <td>{{ l.quantity }} {{ l.unit }}</td>
              <td>{{ formatVnd(l.unitPrice) }}</td>
              <td><strong>{{ formatVnd(l.amount) }}</strong></td>
            </tr>
          </tbody>
        </table>

        <div class="total-bar">
          <span>Tổng cộng</span>
          <strong>{{ formatVnd(selected.total) }}</strong>
        </div>
        <InvoicePayment v-if="selected.status === 'DRAFT'" :key="selected.id" :invoice="selected" @paid="onInvoiceUpdated" @cancelled="onInvoiceUpdated" />
      </div>
    </section>
  </div>
</template>

