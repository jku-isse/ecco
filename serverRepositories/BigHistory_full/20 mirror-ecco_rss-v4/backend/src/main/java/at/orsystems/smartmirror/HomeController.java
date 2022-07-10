package at.orsystems.smartmirror;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@Controller
@RequestMapping("/")
public class HomeController {
    @GetMapping
    public String home(Model model){
        return "forward:/index.html";
    }
}
