package me.luucka.mangashelf.users;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.mangashelf.users.dto.CreateUserRequest;
import me.luucka.mangashelf.users.dto.ResetPasswordRequest;
import me.luucka.mangashelf.users.dto.UpdateUserRequest;
import me.luucka.mangashelf.users.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
public class AdminUserController {

  private final AdminUserService service;

  public AdminUserController(AdminUserService service) {
    this.service = service;
  }

  @GetMapping
  public Page<UserResponse> list(@PageableDefault(sort = "username") Pageable pageable) {
    return service.list(pageable).map(UserResponse::of);
  }

  @PostMapping
  public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest body) {
    var created = UserResponse.of(service.create(body));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
    return ResponseEntity.created(location).body(created);
  }

  @PostMapping("/{id}/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body) {
    service.resetPassword(id, body.temporaryPassword());
  }

  @PatchMapping("/{id}")
  public UserResponse changeRole(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest body) {
    return UserResponse.of(service.changeRole(me.id(), id, body.role()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    service.delete(me.id(), id);
  }
}
