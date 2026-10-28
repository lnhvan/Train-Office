package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/group-discount-policies")
public class GroupDiscountPolicyController {

    private final GroupDiscountPolicyService groupDiscountPolicyService;

    public GroupDiscountPolicyController(GroupDiscountPolicyService groupDiscountPolicyService) {
        this.groupDiscountPolicyService = groupDiscountPolicyService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("policies", groupDiscountPolicyService.getAllPolicies());
        return "group-discount-policies/admin-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("policy", new GroupDiscountPolicy());
        return "group-discount-policies/form";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        return groupDiscountPolicyService.getPolicyById(id)
                .map(policy -> {
                    model.addAttribute("policy", policy);
                    return "group-discount-policies/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.groupDiscount.notFound"));
                    return "redirect:/admin/group-discount-policies";
                });
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("policy") GroupDiscountPolicy policy,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            groupDiscountPolicyService.savePolicy(policy);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.groupDiscount.saved"));
            return "redirect:/admin/group-discount-policies";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "group-discount-policies/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            groupDiscountPolicyService.deletePolicy(id);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.groupDiscount.deleted"));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/group-discount-policies";
    }
}