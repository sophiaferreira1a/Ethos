package br.com.projeto.ocorrencias.integracao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Marca um teste de integracao: sobe a aplicacao completa (Spring Security, JPA, Flyway)
 * contra um PostgreSQL <b>separado</b> do banco de desenvolvimento.
 *
 * <p>So roda quando a variavel {@code TEST_DB_URL} esta definida; sem ela o teste e
 * ignorado (aparece como "skipped"), entao {@code mvnw test} continua passando para
 * quem nao tem o banco de teste. Veja {@code docs/BACKEND.md}.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = BaseIntegracaoTest.VARIAVEL_URL, matches = ".+")
public @interface TesteIntegracao {
}
