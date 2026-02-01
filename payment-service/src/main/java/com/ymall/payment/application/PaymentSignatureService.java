package com.ymall.payment.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class PaymentSignatureService {
    private final String secret;

    public PaymentSignatureService(@Value("${payment.callback.secret:demo-secret}") String secret) {
        this.secret = secret;
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("signature failed", ex);
        }
    }

    public boolean verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return sign(payload).equalsIgnoreCase(signature);
    }
}
