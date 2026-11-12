package cn.edu.achievement;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaController {
    @GetMapping({"/login","/dashboard","/achievements","/achievements/{id}","/review","/reviews","/users","/indicators","/profile","/exports","/archive"})
    String frontend(){return "forward:/index.html";}
}
