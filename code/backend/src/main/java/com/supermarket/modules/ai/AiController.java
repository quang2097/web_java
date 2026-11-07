package com.supermarket.modules.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AiController {
    private final AiService service;
    public AiController(AiService service) { this.service = service; }

    public record ChatRequest(@NotBlank @Size(max = 1000) String question) {}

    @GetMapping("/api/pos/ai/status")
    public AiService.AiStatus status() { return service.status(); }

    /** Thu ngân gửi ảnh chụp sản phẩm để nhận diện khi không quét được mã vạch. */
    @PostMapping("/api/pos/ai/recognize-product")
    public AiService.RecognitionResult recognize(@RequestParam("image") MultipartFile image) {
        return service.recognizeProduct(image);
    }

    /** Quản lý hỏi trợ lý về doanh thu, tồn kho, dự báo nhập hàng. */
    @PostMapping("/api/management/ai/chat")
    public AiService.ChatAnswer chat(@Valid @RequestBody ChatRequest r) { return service.ask(r.question()); }
}