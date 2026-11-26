# CiviCollect — API REST (Spring Boot 3 / Java 17)

Backend de la plateforme municipale de participation citoyenne décrite dans le cahier des charges :
**recueillir** les besoins des habitants et organisations, les **qualifier**, les **analyser** et les **prioriser**
de façon transparente et explicable.

## Démarrage rapide

```bash
# Mode développement : base H2 en mémoire, aucune installation requise
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Tests (unitaires + parcours d'intégration sur H2)
mvn test

# Production : PostgreSQL + API via Docker
JWT_SECRET=<64+ caractères aléatoires> ADMIN_PASSWORD=<mot de passe fort> docker compose up --build
```

* Documentation interactive : <http://localhost:8080/swagger-ui.html> (bouton *Authorize* → coller le JWT)
* Compte administrateur créé au premier démarrage : `admin@civicollect.sn` / `Admin@12345`
  (**à changer** ; surchargeable par `ADMIN_EMAIL` / `ADMIN_PASSWORD`).
* Les zones, services et catégories créés au démarrage sont des **exemples** à adapter (API `/api/admin/*`).

## Architecture

```
web (controllers REST, validation @Valid, @PreAuthorize)
 └─ service (règles de gestion, transactions, mapping vers DTO)
     ├─ service.scoring (moteur de priorisation – pattern Strategy)
     ├─ event (Observer : changement de statut → notifications)
     └─ repository (Spring Data JPA, Specifications)
          └─ domain (entités JPA + énumérations métier)
security (JWT stateless, rôles) · exception (ProblemDetail RFC 7807) · config · dto · util
```

| Principe / fondamental | Où le voir dans le code |
|---|---|
| Architecture en couches, séparation des responsabilités | packages `web` → `service` → `repository` → `domain` |
| DTO (aucune entité JPA exposée) | package `dto`, `NeedMapper` |
| Injection de dépendances par constructeur | `@RequiredArgsConstructor` partout |
| **Strategy** (ouvert/fermé) | `ScoringStrategy` + 1 classe par critère ; `MessageChannel` (e-mail, SMS) |
| **State** (machine à états) | `NeedStatus.canMoveTo(...)` : toutes les transitions du cycle de vie en un seul endroit |
| **Observer** | `NeedStatusChangedEvent` → `NeedEventListener` → `NotificationService` |
| **Specification** | `NeedSpecifications` : filtres de recherche composables |
| Validation déclarative | Bean Validation sur les DTO, contraintes d'unicité en base (RG7) |
| Gestion centralisée des erreurs | `GlobalExceptionHandler` (ProblemDetail) + exceptions métier typées |
| Sécurité | JWT, BCrypt, `@PreAuthorize`, limitation de débit, honeypot, contrôle de signature des fichiers |
| Transactions | `@Transactional` sur les services ; audit et historique dans la même transaction |
| Tests | `src/test` : machine à états, stratégies de scoring, doublons, parcours complet MockMvc |

## Score de priorité (RF23-RF26)

```
scoreBase  = 100 × Σ(poids_i × score_i) / Σ(poids_i)       score_i ∈ [0, 1]
scoreFinal = borné [0, 100] (scoreBase + ajustement manuel motivé)
```

| Critère (code) | Type | Calcul |
|---|---|---|
| `SUPPORTS` | auto | demandes + soutiens, **plafonné** (`app.scoring.support-cap`) contre la domination des groupes organisés |
| `BENEFICIARIES` | auto | échelle logarithmique normalisée sur le max |
| `URGENCY` | auto | FAIBLE 0,25 → CRITIQUE 1,0 |
| `COST_FEASIBILITY` | auto | coût faible = score élevé (inconnu = 0,5) |
| `EQUITY` | auto | compense les zones sous-représentées (dépôts / habitant si population connue) |
| `GRAVITY`, `SOCIAL_IMPACT`, `HEALTH_IMPACT` | manuel | valeur 0-10 saisie par un agent |

* Classement **reproductible** (tri déterministe) et **explicable** (`breakdown` : contribution en points de chaque critère).
* Ajouter un critère : créer un `@Component implements ScoringStrategy` + une ligne `Criterion` (admin).
* Simulation de scénarios : `POST /api/priorities/simulate` avec des poids alternatifs, rien n'est enregistré.

## Rôles et droits

| Rôle | Peut |
|---|---|
| `HABITANT`, `ASSOCIATION` | s'inscrire, déposer/modifier un brouillon, soumettre, soutenir, commenter, suivre ses besoins |
| `AGENT` | modérer (valider, rejeter motivé, demander un complément), saisir pour un tiers, affecter, estimer, regrouper, évaluer les critères manuels |
| `ELU` | consulter analyses et classement, retenir un besoin ; si **habilité** : modifier les poids, ajuster le classement (RG4/RG5) |
| `ADMIN` | tout ce qui précède + utilisateurs, référentiels, critères, modèles de messages, journal d'audit |

## Endpoints principaux

| Zone | Endpoints | Exigences |
|---|---|---|
| Auth | `POST /api/auth/register · login · forgot-password · reset-password` ; `GET/PUT /api/me` | RF1-RF5 |
| Dépôt | `POST /api/needs` (brouillon), `PUT /api/needs/{id}`, `POST /api/needs/{id}/submit`, `POST /api/public/needs/anonymous`, `POST /api/needs/agent-entry` | RF4, RF6-RF9, RF11 |
| Collaboration | `POST/DELETE /api/needs/{id}/support`, `/comments`, `/attachments` | RF10, RG7 |
| Consultation | `GET /api/needs?q=&categoryId=&zoneId=&status=…`, `/{id}`, `/{id}/history` | RF21, RF31 |
| Modération | `GET /api/moderation/queue`, `POST …/validate · reject · request-complement · status · merge`, `PUT …/assignment · estimate`, `GET /api/needs/{id}/duplicates` | RF12-RF17, RF28 |
| Analyse | `GET /api/analytics/dashboard · map · export?format=csv\|pdf` | RF18-RF21 |
| Priorisation | `GET /api/priorities/ranking`, `POST …/simulate · recalculate`, `PUT …/criteria/{id}/weight`, `PUT …/needs/{id}/evaluations`, `POST …/needs/{id}/adjustment`, `GET …/report?format=pdf\|csv` | RF23-RF27 |
| Transparence | `GET /api/public/needs`, `/needs/map`, `/track/{numéro}` | RF11, RF30 |
| Notifications | `GET /api/me/notifications` (+ e-mail/SMS via `MessageChannel`) | RF29 |
| Administration | `/api/admin/users · categories · zones · services · notification-templates · audit` | RF32-RF35 |

## Règles de gestion

RG1 `Need.requester` unique + `Support` · RG2 `NeedStatus.isRankable()` · RG3 rejet sans motif refusé ·
RG4 `PriorityService.updateWeight` (`canManagePriorities`) · RG5 `Decision` + justification obligatoire ·
RG6 `Need.mergedInto` · RG7 contrainte unique `(user_id, need_id)` + contrôle service.

## Limites connues / pistes d'amélioration

* **Non implémenté** (marqué optionnel dans le cahier des charges) : analyse thématique automatique (RF22), canal USSD, import CSV.
* E-mail et SMS sont **simulés** (`LogEmailChannel`, `LogSmsChannel`) : brancher `JavaMailSender` / un agrégateur SMS en implémentant `MessageChannel`.
* Géolocalisation en latitude/longitude simples ; PostGIS pourra être ajouté pour les requêtes spatiales.
* Recherche plein texte par `LIKE` ; passer à `tsvector` PostgreSQL si le volume l'exige.
* `ddl-auto=update` pour le développement : utiliser Flyway/Liquibase en production.
* Limitation de débit en mémoire : à déporter (Redis) si plusieurs instances ; jetons JWT sans révocation (durée courte de 2 h).
* Stockage des pièces jointes sur disque local : prévoir un stockage objet (S3/MinIO).
