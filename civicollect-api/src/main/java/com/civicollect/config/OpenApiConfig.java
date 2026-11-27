package com.civicollect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI civiCollectOpenApi() {
        return new OpenAPI()
                .tags(List.of(
                        new Tag().name("Authentification").description("Inscription, connexion et gestion des jetons"),
                        new Tag().name("Utilisateurs").description("Gestion des comptes et des rôles"),
                        new Tag().name("Catégories").description("Gestion des catégories de besoins"),
                        new Tag().name("Quartiers").description("Gestion des quartiers"),
                        new Tag().name("Contributions").description("Soumission et consultation des contributions"),
                        new Tag().name("Modération et qualification").description("Examen et traitement des contributions"),
                        new Tag().name("Soutiens").description("Soutien citoyen aux contributions"),
                        new Tag().name("Doublons").description("Détection et traitement des doublons"),
                        new Tag().name("Analyses").description("Statistiques et analyses"),
                        new Tag().name("Priorisation").description("Classement et calcul des priorités"),
                        new Tag().name("Notifications").description("Consultation et gestion des notifications"),
                        new Tag().name("Rapports").description("Génération et export des rapports"),
                        new Tag().name("Administration").description("Tableau de bord, journaux et paramètres")
                ))
                .info(new Info().title("CiviCollect API")
                        .description("Plateforme municipale de participation citoyenne")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
