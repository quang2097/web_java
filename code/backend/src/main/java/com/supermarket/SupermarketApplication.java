package com.supermarket;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SupermarketApplication {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(SupermarketApplication.class, args);
    }

    /**
     * Tự động đọc file .env (ở thư mục code/.env hoặc thư mục hiện tại) vào System properties
     * để bạn có thể bấm nút Run hàm main() trực tiếp trong IDE (VS Code / IntelliJ / Eclipse).
     */
    private static void loadDotEnv() {
        for (Path p : new Path[]{Path.of(".env"), Path.of("../.env"), Path.of("code/.env")}) {
            if (Files.isRegularFile(p)) {
                try {
                    for (String raw : Files.readAllLines(p)) {
                        String line = raw.trim();
                        int eq = line.indexOf('=');
                        if (!line.isEmpty() && !line.startsWith("#") && eq > 0) {
                            String k = line.substring(0, eq).trim();
                            String v = line.substring(eq + 1).trim().replaceAll("^['\"]|['\"]$", "");
                            if (System.getenv(k) == null && System.getProperty(k) == null) {
                                System.setProperty(k, v);
                            }
                        }
                    }
                    break;
                } catch (Exception ignored) {}
            }
        }
    }
}
