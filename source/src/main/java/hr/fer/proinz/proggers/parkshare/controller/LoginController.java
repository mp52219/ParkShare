package hr.fer.proinz.proggers.parkshare.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class LoginController {

    /**
     * Login router, used for separating landing pages based on user role
     *
     * @param req used for getting user role
     * @return redirects to desired route
     */
    @GetMapping("/loginRouter")
    public String redirectToLandingPage(HttpServletRequest req) {
        if (req.isUserInRole("ADMIN")) {
            return "redirect:/admin";
        } else if (req.isUserInRole("CLIENT")) {
            return "redirect:/profile";
        } else {
            return "redirect:/profile";
        }
    }
}