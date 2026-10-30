package br.com.projeto.ocorrencias;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto completo, o que exige banco real (DB_URL, DB_USER, DB_PASSWORD).
 * Habilitar manualmente quando as variaveis estiverem no ambiente.
 */
@Disabled("Requer banco de dados configurado no ambiente")
@SpringBootTest
class SistemaOcorrenciasApplicationTests {

	@Test
	void contextLoads() {
	}

}
