<script setup>
import { computed, ref, onBeforeUnmount } from 'vue';
import InvoicePayment from '../components/InvoicePayment.vue';
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
const searching = ref(false);
const scanning = ref(false);
const aiAvailable = ref(false);
let searchSequence = 0;
let recommendationSequence = 0;
let searchTimer;
const cartLocked = computed(() => busy.value || Boolean(activeInvoice.value));
onBeforeUnmount(() => clearTimeout(searchTimer));
function scheduleSearch() {
  clearTimeout(searchTimer);
  searchSequence++;
  searchTimer = setTimeout(runSearch, 250);
}
function clearCart() {
  if (cartLocked.value) return;
  cart.value = [];
  recommendations.value = [];
  recommendationSequence++;
}
apiFetch('/api/pos/ai/status').then(status => { aiAvailable.value = status.geminiConfigured; }).catch(() => {});

const filteredSkus = computed(() => {
  if (selectedCategory.value === 'ALL') return searchResults.value;
  return searchResults.value.filter((s) => s.categoryName === selectedCategory.value);
});

// Hoá đơn vừa tạo / đang chờ thanh toán
const activeInvoice = ref(null);
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

function clearMessages() {
  notice.value = '';
  errorText.value = '';
}

function addSkuToCart(sku, qty = 1) {
  if (cartLocked.value) return;
  clearMessages();
  completedInvoice.value = null;
  const existing = cart.value.find((i) => i.id === sku.id);
  if ((existing?.quantity || 0) + qty > Math.min(sku.stock, 9999)) {
    errorText.value = 'Số lượng vượt tồn kho hiện tại hoặc giới hạn 9999 sản phẩm.';
    return;
  }
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
  if (cartLocked.value) return;
  if (delta > 0 && item.quantity >= Math.min(item.stock, 9999)) return;
  item.quantity += delta;
  if (item.quantity <= 0) {
    cart.value = cart.value.filter((i) => i.id !== item.id);
  }
  loadRecommendations();
}

async function scanBarcode() {
  const code = barcode.value.trim();
  if (!code || cartLocked.value || scanning.value) return;
  scanning.value = true;
  clearMessages();
  try {
    const sku = await apiFetch(`/api/pos/skus/by-code/${encodeURIComponent(code)}`);
    addSkuToCart(sku, 1);
    barcode.value = '';
    if (!errorText.value) notice.value = `Đã thêm: ${sku.productName} ${sku.variant || ''}`;
  } catch (err) {
    errorText.value = err.message;
  } finally { scanning.value = false; }
}

async function runSearch() {
  clearTimeout(searchTimer);
  const sequence = ++searchSequence;
  searching.value = true;
  try {
    const [skus, cats] = await Promise.all([
      apiFetch(`/api/pos/skus?q=${encodeURIComponent(searchQuery.value.trim())}`),
      categories.value.length ? Promise.resolve(categories.value) : apiFetch('/api/pos/categories'),
    ]);
    if (sequence !== searchSequence) return;
    searchResults.value = skus;
    categories.value = cats;
  } catch (err) {
    if (sequence === searchSequence) errorText.value = err.message;
  } finally { if (sequence === searchSequence) searching.value = false; }
}

async function loadRecommendations() {
  const sequence = ++recommendationSequence;
  const ids = cart.value.map((i) => i.id);
  if (!ids.length) {
    recommendations.value = [];
    return;
  }
  try {
    const result = await apiFetch(`/api/pos/recommendations?skuIds=${ids.join(',')}`);
    if (sequence === recommendationSequence) recommendations.value = result;
  } catch {
    if (sequence === recommendationSequence) recommendations.value = [];
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
  const file = event.target.files?.[0] || null;
  aiMatches.value = [];
  aiExplanation.value = '';
  if (file && (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
    errorText.value = 'Chọn ảnh JPEG, PNG hoặc WEBP có dung lượng tối đa 5MB.';
    aiImageFile.value = null;
    event.target.value = '';
    return;
  }
  aiImageFile.value = file;
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
  if (!cart.value.length || cartLocked.value) return;
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
    // Align the cart with the server's current prices and quantities.
    cart.value = inv.lines.map(line => ({ id: line.skuId, code: line.code, name: line.name, unit: line.unit, unitPrice: Number(line.unitPrice), quantity: line.quantity, stock: cart.value.find(i => i.id === line.skuId)?.stock || line.quantity }));
  } catch (err) {
    errorText.value = err.message;
  } finally {
    busy.value = false;
  }
}

function onPaid(paid) {
  completedInvoice.value = paid;
  activeInvoice.value = null;
  clearCart();
  customerName.value = '';
  customerPhone.value = '';
  notice.value = `Thanh toán thành công hoá đơn ${paid.code}!`;
  runSearch();
}
function onCancelled() {
  activeInvoice.value = null;
  clearMessages();
  notice.value = 'Đã huỷ hoá đơn nháp. Bạn có thể sửa giỏ hàng.';
}
function printReceipt() { window.print(); }

runSearch();
</script>

<template>
  <div class="pos-grid">
    <!-- Cột trái: Quét mã vạch, tìm mặt hàng, nhận diện AI -->
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">TRA CỨU HÀNG HOÁ</span>
          <h3>Quét mã & chọn mặt hàng</h3>
        </div>
      </div>

      <form class="bar-row" @submit.prevent="scanBarcode">
        <input
          v-model="barcode" :disabled="cartLocked || scanning" maxlength="64"
          aria-label="Mã vạch mặt hàng"
          placeholder="Đưa máy quét vào đây hoặc nhập mã vạch (VD: 8934563138165)..."
          autofocus
        />
        <button type="submit" class="btn-solid" :disabled="cartLocked || scanning">+ Quét mã</button>
      </form>

      <div class="bar-row compact">
        <input
          v-model="searchQuery"
          aria-label="Tìm mặt hàng theo tên hoặc mã SKU"
          placeholder="Tìm theo tên hàng hoặc mã SKU..."
          @input="scheduleSearch"
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

      <p v-if="searching" role="status" class="loading-hint">Đang tìm mặt hàng…</p>
      <div class="table-scroll sku-list" tabindex="0" role="region" aria-label="Danh sách mặt hàng">
      <table class="data-table">
        <thead><tr><th>Mặt hàng / Mã SKU</th><th>Tồn kho</th><th class="numeric">Đơn giá</th><th><span class="sr-only">Thao tác</span></th></tr></thead>
        <tbody>
        <tr
          v-for="sku in filteredSkus"
          :key="sku.id"
          :class="{ 'out-of-stock': sku.stock <= 0 }"
        >
          <td>
            <strong>{{ sku.productName }}</strong>
            <div class="meta">{{ sku.variant }} · {{ sku.code }}</div>
            <small class="sku-variant">{{ sku.categoryName || 'Hàng hoá' }}</small>
          </td>
          <td>
            <span :class="['stock-badge', sku.stock <= 10 ? 'warn' : 'ok']">
              Tồn: {{ sku.stock }} {{ sku.unit }}
            </span>
          </td>
          <td class="numeric"><strong>{{ formatVnd(sku.unitPrice) }}</strong></td>
          <td>
            <button type="button" class="btn-sm" :aria-label="`Thêm ${sku.productName} vào hoá đơn`" :disabled="cartLocked || sku.stock <= 0" @click="addSkuToCart(sku, 1)">
              + Chọn
            </button>
          </td>
        </tr>
        <tr v-if="!searching && !filteredSkus.length"><td colspan="4" class="empty-hint">Không có mặt hàng phù hợp.</td></tr>
        </tbody>
      </table>
      </div>

      <!-- Khung AI-1: Nhận diện sản phẩm bằng ảnh khi mã vạch mờ/hỏng -->
      <div class="ai-box">
        <div class="ai-head">
          <strong>Nhận diện sản phẩm bằng ảnh</strong>
          <span>Dùng khi mã vạch bị rách/mờ hoặc hàng không dán tem</span>
        </div>
        <div class="bar-row compact">
          <input :disabled="!aiAvailable || aiBusy" aria-label="Ảnh sản phẩm (tối đa 5MB)" type="file" accept="image/jpeg,image/png,image/webp" @change="onSelectAiImage" />
          <button type="button" class="btn-outline" :disabled="!aiAvailable || !aiImageFile || aiBusy" @click="recognizeByAi">
            {{ aiBusy ? 'Đang nhận diện…' : 'Nhận diện ảnh' }}
          </button>
        </div>
        <p v-if="!aiAvailable" class="meta">Nhận diện ảnh chưa khả dụng. Vui lòng quét mã hoặc tìm sản phẩm.</p>
        <p v-if="aiExplanation" class="ai-note">{{ aiExplanation }}</p>
        <div v-if="aiMatches.length" class="chip-row">
          <button
            v-for="m in aiMatches"
            :key="m.id"
            type="button"
            class="chip"
            :disabled="cartLocked || m.stock <= 0" @click="addSkuToCart(m, 1)"
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
          <span class="badge">HOÁ ĐƠN HIỆN TẠI</span>
          <h3>Chi tiết bán hàng ({{ cartCount }} sản phẩm)</h3>
        </div>
        <button v-if="cart.length && !activeInvoice" type="button" class="btn-ghost" :disabled="cartLocked" @click="clearCart">
          Xoá giỏ
        </button>
      </div>

      <p v-if="notice" class="alert ok" role="status">{{ notice }}</p>
      <p v-if="errorText" class="alert err" role="alert">{{ errorText }}</p>

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
                <button type="button" :disabled="cartLocked" :aria-label="`Giảm số lượng ${item.name}`" @click="updateQty(item, -1)">−</button>
                <span>{{ item.quantity }}</span>
                <button type="button" :disabled="cartLocked || item.quantity >= Math.min(item.stock, 9999)" :aria-label="`Tăng số lượng ${item.name}`" @click="updateQty(item, 1)">+</button>
              </div>
            </td>
            <td><strong>{{ formatVnd(item.unitPrice * item.quantity) }}</strong></td>
          </tr>
        </TransitionGroup>
      </table>
      <p v-else class="empty-hint">Quét mã vạch hoặc chọn mặt hàng từ danh sách để lập hoá đơn.</p>

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
            <label for="posview-field-1">Tên khách hàng (tuỳ chọn)</label>
            <input id="posview-field-1" v-model="customerName" :disabled="busy" maxlength="120" aria-label="Tên khách hàng" placeholder="Khách lẻ" />
          </div>
          <div>
            <label for="posview-field-2">Số điện thoại (tuỳ chọn)</label>
            <input id="posview-field-2" v-model="customerPhone" :disabled="busy" maxlength="20" aria-label="Số điện thoại khách hàng" placeholder="VD: 0901234567" />
          </div>
        </div>
        <button type="button" class="btn-solid full" :disabled="busy" @click="startCheckout">
          {{ busy ? 'Đang tạo hoá đơn…' : 'Tiến hành thanh toán →' }}
        </button>
      </div>

      <InvoicePayment v-if="activeInvoice" :key="activeInvoice.id" :invoice="activeInvoice" @paid="onPaid" @cancelled="onCancelled" />

      <!-- Phiếu in hoá đơn vừa hoàn tất -->
      <div v-if="completedInvoice" class="receipt-box receipt-print">
        <div class="pay-header">
          <strong>Phiếu thanh toán {{ completedInvoice.code }}</strong>
          <button type="button" class="btn-outline no-print" @click="printReceipt">In hoá đơn</button>
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

