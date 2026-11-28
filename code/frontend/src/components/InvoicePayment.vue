<script setup>
import { computed, ref } from 'vue';
import { apiFetch, formatVnd } from '../api/http';

const props = defineProps({ invoice: { type: Object, required: true } });
const emit = defineEmits(['paid', 'cancelled']);
const method = ref('CASH');
const received = ref(String(props.invoice.total));
const transferConfirmed = ref(false);
const transferRef = ref('');
const busy = ref(false);
const error = ref('');
const qrFailed = ref(false);
const validCash = computed(() => received.value !== '' && Number.isInteger(Number(received.value))
  && Number(received.value) >= Number(props.invoice.total) && Number(received.value) <= 999999999999999);
const change = computed(() => Math.max(0, Number(received.value || 0) - Number(props.invoice.total)));

async function submit(action) {
  if (busy.value) return;
  if (action === 'pay' && (method.value === 'CASH' ? !validCash.value : !transferConfirmed.value || !props.invoice.qrUrl)) return;
  busy.value = true;
  error.value = '';
  try {
    const result = await apiFetch(`/api/pos/invoices/${props.invoice.id}/${action}`, {
      method: 'POST',
      ...(action === 'pay' ? { body: { method: method.value, received: Number(received.value), transferRef: transferRef.value.trim() || null } } : {}),
    });
    emit(result.status === 'PAID' ? 'paid' : 'cancelled', result);
  } catch (err) {
    // The server may have committed before the connection was interrupted.
    try {
      const current = await apiFetch(`/api/pos/invoices/${props.invoice.id}`);
      if (current.status !== 'DRAFT') {
        emit(current.status === 'PAID' ? 'paid' : 'cancelled', current);
        return;
      }
    } catch { /* Keep the draft available for a later retry. */ }
    error.value = err.message;
  } finally { busy.value = false; }
}
</script>

<template>
  <fieldset class="view-fields payment-panel" :disabled="busy" :aria-busy="busy">
    <div class="pay-header">
      <strong>Hoá đơn {{ invoice.code }} · {{ formatVnd(invoice.total) }}</strong>
      <span class="tag warn">Chờ thanh toán</span>
    </div>
    <p v-if="error" role="alert" class="alert err">{{ error }}</p>
    <div class="method-tabs" aria-label="Phương thức thanh toán">
      <button type="button" :class="['tab-btn', { active: method === 'CASH' }]" :aria-pressed="method === 'CASH'" @click="method = 'CASH'">Tiền mặt</button>
      <button type="button" :class="['tab-btn', { active: method === 'TRANSFER' }]" :aria-pressed="method === 'TRANSFER'" :disabled="!invoice.qrUrl" @click="method = 'TRANSFER'">Chuyển khoản VietQR</button>
    </div>
    <p v-if="!invoice.qrUrl" class="meta">Chuyển khoản chưa khả dụng. Vui lòng thanh toán tiền mặt.</p>
    <form @submit.prevent="submit('pay')">
      <div v-if="method === 'CASH'" class="cash-area">
        <label :for="`cash-${invoice.id}`">Số tiền khách đưa (VNĐ)</label>
        <input :id="`cash-${invoice.id}`" v-model="received" type="number" :min="Number(invoice.total)" max="999999999999999" step="1" required />
        <p class="change-line">Tiền thừa trả khách: <strong>{{ formatVnd(change) }}</strong></p>
      </div>
      <div v-else class="qr-area">
        <p>Nội dung chuyển khoản: <strong>{{ invoice.transferContent }}</strong></p>
        <img v-if="invoice.qrUrl && !qrFailed" :src="invoice.qrUrl" alt="Mã VietQR thanh toán" class="qr-img" @error="qrFailed = true" />
        <p v-if="qrFailed" class="alert err" role="alert">Không tải được mã QR. Thử lại hoặc chọn tiền mặt.</p>
        <p class="meta">Kiểm tra tiền đã vào tài khoản ngân hàng trước khi xác nhận. Hệ thống không tự đối soát ngân hàng.</p>
        <label class="check-label"><input v-model="transferConfirmed" type="checkbox" required /> Tôi đã kiểm tra và nhận đủ {{ formatVnd(invoice.total) }}.</label>
        <label :for="`transfer-${invoice.id}`">Mã giao dịch ngân hàng (tuỳ chọn)</label>
        <input :id="`transfer-${invoice.id}`" v-model="transferRef" maxlength="64" />
      </div>
      <div class="bar-row">
        <button type="submit" class="btn-solid" :disabled="busy || (method === 'CASH' ? !validCash : !transferConfirmed)">{{ busy ? 'Đang xử lý…' : 'Xác nhận đã thu tiền' }}</button>
        <button type="button" class="btn-outline" @click="submit('cancel')">Huỷ hoá đơn</button>
      </div>
    </form>
  </fieldset>
</template>
