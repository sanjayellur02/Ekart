package com.example.ekart.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.example.ekart.dto.Customer;
import com.example.ekart.dto.Vendor;

import jakarta.mail.internet.MimeMessage;

@Component
public class EmailSender {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    // ===================== SEND OTP TO VENDOR =====================
    public void send(Vendor vendor) {

        String email = vendor.getEmail();
        int otp = vendor.getOtp();
        String name = vendor.getName();

        // ✅ SHOW OTP IN CONSOLE
        System.out.println("🔐 VENDOR OTP IS: " + otp);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom("dwarakeeshtalavar@gmail.com", "Ekart Site");
            helper.setTo(email);
            helper.setSubject("OTP for Email Verification");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("otp", otp);

            String html = templateEngine.process("otp-email.html", context);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception e) {
            System.err.println("❌ Vendor mail failed, OTP printed in console");
        }
    }

    // ===================== SEND OTP TO CUSTOMER =====================
    public void send(Customer customer) {

        String email = customer.getEmail();
        int otp = customer.getOtp();
        String name = customer.getName();

        // ✅ SHOW OTP IN CONSOLE
        System.out.println("🔐 CUSTOMER OTP IS: " + otp);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom("dwarakeeshtalavar@gmail.com", "Ekart Site");
            helper.setTo(email);
            helper.setSubject("OTP for Email Verification");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("otp", otp);

            String html = templateEngine.process("otp-email.html", context);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception e) {
            System.err.println("❌ Customer mail failed, OTP printed in console");
        }
    }
}
