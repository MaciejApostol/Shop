package com.shop.controller;

import com.shop.entity.*;
import com.shop.event.EmailEvent;
import com.shop.service.*;
import com.shop.validation.group.ResetPassword;
import com.shop.validation.group.defaultFirst.DefaultAndCreate;
import com.shop.validation.group.defaultFirst.DefaultAndExists;
import com.shop.validation.group.defaultFirst.DefaultAndUpdateEmail;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RoleService roleService;
    private final JwtTokenService jwtTokenService;
    private final RegistrationTokenService registrationTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${react.origin}")
    private String origin;

    private void sendTokenEmail(User user, String subjectCode, String template, String url) {
        RegistrationToken registrationToken = registrationTokenService.generateAndSaveToken(user);
        Map<String, Object> variables = Map.of(
                "user", user,
                "url", origin + url + registrationToken.getToken());
        EmailEvent emailEvent = new EmailEvent(user.getEmail(), subjectCode, template, variables);
        eventPublisher.publishEvent(emailEvent);
    }

    private void sendRegistrationTokenEmail(User user) {
        sendTokenEmail(user,
                "confirm.registration.subject",
                "confirm-registration.html",
                "confirm-registration?token=");
    }

    @PostMapping("/create")
    public User createUser(@RequestBody @Validated(DefaultAndCreate.class) User user) {
        userService.save(user);
        roleService.save(new Role(RoleEnum.ROLE_USER, user));
        sendRegistrationTokenEmail(user);
        return new User();
    }

    @GetMapping("/confirm-registration")
    public ResponseEntity<?> confirmRegistration(@Validated(DefaultAndExists.class) RegistrationToken token,
                                                 HttpServletResponse response) throws BindException {
        RegistrationToken validatedToken = registrationTokenService.validateToken(token);
        User user = validatedToken.getUser();
        user.setEnabled(true);
        userService.save(user);
        jwtTokenService.authWithoutPassword(user, response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/resend-registration-token")
    public ResponseEntity<?> resendRegistrationToken(@Validated(DefaultAndExists.class) RegistrationToken token) {
        RegistrationToken existingToken = registrationTokenService.findByToken(token.getToken());
        User user = existingToken.getUser();
        sendRegistrationTokenEmail(user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/send-reset-password-token")
    public ResponseEntity<?> sendResetPasswordToken(@RequestBody @Validated(DefaultAndExists.class) User user) {
        User existingUser = userService.findByEmail(user.getEmail());
        if (existingUser.isEnabled()) {
            sendTokenEmail(existingUser,
                    "reset.password.subject",
                    "reset-password.html",
                    "reset-password?token=");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-reset-password-token")
    public ResponseEntity<?> checkResetPasswordToken(@Validated(DefaultAndExists.class) RegistrationToken token)
            throws BindException {
        registrationTokenService.validateToken(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public User resetPassword(@Validated(DefaultAndExists.class) RegistrationToken token,
                              @RequestBody @Validated(ResetPassword.class)
                              User user) throws BindException {
        RegistrationToken validatedToken = registrationTokenService.validateToken(token);
        User existingUser = validatedToken.getUser();
        user.setPassword(user.getPassword());
        userService.save(existingUser);
        return existingUser;
    }

    @PostMapping("/update-email")
//    @PreAuthorize("hasRole('ROLE_USER')")
    public User updateUser(@RequestBody @Validated(DefaultAndUpdateEmail.class) User user) {
        String email = user.getEmail();
        String newEmail = user.getNewEmail();
        User existingUser = userService.findByEmail(email);
        existingUser.setNewEmail(newEmail);
        userService.save(existingUser);
        return existingUser;
    }
}