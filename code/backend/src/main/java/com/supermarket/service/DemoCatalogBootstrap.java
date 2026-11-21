package com.supermarket.service;

import com.supermarket.domain.*;
import com.supermarket.repository.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khởi tạo dữ liệu mẫu siêu thị (danh mục, nhà cung cấp, mặt hàng, tồn kho và hoá đơn lịch sử)
 * khi CSDL chưa có mặt hàng nào và không chạy trong môi trường test.
 */
@Component @Order(2)
public class DemoCatalogBootstrap implements ApplicationRunner {
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final SkuRepository skus;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventory;
    private final EmployeeRepository employees;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final PaymentRepository payments;
    private final Environment env;

    public DemoCatalogBootstrap(CategoryRepository categories, SupplierRepository suppliers, ProductRepository products,
                                SkuRepository skus, WarehouseRepository warehouses, InventoryRepository inventory,
                                EmployeeRepository employees, InvoiceRepository invoices, InvoiceLineRepository lines,
                                PaymentRepository payments, Environment env) {
        this.categories = categories; this.suppliers = suppliers; this.products = products; this.skus = skus;
        this.warehouses = warehouses; this.inventory = inventory; this.employees = employees;
        this.invoices = invoices; this.lines = lines; this.payments = payments; this.env = env;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        String url = env.getProperty("spring.datasource.url", "");
        if (url.startsWith("jdbc:h2:mem:")) return;

        if (products.count() == 0) {
            var wh = warehouses.findFirstByOrderByIdAsc().orElseGet(() -> {
                var w = new Warehouse();
                w.code = "MAIN"; w.name = "Kho cửa hàng";
                return warehouses.save(w);
            });

            var catBeverage = saveCategory("Đồ uống & Sữa");
            var catFood = saveCategory("Thực phẩm khô & Gia vị");
            var catSnack = saveCategory("Bánh kẹo & Ăn vặt");
            var catHome = saveCategory("Hóa mỹ phẩm & Gia dụng");

            saveSupplier("Công ty CP Sữa Việt Nam (Vinamilk)", "02854155555", "Quận 7, TP.HCM");
            saveSupplier("Công ty CP Hàng tiêu dùng Masan", "02862563862", "Quận 1, TP.HCM");
            saveSupplier("Công ty TNHH Nước Giải Khát Suntory PepsiCo", "02838219437", "Quận 1, TP.HCM");

            saveItem(wh, catBeverage, "Sữa tươi tiệt trùng Vinamilk 100%", "8934673100113", "Lốc 4 hộp 180ml", "lốc", 32000, 48, 15);
            saveItem(wh, catBeverage, "Nước ngọt Coca-Cola", "8935049501503", "Lon 320ml", "lon", 10500, 120, 24);
            saveItem(wh, catBeverage, "Nước khoáng LaVie", "8935001711049", "Chai 500ml", "chai", 6000, 90, 24);
            saveItem(wh, catBeverage, "Trà Ô Long Tea+ Plus", "8934588233074", "Chai 450ml", "chai", 11000, 8, 20);
            saveItem(wh, catFood, "Mì Hảo Hảo tôm chua cay", "8934563138165", "Gói 75g", "gói", 4500, 200, 50);
            saveItem(wh, catFood, "Dầu ăn thực vật Neptune Light", "8934707022183", "Chai 1 lít", "chai", 56000, 25, 10);
            saveItem(wh, catFood, "Nước mắm Nam Ngư Đệ Nhị", "8936017362052", "Chai 900ml", "chai", 42000, 6, 12);
            saveItem(wh, catFood, "Gạo ST25 Ông Cua", "8938505071015", "Túi 5kg", "túi", 175000, 18, 8);
            saveItem(wh, catSnack, "Bánh quy Oreo Vani", "8934680021111", "Gói 119.6g", "gói", 17500, 35, 12);
            saveItem(wh, catSnack, "Snack khoai tây Lay's Classic", "8936079121415", "Gói 52g", "gói", 12000, 5, 15);
            saveItem(wh, catHome, "Nước rửa chén Sunlight Chanh", "8934868133056", "Túi 750g", "túi", 28000, 30, 10);
            saveItem(wh, catHome, "Khăn giấy lụa Pulppy", "8935012501110", "Hộp 180 tờ", "hộp", 22000, 7, 12);
        }

        var emp = employees.findAll().stream().findFirst().orElse(null);
        var allSkus = skus.findAllByOrderByIdAsc();
        if (emp != null && invoices.count() < 6 && allSkus.size() >= 12) {
            seedPaidInvoice(emp, Duration.ofDays(20), Payment.Method.CASH, new Sku[]{allSkus.get(0), allSkus.get(4), allSkus.get(8)}, new int[]{3, 20, 4});
            seedPaidInvoice(emp, Duration.ofDays(13), Payment.Method.TRANSFER, new Sku[]{allSkus.get(1), allSkus.get(4), allSkus.get(9)}, new int[]{12, 30, 6});
            seedPaidInvoice(emp, Duration.ofDays(8), Payment.Method.CASH, new Sku[]{allSkus.get(0), allSkus.get(3), allSkus.get(6), allSkus.get(10)}, new int[]{5, 10, 4, 3});
            seedPaidInvoice(emp, Duration.ofDays(4), Payment.Method.TRANSFER, new Sku[]{allSkus.get(1), allSkus.get(4), allSkus.get(9), allSkus.get(11)}, new int[]{18, 40, 8, 5});
            seedPaidInvoice(emp, Duration.ofDays(2), Payment.Method.CASH, new Sku[]{allSkus.get(2), allSkus.get(5), allSkus.get(7)}, new int[]{24, 3, 2});
            seedPaidInvoice(emp, Duration.ofHours(2), Payment.Method.TRANSFER, new Sku[]{allSkus.get(0), allSkus.get(3), allSkus.get(4), allSkus.get(8)}, new int[]{4, 8, 25, 5});
        }
    }

    private Category saveCategory(String name) {
        var c = new Category(); c.name = name; return categories.save(c);
    }
    private void saveSupplier(String name, String phone, String address) {
        var s = new Supplier(); s.name = name; s.phone = phone; s.address = address; suppliers.save(s);
    }
    private Sku saveItem(Warehouse wh, Category cat, String name, String code, String variant, String unit,
                         long price, int qty, int minQty) {
        var p = new Product(); p.name = name; p.category = cat; p.active = true; products.save(p);
        var s = new Sku(); s.product = p; s.code = code; s.variant = variant; s.unit = unit;
        s.unitPrice = BigDecimal.valueOf(price); skus.save(s);
        var inv = new Inventory(); inv.warehouse = wh; inv.sku = s; inv.quantity = qty; inv.minQuantity = minQty;
        inventory.save(inv);
        return s;
    }
    private void seedPaidInvoice(Employee emp, Duration ago, Payment.Method method, Sku[] items, int[] qtys) {
        var t = Instant.now().minus(ago);
        var inv = new Invoice(); inv.employee = emp; inv.createdAt = t; inv.paidAt = t; inv.status = Invoice.Status.PAID;
        invoices.save(inv);
        var total = BigDecimal.ZERO;
        for (int i = 0; i < items.length; i++) {
            var l = new InvoiceLine(); l.invoice = inv; l.sku = items[i]; l.quantity = qtys[i]; l.unitPrice = items[i].unitPrice;
            lines.save(l);
            total = total.add(l.unitPrice.multiply(BigDecimal.valueOf(qtys[i])));
        }
        inv.total = total;
        var pay = new Payment(); pay.invoice = inv; pay.method = method; pay.amount = total; pay.received = total;
        pay.createdAt = t; payments.save(pay);
    }
}

