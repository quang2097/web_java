package com.supermarket.modules.stock;

import com.supermarket.common.ApiException;
import com.supermarket.domain.*;
import com.supermarket.modules.catalog.CatalogViews.WarehouseView;
import com.supermarket.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tồn kho và phiếu nhập hàng. Kho đầu tiên được coi là kho bán hàng tại quầy. */
@Service
public class StockService {
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventory;
    private final SkuRepository skus;
    private final SupplierRepository suppliers;
    private final ImportReceiptRepository receipts;
    private final ImportLineRepository importLines;
    private final AccountRepository accounts;

    public StockService(WarehouseRepository warehouses, InventoryRepository inventory, SkuRepository skus,
                        SupplierRepository suppliers, ImportReceiptRepository receipts, ImportLineRepository importLines,
                        AccountRepository accounts) {
        this.warehouses = warehouses; this.inventory = inventory; this.skus = skus; this.suppliers = suppliers;
        this.receipts = receipts; this.importLines = importLines; this.accounts = accounts;
    }

    @Transactional
    public Warehouse storeWarehouse() {
        return warehouses.findFirstByOrderByIdAsc().orElseGet(() -> {
            var w = new Warehouse();
            w.code = "MAIN"; w.name = "Kho cửa hàng";
            return warehouses.save(w);
        });
    }

    @Transactional
    public Inventory ensureInventoryRow(Sku sku) {
        return ensureInventoryRow(storeWarehouse(), sku);
    }

    private Inventory ensureInventoryRow(Warehouse w, Sku sku) {
        return inventory.findByWarehouseIdAndSkuId(w.id, sku.id).orElseGet(() -> {
            var i = new Inventory();
            i.warehouse = w; i.sku = sku; i.quantity = 0;
            return inventory.saveAndFlush(i);
        });
    }

    public record InventoryView(Long id, Long warehouseId, String warehouseName, Long skuId, String code, String productName,
                                String variant, String unit, String categoryName, int quantity, int minQuantity, boolean low) {
        static InventoryView of(Inventory i) {
            var s = i.sku; var p = s.product;
            return new InventoryView(i.id, i.warehouse.id, i.warehouse.name, s.id, s.code, p.name, s.variant, s.unit,
                    p.category == null ? null : p.category.name, i.quantity, i.minQuantity, i.quantity <= i.minQuantity);
        }
    }
    public record ImportLineInput(Long skuId, int quantity, BigDecimal unitCost) {}
    public record ImportInput(Long supplierId, Long warehouseId, String note, List<ImportLineInput> lines) {}
    public record ImportLineView(Long skuId, String code, String name, int quantity, BigDecimal unitCost) {}
    public record ImportView(Long id, String supplierName, String warehouseName, String employeeName, Instant createdAt,
                             String note, BigDecimal total, List<ImportLineView> lines) {}

    @Transactional(readOnly = true)
    public List<InventoryView> listInventory() {
        return inventory.findAllDetailed().stream().map(InventoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryView> lowStock() {
        return listInventory().stream().filter(InventoryView::low)
                .sorted(java.util.Comparator.comparingInt(InventoryView::quantity)).toList();
    }

    @Transactional
    public void setMinQuantity(long inventoryId, int min) {
        if (min < 0) throw ApiException.badRequest("Ngưỡng cảnh báo không hợp lệ.");
        inventory.findById(inventoryId).orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn kho.")).minQuantity = min;
    }

    @Transactional(readOnly = true)
    public List<WarehouseView> listWarehouses() {
        return warehouses.findAll().stream().map(WarehouseView::of).toList();
    }

    @Transactional
    public WarehouseView createWarehouse(String code, String name) {
        if (warehouses.existsByCode(code.trim())) throw ApiException.conflict("Mã kho đã tồn tại.");
        var w = new Warehouse();
        w.code = code.trim(); w.name = name.trim();
        return WarehouseView.of(warehouses.save(w));
    }

    /** Nhập hàng: tạo phiếu nhập và cộng tồn kho trong cùng một giao dịch. */
    @Transactional
    public ImportView createImport(String accountName, ImportInput in) {
        if (in.lines() == null || in.lines().isEmpty()) throw ApiException.badRequest("Phiếu nhập cần ít nhất một mặt hàng.");
        var account = accounts.findByAccountName(accountName).orElseThrow(() -> ApiException.forbidden("Tài khoản không tồn tại."));
        var r = new ImportReceipt();
        r.employee = account.employee;
        r.warehouse = in.warehouseId() == null ? storeWarehouse()
                : warehouses.findById(in.warehouseId()).orElseThrow(() -> ApiException.badRequest("Kho không hợp lệ."));
        r.supplier = in.supplierId() == null ? null
                : suppliers.findById(in.supplierId()).orElseThrow(() -> ApiException.badRequest("Nhà cung cấp không hợp lệ."));
        r.note = in.note() == null || in.note().isBlank() ? null : in.note().trim();
        receipts.save(r);
        var total = BigDecimal.ZERO;
        for (var li : in.lines()) {
            if (li.quantity() <= 0) throw ApiException.badRequest("Số lượng nhập phải lớn hơn 0.");
            if (li.unitCost() == null || li.unitCost().signum() < 0) throw ApiException.badRequest("Giá nhập không hợp lệ.");
            var sku = skus.findById(li.skuId()).orElseThrow(() -> ApiException.badRequest("Mặt hàng không hợp lệ."));
            var line = new ImportLine();
            line.receipt = r; line.sku = sku; line.quantity = li.quantity(); line.unitCost = li.unitCost();
            importLines.save(line);
            ensureInventoryRow(r.warehouse, sku).quantity += li.quantity();
            total = total.add(li.unitCost().multiply(BigDecimal.valueOf(li.quantity())));
        }
        r.total = total;
        inventory.flush();
        return view(r);
    }

    private ImportView view(ImportReceipt r) {
        var lines = importLines.findByReceiptIdOrderByIdAsc(r.id).stream()
                .map(l -> new ImportLineView(l.sku.id, l.sku.code, l.sku.product.name + " " + l.sku.variant, l.quantity, l.unitCost))
                .toList();
        return new ImportView(r.id, r.supplier == null ? null : r.supplier.name, r.warehouse.name, r.employee.fullName,
                r.createdAt, r.note, r.total, lines);
    }

    @Transactional(readOnly = true)
    public List<ImportView> recentImports() {
        return receipts.findRecent(PageRequest.of(0, 50)).stream().map(this::view).toList();
    }
}