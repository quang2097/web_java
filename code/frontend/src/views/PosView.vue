<script setup>
import { computed, ref } from 'vue';
import { apiFetch, formatVnd } from '../api/http';

const barcode = ref('');
const searchQuery = ref('');
const selectedCategory = ref('ALL');
const categories = ref([]);
const searchResults = ref([]);
const cart = ref([]);
const recommendations = ref([]);
const customerName = ref('');
const customerPhone = ref('');
const busy = ref(false);
const notice = ref('');
const errorText = ref('');
const bumpId = ref(null);

const filteredSkus = computed(() => {
  if (selectedCategory.value === 'ALL') return searchResults.value;
  return searchResults.value.filter((s) => s.categoryName === selectedCategory.value);
});

// Hoá đơn vừa tạo / đang chờ thanh toán
const activeInvoice = ref(null);
const paymentMethod = ref('CASH');
const cashReceived = ref('');
const completedInvoice = ref(null);

// Nhận diện bằng camera/ảnh (AI-1)
const aiImageFile = ref(null);
const aiBusy = ref(false);
const aiMatches = ref([]);
const aiExplanation = ref('');

const cartTotal = computed(() =>
  cart.value.reduce((sum, item) => sum + Number(item.unitPrice) * item.quantity, 0)
);
const cartCount = computed(() =>
  cart.value.reduce((sum, item) => sum + item.quantity, 0)
);
const changePreview = computed(() => {
  if (!activeInvoice.value || paymentMethod.value !== 'CASH') return 0;
  const rec = Number(cashReceived.value || 0);
  return Math.max(0, rec - Number(activeInvoice.value.total));
});

function clearMessages() {
  notice.value = '';
  errorText.value = '';
}

function addSkuToCart(sku, qty = 1) {
  clearMessages();
  const existing = cart.value.find((i) => i.id === sku.id);
  if (existing) {
    existing.quantity += qty;
  } else {
    cart.value.push({
      id: sku.id,
      code: sku.code,
      name: `${sku.productName} ${sku.variant || ''}`.trim(),
      unit: sku.unit,
      unitPrice: Number(sku.unitPrice),
      stock: sku.stock,
      quantity: qty,
    });
  }
  bumpId.value = sku.id;
  setTimeout(() => {
    if (bumpId.value === sku.id) bumpId.value = null;
  }, 450);
  loadRecommendations();
}

function updateQty(item, delta) {
  item.quantity += delta;
  if (item.quantity <= 0) {
    cart.value = cart.value.filter((i) => i.id !== item.id);
  }
  loadRecommendations();
}

async function scanBarcode() {
  const code = barcode.value.trim();
  if (!code) return;
  clearMessages();
  try {
    const sku = await apiFetch(`/api/pos/skus/by-code/${encodeURIComponent(code)}`);
    addSkuToCart(sku, 1);
    barcode.value = '';
    notice.value = `Đã thêm: ${sku.productName} ${sku.variant || ''}`;
  } catch (err) {
    errorText.value = err.message;
  }
}

async function runSearch() {
  try {
    const [skus, cats] = await Promise.all([
      apiFetch(`/api/pos/skus?q=${encodeURIComponent(searchQuery.value.trim())}`),
      categories.value.length ? Promise.resolve(categories.value) : apiFetch('/api/pos/categories'),
    ]);
    searchResults.value = skus;
    categories.value = cats;
  } catch (err) {
    errorText.value = err.message;
  }
}

async function loadRecommendations() {
  const ids = cart.value.map((i) => i.id);
  if (!ids.length) {
    recommendations.value = [];
    return;
  }
  try {
    recommendations.value = await apiFetch(`/api/pos/recommendations?skuIds=${ids.join(',')}`);
  } catch {
    recommendations.value = [];
  }
}

async function addRecommendation(rec) {
  try {
    const sku = await apiFetch(`/api/pos/skus/by-code/${encodeURIComponent(rec.code)}`);
    addSkuToCart(sku, 1);
  } catch (err) {
    errorText.value = err.message;
  }
}

function onSelectAiImage(event) {
  aiImageFile.value = event.target.files?.[0] || null;
}

async function recognizeByAi() {
  if (!aiImageFile.value || aiBusy.value) return;
  aiBusy.value = true;
  clearMessages();
  aiMatches.value = [];
  aiExplanation.value = '';
  try {
    const fd = new FormData();
    fd.append('image', aiImageFile.value);
    const res = await apiFetch('/api/pos/ai/recognize-product', { method: 'POST', body: fd });
    aiMatches.value = res.matches || [];
    aiExplanation.value = res.rawExplanation || '';
  } catch (err) {
    errorText.value = err.message;
  } finally {
    aiBusy.value = false;
  }
}

async function startCheckout() {
  if (!cart.value.length || busy.value) return;
  busy.value = true;
  clearMessages();
  completedInvoice.value = null;
  try {
    const inv = await apiFetch('/api/pos/invoices', {
      method: 'POST',
      body: {
        lines: cart.value.map((i) => ({ skuId: i.id, quantity: i.quantity })),
        customerName: customerName.value.trim() || null,
        customerPhone: customerPhone.value.trim() || null,
      },
    });
    activeInvoice.value = inv;
    cashReceived.value = String(inv.total);
  } catch (err) {
    errorText.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function confirmPayment() {
  if (!activeInvoice.value || busy.value) return;
  busy.value = true;
  clearMessages();
  try {
    const paid = await apiFetch(`/api/pos/invoices/${activeInvoice.value.id}/pay`, {
      method: 'POST',
      body: {
        method: paymentMethod.value,
        received: paymentMethod.value === 'CASH' ? Number(cashReceived.value || 0) : activeInvoice.value.total,
      },
    });
    completedInvoice.value = paid;
    activeInvoice.value = null;
    cart.value = [];
    recommendations.value = [];
    customerName.value = '';
    customerPhone.value = '';
    notice.value = `Thanh toán thành công hoá đơn ${paid.code}!`;
    runSearch();
  } catch (err) {
    errorText.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function cancelDraft() {
  if (!activeInvoice.value || busy.value) return;
  busy.value = true;
  try {
    await apiFetch(`/api/pos/invoices/${activeInvoice.value.id}/cancel`, { method: 'POST' });
    activeInvoice.value = null;
    notice.value = 'Đã huỷ hoá đơn nháp.';
  } catch (err) {
    errorText.value = err.message;
  } finally {
    busy.value = false;
  }
}

runSearch();
</script>

<template>
  <div class="pos-grid">
    <!-- Cột trái: Quét mã vạch, tìm mặt hàng, nhận diện AI -->
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">CA SỬ DỤNG · QUÉT MÃ HÀNG & TÍNH GIÁ</span>
          <h3>Quét mã vạch hoặc chọn mặt hàng</h3>
        </div>
      </div>

      <form class="bar-row" @submit.prevent="scanBarcode">
        <input
          v-model="barcode"
          placeholder="Đưa máy quét vào đây hoặc nhập mã vạch (VD: 8934563138165)..."
          autofocus
        />
        <button type="submit" class="btn-solid">+ Quét mã</button>
      </form>

      <div class="bar-row compact">
        <input
          v-model="searchQuery"
          placeholder="Tìm theo tên hàng hoặc mã SKU..."
          @input="runSearch"
        />
        <button type="button" class="btn-outline" @click="runSearch">Làm mới</button>
      </div>

      <div v-if="categories.length" class="cat-tabs">
        <button
          type="button"
          :class="['cat-pill', { active: selectedCategory === 'ALL' }]"
          @click="selectedCategory = 'ALL'"
        >
          Tất cả ({{ searchResults.length }})
        </button>
        <button
          v-for="c in categories"
          :key="c.id"
          type="button"
          :class="['cat-pill', { active: selectedCategory === c.name }]"
          @click="selectedCategory = c.name"
        >
          {{ c.name }}
        </button>
      </div>

      <TransitionGroup name="list-move" tag="div" class="sku-grid">
        <div
          v-for="sku in filteredSkus"
          :key="sku.id"
          :class="['sku-card', { 'out-of-stock': sku.stock <= 0 }]"
          @click="sku.stock > 0 && addSkuToCart(sku, 1)"
        >
          <div class="sku-card-top">
            <span class="sku-cat">{{ sku.categoryName || 'Hàng hoá' }}</span>
            <span :class="['stock-badge', sku.stock <= 10 ? 'warn' : 'ok']">
              Tồn: {{ sku.stock }} {{ sku.unit }}
            </span>
          </div>
          <strong class="sku-title">{{ sku.productName }}</strong>
          <small class="sku-variant">{{ sku.variant }} · Mã: {{ sku.code }}</small>
          <div class="sku-card-foot">
            <span class="price">{{ formatVnd(sku.unitPrice) }}</span>
            <button type="button" class="btn-sm" :disabled="sku.stock <= 0" @click.stop="addSkuToCart(sku, 1)">
              + Chọn
            </button>
          </div>
        </div>
      </TransitionGroup>

      <!-- Khung AI-1: Nhận diện sản phẩm bằng ảnh khi mã vạch mờ/hỏng -->
      <div class="ai-box">
        <div class="ai-head">
          <strong>📷 Nhận diện sản phẩm bằng AI (Gemini Vision)</strong>
          <span>Dùng khi mã vạch bị rách/mờ hoặc hàng không dán tem</span>
        </div>
        <div class="bar-row compact">
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onSelectAiImage" />
          <button type="button" class="btn-outline" :disabled="!aiImageFile || aiBusy" @click="recognizeByAi">
            {{ aiBusy ? 'Đang nhận diện…' : 'Nhận diện ảnh' }}
          </button>
        </div>
        <p v-if="aiExplanation" class="ai-note">{{ aiExplanation }}</p>
        <div v-if="aiMatches.length" class="chip-row">
          <button
            v-for="m in aiMatches"
            :key="m.id"
            type="button"
            class="chip"
            @click="addSkuToCart(m, 1)"
          >
            + {{ m.productName }} {{ m.variant }} ({{ formatVnd(m.unitPrice) }})
          </button>
        </div>
      </div>
    </section>

    <!-- Cột phải: Giỏ hàng, tính tiền & Thanh toán tiền mặt / VietQR -->
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">CA SỬ DỤNG · ĐẾM SỐ LƯỢNG & THANH TOÁN</span>
          <h3>Giỏ hàng tại quầy ({{ cartCount }} sản phẩm)</h3>
        </div>
        <button v-if="cart.length && !activeInvoice" type="button" class="btn-ghost" @click="cart = []">
          Xoá giỏ
        </button>
      </div>

      <p v-if="notice" class="alert ok">{{ notice }}</p>
      <p v-if="errorText" class="alert err">{{ errorText }}</p>

      <table v-if="cart.length" class="data-table">
        <thead>
          <tr>
            <th>Mặt hàng</th>
            <th>Đơn giá</th>
            <th>Số lượng</th>
            <th>Thành tiền</th>
          </tr>
        </thead>
        <TransitionGroup name="list-move" tag="tbody">
          <tr v-for="item in cart" :key="item.id" :class="{ 'row-bump': bumpId === item.id }">
            <td>
              <strong>{{ item.name }}</strong>
              <div class="meta">{{ item.code }} · {{ item.unit }}</div>
            </td>
            <td>{{ formatVnd(item.unitPrice) }}</td>
            <td>
              <div class="qty-ctrl">
                <button type="button" :disabled="Boolean(activeInvoice)" @click="updateQty(item, -1)">−</button>
                <span>{{ item.quantity }}</span>
                <button type="button" :disabled="Boolean(activeInvoice)" @click="updateQty(item, 1)">+</button>
              </div>
            </td>
            <td><strong>{{ formatVnd(item.unitPrice * item.quantity) }}</strong></td>
          </tr>
        </TransitionGroup>
      </table>
      <p v-else class="empty-hint">Chưa có mặt hàng nào trong giỏ. Hãy quét mã vạch hoặc bấm "+ Thêm" ở danh sách bên trái.</p>

      <!-- Gợi ý mua kèm từ lịch sử hoá đơn -->
      <div v-if="recommendations.length && !activeInvoice" class="rec-strip">
        <span>Khách thường mua kèm:</span>
        <button
          v-for="r in recommendations"
          :key="r.skuId"
          type="button"
          class="chip"
          @click="addRecommendation(r)"
        >
          + {{ r.name }} ({{ formatVnd(r.revenue) }})
        </button>
      </div>

      <div class="total-bar">
        <span>Tổng thanh toán</span>
        <strong>{{ formatVnd(cartTotal) }}</strong>
      </div>

      <!-- Bước 1: Tạo hoá đơn thanh toán -->
      <div v-if="!activeInvoice && cart.length" class="checkout-box">
        <div class="two-col">
          <div>
            <label>Tên khách hàng (tuỳ chọn)</label>
            <input v-model="customerName" placeholder="Khách lẻ" />
          </div>
          <div>
            <label>Số điện thoại (tuỳ chọn)</label>
            <input v-model="customerPhone" placeholder="VD: 0901234567" />
          </div>
        </div>
        <button type="button" class="btn-solid full" :disabled="busy" @click="startCheckout">
          {{ busy ? 'Đang tạo hoá đơn…' : 'Tiến hành thanh toán →' }}
        </button>
      </div>

      <!-- Bước 2: Chọn tiền mặt hoặc chuyển khoản VietQR -->
      <div v-if="activeInvoice" class="payment-panel">
        <div class="pay-header">
          <strong>Hoá đơn {{ activeInvoice.code }} · {{ formatVnd(activeInvoice.total) }}</strong>
          <span class="tag warn">Chờ thanh toán</span>
        </div>

        <div class="method-tabs">
          <button
            type="button"
            :class="['tab-btn', { active: paymentMethod === 'CASH' }]"
            @click="paymentMethod = 'CASH'"
          >
            💵 Thanh toán tiền mặt
          </button>
          <button
            type="button"
            :class="['tab-btn', { active: paymentMethod === 'TRANSFER' }]"
            @click="paymentMethod = 'TRANSFER'"
          >
            📱 Chuyển khoản (Tạo mã QR)
          </button>
        </div>

        <div v-if="paymentMethod === 'CASH'" class="cash-area">
          <label>Số tiền khách đưa (VNĐ)</label>
          <input v-model="cashReceived" type="number" min="0" step="1000" />
          <p class="change-line">
            Tiền thừa trả khách: <strong>{{ formatVnd(changePreview) }}</strong>
          </p>
        </div>

        <div v-else class="qr-area">
          <p class="meta">Nội dung chuyển khoản: <strong>{{ activeInvoice.transferContent }}</strong></p>
          <img
            v-if="activeInvoice.qrUrl"
            :src="activeInvoice.qrUrl"
            alt="Mã VietQR thanh toán"
            class="qr-img"
          />
        </div>

        <div class="bar-row">
          <button type="button" class="btn-solid" :disabled="busy" @click="confirmPayment">
            {{ busy ? 'Đang xác nhận…' : '✓ Xác nhận đã thu tiền' }}
          </button>
          <button type="button" class="btn-outline" :disabled="busy" @click="cancelDraft">
            Huỷ hoá đơn
          </button>
        </div>
      </div>

      <!-- Phiếu in hoá đơn vừa hoàn tất -->
      <div v-if="completedInvoice" class="receipt-box">
        <div class="pay-header">
          <strong>Phiếu thanh toán {{ completedInvoice.code }}</strong>
          <span class="tag ok">ĐÃ THANH TOÁN</span>
        </div>
        <p class="meta">
          Thu ngân: {{ completedInvoice.cashier }} · Phương thức:
          {{ completedInvoice.payment?.method === 'TRANSFER' ? 'Chuyển khoản QR' : 'Tiền mặt' }}
        </p>
        <ul class="receipt-lines">
          <li v-for="l in completedInvoice.lines" :key="l.skuId">
            <span>{{ l.name }} × {{ l.quantity }}</span>
            <strong>{{ formatVnd(l.amount) }}</strong>
          </li>
        </ul>
        <div class="receipt-foot">
          <span>Tổng cộng: <strong>{{ formatVnd(completedInvoice.total) }}</strong></span>
          <span v-if="completedInvoice.payment?.changeAmount > 0">
            Tiền thừa: {{ formatVnd(completedInvoice.payment.changeAmount) }}
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

