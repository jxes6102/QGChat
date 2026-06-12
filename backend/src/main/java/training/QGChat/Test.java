package training.QGChat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Test {

    public static void main(String[] args) {
        // 啟動 Spring Boot 應用程式。
        System.out.println("Open SpringBoot");
        SpringApplication.run(Test.class, args);
    }

    // http://localhost:8082/
    @GetMapping("/test")
    public String testPage() {
        // 簡單健康檢查端點，可用來確認服務是否啟動。
        return "testPage";
    }
}
