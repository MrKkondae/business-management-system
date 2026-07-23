package com.bms.backend.system.application.bootstrap;

public record BootstrapAdminInput(
        String organizationName,
        String employeeNumber,
        String administratorName,
        String loginId,
        String temporaryPassword,
        String emailAddress,
        String mobileNumber) {

    @Override
    public String toString() {
        return "BootstrapAdminInput[organizationName=%s, employeeNumber=%s, administratorName=%s, "
                .formatted(organizationName, employeeNumber, administratorName)
                + "loginId=%s, temporaryPassword=***, emailAddress=%s, mobileNumber=%s]"
                        .formatted(loginId, emailAddress, mobileNumber);
    }
}
