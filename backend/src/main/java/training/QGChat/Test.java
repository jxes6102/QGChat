package training.QGChat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Test {

    public static void main(String[] args) {
        System.out.println("Open SpringBoot");
        SpringApplication.run(Test.class, args);
    }

    // http://localhost:8082/
    @GetMapping("/test")
    public String testPage() {
        return "testPage";
    }
}
