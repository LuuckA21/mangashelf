package me.luucka.mangashelf.users.dto;

public record ResetPasswordRequest(@ValidPassword String temporaryPassword) {}
