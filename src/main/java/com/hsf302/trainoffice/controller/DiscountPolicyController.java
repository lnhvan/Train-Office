package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/discount-policies")
public class DiscountPolicyController {

    private final DiscountPolicyService discountPolicyService;

    public DiscountPolicyController(DiscountPolicyService discountPolicyService) {
        this.discountPolicyService = discountPolicyService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("policies", discountPolicyService.getAllPolicies());
        return "discount-policies/admin-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("policy", new DiscountPolicy());
        return "discount-policies/form";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        return discountPolicyService.getPolicyById(id)
                .map(policy -> {
                    model.addAttribute("policy", policy);
                    return "discount-policies/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Discount policy not found.");
                    return "redirect:/admin/discount-policies";
                });
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("policy") DiscountPolicy policy,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            discountPolicyService.savePolicy(policy);
            redirectAttributes.addFlashAttribute("successMessage", "Discount policy saved successfully.");
            return "redirect:/admin/discount-policies";

        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "discount-policies/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            discountPolicyService.deletePolicy(id);
            redirectAttributes.addFlashAttribute("successMessage", "Discount policy deleted successfully.");

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/discount-policies";
    }
}