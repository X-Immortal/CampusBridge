package com.campusbridge.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusbridge.dto.query.TaskQuery;
import com.campusbridge.config.MybatisPlusConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.annotation.FieldStrategy;

import com.campusbridge.common.BusinessException;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.Task;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.pojo.TaskApplication;
import com.campusbridge.service.CompletionService;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.CreditService;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.TaskService;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.service.TaskTimeoutService;
import com.campusbridge.service.impl.CompletionServiceImpl;
import com.campusbridge.service.impl.CreditServiceImpl;
import com.campusbridge.service.impl.ReviewServiceImpl;
import com.campusbridge.service.impl.TaskApplicationServiceImpl;
import com.campusbridge.service.impl.TaskHistoryServiceImpl;
import com.campusbridge.service.impl.TaskModerationService;
import com.campusbridge.service.impl.NotificationServiceImpl;
import com.campusbridge.service.impl.TaskServiceImpl;
import com.campusbridge.service.impl.TaskTimeoutServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

/**
 * Runs against an explicitly supplied disposable MySQL database whose name ends in _test.
 * Recreates its tables from the repository schema and persists in-site notifications; sends no external messages.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUSBRIDGE_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskConsistencyMySqlTest {
    private AnnotationConfigApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private TaskService tasks;
    private CompletionService completions;
    private CreditService credits;
    private ReviewService reviews;
    private NotificationService notifications;

    @BeforeAll
    void initializeDatabase() throws Exception {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        dataSource = context.getBean(DataSource.class);
        jdbc = new JdbcTemplate(dataSource);
        tasks = context.getBean(TaskService.class);
        completions = context.getBean(CompletionService.class);
        credits = context.getBean(CreditService.class);
        reviews = context.getBean(ReviewService.class);
        notifications = context.getBean(NotificationService.class);
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            List<String> tableNames = tableNames(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String table : tableNames) {
                    statement.execute("DROP TABLE `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            String schema = Files.readString(Path.of("../deploy/mysql/schema.sql"))
                    .replace("CREATE SCHEMA IF NOT EXISTS campusbridge;", "")
                    .replace("USE campusbridge;", "");
            ScriptUtils.executeSqlScript(connection,
                    new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @BeforeEach
    void createUsers() throws Exception {
        reset(notifications);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tableNames(connection)) {
                statement.execute("TRUNCATE TABLE `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        jdbc.update("INSERT INTO users (id, username, password_hash, role) VALUES "
                + "(1,'requester','test','USER'), (2,'provider','test','USER'), "
                + "(3,'other','test','USER'), (4,'admin','test','ADMIN')");
        jdbc.update("INSERT INTO campus_verifications "
                + "(user_id,method,real_name_cipher,student_number_cipher,school,status) "
                + "VALUES (1,'MANUAL_REVIEW','test-name','test-number','Test Campus','VERIFIED')");
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void incompleteDraftIsPrivateAndCannotPublishUntilRequiredFieldsExist() {
        TaskView draft = tasks.saveDraft(1L, new RequestDtos.CreateTaskRequest());
        assertThat(tasks.getTaskDetail(draft.getId(), 1L).getStatus()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> tasks.getTaskDetail(draft.getId(), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tasks.getTaskDetail(draft.getId(), 2L))
                .isInstanceOf(BusinessException.class);
        assertThat(tasks.listTasks(new TaskQuery()).getTasks()).isEmpty();
        assertThat(tasks.listTasks(TaskQuery.builder().status("DRAFT").build()).getTasks()).isEmpty();
        assertThatThrownBy(() -> tasks.resendTask(1L, draft.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(status(draft.getId())).isEqualTo("DRAFT");

        TaskView completeDraft = tasks.saveDraft(1L, request());
        assertThat(tasks.resendTask(1L, completeDraft.getId()).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void invalidPublishedEditRollsBackAndAcceptedTaskCannotBeReopenedByEdit() throws Exception {
        TaskView task = tasks.createTask(1L, request());
        RequestDtos.UpdateTaskRequest edit = new RequestDtos.UpdateTaskRequest();
        edit.setTitle(" ");
        assertThatThrownBy(() -> tasks.updateTask(1L, task.getId(), edit))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id=?", String.class, task.getId()))
                .isEqualTo("Collect test parcel");

        edit.setTitle("Changed title");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            List<Future<?>> pending = new ArrayList<>();
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
                context.getBean(TaskMapper.class).selectByIdForUpdate(task.getId());
                pending.add(executor.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> tasks.updateTask(1L, task.getId(), edit))
                            .isInstanceOf(BusinessException.class);
                }));
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    throw new IllegalStateException(ex);
                }
                jdbc.update("UPDATE tasks SET provider_id=2,status='ACCEPTED' WHERE id=?", task.getId());
            });
            pending.get(0).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(status(task.getId())).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT provider_id FROM tasks WHERE id=?", Long.class, task.getId()))
                .isEqualTo(2L);
    }

    @Test
    void concurrentFirstCreditAdjustmentsPreserveEveryIncrementAndAuditRecord() throws Exception {
        List<Callable<Boolean>> operations = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            operations.add(() -> {
                credits.adjustCredit(2L, 5, "Concurrent test adjustment", "TEST", null);
                return true;
            });
        }
        assertThat(runTogether(operations)).containsOnly(true).hasSize(20);
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT level FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_records WHERE user_id=2", Integer.class))
                .isEqualTo(20);
    }

    @Test
    void competingCompletionEndpointsAwardOnceAndTimeoutUsesTheSameRewardRule() throws Exception {
        TaskView task = taskInState("PENDING_CONFIRMATION");
        RequestDtos.TaskStatusTransitionRequest confirm = new RequestDtos.TaskStatusTransitionRequest();
        confirm.setAction(ApiEnums.TaskStatusTransitionAction.CONFIRM_COMPLETION);
        List<Boolean> results = runTogether(List.of(
                () -> attempt(() -> completions.confirmCompletion(1L, task.getId())),
                () -> attempt(() -> tasks.transitionTaskStatus(1L, task.getId(), confirm))));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(5);

        TaskView timeout = taskInState("PENDING_CONFIRMATION");
        jdbc.update("UPDATE tasks SET updated_at=DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id=?", timeout.getId());
        jdbc.update("INSERT INTO task_histories(task_id,from_status,to_status,note,created_at) "
                + "VALUES (?,'PENDING_CONFIRMATION','PENDING_CONFIRMATION',"
                + "'TIMEOUT_REMINDER_PENDING_CONFIRMATION',DATE_SUB(NOW(), INTERVAL 2 DAY))", timeout.getId());
        context.getBean(TaskTimeoutService.class).processTimeouts(LocalDateTime.now());
        assertThat(status(timeout.getId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_records WHERE user_id=2", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void completingDifferentTasksConcurrentlyDoesNotLoseProviderCredit() throws Exception {
        TaskView first = taskInState("PENDING_CONFIRMATION");
        TaskView second = taskInState("PENDING_CONFIRMATION");
        List<Boolean> outcomes = runTogether(List.of(
                () -> attempt(() -> completions.confirmCompletion(1L, first.getId())),
                () -> attempt(() -> completions.confirmCompletion(1L, second.getId()))));
        assertThat(outcomes).containsOnly(true).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_records WHERE user_id=2", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void administratorCompletionRollsBackStatusRewardAndHistoryIfNotificationFails() {
        TaskView task = taskInState("DISPUTED");
        doThrow(new IllegalStateException("notification persistence failed")).when(notifications)
                .notifyTaskStatusChanged(any(), anyString(), anyString(), anyLong(), anyString());
        assertThatThrownBy(() -> context.getBean(TaskModerationService.class).dismissAppeal(4L, task.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(status(task.getId())).isEqualTo("DISPUTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credits", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_histories WHERE note='ADMIN_DISMISSED_APPEAL'", Integer.class)).isZero();

        reset(notifications);
        context.getBean(TaskModerationService.class).dismissAppeal(4L, task.getId());
        assertThat(status(task.getId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void oppositeParticipantRewardsCanPersistNotificationsWithoutUserRowDeadlock() throws Exception {
        TaskView first = taskInState("PENDING_CONFIRMATION");
        TaskView second = taskInState("PENDING_CONFIRMATION");
        jdbc.update("UPDATE tasks SET requester_id=2,provider_id=1 WHERE id=?", second.getId());
        CountDownLatch bothAwardsReached = new CountDownLatch(2);
        NotificationService delegate = context.getBean("notificationDelegate", NotificationService.class);
        doAnswer(invocation -> {
            bothAwardsReached.countDown();
            if (!bothAwardsReached.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent rewards did not both reach notification persistence");
            }
            return delegatesTo(delegate).answer(invocation);
        }).when(notifications).notifyTaskStatusChanged(any(), anyString(), anyString(), anyLong(), anyString());

        assertThat(runTogether(List.of(
                () -> attempt(() -> completions.confirmCompletion(1L, first.getId())),
                () -> attempt(() -> completions.confirmCompletion(2L, second.getId())))))
                .containsOnly(true).hasSize(2);
        assertThat(jdbc.queryForList("SELECT points FROM user_credits ORDER BY user_id", Integer.class))
                .containsExactly(5, 5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isEqualTo(2);
    }

    @Test
    void timeoutCommitsEachTaskBeforeCompetingManualCompletionOfTheNextTask() throws Exception {
        TaskView first = taskInState("PENDING_CONFIRMATION");
        TaskView second = taskInState("PENDING_CONFIRMATION");
        for (TaskView task : List.of(first, second)) {
            jdbc.update("UPDATE tasks SET updated_at=DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id=?", task.getId());
            jdbc.update("INSERT INTO task_histories(task_id,from_status,to_status,note,created_at) "
                    + "VALUES (?,'PENDING_CONFIRMATION','PENDING_CONFIRMATION',"
                    + "'TIMEOUT_REMINDER_PENDING_CONFIRMATION',DATE_SUB(NOW(), INTERVAL 2 DAY))", task.getId());
        }
        CountDownLatch timeoutAwardedFirst = new CountDownLatch(1);
        CountDownLatch manualLockedSecond = new CountDownLatch(1);
        NotificationService delegate = context.getBean("notificationDelegate", NotificationService.class);
        doAnswer(invocation -> {
            Task notifyingTask = invocation.getArgument(0);
            Long operator = invocation.getArgument(3);
            if (first.getId().equals(notifyingTask.getId()) && operator == null) {
                timeoutAwardedFirst.countDown();
                if (!manualLockedSecond.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout batch retained the next task lock");
                }
            }
            return delegatesTo(delegate).answer(invocation);
        }).when(notifications).notifyTaskStatusChanged(any(), anyString(), anyString(), any(), anyString());

        assertThat(runTogether(List.of(
                () -> {
                    context.getBean(TaskTimeoutService.class).processTimeouts(LocalDateTime.now());
                    return true;
                },
                () -> {
                    assertThat(timeoutAwardedFirst.await(10, TimeUnit.SECONDS)).isTrue();
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                            .executeWithoutResult(transaction -> {
                                context.getBean(TaskMapper.class).selectByIdForUpdate(second.getId());
                                manualLockedSecond.countDown();
                                completions.confirmCompletion(1L, second.getId());
                            });
                    return true;
                }))).containsOnly(true).hasSize(2);
        assertThat(status(first.getId())).isEqualTo("COMPLETED");
        assertThat(status(second.getId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=2", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_records", Integer.class)).isEqualTo(2);
    }

    @Test
    void simultaneousCompletionProofsForOneTaskReturnAConflictWithoutAForeignKeyLockUpgrade() throws Exception {
        TaskView task = taskInState("IN_PROGRESS");
        RequestDtos.SubmitCompletionRequest proof = new RequestDtos.SubmitCompletionRequest("Delivered", List.of());
        assertThat(runTogether(List.of(
                () -> attempt(() -> completions.submitCompletionProof(2L, task.getId(), proof)),
                () -> attempt(() -> completions.submitCompletionProof(2L, task.getId(), proof)))))
                .containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM completion_proofs", Integer.class)).isEqualTo(1);
        assertThat(status(task.getId())).isEqualTo("PENDING_CONFIRMATION");
    }

    @Test
    void taskPublicationRejectsAttachmentsThatWouldOtherwiseBeSilentlyDiscarded() {
        RequestDtos.CreateTaskRequest unsupported = request();
        unsupported.setAttachmentIds(List.of(1L));
        assertThatThrownBy(() -> tasks.createTask(1L, unsupported)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tasks.saveDraft(1L, unsupported)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class)).isZero();
    }

    @Test
    void genericCancelAppliesTheExistingInProgressPenalty() {
        TaskView task = taskInState("IN_PROGRESS");
        RequestDtos.TaskStatusTransitionRequest cancel = new RequestDtos.TaskStatusTransitionRequest();
        cancel.setAction(ApiEnums.TaskStatusTransitionAction.CANCEL);
        cancel.setReason("Requester cancels");
        tasks.transitionTaskStatus(1L, task.getId(), cancel);
        assertThat(status(task.getId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT points FROM user_credits WHERE user_id=1", Integer.class))
                .isEqualTo(-1);
    }

    @Test
    void reviewsRejectUnrelatedRecipientsAndConcurrentDuplicates() throws Exception {
        TaskView task = taskInState("COMPLETED");
        RequestDtos.CreateReviewRequest review = new RequestDtos.CreateReviewRequest(3L, 5, "Test review");
        assertThatThrownBy(() -> reviews.createReview(1L, task.getId(), review))
                .isInstanceOf(BusinessException.class);
        review.setRevieweeId(1L);
        assertThatThrownBy(() -> reviews.createReview(1L, task.getId(), review))
                .isInstanceOf(BusinessException.class);
        review.setRevieweeId(2L);
        List<Boolean> outcomes = runTogether(List.of(
                () -> attempt(() -> reviews.createReview(1L, task.getId(), review)),
                () -> attempt(() -> reviews.createReview(1L, task.getId(), review))));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isEqualTo(1);
    }

    @Test
    void oneTemporaryAttachmentCannotBeClaimedByTwoCompletionProofs() throws Exception {
        TaskView first = taskInState("IN_PROGRESS");
        TaskView second = taskInState("IN_PROGRESS");
        jdbc.update("INSERT INTO attachments "
                + "(id,uploader_id,owner_type,type,file_name,file_url,content_type,size,status) "
                + "VALUES (1,2,'COMPLETION_PROOF','IMAGE','test.png','https://example.invalid/test.png',"
                + "'image/png',10,'TEMPORARY')");
        RequestDtos.SubmitCompletionRequest proof = new RequestDtos.SubmitCompletionRequest("Delivered", List.of(1L));
        List<Boolean> outcomes = runTogether(List.of(
                () -> attempt(() -> completions.submitCompletionProof(2L, first.getId(), proof)),
                () -> attempt(() -> completions.submitCompletionProof(2L, second.getId(), proof))));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM completion_proofs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM attachments WHERE id=1", String.class)).isEqualTo("BOUND");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE status='PENDING_CONFIRMATION'", Integer.class)).isEqualTo(1);
        assertThat(context.getBean(AttachmentMapper.class)
                .bindTemporaryToOwner(1L, 2L, "COMPLETION_PROOF", 999L)).isZero();
    }

    @Test
    void previousLifecycleReminderDoesNotSkipTheNewGracePeriod() {
        TaskView task = taskInState("OPEN");
        jdbc.update("UPDATE tasks SET deadline=DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id=?", task.getId());
        jdbc.update("INSERT INTO task_histories(task_id,from_status,to_status,note,created_at) "
                + "VALUES (?,'OPEN','OPEN','TIMEOUT_REMINDER_OPEN',DATE_SUB(NOW(), INTERVAL 3 DAY))", task.getId());
        context.getBean(TaskTimeoutService.class).processTimeouts(LocalDateTime.now());
        assertThat(status(task.getId())).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_histories WHERE task_id=? AND note='TIMEOUT_REMINDER_OPEN'",
                Integer.class, task.getId())).isEqualTo(2);
    }

    @Test
    void acceptingWhileAnotherUserAppliesCannotLeaveAnUnprocessedPendingApplication() throws Exception {
        TaskView task = tasks.createTask(1L, request());
        TaskApplicationService applications = context.getBean(TaskApplicationService.class);
        RequestDtos.CreateTaskApplicationRequest apply = new RequestDtos.CreateTaskApplicationRequest("Can help");
        TaskApplication selected = applications.createApplication(2L, task.getId(), apply);
        runTogether(List.of(
                () -> attempt(() -> applications.acceptApplication(1L, task.getId(), selected.getId())),
                () -> attempt(() -> applications.createApplication(3L, task.getId(), apply))));
        assertThat(status(task.getId())).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_applications WHERE task_id=? AND status='PENDING'",
                Integer.class, task.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_applications WHERE task_id=? AND status='ACCEPTED'",
                Integer.class, task.getId())).isEqualTo(1);
    }

    @Test
    void publicFilteringDoesNotHideTheAdministratorsPendingQueue() {
        TaskView task = taskInState("PENDING_REVIEW");
        TaskMapper mapper = context.getBean(TaskMapper.class);
        var publicPage = mapper.selectWithFilters(new Page<>(1, 20),
                TaskQuery.builder().status("PENDING_REVIEW").build());
        assertThat(publicPage.getRecords()).isEmpty();
        assertThat(publicPage.getTotal()).isZero();
        var adminPage = mapper.selectWithFilters(new Page<>(1, 20),
                TaskQuery.builder().status("PENDING_REVIEW").includePrivate(true).build());
        assertThat(adminPage.getRecords()).extracting(TaskView::getId).containsExactly(task.getId());
        assertThat(adminPage.getTotal()).isEqualTo(1);
        var recordsOnly = mapper.selectWithFilters(new Page<>(1, 20, false),
                TaskQuery.builder().status("PENDING_REVIEW").includePrivate(true).build());
        assertThat(recordsOnly.getRecords()).extracting(TaskView::getId).containsExactly(task.getId());
    }

    @Test
    void filteredTaskPagesCountAllMatchesAndKeepPrivateTasksOut() {
        TaskView first = taskInState("OPEN");
        TaskView second = taskInState("OPEN");
        TaskView otherCampus = taskInState("OPEN");
        TaskView expired = taskInState("OPEN");
        taskInState("DRAFT");
        jdbc.update("UPDATE tasks SET campus='Other Campus' WHERE id=?", otherCampus.getId());
        jdbc.update("UPDATE tasks SET deadline=DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id=?", expired.getId());
        jdbc.update("UPDATE tasks SET deadline=DATE_ADD(NOW(), INTERVAL 2 DAY) WHERE id=?", second.getId());

        TaskQuery query = TaskQuery.builder()
                .keyword("parcel").category("ERRAND").campus("Test Campus")
                .rewardType("POINTS").requiresProof(true).excludeExpired(true)
                .sortBy("DEADLINE_ASC").page(2).size(1).includePrivate(true).build();
        var page = tasks.listTasks(query);
        assertThat(page.getTasks()).extracting(TaskView::getId).containsExactly(second.getId());
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(1);

        query.setPage(3);
        var emptyPage = tasks.listTasks(query);
        assertThat(emptyPage.getTasks()).isEmpty();
        assertThat(emptyPage.getTotal()).isEqualTo(2);
        assertThat(emptyPage.getPage()).isEqualTo(3);
        query.setPage(1);
        assertThat(tasks.listTasks(query).getTasks()).extracting(TaskView::getId).containsExactly(first.getId());
        query.setRequiresProof(false);
        assertThat(tasks.listTasks(query).getTasks()).isEmpty();
        assertThat(tasks.listTasks(query).getTotal()).isZero();
    }

    private RequestDtos.CreateTaskRequest request() {
        RequestDtos.CreateTaskRequest request = new RequestDtos.CreateTaskRequest();
        request.setCategory(ApiEnums.TaskCategory.ERRAND);
        request.setTitle("Collect test parcel");
        request.setDescription("Deliver a test parcel to the campus gate");
        request.setCampus("Test Campus");
        request.setLocation("Test Gate");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        request.setRewardType(ApiEnums.RewardType.POINTS);
        request.setRewardAmount(5);
        request.setCompletionStandard("Recipient confirms delivery");
        request.setRequiresProof(true);
        return request;
    }

    private TaskView taskInState(String state) {
        TaskView task = tasks.createTask(1L, request());
        jdbc.update("UPDATE tasks SET status=?, provider_id=2 WHERE id=?", state, task.getId());
        return task;
    }

    private String status(Long taskId) {
        return jdbc.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, taskId);
    }

    private boolean attempt(Runnable action) {
        try {
            action.run();
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private List<Boolean> runTogether(List<Callable<Boolean>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start");
                    }
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> tableNames(Connection connection) throws Exception {
        List<String> names = new ArrayList<>();
        try (ResultSet tables = connection.getMetaData()
                .getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.campusbridge.mapper")
    @Import({TaskServiceImpl.class, TaskApplicationServiceImpl.class, CompletionServiceImpl.class,
            CreditServiceImpl.class, ReviewServiceImpl.class, TaskHistoryServiceImpl.class,
            TaskTimeoutServiceImpl.class, TaskModerationService.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setUrl(System.getenv("CAMPUSBRIDGE_TEST_MYSQL_URL"));
            source.setUsername(System.getenv().getOrDefault("CAMPUSBRIDGE_TEST_MYSQL_USER", "root"));
            source.setPassword(System.getenv().getOrDefault("CAMPUSBRIDGE_TEST_MYSQL_PASSWORD", ""));
            return source;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:com/campusbridge/mapper/*.xml"));
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
            factory.setGlobalConfig(new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()
                    .setInsertStrategy(FieldStrategy.ALWAYS).setUpdateStrategy(FieldStrategy.ALWAYS)));
            SqlSessionFactory sessions = factory.getObject();
            sessions.getConfiguration().addMappers("com.campusbridge.mapper");
            return sessions;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        NotificationServiceImpl notificationDelegate(
                ConversationMapper conversations, MessageMapper messages, UserMapper users) {
            return new NotificationServiceImpl(conversations, messages, users);
        }

        @Bean
        @Primary
        NotificationService notificationService(NotificationServiceImpl notificationDelegate) {
            return mock(NotificationService.class, delegatesTo(notificationDelegate));
        }

        @Bean
        ContentModerationService contentModerationService() {
            return mock(ContentModerationService.class);
        }
    }

    @Test
    void taskNamesBelongToTheReadModelAndLockedEntityRemainsWritable() {
        jdbc.update("INSERT INTO user_profiles(user_id,nickname) VALUES(1,'Requester'),(2,'Provider')");
        TaskView created = tasks.createTask(1L, request());
        jdbc.update("UPDATE tasks SET provider_id=2 WHERE id=?", created.getId());
        TaskMapper mapper = context.getBean(TaskMapper.class);
        TaskView view = mapper.selectDetailById(created.getId());
        assertThat(view.getRequesterName()).isEqualTo("Requester");
        assertThat(view.getProviderName()).isEqualTo("Provider");
        view.setTitle("Display-only change");

        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            Task entity = mapper.selectByIdForUpdate(created.getId());
            assertThat(entity.getTitle()).isEqualTo("Collect test parcel");
            entity.setTitle("Updated task");
            assertThat(mapper.updateById(entity)).isEqualTo(1);
        });
        TaskView updated = tasks.getTaskDetail(created.getId(), 1L);
        assertThat(updated.getTitle()).isEqualTo("Updated task");
        assertThat(updated.getRequesterName()).isEqualTo("Requester");
        assertThat(updated.getProviderName()).isEqualTo("Provider");
    }
}
