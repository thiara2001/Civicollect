package com.civicollect.config;

import com.civicollect.domain.*;
import com.civicollect.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Données de démarrage idempotentes : compte administrateur, référentiels d'exemple, critères de priorisation
 * et modèles de messages. Les zones et services sont des EXEMPLES à adapter à la commune réelle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ZoneRepository zones;
    private final MunicipalServiceRepository services;
    private final CriterionRepository criteria;
    private final NotificationTemplateRepository templates;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedCategories();
        seedZones();
        seedServices();
        seedCriteria();
        seedTemplates();
    }

    private void seedAdmin() {
        if (users.countByRole(Role.ADMIN) > 0) return;
        User admin = new User();
        admin.setFullName("Administrateur CiviCollect");
        admin.setEmail(props.bootstrap().adminEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(props.bootstrap().adminPassword()));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.warn("Compte administrateur initial créé ({}). Changez son mot de passe immédiatement.", admin.getEmail());
    }

    private void seedCategories() {
        if (categories.count() > 0) return;
        for (String name : new String[]{"Voirie", "Eau potable", "Santé", "Éducation", "Assainissement",
                "Jeunesse et sport", "Éclairage public", "Marchés et commerce"}) {
            Category c = new Category();
            c.setName(name);
            categories.save(c);
        }
    }

    private void seedZones() {
        if (zones.count() > 0) return;
        Object[][] sample = {
                {"Randoulène", 14.8000, -16.9400}, {"Médina Fall", 14.7900, -16.9300},
                {"Cité Lamy", 14.7850, -16.9200}, {"Escale", 14.7910, -16.9250}, {"Thiès Nord", 14.8100, -16.9250}};
        for (Object[] s : sample) {
            Zone z = new Zone();
            z.setName((String) s[0]);
            z.setLatitude((Double) s[1]);
            z.setLongitude((Double) s[2]);
            zones.save(z);
        }
    }

    private void seedServices() {
        if (services.count() > 0) return;
        for (String name : new String[]{"Service technique et voirie", "Service eau et assainissement",
                "Service santé et action sociale", "Service éducation, jeunesse et sport"}) {
            MunicipalService s = new MunicipalService();
            s.setName(name);
            services.save(s);
        }
    }

    private void seedCriteria() {
        if (criteria.count() > 0) return;
        add("SUPPORTS", "Nombre de demandes et de soutiens", 15, CriterionType.AUTO);
        add("BENEFICIARIES", "Nombre de personnes touchées", 15, CriterionType.AUTO);
        add("URGENCY", "Urgence", 15, CriterionType.AUTO);
        add("GRAVITY", "Gravité", 10, CriterionType.MANUAL);
        add("SOCIAL_IMPACT", "Impact social", 10, CriterionType.MANUAL);
        add("HEALTH_IMPACT", "Impact sanitaire", 10, CriterionType.MANUAL);
        add("COST_FEASIBILITY", "Coût estimé et faisabilité", 10, CriterionType.AUTO);
        add("EQUITY", "Équité territoriale et sociale", 15, CriterionType.AUTO);
    }

    private void add(String code, String label, double weight, CriterionType type) {
        Criterion c = new Criterion();
        c.setCode(code);
        c.setLabel(label);
        c.setWeight(weight);
        c.setType(type);
        criteria.save(c);
    }

    private void seedTemplates() {
        if (templates.count() > 0) return;
        Map<NeedStatus, String[]> defaults = Map.of(
                NeedStatus.RECU, new String[]{"Accusé de réception {trackingNumber}",
                        "Votre demande « {title} » a bien été reçue. Numéro de suivi : {trackingNumber}."},
                NeedStatus.EN_ANALYSE, new String[]{"Votre demande {trackingNumber} est en analyse",
                        "Votre demande « {title} » a été validée et est en cours d'analyse."},
                NeedStatus.RETENU, new String[]{"Votre demande {trackingNumber} a été retenue",
                        "Bonne nouvelle : votre demande « {title} » a été retenue par la municipalité."},
                NeedStatus.PLANIFIE, new String[]{"Votre demande {trackingNumber} est planifiée",
                        "Votre demande « {title} » est désormais planifiée."},
                NeedStatus.EN_COURS, new String[]{"Réalisation en cours ({trackingNumber})",
                        "Les travaux liés à votre demande « {title} » ont commencé."},
                NeedStatus.REALISE, new String[]{"Demande {trackingNumber} réalisée",
                        "Votre demande « {title} » a été réalisée. Merci de votre participation."},
                NeedStatus.REJETE, new String[]{"Votre demande {trackingNumber} n'a pas été retenue",
                        "Votre demande « {title} » n'a pas pu être retenue. Motif : {reason}"});
        defaults.forEach((status, tpl) -> {
            NotificationTemplate t = new NotificationTemplate();
            t.setStatus(status);
            t.setSubject(tpl[0]);
            t.setBody(tpl[1]);
            templates.save(t);
        });
    }
}
