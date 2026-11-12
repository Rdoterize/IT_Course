package cn.edu.achievement;

import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(11); }
    @Bean UserDetailsService userDetailsService(Database db) {
        return username -> {
            Rows.User u = db.userByName(username);
            if (u == null) throw new UsernameNotFoundException("账号或密码错误");
            return User.withUsername(u.username).password(u.passwordHash).roles(u.role).disabled(!u.enabled).build();
        };
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repository, HttpSessionCsrfTokenRepository csrf) throws Exception {
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .csrf(c -> c.csrfTokenRepository(csrf))
            .securityContext(c -> c.securityContextRepository(repository))
            .requestCache(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> { res.setStatus(401); res.setContentType(MediaType.APPLICATION_JSON_VALUE); res.setCharacterEncoding("UTF-8"); res.getWriter().write("{\"message\":\"请先登录\"}"); })
                .accessDeniedHandler((req,res,ex) -> { res.setStatus(403); res.setContentType(MediaType.APPLICATION_JSON_VALUE); res.setCharacterEncoding("UTF-8"); res.getWriter().write("{\"message\":\"请求无权限或安全令牌已过期，请刷新后重试\"}"); }))
            .headers(h -> h.contentTypeOptions(c -> {}).frameOptions(f -> f.deny()))
            .build();
    }
}
