// ── SmartHire · src/test/java/com/smarthire/service/AiScoringServiceTest.java ──
package com.smarthire.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.domain.entities.Application;
import com.smarthire.domain.entities.Job;
import com.smarthire.domain.entities.User;
import com.smarthire.domain.repositories.ApplicationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiScoringService — TF-IDF engine tests")
class AiScoringServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock RestTemplate restTemplate;
    @InjectMocks AiScoringService service;

    @BeforeEach
    void setUp() {
        service = new AiScoringService(applicationRepository, new ObjectMapper(), restTemplate);
        // Disable Claude so tests use TF-IDF only
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeEnabled", false);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeApiKey", "");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeApiUrl",
            "https://api.anthropic.com/v1/messages");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeModel",
            "claude-sonnet-4-20250514");
    }

    @Test
    @DisplayName("TF-IDF score is 0.0 for completely unrelated texts")
    void tfidf_unrelatedTexts_returnsNearZero() {
        double score = service.computeCosineSimilarity(
            "banana orange fruit tropical vitamin",
            "kubernetes docker container orchestration deployment"
        );
        assertThat(score).isLessThan(0.05);
    }

    @Test
    @DisplayName("TF-IDF score is > 0.8 for identical texts")
    void tfidf_identicalTexts_returnsHighScore() {
        String text = "experienced java spring boot developer with postgresql redis microservices";
        double score = service.computeCosineSimilarity(text, text);
        assertThat(score).isGreaterThan(0.80);
    }

    @Test
    @DisplayName("TF-IDF score is high for highly similar texts")
    void tfidf_similarTexts_returnsHighScore() {
        String resume = "Java Spring Boot developer 3 years PostgreSQL Redis REST APIs microservices Docker";
        String jd     = "We need a Java Spring Boot engineer with PostgreSQL experience and REST API knowledge";
        double score  = service.computeCosineSimilarity(resume, jd);
        assertThat(score).isGreaterThan(0.40);
    }

    @Test
    @DisplayName("Null resume text throws IllegalArgumentException, application not saved")
    void nullResumeText_throwsGracefully() {
        assertThatThrownBy(() -> service.computeCosineSimilarity(null, "valid job description"))
            .satisfiesAnyOf(
                ex -> assertThat(ex).isInstanceOf(IllegalArgumentException.class),
                ex -> assertThat(service.computeCosineSimilarity(null, "text")).isEqualTo(0.0)
            );
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("Empty strings return 0.0 similarity")
    void emptyStrings_returnZero() {
        assertThat(service.computeCosineSimilarity("", "some text")).isEqualTo(0.0);
        assertThat(service.computeCosineSimilarity("some text", "")).isEqualTo(0.0);
        assertThat(service.computeCosineSimilarity("", "")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Claude API disabled — scoring_method is tfidf_only")
    void claudeDisabled_scoringMethodIsTfidfOnly() {
        UUID appId = UUID.randomUUID();
        Application app = buildMockApplication(appId);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenReturn(app);

        service.scoreApplicationAsync(appId);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository, timeout(3000)).save(captor.capture());
        Application saved = captor.getValue();

        assertThat(saved.isScoringComplete()).isTrue();
        assertThat(saved.getKeywordMatches()).containsEntry("scoring_method", "tfidf_only");
        assertThat(saved.getAiScore()).isNotNull();
    }

    private Application buildMockApplication(UUID id) {
        User candidate = User.builder().id(UUID.randomUUID()).email("test@test.com").build();
        Job job = Job.builder()
            .id(UUID.randomUUID())
            .title("Java Developer")
            .description("Spring Boot Java developer needed")
            .requirements("Java Spring Boot PostgreSQL")
            .build();
        return Application.builder()
            .id(id)
            .job(job)
            .candidate(candidate)
            .resumeUrl("https://example.com/resume.pdf")
            .build();
    }
}
