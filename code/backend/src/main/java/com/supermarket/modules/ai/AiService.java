package com.supermarket.modules.ai;

import com.supermarket.common.ApiException;
import com.supermarket.modules.catalog.CatalogService;
import com.supermarket.modules.catalog.CatalogViews.SkuView;
import com.supermarket.modules.report.ReportService;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private final GeminiClient gemini;
    private final CatalogService catalog;
    private final ReportService reports;

    public AiService(GeminiClient gemini, CatalogService catalog, ReportService reports) {
        this.gemini = gemini; this.catalog = catalog; this.reports = reports;
    }

    public record AiStatus(boolean geminiConfigured, String model) {}
    public record RecognitionResult(List<SkuView> matches, String rawExplanation) {}
    public record ChatAnswer(String answer, String mode) {}

    public AiStatus status() { return new AiStatus(gemini.isConfigured(), gemini.model()); }

    /**
     * AI-1 (ca sử dụng "Nhận diện mã hàng"): khi mã vạch bị mờ/rách hoặc hàng tươi sống không có mã,
     * thu ngân chụp ảnh sản phẩm -> Gemini Vision đối chiếu với danh mục đang bán và trả về tối đa 3 SKU phù hợp nhất.
     */
    public RecognitionResult recognizeProduct(MultipartFile image) {
        if (image == null || image.isEmpty()) throw ApiException.badRequest("Vui lòng chọn hoặc chụp một ảnh sản phẩm.");
        String type = Optional.ofNullable(image.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) throw ApiException.badRequest("Chỉ hỗ trợ ảnh JPEG, PNG hoặc WEBP.");
        var active = catalog.allActiveSkus();
        if (active.isEmpty()) return new RecognitionResult(List.of(), "Chưa có sản phẩm nào trong hệ thống.");

        var catalogLines = active.stream()
                .map(s -> s.code() + " | " + s.displayName() + " | " + (s.categoryName() == null ? "" : s.categoryName()))
                .collect(Collectors.joining("\n"));
        var prompt = """
                Bạn là hệ thống nhận diện hàng hoá tại quầy thu ngân siêu thị.
                Quan sát ảnh và chọn tối đa 3 mặt hàng khớp nhất trong danh sách dưới đây.
                Dòng đầu tiên: CHỈ ghi các mã CODE cách nhau bởi dấu phẩy (ví dụ: 8934563138165,8934588012112), hoặc NONE nếu không khớp.
                Dòng thứ hai: giải thích ngắn gọn bằng tiếng Việt bạn nhìn thấy gì trong ảnh.
                Danh sách mặt hàng (CODE | Tên | Danh mục):
                """ + catalogLines;
        String base64;
        try { base64 = Base64.getEncoder().encodeToString(image.getBytes()); }
        catch (Exception e) { throw ApiException.badRequest("Không đọc được file ảnh."); }

        String reply = gemini.generateVision(prompt, type, base64);
        var lines = reply.split("\\R", 2);
        var byCode = active.stream().collect(Collectors.toMap(s -> s.code().toUpperCase(Locale.ROOT), s -> s, (a, b) -> a));
        var matches = new ArrayList<SkuView>();
        var m = CODE_PATTERN.matcher(lines[0]);
        while (m.find() && matches.size() < 3) {
            var sku = byCode.get(m.group().toUpperCase(Locale.ROOT));
            if (sku != null && !matches.contains(sku)) matches.add(sku);
        }
        String explanation = lines.length > 1 ? lines[1].trim() : reply;
        return new RecognitionResult(matches, explanation);
    }

    /**
     * AI-2 & AI-3: Trợ lý hỏi đáp số liệu kinh doanh và phân tích nhập hàng cho Quản lý.
     * Dữ liệu lấy từ ReportService (Java tính sẵn, AI không bao giờ được chạy SQL).
     * Nếu chưa đặt GEMINI_API_KEY, hệ thống tự động trả lời bằng bộ phân tích nội bộ để luôn dùng được.
     */
    public ChatAnswer ask(String question) {
        if (question == null || question.isBlank()) throw ApiException.badRequest("Vui lòng nhập câu hỏi.");
        var s = reports.summary(14);
        var fc = reports.forecast();
        var context = buildDataSnapshot(s, fc);
        if (gemini.isConfigured()) {
            var sys = """
                    Bạn là trợ lý phân tích dữ liệu của Hệ thống Quản lý Siêu thị.
                    CHỈ sử dụng số liệu trong phần DỮ LIỆU HỆ THỐNG dưới đây; không tự bịa số.
                    Trả lời ngắn gọn, rõ ràng bằng tiếng Việt, định dạng tiền tệ VNĐ dễ đọc, đưa ra gợi ý hành động cụ thể cho Quản lý.
                    DỮ LIỆU HỆ THỐNG:
                    """ + context;
            try {
                return new ChatAnswer(gemini.generateText(sys, question.trim()), "GEMINI");
            } catch (ApiException e) {
                return new ChatAnswer(localAnswer(question, s, fc)
                        + "\n\n[Lưu ý từ Gemini API: " + e.getMessage() + "]", "LOCAL");
            }
        }
        return new ChatAnswer(localAnswer(question, s, fc), "LOCAL");
    }

    private static String vnd(Number n) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n) + " đ";
    }

    private static String buildDataSnapshot(ReportService.Summary s, List<ReportService.ForecastRow> fc) {
        var sb = new StringBuilder();
        sb.append("- Hôm nay: ").append(vnd(s.todayRevenue())).append(" (").append(s.todayInvoices()).append(" hoá đơn)\n");
        sb.append("- 7 ngày qua: ").append(vnd(s.weekRevenue())).append(" (").append(s.weekInvoices()).append(" hoá đơn)\n");
        sb.append("- Tháng này: ").append(vnd(s.monthRevenue())).append(" (").append(s.monthInvoices()).append(" hoá đơn)\n");
        sb.append("- Doanh thu từng ngày (14 ngày): ");
        s.daily().forEach(d -> sb.append(d.date()).append("=").append(vnd(d.revenue())).append("(").append(d.invoices()).append(" HĐ); "));
        sb.append("\n- Top bán chạy (14 ngày): ");
        s.topProducts().forEach(p -> sb.append(p.name()).append(" [").append(p.code()).append("]: ")
                .append(p.quantity()).append(" SP, ").append(vnd(p.revenue())).append("; "));
        sb.append("\n- Sắp hết hàng (").append(s.lowStockCount()).append(" mặt hàng): ");
        s.lowStock().forEach(i -> sb.append(i.productName()).append(" ").append(i.variant())
                .append(" còn ").append(i.quantity()).append(" ").append(i.unit()).append(" (ngưỡng ").append(i.minQuantity()).append("); "));
        sb.append("\n- Dự báo 7 ngày tới & gợi ý nhập: ");
        fc.stream().limit(12).forEach(r -> sb.append(r.name()).append(": tồn ").append(r.currentStock())
                .append(", bán 4 tuần ").append(r.weeklySales()).append(", dự báo 7 ngày=").append(r.forecastNext7Days())
                .append(", nên nhập=").append(r.suggestedImport()).append(" ").append(r.unit()).append("; "));
        return sb.toString();
    }

    private static String localAnswer(String q, ReportService.Summary s, List<ReportService.ForecastRow> fc) {
        String lower = q.toLowerCase(Locale.ROOT);
        var sb = new StringBuilder();
        if (lower.contains("nhập") || lower.contains("dự báo") || lower.contains("hết") || lower.contains("tồn")) {
            var need = fc.stream().filter(r -> r.suggestedImport() > 0).limit(8).toList();
            if (need.isEmpty()) sb.append("Hiện tại tồn kho của tất cả mặt hàng đều đủ cho nhu cầu dự kiến 7 ngày tới.\n");
            else {
                sb.append("Các mặt hàng nên ưu tiên nhập thêm cho 7 ngày tới:\n");
                for (var r : need)
                    sb.append("• ").append(r.name()).append(" (").append(r.code()).append("): tồn ").append(r.currentStock())
                            .append(" ").append(r.unit()).append(", dự báo bán ").append(r.forecastNext7Days())
                            .append(" → gợi ý nhập ").append(r.suggestedImport()).append(" ").append(r.unit()).append("\n");
            }
        }
        if (lower.contains("bán chạy") || lower.contains("top") || sb.isEmpty()) {
            sb.append("Tổng quan doanh thu:\n")
                    .append("• Hôm nay: ").append(vnd(s.todayRevenue())).append(" (").append(s.todayInvoices()).append(" hoá đơn)\n")
                    .append("• 7 ngày qua: ").append(vnd(s.weekRevenue())).append(" (").append(s.weekInvoices()).append(" hoá đơn)\n")
                    .append("• Tháng này: ").append(vnd(s.monthRevenue())).append(" (").append(s.monthInvoices()).append(" hoá đơn)\n");
            if (!s.topProducts().isEmpty()) {
                sb.append("Mặt hàng bán chạy nhất 14 ngày qua:\n");
                s.topProducts().stream().limit(5).forEach(p -> sb.append("• ").append(p.name())
                        .append(": ").append(p.quantity()).append(" SP (").append(vnd(p.revenue())).append(")\n"));
            }
        }
        sb.append("\n(Đang dùng bộ phân tích nội bộ. Đặt biến môi trường GEMINI_API_KEY để bật trả lời tự nhiên bằng Google Gemini.)");
        return sb.toString();
    }
}