package com.gestion.cursos.config;

import org.flowable.engine.IdentityService;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Inicializador de usuarios y grupos en Flowable BPM (IdentityService).
 * Requisitos Sección 2, 3.3 y 4.1.2:
 * - Grupo 'Administración':
 *     - admin_laura (Jefe administrativo)
 *     - admin_martin (Administrativo)
 * - Grupo 'Docentes':
 *     - docente_carlos (Jefe del equipo docente)
 *     - docente_ana (Docente regular)
 *     - docente_pedro (Docente regular)
 * - Grupo 'Empresas':
 *     - contacto@techsolutions.com (Tech Solutions S.A.)
 *     - mlopez@innovalog.com (Innovación & Logística Global SRL)
 */
@Component
public class FlowableIdentityDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowableIdentityDataInitializer.class);

    public static final String GRUPO_ADMINISTRACION = "Administración";
    public static final String GRUPO_DOCENTES = "Docentes";
    public static final String GRUPO_EMPRESAS = "Empresas";

    public static final String USER_JEFE_ADMIN = "admin_laura";
    public static final String USER_ADMIN_MARTIN = "admin_martin";
    public static final String USER_JEFE_DOCENTE = "docente_carlos";
    public static final String USER_DOCENTE_ANA = "docente_ana";
    public static final String USER_DOCENTE_PEDRO = "docente_pedro";

    private final IdentityService identityService;

    public FlowableIdentityDataInitializer(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void run(String... args) {
        log.info("Inicializando grupos y usuarios en Flowable IdentityService...");

        // 1. Crear grupos
        crearGrupoSiNoExiste(GRUPO_ADMINISTRACION, "Equipo de Administración", "assignment");
        crearGrupoSiNoExiste(GRUPO_DOCENTES, "Equipo Docente", "assignment");
        crearGrupoSiNoExiste(GRUPO_EMPRESAS, "Empresas Solicitantes", "assignment");

        // 2. Crear usuarios de Administración
        crearUsuarioSiNoExiste(USER_JEFE_ADMIN, "Laura", "Gómez (Jefe Administrativo)", "laura@institucion.edu", "password", GRUPO_ADMINISTRACION);
        crearUsuarioSiNoExiste(USER_ADMIN_MARTIN, "Martín", "Pérez (Administrativo)", "martin@institucion.edu", "password", GRUPO_ADMINISTRACION);

        // 3. Crear usuarios Docentes
        crearUsuarioSiNoExiste(USER_JEFE_DOCENTE, "Carlos", "Rodríguez (Jefe Docente)", "carlos@institucion.edu", "password", GRUPO_DOCENTES);
        crearUsuarioSiNoExiste(USER_DOCENTE_ANA, "Ana", "Fernández (Docente)", "ana@institucion.edu", "password", GRUPO_DOCENTES);
        crearUsuarioSiNoExiste(USER_DOCENTE_PEDRO, "Pedro", "López (Docente)", "pedro@institucion.edu", "password", GRUPO_DOCENTES);

        // 4. Crear cuentas iniciales de Empresas (Sección 4.1.2)
        crearUsuarioSiNoExiste("contacto@techsolutions.com", "Tech Solutions", "S.A.", "contacto@techsolutions.com", "empresa123", GRUPO_EMPRESAS);
        crearUsuarioSiNoExiste("mlopez@innovalog.com", "Innovación & Logística", "SRL", "mlopez@innovalog.com", "empresa123", GRUPO_EMPRESAS);

        log.info("Grupos y usuarios de Flowable configurados exitosamente.");
    }

    public void crearGrupoSiNoExiste(String groupId, String groupName, String type) {
        Group group = identityService.createGroupQuery().groupId(groupId).singleResult();
        if (group == null) {
            group = identityService.newGroup(groupId);
            group.setName(groupName);
            group.setType(type);
            identityService.saveGroup(group);
            log.info("Grupo creado en Flowable: {}", groupId);
        }
    }

    public void crearUsuarioSiNoExiste(String userId, String firstName, String lastName, String email, String password, String groupId) {
        User user = identityService.createUserQuery().userId(userId).singleResult();
        if (user == null) {
            user = identityService.newUser(userId);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setPassword(password);
            identityService.saveUser(user);
            log.info("Usuario creado en Flowable: {}", userId);
        } else if (password != null && !password.isBlank()) {
            user.setPassword(password);
            identityService.saveUser(user);
        }

        // Vincular usuario al grupo si no es miembro
        if (groupId != null && !groupId.isBlank()) {
            crearGrupoSiNoExiste(groupId, groupId, "assignment");
            Group membership = identityService.createGroupQuery().groupId(groupId).groupMember(userId).singleResult();
            if (membership == null) {
                identityService.createMembership(userId, groupId);
                log.info("Usuario [{}] asignado al grupo [{}]", userId, groupId);
            }
        }
    }
}
