package org.akaza.openclinica.core;

import org.springframework.security.crypto.password.PasswordEncoder;

public class OpenClinicaPasswordEncoder implements PasswordEncoder {

    PasswordEncoder currentPasswordEncoder;
    PasswordEncoder oldPasswordEncoder;

    public OpenClinicaPasswordEncoder() {
    }

    @Override
    public String encode(CharSequence rawPass) {
        return currentPasswordEncoder.encode(rawPass);
    }

    @Override
    public boolean matches(CharSequence rawPass, String encPass) {

        boolean result = false;
        if (currentPasswordEncoder.matches(rawPass, encPass) || oldPasswordEncoder.matches(rawPass, encPass)) {
            result = true;
        }
        return result;
    }

    public PasswordEncoder getCurrentPasswordEncoder() {
        return currentPasswordEncoder;
    }

    public void setCurrentPasswordEncoder(PasswordEncoder currentPasswordEncoder) {
        this.currentPasswordEncoder = currentPasswordEncoder;
    }

    public PasswordEncoder getOldPasswordEncoder() {
        return oldPasswordEncoder;
    }

    public void setOldPasswordEncoder(PasswordEncoder oldPasswordEncoder) {
        this.oldPasswordEncoder = oldPasswordEncoder;
    }

}
