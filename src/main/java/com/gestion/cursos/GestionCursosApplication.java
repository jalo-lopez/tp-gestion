package com.gestion.cursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de inicio para la aplicación de Gestión de Cursos BPM.
 * Inicializa el contexto de Spring Boot y el motor embebido de Flowable BPM.
 */
@SpringBootApplication
public class GestionCursosApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionCursosApplication.class, args);
    }
}
