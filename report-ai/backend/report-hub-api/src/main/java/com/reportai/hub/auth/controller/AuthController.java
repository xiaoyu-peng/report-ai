package com.reportai.hub.auth.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.common.entity.Role;
import com.reportai.hub.common.entity.User;
import com.reportai.hub.common.util.JwtUtil;
import com.reportai.hub.role.service.RoleService;
import com.reportai.hub.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        User user = userService.getByUsername(request.getUsername());

        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }

        if (!"active".equals(user.getStatus())) {
            return Result.error(401, "账户已被禁用或锁定");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return Result.error(401, "用户名或密码错误");
        }

        List<String> roles = new ArrayList<>();
        List<String> permissions = new ArrayList<>();

        if (user.getRoleId() != null) {
            Role role = roleService.getById(user.getRoleId());
            if (role != null) {
                roles.add(role.getCode().toUpperCase());
                if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                    String permsStr = role.getPermissions();
                    if (permsStr.startsWith("[")) {
                        permsStr = permsStr.substring(1, permsStr.length() - 1);
                        String[] perms = permsStr.replace("\"", "").split(",");
                        for (String perm : perms) {
                            String trimmed = perm.trim();
                            if (!trimmed.isEmpty()) {
                                permissions.add(trimmed);
                            }
                        }
                    }
                }
            }
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roles);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("userId", user.getId());
        data.put("tenantId", user.getTenantId());
        data.put("avatar", user.getAvatar());
        data.put("roles", roles);
        data.put("permissions", permissions);

        return Result.success(data);
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        Long userId = jwtUtil.getUserId(token);

        User user = userService.getById(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", userId);
        data.put("username", jwtUtil.getUsername(token));
        data.put("tenantId", jwtUtil.getTenantId(token));
        data.put("avatar", user != null ? user.getAvatar() : null);
        data.put("deptId", user != null ? user.getDeptId() : null);

        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
