package com.supermarket.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.common.ApiException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Gọi Google Gemini API từ backend (không lộ API key ra trình duyệt). */
@Component
public class GeminiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiClient(ObjectMapper json,
                        @Value("${app.ai.gemini-api-key:}") String apiKey,
                        @Value("${app.ai.gemini-model:gemini-2.5-flash}") String model,
                        @Value("${app.ai.gemini-base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl) {
        this.json = json; this.apiKey = apiKey == null ? "" : apiKey.trim(); this.model = model; this.baseUrl = baseUrl;
    }

    public boolean isConfigured() { return !apiKey.isBlank(); }
    public String model() { return model; }

    public String generateText(String systemInstruction, String userPrompt) {
        return send(List.of(Map.of("text", userPrompt)), systemInstruction);
    }

    public String generateVision(String prompt, String mimeType, String base64Image) {
        return send(List.of(
                Map.of("inline_data", Map.of("mime_type", mimeType, "data", base64Image)),
                Map.of("text", prompt)), null);
    }

    private String send(List<Object> parts, String systemInstruction) {
        if (!isConfigured())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình GEMINI_API_KEY. Hãy đặt biến môi trường GEMINI_API_KEY để sử dụng tính năng này.");
        try {
            var body = new LinkedHashMap<String, Object>();
            if (systemInstruction != null && !systemInstruction.isBlank())
                body.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            body.put("contents", List.of(Map.of("parts", parts)));
            body.put("generationConfig", Map.of("temperature", 0.2));
            var endpoint = baseUrl;
            if (apiKey.startsWith("AQ.") && endpoint.contains("generativelanguage.googleapis.com")) {
                endpoint = "https://aiplatform.googleapis.com/v1/publishers/google/models";
            }
            var uri = URI.create(endpoint + "/" + model + ":generateContent?key=" + apiKey);
            var req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini trả lỗi HTTP " + res.statusCode() + ". Vui lòng kiểm tra lại API key.");
            JsonNode root = json.readTree(res.body());
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
        } catch (ApiException e) { throw e; }
        catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không kết nối được tới Gemini API. Vui lòng thử lại sau.");
        }
    }
}