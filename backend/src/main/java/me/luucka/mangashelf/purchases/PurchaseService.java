package me.luucka.mangashelf.purchases;

import java.util.UUID;
import me.luucka.mangashelf.purchases.dto.PurchaseSettings;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

  private final AppUserRepository users;

  public PurchaseService(AppUserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public PurchaseSettings settings(UUID userId) {
    return toDto(users.findById(userId).orElseThrow());
  }

  @Transactional
  public PurchaseSettings updateSettings(UUID userId, PurchaseSettings s) {
    var user = users.findById(userId).orElseThrow();
    user.setPurchaseSettings(s.chfRate(), s.discountPercent());
    return toDto(user);
  }

  private static PurchaseSettings toDto(AppUser u) {
    return new PurchaseSettings(u.getChfRate(), u.getDiscountPercent());
  }
}
