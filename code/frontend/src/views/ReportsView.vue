<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue';
import { Chart, registerables } from 'chart.js';
import { apiFetch, formatVnd } from '../api/http';

Chart.register(...registerables);

const summary = ref(null);
const canvasRef = ref(null);
let chartInstance = null;

// Trợ lý AI-2
const question = ref('');
const messages = ref([
  {
    role: 'assistant',
    mode: 'SYSTEM',
    text: 'Xin chào Quản lý! Bạn có thể hỏi về doanh thu hôm nay/7 ngày/tháng này, mặt hàng bán chạy, hàng sắp hết hoặc gợi ý nhập hàng 7 ngày tới.',
  },
]);
const aiBusy = ref(false);

const quickQuestions = [
  'Tổng quan doanh thu hôm nay và 7 ngày qua?',
  'Những mặt hàng nào đang bán chạy nhất?',
  'Mặt hàng nào sắp hết và cần nhập thêm bao nhiêu cho 7 ngày tới?',
];

async function loadSummary() {
  summary.value = await apiFetch('/api/management/reports/summary?days=14');
  await nextTick();
  renderChart();
}

function renderChart() {
  if (!canvasRef.value || !summary.value) return;
  if (chartInstance) chartInstance.destroy();
  const daily = summary.value.daily || [];
  chartInstance = new Chart(canvasRef.value, {
    type: 'bar',
    data: {
      labels: daily.map((d) => d.date.slice(5)),
      datasets: [
        {
          label: 'Doanh thu (VNĐ)',
          data: daily.map((d) => Number(d.revenue)),
          backgroundColor: '#214b37',
          hoverBackgroundColor: '#306447',
          borderRadius: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 650, easing: 'easeOutQuart' },
      plugins: { legend: { display: false } },
    },
  });
}

async function askAi(preset) {
  const q = (preset || question.value).trim();
  if (!q || aiBusy.value) return;
  question.value = '';
  messages.value.push({ role: 'user', text: q });
  aiBusy.value = true;
  try {
    const res = await apiFetch('/api/management/ai/chat', {
      method: 'POST',
      body: { question: q },
    });
    messages.value.push({ role: 'assistant', mode: res.mode, text: res.answer });
  } catch (err) {
    messages.value.push({ role: 'assistant', mode: 'ERROR', text: err.message });
  } finally {
    aiBusy.value = false;
  }
}

onBeforeUnmount(() => {
  if (chartInstance) chartInstance.destroy();
});

loadSummary();
</script>

<template>
  <div>
    <!-- Thẻ KPI tổng quan -->
    <div v-if="summary" class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-top">
          <span>DOANH THU HÔM NAY</span>
          <i class="kpi-ico">💰</i>
        </div>
        <strong>{{ formatVnd(summary.todayRevenue) }}</strong>
        <small>{{ summary.todayInvoices }} hoá đơn đã thanh toán</small>
      </div>
      <div class="kpi-card">
        <div class="kpi-top">
          <span>7 NGÀY QUA</span>
          <i class="kpi-ico">📈</i>
        </div>
        <strong>{{ formatVnd(summary.weekRevenue) }}</strong>
        <small>{{ summary.weekInvoices }} hoá đơn</small>
      </div>
      <div class="kpi-card">
        <div class="kpi-top">
          <span>THÁNG NÀY</span>
          <i class="kpi-ico">🗓️</i>
        </div>
        <strong>{{ formatVnd(summary.monthRevenue) }}</strong>
        <small>{{ summary.monthInvoices }} hoá đơn</small>
      </div>
      <div class="kpi-card">
        <div class="kpi-top">
          <span>CẢNH BÁO TỒN KHO</span>
          <i class="kpi-ico">⚠️</i>
        </div>
        <strong :class="{ 'low-text': summary.lowStockCount > 0 }">
          {{ summary.lowStockCount }} / {{ summary.totalSkus }} SKU
        </strong>
        <small>Mặt hàng chạm ngưỡng tối thiểu</small>
      </div>
    </div>

    <div class="pos-grid">
      <section class="card">
        <div class="card-head">
          <div>
            <span class="badge">BIỂU ĐỒ DOANH THU 14 NGÀY</span>
            <h3>Xu hướng doanh thu & Top bán chạy</h3>
          </div>
          <button type="button" class="btn-outline" @click="loadSummary">Làm mới</button>
        </div>

        <div class="chart-wrap">
          <canvas ref="canvasRef"></canvas>
        </div>

        <h4 style="margin-top: 20px">Top sản phẩm bán chạy (14 ngày)</h4>
        <table v-if="summary?.topProducts?.length" class="data-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Sản phẩm</th>
              <th>SL bán</th>
              <th>Doanh thu</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in summary.topProducts" :key="p.skuId">
              <td><code>{{ p.code }}</code></td>
              <td><strong>{{ p.name }}</strong></td>
              <td>{{ p.quantity }}</td>
              <td><strong>{{ formatVnd(p.revenue) }}</strong></td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Trợ lý AI hỏi đáp dữ liệu (AI-2) -->
      <section class="card">
        <div class="card-head">
          <div>
            <span class="badge">TRỢ LÝ PHÂN TÍCH DỮ LIỆU AI (GEMINI / LOCAL)</span>
            <h3>Hỏi đáp trực tiếp số liệu kinh doanh & tồn kho</h3>
          </div>
        </div>

        <div class="chip-row">
          <button
            v-for="q in quickQuestions"
            :key="q"
            type="button"
            class="chip"
            @click="askAi(q)"
          >
            {{ q }}
          </button>
        </div>

        <div class="chat-feed">
          <TransitionGroup name="list-move">
            <div
              v-for="(m, idx) in messages"
              :key="idx"
              :class="['chat-bubble', m.role === 'user' ? 'user' : 'bot']"
            >
              <div class="chat-meta">
                <strong>{{ m.role === 'user' ? 'Bạn' : '🤖 Trợ lý AI' }}</strong>
                <span v-if="m.mode" class="tag">{{ m.mode }}</span>
              </div>
              <pre class="chat-text">{{ m.text }}</pre>
            </div>
          </TransitionGroup>

          <div v-if="aiBusy" class="chat-bubble bot typing-bubble">
            <div class="chat-meta"><strong>🤖 Trợ lý AI đang phân tích số liệu</strong></div>
            <div class="typing-dots"><span></span><span></span><span></span></div>
          </div>
        </div>

        <form class="bar-row" @submit.prevent="askAi()">
          <input
            v-model="question"
            placeholder="Nhập câu hỏi (VD: Mặt hàng nào cần nhập gấp tuần này?)..."
            :disabled="aiBusy"
          />
          <button type="submit" class="btn-solid" :disabled="aiBusy">
            {{ aiBusy ? 'Đang phân tích…' : 'Gửi câu hỏi' }}
          </button>
        </form>
      </section>
    </div>
  </div>
</template>
