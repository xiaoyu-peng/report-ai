package com.reportai.hub.common.filter;

import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        
        if (path.startsWith("/api/v1/runtime/") || path.startsWith("/api/v1/init/")) {
            AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                "runtime", "runtime_user", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            SecurityContextHolder.getContext().setAuthentication(anonymousToken);
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token) && jwtUtil.isTokenValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                Long tenantId = jwtUtil.getTenantId(token);
                List<String> roles = jwtUtil.getRoles(token);
                
                UserContext.setUserId(userId);
                UserContext.setUsername(username);
                UserContext.setTenantId(tenantId);
                UserContext.setRoles(roles);
                
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (roles != null) {
                    for (String role : roles) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                } else {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userId, 
                        null, 
                        authorities
                    );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                    "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
                );
                SecurityContextHolder.getContext().setAuthentication(anonymousToken);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
