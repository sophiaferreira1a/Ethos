package br.com.projeto.ocorrencias.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail invalido")
        @Size(max = 150, message = "e-mail muito longo")
        String email,

        @NotBlank(message = "informe a senha")
        @Size(max = 72, message = "senha muito longa")
        String senha) {
}
