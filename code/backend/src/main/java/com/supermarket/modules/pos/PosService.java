package com.supermarket.modules.pos;

import com.supermarket.common.ApiException;
import com.supermarket.domain.*;
import com.supermarket.modules.stock.StockService;
import com.supermarket.repository.*;
import com.supermarket.service.InventoryService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ca sử dụng: Quét mã hàng, Tính giá cả, Thanh toán (tiền mặt / chuyển khoản + tạo mã QR).
 * Luồng: tạo hoá đơn nháp (DRAFT) từ giỏ hàng -> thanh toán: trừ kho + ghi payment + chuyển PAID trong cùng một giao dịch.
 */
@Service
public class PosService {
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final PaymentRepository payments;
    private final SkuRepository skus;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final InventoryRepository inventory;
    private final InventoryService inventoryService;
    private final StockService stock;
    private final String bankId, accountNo, accountName;

    public PosService(InvoiceRepository invoices, InvoiceLineRepository lines, PaymentRepository payments, SkuRepository skus,
                      AccountRepository accounts, CustomerRepository customers, InventoryRepository inventory,
                      InventoryService inventoryService, StockService stock,
                      @Value("${app.payment.bank-id:}") String bankId,
                      @Value("${app.payment.account-no:}") String accountNo,
                      @Value("${app.payment.account-name:}") String accountName) {
        this.invoices = invoices; this.lines = lines; this.payments = payments; this.skus = skus; this.accounts = accounts;
        this.customers = customers; this.inventory = inventory; this.inventoryService = inventoryService; this.stock = stock;
        this.bankId = bankId; this.accountNo = accountNo; this.accountName = accountName;
    }

    public record CartLine(Long skuId, int quantity) {}
    public record CreateInvoice(List<CartLine> lines, String customerName, String customerPhone) {}
    public record PayRequest(Payment.Method method, BigDecimal received, String transferRef) {}

    public record LineView(Long skuId, String code, String name, String unit, int quantity, BigDecimal unitPrice, BigDecimal amount) {}
    public record PaymentView(Payment.Method method, BigDecimal amount, BigDecimal received, BigDecimal changeAmount, String transferRef) {}
    public record InvoiceView(Long id, String code, Invoice.Status status, Instant createdAt, Instant paidAt, String cashier,
                              String customerName, String customerPhone, BigDecimal total, List<LineView> lines,
                              PaymentView payment, String transferContent, String qrUrl) {}

    static String code(long id) { return "HD" + String.format("%06d", id); }

    private Account account(String name) {
        return accounts.findByAccountName(name).orElseThrow(() -> ApiException.forbidden("Tài khoản không tồn tại."));
    }

    /** Tạo hoá đơn nháp. Giá lấy từ CSDL tại thời điểm bán, không tin giá do client gửi lên. */
    @Transactional
    public InvoiceView createInvoice(String cashierName, CreateInvoice in) {
        if (in.lines() == null || in.lines().isEmpty()) throw ApiException.badRequest("Giỏ hàng đang trống.");
        var merged = new LinkedHashMap<Long, Integer>();
        for (var l : in.lines()) {
            if (l.skuId() == null || l.quantity() <= 0 || l.quantity() > 9999) throw ApiException.badRequest("Số lượng không hợp lệ.");
            merged.merge(l.skuId(), l.quantity(), Integer::sum);
        }
        var warehouse = stock.storeWarehouse();
        var invoice = new Invoice();
        invoice.employee = account(cashierName).employee;
        invoice.customer = customer(in.customerName(), in.customerPhone());
        invoices.save(invoice);
        var total = BigDecimal.ZERO;
        for (var e : merged.entrySet()) {
            var sku = skus.findById(e.getKey()).orElseThrow(() -> ApiException.badRequest("Mặt hàng không tồn tại."));
            if (!sku.product.active) throw ApiException.badRequest(sku.product.name + " đã ngừng kinh doanh.");
            int available = inventory.findByWarehouseIdAndSkuId(warehouse.id, sku.id).map(i -> i.quantity).orElse(0);
            if (available < e.getValue())
                throw ApiException.conflict("Không đủ hàng: " + sku.product.name + " " + sku.variant + " (còn " + available + ").");
            var line = new InvoiceLine();
            line.invoice = invoice; line.sku = sku; line.quantity = e.getValue(); line.unitPrice = sku.unitPrice;
            lines.save(line);
            total = total.add(sku.unitPrice.multiply(BigDecimal.valueOf(e.getValue())));
        }
        invoice.total = total;
        return view(invoice);
    }

    private Customer customer(String name, String phone) {
        if (phone == null || phone.isBlank()) return null;
        var p = phone.trim();
        if (!p.matches("[0-9+ ]{8,20}")) throw ApiException.badRequest("Số điện thoại khách hàng không hợp lệ.");
        return customers.findFirstByPhone(p).orElseGet(() -> {
            var c = new Customer();
            c.phone = p;
            c.fullName = name == null || name.isBlank() ? "Khách hàng " + p : name.trim();
            return customers.save(c);
        });
    }

    /** Thanh toán: trừ kho từng dòng (khoá lạc quan), ghi payment, chuyển hoá đơn sang PAID — tất cả hoặc không gì cả. */
    @Transactional
    public InvoiceView pay(long invoiceId, PayRequest in) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Không tìm thấy hoá đơn."));
        if (invoice.status != Invoice.Status.DRAFT) throw ApiException.conflict("Hoá đơn đã được thanh toán hoặc đã huỷ.");
        if (in.method() == null) throw ApiException.badRequest("Chọn phương thức thanh toán.");
        var received = in.method() == Payment.Method.TRANSFER ? invoice.total : in.received();
        if (received == null || received.compareTo(invoice.total) < 0) throw ApiException.badRequest("Số tiền khách đưa chưa đủ.");

        var warehouse = stock.storeWarehouse();
        for (var line : lines.findByInvoiceIdOrderByIdAsc(invoice.id)) {
            var row = inventory.findByWarehouseIdAndSkuId(warehouse.id, line.sku.id)
                    .orElseThrow(() -> ApiException.conflict("Không đủ hàng: " + line.sku.product.name));
            try {
                inventoryService.decreaseStock(row.id, line.quantity);
            } catch (IllegalStateException e) {
                throw ApiException.conflict("Không đủ hàng: " + line.sku.product.name + " " + line.sku.variant + " (còn " + row.quantity + ").");
            }
        }
        var p = new Payment();
        p.invoice = invoice; p.method = in.method(); p.amount = invoice.total; p.received = received;
        p.changeAmount = received.subtract(invoice.total);
        p.transferRef = in.method() == Payment.Method.TRANSFER ? transferContent(invoice) : null;
        payments.save(p);
        invoice.status = Invoice.Status.PAID;
        invoice.paidAt = Instant.now();
        invoices.flush();
        return view(invoice);
    }

    @Transactional
    public InvoiceView cancel(long invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Không tìm thấy hoá đơn."));
        if (invoice.status != Invoice.Status.DRAFT) throw ApiException.conflict("Chỉ huỷ được hoá đơn chưa thanh toán.");
        invoice.status = Invoice.Status.CANCELLED;
        return view(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceView get(long id) {
        return view(invoices.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hoá đơn.")));
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> recent(int limit) {
        return invoices.findRecent(PageRequest.of(0, Math.min(Math.max(limit, 1), 200))).stream().map(this::view).toList();
    }

    public record PaymentConfig(boolean qrEnabled, String bankId, String accountNo, String accountName) {}
    public PaymentConfig paymentConfig() {
        return new PaymentConfig(qrEnabled(), bankId, accountNo, accountName);
    }
    private boolean qrEnabled() { return !bankId.isBlank() && !accountNo.isBlank(); }

    private static String transferContent(Invoice i) { return "THANHTOAN " + code(i.id); }

    /** Ảnh mã QR chuẩn VietQR (napas247) do img.vietqr.io sinh ra, chứa sẵn số tiền và nội dung chuyển khoản. */
    private String qrUrl(Invoice i) {
        if (!qrEnabled() || i.status != Invoice.Status.DRAFT) return null;
        var enc = (java.util.function.Function<String, String>) s -> URLEncoder.encode(s, StandardCharsets.UTF_8);
        return "https://img.vietqr.io/image/" + enc.apply(bankId) + "-" + enc.apply(accountNo) + "-compact2.png?amount="
                + i.total.toBigInteger() + "&addInfo=" + enc.apply(transferContent(i)) + "&accountName=" + enc.apply(accountName);
    }

    private InvoiceView view(Invoice i) {
        var lineViews = lines.findByInvoiceIdOrderByIdAsc(i.id).stream().map(l -> new LineView(l.sku.id, l.sku.code,
                (l.sku.product.name + " " + l.sku.variant).trim(), l.sku.unit, l.quantity, l.unitPrice,
                l.unitPrice.multiply(BigDecimal.valueOf(l.quantity)))).toList();
        var payment = payments.findByInvoiceId(i.id)
                .map(p -> new PaymentView(p.method, p.amount, p.received, p.changeAmount, p.transferRef)).orElse(null);
        return new InvoiceView(i.id, code(i.id), i.status, i.createdAt, i.paidAt, i.employee.fullName,
                i.customer == null ? null : i.customer.fullName, i.customer == null ? null : i.customer.phone,
                i.total, lineViews, payment, transferContent(i), qrUrl(i));
    }
}