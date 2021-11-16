package hr.fer.proinz.proggers.parkshare.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/test")
public class TestController {
    @GetMapping("/test")
    public String test() {
        return "index";
    }
    @GetMapping("/profile")
    public String t() {
        return "userpage";
    }
}