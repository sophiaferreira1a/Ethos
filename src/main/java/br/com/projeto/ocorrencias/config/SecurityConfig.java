package br.com.projeto.ocorrencias.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticacao por sessao (cookie JSESSIONID) e autorizacao por perfil.
 *
 * <p>Regras da API (secao 9 da documentacao):</p>
 * <ul>
 *   <li>POST /login publico;</li>
 *   <li>POST /ocorrencias apenas PROFESSOR;</li>
 *   <li>leitura, lancamento e historico apenas COORDENACAO;</li>
 *   <li>PUT/DELETE conforme a regra de propriedade aplicada no service do Mateus.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String COOKIE_SESSAO = "JSESSIONID";
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder codificadorSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager gerenciadorAutenticacao(AuthenticationConfiguration configuracao) throws Exception {
        return configuracao.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository repositorioContexto() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] origens) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(Arrays.stream(origens).map(String::trim).toList());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Requested-With"));
        configuracao.setAllowCredentials(true);
        configuracao.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // API JSON consumida por fetch: CSRF desabilitado e cookie de sessao
                // limitado por SameSite (ver application.properties).
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(saida -> saida
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((requisicao, resposta, autenticacao) ->
                                resposta.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .deleteCookies(COOKIE_SESSAO))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint((requisicao, resposta, excecao) ->
                                escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, "Autenticacao necessaria"))
                        .accessDeniedHandler((requisicao, resposta, excecao) ->
                                escrever(resposta, HttpServletResponse.SC_FORBIDDEN, "Acesso negado para o seu perfil")))
                .authorizeHttpRequests(acesso -> acesso
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Ocorrencias (Mateus)
                        .requestMatchers(HttpMethod.POST, "/ocorrencias").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.GET, "/ocorrencias/*/historico").hasRole("COORDENACAO")
                        .requestMatchers(HttpMethod.GET, "/ocorrencias", "/ocorrencias/*").hasRole("COORDENACAO")
                        .requestMatchers(HttpMethod.PATCH, "/ocorrencias/*/lancada").hasRole("COORDENACAO")
                        // Regra de propriedade fica no service; aqui so o perfil.
                        .requestMatchers(HttpMethod.PUT, "/ocorrencias/*").hasAnyRole("PROFESSOR", "COORDENACAO")
                        .requestMatchers(HttpMethod.DELETE, "/ocorrencias/*").hasAnyRole("PROFESSOR", "COORDENACAO")

                        // Indicadores (Bernardo)
                        .requestMatchers(HttpMethod.GET, "/indicadores").hasRole("COORDENACAO")

                        .anyRequest().authenticated());

        return http.build();
    }

    private void escrever(HttpServletResponse resposta, int status, String mensagem) {
        try {
            resposta.setStatus(status);
            resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resposta.setCharacterEncoding("UTF-8");
            // JSON fixo (mensagens controladas) para nao acoplar o filtro ao serializador.
            resposta.getWriter().write("{\"status\":" + status + ",\"erro\":\"" + mensagem + "\"}");
        } catch (IOException erro) {
            log.error("Falha ao escrever resposta de erro de seguranca", erro);
        }
    }
}
