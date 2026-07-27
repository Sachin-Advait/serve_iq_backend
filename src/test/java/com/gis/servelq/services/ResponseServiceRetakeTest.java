package com.gis.servelq.services;

import com.gis.servelq.dto.SurveySubmissionRequest;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.ResponseModel;
import com.gis.servelq.models.SurveyDefinition;
import com.gis.servelq.models.User;
import com.gis.servelq.repository.QuizSurveyRepository;
import com.gis.servelq.repository.ResponseRepo;
import com.gis.servelq.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the retake accounting on quiz submission.
 *
 * The bug these are here to pin down: maxRetake is a single column on the shared
 * quiz row, but storeResponse decremented it on every submission by anybody
 * while comparing it against the submitting user's own attempt count. So a quiz
 * with maxRetake=3 aimed at a whole branch stopped accepting anything from
 * anyone after the first three submissions in total.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResponseServiceRetakeTest {

    private static final UUID QUIZ_ID = UUID.randomUUID();

    @Mock private QuizSurveyRepository quizSurveyRepo;
    @Mock private ResponseRepo responseRepo;
    @Mock private UserRepository userRepository;

    private ResponseService responseService;

    private QuizSurveyModel quiz;

    @BeforeEach
    void setUp() {
        responseService = new ResponseService(quizSurveyRepo, responseRepo, userRepository);

        quiz = new QuizSurveyModel();
        quiz.setId(QUIZ_ID);
        quiz.setType("quiz");
        quiz.setMaxRetake(2);
        quiz.setTargetedUsers(new HashSet<>(Set.of("alice", "bob", "carol")));
        quiz.setDefinitionJson(oneQuestionQuiz());
        quiz.setAnswerKey(new HashMap<>(Map.of("q1", "yes")));

        when(quizSurveyRepo.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(responseRepo.save(any(ResponseModel.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Smallest definition the scorer will accept: one page, one 10 mark question. */
    private SurveyDefinition oneQuestionQuiz() {
        SurveyDefinition.Element q = new SurveyDefinition.Element();
        q.setType("radiogroup");
        q.setName("q1");
        q.setTitle("Is this a test?");
        q.setMarks(10);
        q.setChoices(List.of("yes", "no"));
        q.setCorrectAnswer("yes");

        SurveyDefinition.Page page = new SurveyDefinition.Page();
        page.setElements(List.of(q));

        SurveyDefinition definition = new SurveyDefinition();
        definition.setPages(List.of(page));
        return definition;
    }

    private void userExists(String id) {
        User u = new User();
        u.setId(id);
        u.setName(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
    }

    private void attemptsSoFar(String userId, int count) {
        List<ResponseModel> existing = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            existing.add(new ResponseModel());
        }
        when(responseRepo.findByQuizSurveyIdAndUserId(QUIZ_ID, userId)).thenReturn(existing);
    }

    private SurveySubmissionRequest submission(String userId) {
        SurveySubmissionRequest req = new SurveySubmissionRequest();
        req.setUserId(userId);
        req.setAnswers(new HashMap<>(Map.of("q1", "yes")));
        return req;
    }

    @Test
    void firstAttemptIsAccepted() {
        userExists("alice");
        attemptsSoFar("alice", 0);

        assertThat(responseService.storeResponse(QUIZ_ID, submission("alice"))).isNotNull();
    }

    @Test
    void attemptsUpToTheLimitAreAccepted() {
        userExists("alice");
        attemptsSoFar("alice", 1);   // maxRetake is 2, so a second attempt is fine

        assertThat(responseService.storeResponse(QUIZ_ID, submission("alice"))).isNotNull();
    }

    @Test
    void attemptBeyondTheLimitIsRejected() {
        userExists("alice");
        attemptsSoFar("alice", 2);

        assertThatThrownBy(() -> responseService.storeResponse(QUIZ_ID, submission("alice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max quiz attempts exceeded");
    }

    /**
     * The regression test for the lockout. Bob submitting must not consume any
     * of Alice's allowance.
     */
    @Test
    void oneUsersSubmissionsDoNotConsumeAnotherUsersAllowance() {
        userExists("bob");
        attemptsSoFar("bob", 0);
        responseService.storeResponse(QUIZ_ID, submission("bob"));

        userExists("carol");
        attemptsSoFar("carol", 0);
        responseService.storeResponse(QUIZ_ID, submission("carol"));

        // Alice has not submitted at all yet, so she still gets her full quota.
        userExists("alice");
        attemptsSoFar("alice", 0);

        assertThat(responseService.storeResponse(QUIZ_ID, submission("alice"))).isNotNull();
    }

    /**
     * The shared counter must stay put. It was being decremented and saved on
     * every submit, which both corrupted the limit for everyone and rewrote two
     * large jsonb columns to change one integer.
     */
    @Test
    void submittingDoesNotMutateTheSharedQuizRow() {
        userExists("alice");
        attemptsSoFar("alice", 0);

        responseService.storeResponse(QUIZ_ID, submission("alice"));

        assertThat(quiz.getMaxRetake()).isEqualTo(2);
        verify(quizSurveyRepo, never()).save(any(QuizSurveyModel.class));
    }

    @Test
    void userWhoIsNotTargetedCannotSubmit() {
        userExists("dave");

        assertThatThrownBy(() -> responseService.storeResponse(QUIZ_ID, submission("dave")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void surveysAcceptOnlyOneSubmission() {
        quiz.setType("survey");
        userExists("alice");
        attemptsSoFar("alice", 1);

        assertThatThrownBy(() -> responseService.storeResponse(QUIZ_ID, submission("alice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already submitted");
    }
}
