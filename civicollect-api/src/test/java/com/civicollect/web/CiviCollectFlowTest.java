package com.civicollect.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcours complet : inscription → dépôt → modération → soutien → classement, avec contrôle des droits. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CiviCollectFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String register(String name, String email) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"Motdepasse1","requesterType":"HABITANT","consent":true}
                """.formatted(name, email);
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json(r).get("token").asText();
    }

    private String login(String identifier, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andExpect(status().isOk()).andReturn();
        return json(r).get("token").asText();
    }

    @Test
    void fullLifecycle() throws Exception {
        String citizen = register("Awa Diop", "awa@example.sn");
        String citizen2 = register("Moussa Fall", "moussa@example.sn");

        long categoryId = json(mvc.perform(get("/api/reference/categories")).andReturn()).get(0).get("id").asLong();

        // 1. brouillon puis soumission
        MvcResult draft = mvc.perform(post("/api/needs").header("Authorization", "Bearer " + citizen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Route défoncée\",\"description\":\"Nids de poule dangereux devant l'école\","
                                + "\"categoryId\":" + categoryId + ",\"urgency\":\"ELEVEE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BROUILLON")).andReturn();
        long needId = json(draft).get("id").asLong();

        mvc.perform(post("/api/needs/" + needId + "/submit").header("Authorization", "Bearer " + citizen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECU"))
                .andExpect(jsonPath("$.trackingNumber", matchesPattern("CC-\\d{4}-\\d{6}")));

        // 2. un citoyen ne peut pas modérer
        mvc.perform(post("/api/moderation/needs/" + needId + "/validate").header("Authorization", "Bearer " + citizen))
                .andExpect(status().isForbidden());

        // 3. un besoin non validé n'est pas soutenable ni visible d'un autre citoyen
        mvc.perform(post("/api/needs/" + needId + "/support").header("Authorization", "Bearer " + citizen2))
                .andExpect(status().isNotFound());

        // 4. création d'un agent par l'administrateur puis validation
        String admin = login("admin@civicollect.sn", "Admin@12345");
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Agent Ndiaye\",\"email\":\"agent@mairie.sn\",\"password\":\"Motdepasse1\",\"role\":\"AGENT\"}"))
                .andExpect(status().isCreated());
        String agent = login("agent@mairie.sn", "Motdepasse1");

        mvc.perform(post("/api/moderation/needs/" + needId + "/reject").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());                         // RG3 : rejet motivé

        mvc.perform(post("/api/moderation/needs/" + needId + "/validate").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EN_ANALYSE"));

        // 5. soutien unique (RG7)
        mvc.perform(post("/api/needs/" + needId + "/support").header("Authorization", "Bearer " + citizen2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supportCount").value(1));
        mvc.perform(post("/api/needs/" + needId + "/support").header("Authorization", "Bearer " + citizen2))
                .andExpect(status().isConflict());

        // 6. transition interdite (EN_ANALYSE -> REALISE)
        mvc.perform(post("/api/moderation/needs/" + needId + "/status").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"REALISE\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 7. classement explicable
        mvc.perform(get("/api/priorities/ranking").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].breakdown").isNotEmpty());

        // 8. suivi public par numéro
        String tracking = json(mvc.perform(get("/api/needs/" + needId).header("Authorization", "Bearer " + citizen)).andReturn())
                .get("trackingNumber").asText();
        mvc.perform(get("/api/public/track/" + tracking))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EN_ANALYSE"));
    }

    @Test
    void protectedRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/needs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousSubmissionWorksAndHoneypotBlocksBots() throws Exception {
        long categoryId = json(mvc.perform(get("/api/reference/categories")).andReturn()).get(0).get("id").asLong();
        String ok = "{\"title\":\"Fuite d'eau\",\"description\":\"Fuite sur la conduite principale\",\"categoryId\":" + categoryId + "}";
        mvc.perform(post("/api/public/needs/anonymous").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber", matchesPattern("CC-\\d{4}-\\d{6}")));

        String bot = "{\"title\":\"Spam\",\"description\":\"Achetez maintenant\",\"categoryId\":" + categoryId + ",\"website\":\"http://spam\"}";
        mvc.perform(post("/api/public/needs/anonymous").contentType(MediaType.APPLICATION_JSON).content(bot))
                .andExpect(status().isUnprocessableEntity());
    }
}
