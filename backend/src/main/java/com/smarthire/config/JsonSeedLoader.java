package com.smarthire.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.domain.Candidate;
import com.smarthire.domain.JobDescription;
import com.smarthire.domain.Question;
import com.smarthire.domain.Role;
import com.smarthire.domain.User;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.JdRepository;
import com.smarthire.repository.QuestionRepository;
import com.smarthire.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Requirement: "All data lives in Input_Data.json and is loaded into the in-memory
 * store on startup." (docx §4/§5 Seed Loader)
 *
 * Runs after Flyway has created the empty schema (V1__init.sql). Idempotent: seeds
 * only when the users table is empty. Since the DB is in-memory, every restart
 * replays: Flyway V1 → this loader → identical seed state.
 */
@Component
public class JsonSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonSeedLoader.class);
    private static final String SEED_FILE = "seed/Input_Data.json";

    private final UserRepository userRepo;
    private final JdRepository jdRepo;
    private final QuestionRepository questionRepo;
    private final CandidateRepository candidateRepo;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper mapper;

    public JsonSeedLoader(UserRepository userRepo, JdRepository jdRepo,
                          QuestionRepository questionRepo, CandidateRepository candidateRepo,
                          PasswordEncoder passwordEncoder, ObjectMapper mapper) {
        this.userRepo = userRepo;
        this.jdRepo = jdRepo;
        this.questionRepo = questionRepo;
        this.candidateRepo = candidateRepo;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedFile {
        public List<SeedJd> job_descriptions;
        public List<SeedQuestion> questions;
        public List<SeedCandidate> candidates;
        public List<SeedUser> users;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedJd {
        public String id;
        public String title;
        public List<String> must_have;
        public List<String> nice_to_have;
        public int experience_years;
        public String education;
        public String location;
        public Map<String, Double> weight;
        public double pass_threshold;
        public double confidence_cutoff;
        public String summary;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedQuestion {
        public String id;
        public String jd_id;
        public String text;
        public String reference_answer;
        public Object rubric;              // banded rubric (score_5/score_3/score_0) — stored as JSON text
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedCandidate {
        public String id;
        public String user;                // username → linked User
        public String name;
        public String email;
        public String applied_id;          // jd string id → resolved to numeric jd
        public int experience_years;
        public String education;
        public String location;
        public String profile_type;
        public String resume;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedUser {
        public String username;
        public String role;
        public String password;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepo.count() > 0) {
            log.info("Seed skipped — data already present");
            return;
        }
        SeedFile seed = mapper.readValue(new ClassPathResource(SEED_FILE).getInputStream(), SeedFile.class);

        // 1. Users (roles normalized to uppercase; passwords BCrypt-encoded here)
        Map<String, User> usersByName = new HashMap<>();
        for (SeedUser su : seed.users) {
            User u = new User();
            u.setUsername(su.username);
            u.setPasswordHash(passwordEncoder.encode(su.password));
            u.setRole(Role.valueOf(su.role.toUpperCase()));
            userRepo.save(u);
            usersByName.put(su.username, u);
        }
        log.info("Seeded {} users", usersByName.size());

        // 2. Job descriptions (string ids → numeric identity ids, kept in a map)
        Map<String, Long> jdIdMap = new HashMap<>();
        for (SeedJd s : seed.job_descriptions) {
            JobDescription jd = new JobDescription();
            jd.setTitle(s.title);
            jd.setLocation(s.location);
            jd.setExperienceYears(s.experience_years);
            jd.setEducation(s.education);
            jd.setMustHave(s.must_have == null ? "" : String.join(", ", s.must_have));
            jd.setNiceToHave(s.nice_to_have == null ? "" : String.join(", ", s.nice_to_have));
            if (s.weight != null) {
                if (s.weight.get("resume") != null) jd.setResumeWeight(s.weight.get("resume"));
                if (s.weight.get("qa") != null) jd.setQaWeight(s.weight.get("qa"));
            }
            jd.setPassThreshold(s.pass_threshold);
            jd.setConfidenceCutoff(s.confidence_cutoff);
            jd.setSummary(s.summary);
            jd = jdRepo.save(jd);
            jdIdMap.put(s.id, jd.getId());
        }
        log.info("Seeded {} job descriptions", jdIdMap.size());

        // 3. Questions — banded rubric serialized to TEXT; the AI layer parses it
        int order = 0;
        Long currentJd = null;
        for (SeedQuestion q : seed.questions) {
            Long jdNumeric = jdIdMap.get(q.jd_id);
            if (!jdNumeric.equals(currentJd)) { order = 0; currentJd = jdNumeric; }
            Question question = new Question();
            question.setJdId(jdNumeric);
            question.setText(q.text);
            question.setReferenceAnswer(q.reference_answer);
            question.setRubric(mapper.writeValueAsString(q.rubric));  // banded rubric as JSON
            question.setOrderIndex(++order);
            questionRepo.save(question);
        }
        log.info("Seeded {} questions", seed.questions.size());

        // 4. Candidates — linked to their login user via username
        for (SeedCandidate c : seed.candidates) {
            Candidate candidate = new Candidate();
            User linked = usersByName.get(c.user);
            if (linked != null) {
                candidate.setUserId(linked.getId());
            }
            candidate.setName(c.name);
            candidate.setEmail(c.email);
            candidate.setAppliedJd(jdIdMap.get(c.applied_id));
            candidate.setExperienceYears(c.experience_years);
            candidate.setEducation(c.education);
            candidate.setLocation(c.location);
            candidate.setProfileType(c.profile_type);
            candidate.setResume(c.resume);
            candidate.setStatus("APPLIED");
            candidate = candidateRepo.save(candidate);
            if (linked != null) {
                linked.setCandidateRefId(candidate.getId());
                userRepo.save(linked);
            }
        }
        log.info("Seeded {} candidates", seed.candidates.size());
        log.info("Seed complete from {}", SEED_FILE);
    }
}
