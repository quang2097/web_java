package com.supermarket.modules.report;

import com.supermarket.domain.*;
import com.supermarket.modules.stock.StockService;
import com.supermarket.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo doanh thu, sản phẩm bán chạy, dự báo nhu cầu 7 ngày tới (trung bình động có trọng số 4 tuần)
 * và gợi ý mua kèm (association rules từ lịch sử hoá đơn).
 */
@Service
public class ReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final SkuRepository skus;
    private final StockService stock;

    public ReportService(InvoiceRepository invoices, InvoiceLineRepository lines, SkuRepository skus, StockService stock) {
        this.invoices = invoices; this.lines = lines; this.skus = skus; this.stock = stock;
    }

    public record DailyRevenue(LocalDate date, BigDecimal revenue, long invoices) {}
    public record TopProduct(Long skuId, String code, String name, long quantity, BigDecimal revenue) {}
    public record Summary(BigDecimal todayRevenue, long todayInvoices, BigDecimal weekRevenue, long weekInvoices,
                          BigDecimal monthRevenue, long monthInvoices, long totalSkus, long lowStockCount,
                          List<DailyRevenue> daily, List<TopProduct> topProducts, List<StockService.InventoryView> lowStock) {}
    public record ForecastRow(Long skuId, String code, String name, String unit, int currentStock, int minQuantity,
                              List<Integer> weeklySales, double forecastNext7Days, int suggestedImport) {}

    private static Instant startOf(LocalDate d) { return d.atStartOfDay(ZONE).toInstant(); }

    @Transactional(readOnly = true)
    public Summary summary(int days) {
        days = Math.min(Math.max(days, 7), 90);
        var today = LocalDate.now(ZONE);
        var tomorrow = startOf(today.plusDays(1));
        var todayList = invoices.findPaidBetween(startOf(today), tomorrow);
        var weekList = invoices.findPaidBetween(startOf(today.minusDays(6)), tomorrow);
        var monthList = invoices.findPaidBetween(startOf(today.withDayOfMonth(1)), tomorrow);
        var rangeList = invoices.findPaidBetween(startOf(today.minusDays(days - 1)), tomorrow);

        var byDay = rangeList.stream().collect(Collectors.groupingBy(i -> i.paidAt.atZone(ZONE).toLocalDate()));
        var daily = new ArrayList<DailyRevenue>();
        for (int k = days - 1; k >= 0; k--) {
            var d = today.minusDays(k);
            var list = byDay.getOrDefault(d, List.of());
            daily.add(new DailyRevenue(d, sum(list), list.size()));
        }
        var low = stock.lowStock();
        return new Summary(sum(todayList), todayList.size(), sum(weekList), weekList.size(),
                sum(monthList), monthList.size(), skus.count(), low.size(),
                daily, topProducts(days, 10), low);
    }

    private static BigDecimal sum(List<Invoice> list) {
        return list.stream().map(i -> i.total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<TopProduct> topProducts(int days, int limit) {
        var today = LocalDate.now(ZONE);
        var paidLines = lines.findPaidBetween(startOf(today.minusDays(Math.max(days, 1) - 1)), startOf(today.plusDays(1)));
        record Acc(Sku s, long qty, BigDecimal rev) {}
        var map = new HashMap<Long, Acc>();
        for (var l : paidLines) {
            var lineRev = l.unitPrice.multiply(BigDecimal.valueOf(l.quantity));
            map.compute(l.sku.id, (k, v) -> v == null ? new Acc(l.sku, l.quantity, lineRev)
                    : new Acc(v.s, v.qty + l.quantity, v.rev.add(lineRev)));
        }
        return map.values().stream()
                .sorted(Comparator.comparingLong(Acc::qty).reversed().thenComparing(a -> a.rev, Comparator.reverseOrder()))
                .limit(Math.min(Math.max(limit, 1), 30))
                .map(a -> new TopProduct(a.s.id, a.s.code, (a.s.product.name + " " + a.s.variant).trim(), a.qty, a.rev))
                .toList();
    }

    /**
     * Dự báo nhu cầu 7 ngày tới theo trung bình động có trọng số (Weighted Moving Average) trên 4 tuần gần nhất:
     *   forecast = 0.4 * W1 + 0.3 * W2 + 0.2 * W3 + 0.1 * W4   (W1 là 7 ngày gần nhất).
     * Gợi ý nhập = max(0, ceil(forecast * 1.2) + minQuantity - currentStock) — giữ đệm an toàn 20% + ngưỡng tối thiểu.
     */
    @Transactional(readOnly = true)
    public List<ForecastRow> forecast() {
        var now = Instant.now();
        var w = new Instant[]{now, now.minus(Duration.ofDays(7)), now.minus(Duration.ofDays(14)),
                now.minus(Duration.ofDays(21)), now.minus(Duration.ofDays(28))};
        var paidLines = lines.findPaidBetween(w[4], w[0]);
        var sales = new HashMap<Long, int[]>();
        for (var l : paidLines) {
            var t = l.invoice.paidAt;
            int idx = !t.isBefore(w[1]) ? 0 : !t.isBefore(w[2]) ? 1 : !t.isBefore(w[3]) ? 2 : 3;
            sales.computeIfAbsent(l.sku.id, k -> new int[4])[idx] += l.quantity;
        }
        var weights = new double[]{0.4, 0.3, 0.2, 0.1};
        return stock.listInventory().stream().map(inv -> {
            var s = sales.getOrDefault(inv.skuId(), new int[4]);
            double fc = 0;
            for (int i = 0; i < 4; i++) fc += weights[i] * s[i];
            fc = BigDecimal.valueOf(fc).setScale(1, RoundingMode.HALF_UP).doubleValue();
            int target = (int) Math.ceil(fc * 1.2) + inv.minQuantity();
            int suggest = Math.max(0, target - inv.quantity());
            return new ForecastRow(inv.skuId(), inv.code(), (inv.productName() + " " + inv.variant()).trim(),
                    inv.unit(), inv.quantity(), inv.minQuantity(), List.of(s[0], s[1], s[2], s[3]), fc, suggest);
        }).sorted(Comparator.comparingInt(ForecastRow::suggestedImport).reversed()
                .thenComparing(Comparator.comparingDouble(ForecastRow::forecastNext7Days).reversed())).toList();
    }

    /** Gợi ý mua kèm: tìm các mặt hàng hay xuất hiện chung hoá đơn với các mặt hàng đang có trong giỏ. */
    @Transactional(readOnly = true)
    public List<TopProduct> boughtTogether(List<Long> cartSkuIds) {
        if (cartSkuIds == null || cartSkuIds.isEmpty()) return List.of();
        var cartSet = new HashSet<>(cartSkuIds);
        var paidLines = lines.findPaidBetween(Instant.now().minus(Duration.ofDays(60)), Instant.now());
        var byInvoice = paidLines.stream().collect(Collectors.groupingBy(l -> l.invoice.id));
        var count = new HashMap<Long, Long>();
        var skuMap = new HashMap<Long, Sku>();
        for (var group : byInvoice.values()) {
            if (group.stream().anyMatch(l -> cartSet.contains(l.sku.id))) {
                for (var l : group) if (!cartSet.contains(l.sku.id) && l.sku.product.active) {
                    count.merge(l.sku.id, 1L, Long::sum);
                    skuMap.put(l.sku.id, l.sku);
                }
            }
        }
        return count.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(4)
                .map(e -> { var s = skuMap.get(e.getKey());
                    return new TopProduct(s.id, s.code, (s.product.name + " " + s.variant).trim(), e.getValue(), s.unitPrice); })
                .toList();
    }
}