package com.wherehouse.information.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InformationPage {

    @GetMapping("/information")
    public String redirectToPage() {
        return "information/informationPage";
    }
}
