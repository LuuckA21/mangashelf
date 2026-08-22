package me.luucka.mangashelf.user;

import jakarta.validation.Valid;
import me.luucka.mangashelf.user.dto.AdminUserResponse;
import me.luucka.mangashelf.user.dto.AdminUserUpdateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService accounts;

    public AdminUserController(AdminUserService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return accounts.listUsers().stream().map(AdminUserResponse::from).toList();
    }

    @PutMapping("/{id}")
    public AdminUserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return AdminUserResponse.from(accounts.updateUser(id, request, principal));
    }
}
