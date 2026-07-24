package com.bms.backend.common.application.authentication;

public record InitialRegistrationCommand(
        String newPassword,
        String newPasswordConfirmation,
        String emailAddress,
        String mobileNumber) {

    @Override
    public String toString() {
        return "InitialRegistrationCommand[newPassword=***, "
                + "newPasswordConfirmation=***, emailAddress=***, mobileNumber=***]";
    }
}
