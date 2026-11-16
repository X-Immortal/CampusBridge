package com.campusbridge.integration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.campusbridge.config.MybatisPlusConfig;
import com.campusbridge.dto.request.CreateConversationRequest;
import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.dto.request.SendMessageRequest;
import com.campusbridge.dto.view.CategoryCount;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.entity.Report;
import com.campusbridge.entity.UserProfile;
import com.campusbridge.enums.MessageType;
import com.campusbridge.mapper.AdminDashboardMapper;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserProfileMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.impl.ConversationServiceImpl;
import com.campusbridge.service.impl.EmailVerificationCodeSender;
import com.campusbridge.service.impl.InteractionServiceImpl;
import com.campusbridge.service.impl.NotificationServiceImpl;
import com.campusbridge.service.impl.ReplyServiceImpl;
import com.campusbridge.service.impl.ReportServiceImpl;
import com.campusbridge.service.impl.VerificationServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Runs the checked-in schema and MyBatis SQL against an explicitly selected disposable MySQL database. */
@EnabledIfEnvironmentVariable(named = "CAMPUSBRIDGE_MYSQL_TEST_URL", matches = ".+")
class ContentPersistenceIntegrationTest {
    private static DriverManagerDataSource dataSource;
    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void initializeDatabase() throws Exception {
        dataSource = new DriverManagerDataSource(
                System.getenv("CAMPUSBRIDGE_MYSQL_TEST_URL"),
                System.getenv().getOrDefault("CAMPUSBRIDGE_MYSQL_TEST_USER", "root"),
                System.getenv().getOrDefault("CAMPUSBRIDGE_MYSQL_TEST_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("campusbridge_content_test");
        String schema = Files.readString(Path.of("../deploy/mysql/schema.sql"))
                .replace("CREATE SCHEMA IF NOT EXISTS campusbridge;", "")
                .replace("USE campusbridge;", "");
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=0");
            Matcher tables = Pattern.compile("CREATE TABLE \\x60([^\\x60]+)\\x60").matcher(schema);
            while (tables.find()) {
                connection.createStatement().execute("DROP TABLE IF EXISTS " + tables.group(1));
            }
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=1");
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig()
                        .setInsertStrategy(FieldStrategy.ALWAYS)
                        .setUpdateStrategy(FieldStrategy.ALWAYS)));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:com/campusbridge/mapper/*.xml"));
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        sessions = factory.getObject();
        sessions.getConfiguration().addMappers("com.campusbridge.mapper");
    }

    @BeforeEach
    void seedRows() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : new String[]{"attachments", "post_interactions", "replies", "posts",
                    "reports", "campus_verifications", "messages", "conversations", "user_blocks", "user_profiles", "users"}) {
                connection.createStatement().execute("TRUNCATE TABLE " + table);
            }
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=1");
        }
        jdbc.update("INSERT INTO users(id,username,password_hash) VALUES(1,'author','unused'),(2,'reader','unused')");
        jdbc.update("INSERT INTO posts(id,author_id,category,title,content,status) "
                + "VALUES(10,1,'INQUIRY_HELP','Original','Body','PUBLISHED')");
    }

    @Test
    void dashboardProjectsDatesAndCountsIntoTypedRows() {
        jdbc.update("UPDATE users SET created_at='2026-01-01 12:00:00'");
        jdbc.update("UPDATE posts SET created_at='2026-01-01 12:00:00'");
        jdbc.update("INSERT INTO reports(reporter_id,target_type,target_id,reason,status) "
                + "VALUES(1,'POST',10,'Test report','PENDING')");
        try (SqlSession session = sessions.openSession(true)) {
            AdminDashboardMapper mapper = session.getMapper(AdminDashboardMapper.class);
            var users = mapper.countNewUsersByDay(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(users).hasSize(1);
            assertThat(users.get(0).getDay()).isEqualTo(java.time.LocalDate.of(2026, 1, 1));
            assertThat(users.get(0).getCount()).isEqualTo(2L);
            var posts = mapper.countNewPostsByDay(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(posts.get(0).getCount()).isEqualTo(1L);
            assertThat(mapper.countReportsByStatus()).extracting(CategoryCount::getCategory)
                    .containsExactly("PENDING");
            assertThat(mapper.countReportsByTargetType()).extracting(CategoryCount::getCategory)
                    .containsExactly("POST");
            assertThat(mapper.countReportsByTargetType().get(0).getCount()).isEqualTo(1L);
        }
    }

    @Test
    void publicQueriesExcludePrivatePostStates() {
        jdbc.update("INSERT INTO posts(id,author_id,category,title,content,status) "
                + "VALUES(11,1,'INQUIRY_HELP','Hidden','Body','HIDDEN'),"
                + "(12,1,'INQUIRY_HELP','Draft','Body','DRAFT')");
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper mapper = session.getMapper(PostMapper.class);
            var page = mapper.selectWithFilters(new Page<>(1, 20), null, null, null);
            assertThat(page.getRecords()).extracting(PostView::getId).containsExactly(10L);
            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(mapper.selectWithFilters(new Page<>(1, 20), null, "HIDDEN", null).getRecords()).isEmpty();
        }
    }

    @Test
    void postAndReplyFiltersKeepPageTotalsConsistent() {
        jdbc.update("INSERT INTO posts(id,author_id,category,title,content,status) "
                + "VALUES(11,1,'INQUIRY_HELP','Parcel question','Body','PUBLISHED'),"
                + "(12,1,'INQUIRY_HELP','Parcel delivery','Body','PUBLISHED'),"
                + "(13,1,'INQUIRY_HELP','Parcel draft','Body','DRAFT'),"
                + "(14,1,'INQUIRY_HELP','Other question','Body','PUBLISHED')");
        jdbc.update("INSERT INTO replies(post_id,author_id,content,created_at) "
                + "VALUES(11,2,'First reply','2026-01-01 10:00:00'),"
                + "(11,2,'Second reply','2026-01-01 11:00:00'),"
                + "(12,2,'Latest reply','2026-01-01 12:00:00'),"
                + "(13,2,'Draft reply','2026-01-01 13:00:00')");
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper mapper = session.getMapper(PostMapper.class);
            var posts = mapper.selectWithFilters(new Page<>(2, 1), "INQUIRY_HELP", "PUBLISHED", "Parcel");
            assertThat(posts.getRecords()).hasSize(1);
            assertThat(posts.getTotal()).isEqualTo(2);
            var replied = mapper.selectRepliedByUser(new Page<>(1, 20), 2L, "INQUIRY_HELP", "PUBLISHED", "Parcel");
            assertThat(replied.getRecords()).extracting(PostView::getId).containsExactly(12L, 11L);
            assertThat(replied.getTotal()).isEqualTo(2);
            var missing = mapper.selectRepliedByUser(new Page<>(1, 20), 2L, "INQUIRY_HELP", "PUBLISHED", "missing");
            assertThat(missing.getRecords()).isEmpty();
            assertThat(missing.getTotal()).isZero();
            var beyondLastPage = mapper.selectRepliedByUser(new Page<>(3, 1), 2L, "INQUIRY_HELP", "PUBLISHED", "Parcel");
            assertThat(beyondLastPage.getRecords()).isEmpty();
            assertThat(beyondLastPage.getTotal()).isEqualTo(2);
            assertThat(beyondLastPage.getCurrent()).isEqualTo(3);
        }
    }

    @Test
    void editingPostPreservesConcurrentCountersAndRejectsChangedStatus() {
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper mapper = session.getMapper(PostMapper.class);
            Post old = mapper.selectById(10L);
            jdbc.update("UPDATE posts SET like_count=8,reply_count=4 WHERE id=10");
            old.setTitle("Edited");
            assertThat(mapper.updateContent(old, "PUBLISHED")).isEqualTo(1);
            assertThat(mapper.selectDetailById(10L).getLikeCount()).isEqualTo(8);
            assertThat(mapper.selectDetailById(10L).getReplyCount()).isEqualTo(4);
            jdbc.update("UPDATE posts SET status='DELETED' WHERE id=10");
            assertThat(mapper.updateContent(old, "PUBLISHED")).isZero();
        }
    }

    @Test
    void concurrentCounterUpdatesDoNotLoseRepliesOrLikes() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> incrementCounters(start));
            Future<?> second = executor.submit(() -> incrementCounters(start));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT reply_count FROM posts WHERE id=10", Integer.class)).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT like_count FROM posts WHERE id=10", Integer.class)).isEqualTo(40);
    }

    private void incrementCounters(CountDownLatch start) {
        try {
            start.await();
            try (SqlSession session = sessions.openSession(true)) {
                PostMapper mapper = session.getMapper(PostMapper.class);
                for (int i = 0; i < 20; i++) {
                    assertThat(mapper.incrementReplyCount(10L)).isEqualTo(1);
                    assertThat(mapper.adjustInteractionCount(10L, "LIKE", 1)).isEqualTo(1);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void simultaneousReplyCreationAvoidsParentForeignKeyLockUpgrade() throws Exception {
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        ReplyServiceImpl service = new ReplyServiceImpl(
                template.getMapper(ReplyMapper.class), template.getMapper(PostMapper.class),
                mock(NotificationService.class));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> createReplies(start, transaction, service, 1L));
            Future<?> second = executor.submit(() -> createReplies(start, transaction, service, 2L));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT reply_count FROM posts WHERE id=10", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM replies WHERE post_id=10", Integer.class)).isEqualTo(20);
    }

    private void createReplies(
            CountDownLatch start, TransactionTemplate transaction, ReplyServiceImpl service, Long authorId) {
        try {
            start.await();
            for (int i = 0; i < 10; i++) {
                transaction.execute(status -> {
                    CreateReplyRequest request = new CreateReplyRequest();
                    request.setContent("Reply");
                    return service.createReply(authorId, 10L, request);
                });
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void resolvingReportCanOnlyWinOnce() {
        jdbc.update("INSERT INTO reports(id,reporter_id,target_type,target_id,reason,status) "
                + "VALUES(1,1,'USER',2,'Spam','PENDING')");
        try (SqlSession session = sessions.openSession(true)) {
            ReportMapper mapper = session.getMapper(ReportMapper.class);
            assertThat(mapper.updateStatusById(1L, "PUNISHED")).isEqualTo(1);
            assertThat(mapper.updateStatusById(1L, "PUNISHED")).isZero();
        }
    }

    @Test
    void concurrentReportDecisionsOnlyUpdateOneRow() throws Exception {
        jdbc.update("INSERT INTO reports(id,reporter_id,target_type,target_id,reason,status) "
                + "VALUES(1,1,'USER',2,'Spam','PROCESSING')");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> resolve = () -> {
                try (SqlSession session = sessions.openSession(true)) {
                    start.await();
                    return session.getMapper(ReportMapper.class).updateStatusById(1L, "PUNISHED");
                }
            };
            Future<Integer> first = executor.submit(resolve);
            Future<Integer> second = executor.submit(resolve);
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=1", String.class))
                    .isEqualTo("PUNISHED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pendingVerificationUpdatesClearOptionalFieldsAndDeleteOnlyPendingRows() {
        jdbc.update("INSERT INTO campus_verifications(id,user_id,method,real_name_cipher,"
                + "student_number_cipher,school,department,evidence_url,reject_reason,status) "
                + "VALUES(1,1,'MANUAL_REVIEW','Name','12345678','Campus','Old department','old.png','Old reason','PENDING')");
        try (SqlSession session = sessions.openSession(true)) {
            CampusVerificationMapper mapper = session.getMapper(CampusVerificationMapper.class);
            CampusVerification entity = mapper.selectById(1L);
            entity.setSchool("Updated campus");
            entity.setDepartment(null);
            entity.setEvidenceUrl(null);
            entity.setRejectReason(null);
            assertThat(mapper.updatePending(entity)).isEqualTo(1);
            var updated = mapper.selectById(1L);
            assertThat(updated.getSchool()).isEqualTo("Updated campus");
            assertThat(updated.getDepartment()).isNull();
            assertThat(updated.getEvidenceUrl()).isNull();
            assertThat(updated.getRejectReason()).isNull();
            assertThat(mapper.deletePendingById(1L)).isEqualTo(1);
            assertThat(mapper.selectById(1L)).isNull();
        }
    }

    @Test
    void lateVerificationReviewCannotOverwriteFinalDecision() {
        jdbc.update("INSERT INTO campus_verifications(id,user_id,method,real_name_cipher,"
                + "student_number_cipher,school,status) "
                + "VALUES(1,1,'MANUAL_REVIEW','Name','12345678','Campus','PENDING')");
        try (SqlSession session = sessions.openSession(true)) {
            CampusVerificationMapper mapper = session.getMapper(CampusVerificationMapper.class);
            CampusVerification earlier = mapper.selectById(1L);
            jdbc.update("UPDATE campus_verifications SET status='VERIFIED' WHERE id=1");
            earlier.setStatus("REJECTED");
            assertThat(mapper.updatePending(earlier)).isZero();
            assertThat(mapper.deletePendingById(1L)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM campus_verifications WHERE id=1", String.class))
                    .isEqualTo("VERIFIED");
        }
    }

    @Test
    void replyAcceptanceAndPostDeletionRespectActualForeignKeysAndEnums() {
        jdbc.update("INSERT INTO replies(id,post_id,author_id,content) VALUES(20,10,2,'Reply')");
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper posts = session.getMapper(PostMapper.class);
            InteractionServiceImpl service = new InteractionServiceImpl(
                    session.getMapper(PostInteractionMapper.class), posts, session.getMapper(ReplyMapper.class));
            service.acceptReply(1L, 10L, 20L);
            assertThat(jdbc.queryForObject("SELECT accepted_reply_id FROM posts WHERE id=10", Long.class))
                    .isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_interactions", Integer.class)).isZero();
            assertThat(posts.markDeletedById(10L)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM replies WHERE post_id=10", Integer.class))
                    .isEqualTo(1);
            assertThat(posts.selectWithFilters(new Page<>(1, 20), null, null, null).getRecords()).isEmpty();
        }
    }

    @Test
    void simultaneousOppositeDirectionPrivateChatsCreateExactlyTwoOwnerViews() throws Exception {
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        ConversationServiceImpl service = new ConversationServiceImpl(
                template.getMapper(ConversationMapper.class), template.getMapper(MessageMapper.class),
                template.getMapper(UserMapper.class), template.getMapper(UserBlockMapper.class),
                template.getMapper(AttachmentMapper.class));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> openConversation(start, transaction, service, 1L, 2L));
            Future<?> second = executor.submit(() -> openConversation(start, transaction, service, 2L, 1L));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE type='PRIVATE'", Integer.class))
                .isEqualTo(2);
    }

    private void openConversation(
            CountDownLatch start, TransactionTemplate transaction, ConversationServiceImpl service,
            Long ownerId, Long peerId) {
        try {
            start.await();
            transaction.execute(status -> {
                CreateConversationRequest request = new CreateConversationRequest();
                request.setPeerUserId(peerId);
                request.setRelatedType("POST");
                request.setRelatedId(10L);
                return service.createConversation(ownerId, request);
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void simultaneousPrivateRepliesPreserveBothCopiesWithoutParentLockUpgrade() throws Exception {
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        ConversationServiceImpl service = new ConversationServiceImpl(
                template.getMapper(ConversationMapper.class), template.getMapper(MessageMapper.class),
                template.getMapper(UserMapper.class), template.getMapper(UserBlockMapper.class),
                template.getMapper(AttachmentMapper.class));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CreateConversationRequest create = new CreateConversationRequest();
        create.setPeerUserId(2L);
        create.setRelatedType("POST");
        create.setRelatedId(10L);
        transaction.execute(status -> service.createConversation(1L, create));
        Long firstConversationId = jdbc.queryForObject(
                "SELECT id FROM conversations WHERE owner_id=1 AND type='PRIVATE'", Long.class);
        Long secondConversationId = jdbc.queryForObject(
                "SELECT id FROM conversations WHERE owner_id=2 AND type='PRIVATE'", Long.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(
                    () -> sendPrivateMessages(start, transaction, service, 1L, firstConversationId));
            Future<?> second = executor.submit(
                    () -> sendPrivateMessages(start, transaction, service, 2L, secondConversationId));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE read_at IS NULL", Integer.class))
                .isEqualTo(20);
    }

    private void sendPrivateMessages(
            CountDownLatch start, TransactionTemplate transaction, ConversationServiceImpl service,
            Long senderId, Long conversationId) {
        try {
            start.await();
            for (int i = 0; i < 10; i++) {
                transaction.execute(status -> {
                    SendMessageRequest request = new SendMessageRequest();
                    request.setType(MessageType.TEXT);
                    request.setContent("Concurrent message");
                    return service.sendMessage(senderId, conversationId, request);
                });
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void concurrentNotificationsShareOneSystemConversationAndKeepBothMessages() throws Exception {
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        NotificationServiceImpl service = new NotificationServiceImpl(
                template.getMapper(ConversationMapper.class), template.getMapper(MessageMapper.class),
                template.getMapper(UserMapper.class));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Post post = template.getMapper(PostMapper.class).selectById(10L);
        Reply reply = new Reply();
        reply.setId(20L);
        reply.setContent("Reply notification");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable send = () -> {
            try {
                start.await();
                for (int i = 0; i < 10; i++) {
                    transaction.execute(status -> {
                        service.notifyPostReplyCreated(post, reply, null, 2L);
                        return null;
                    });
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        };
        try {
            Future<?> first = executor.submit(send);
            Future<?> second = executor.submit(send);
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE type='SYSTEM'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE read_at IS NULL", Integer.class))
                .isEqualTo(20);
    }

    @Test
    void systemConversationUniqueConstraintRejectsDuplicateRelatedObject() {
        String insert = "INSERT INTO conversations(type,owner_id,related_type,related_id,replyable)"
                + " VALUES('SYSTEM',1,'POST',10,0)";
        jdbc.update(insert);
        assertThatThrownBy(() -> jdbc.update(insert))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void attachmentBindingUsesValidEnumAndCannotBeRebound() {
        jdbc.update("INSERT INTO attachments(id,uploader_id,owner_type,type,file_name,file_url,content_type,size) "
                + "VALUES(1,1,'POST','IMAGE','test.png','https://example.test/test.png','image/png',1)");
        try (SqlSession session = sessions.openSession(true)) {
            AttachmentMapper mapper = session.getMapper(AttachmentMapper.class);
            assertThat(mapper.bindTemporaryToOwner(1L, 1L, "POST", 10L)).isEqualTo(1);
            assertThat(mapper.bindTemporaryToOwner(1L, 1L, "POST", 11L)).isZero();
            assertThat(mapper.selectById(1L).getStatus()).isEqualTo("BOUND");
            assertThat(mapper.selectById(1L).getOwnerId()).isEqualTo(10L);
        }
    }

    @Test
    void generatedProfileIdAndNullableFieldsRoundTripThroughBaseMapper() {
        try (SqlSession session = sessions.openSession(true)) {
            UserProfileMapper profiles = session.getMapper(UserProfileMapper.class);
            UserProfile profile = new UserProfile();
            profile.setUserId(1L);
            profile.setNickname("Student");
            profile.setBio("Old bio");
            profile.setContactInfo("Old contact");
            profile.setInterestTags("[\"reading\"]");
            profile.setCreatedAt(LocalDateTime.now());
            profile.setUpdatedAt(LocalDateTime.now());
            assertThat(profiles.insert(profile)).isEqualTo(1);
            assertThat(profile.getId()).isPositive();

            UserProfile current = profiles.selectByUserId(1L);
            current.setBio(null);
            current.setContactInfo(null);
            current.setInterestTags(null);
            assertThat(profiles.updateById(current)).isEqualTo(1);
            UserProfile updated = profiles.selectById(profile.getId());
            assertThat(updated.getNickname()).isEqualTo("Student");
            assertThat(updated.getBio()).isNull();
            assertThat(updated.getContactInfo()).isNull();
            assertThat(updated.getInterestTags()).isNull();
            assertThat(profiles.deleteById(profile.getId())).isEqualTo(1);
            assertThat(profiles.selectByUserId(1L)).isNull();
        }
    }

    @Test
    void authorViewAndEntityWritesUseIndependentModels() {
        jdbc.update("INSERT INTO user_profiles(user_id,nickname) VALUES(1,'Student')");
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper posts = session.getMapper(PostMapper.class);
            PostView view = posts.selectDetailById(10L);
            assertThat(view.getAuthorName()).isEqualTo("Student");
            Post post = posts.selectById(10L);
            view.setTitle("Display-only change");
            assertThat(post.getTitle()).isEqualTo("Original");
            post.setId(null);
            post.setTitle("Another post");
            assertThat(posts.insert(post)).isEqualTo(1);
            assertThat(post.getId()).isGreaterThan(10L);
            post.setTitle("Updated title");
            assertThat(posts.updateById(post)).isEqualTo(1);
            assertThat(posts.selectDetailById(post.getId()).getAuthorName()).isEqualTo("Student");
            assertThat(posts.selectDetailById(post.getId()).getTitle()).isEqualTo("Updated title");
        }
    }

    @Test
    void verificationPaginationCountsOnlyPendingRowsAndKeepsEmptyPageTotal() {
        jdbc.update("INSERT INTO campus_verifications "
                + "(user_id,method,real_name_cipher,student_number_cipher,school,status,created_at) VALUES "
                + "(1,'MANUAL_REVIEW','A','1','Campus','PENDING','2026-01-01'),"
                + "(1,'MANUAL_REVIEW','B','2','Campus','PENDING','2026-01-02'),"
                + "(2,'MANUAL_REVIEW','C','3','Campus','VERIFIED','2026-01-03')");
        try (SqlSession session = sessions.openSession(true)) {
            VerificationServiceImpl service = new VerificationServiceImpl(
                    session.getMapper(CampusVerificationMapper.class), mock(EmailVerificationCodeSender.class));
            var page = service.listPending(2, 1);
            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getItems()).extracting(CampusVerification::getRealNameCipher).containsExactly("A");
            var beyondEnd = service.listPending(3, 1);
            assertThat(beyondEnd.getItems()).isEmpty();
            assertThat(beyondEnd.getTotal()).isEqualTo(2);
        }
    }

    @Test
    void reportPaginationPreservesReporterScopeAndOptionalStatus() {
        jdbc.update("INSERT INTO reports(reporter_id,target_type,target_id,reason,status) VALUES "
                + "(1,'POST',10,'First','PENDING'),(1,'POST',10,'Second','RESOLVED'),"
                + "(2,'POST',10,'Other user','PENDING')");
        try (SqlSession session = sessions.openSession(true)) {
            ReportServiceImpl service = new ReportServiceImpl(session.getMapper(ReportMapper.class),
                    null, null, null, null, null, null, null, null, null);
            var own = service.listReports(1L, null, 1, 1);
            assertThat(own.getTotal()).isEqualTo(2);
            assertThat(own.getItems()).extracting(com.campusbridge.entity.Report::getReason)
                    .containsExactly("Second");
            var filtered = service.listReports(1L, "pending", 1, 20);
            assertThat(filtered.getTotal()).isEqualTo(1);
            assertThat(filtered.getItems()).extracting(com.campusbridge.entity.Report::getReason)
                    .containsExactly("First");
            var admin = service.listReportsForAdmin("PENDING", 1, 20);
            assertThat(admin.getTotal()).isEqualTo(2);
            assertThat(admin.getItems()).extracting(com.campusbridge.entity.Report::getReporterId)
                    .containsExactly(2L, 1L);
        }
    }

    @Test
    void replyAndConversationQueriesMapDisplayFieldsWithoutChangingEntities() {
        jdbc.update("INSERT INTO user_profiles(user_id,nickname) VALUES(1,'Author'),(2,'Reader')");
        jdbc.update("INSERT INTO replies(id,post_id,author_id,content,status) "
                + "VALUES(20,10,2,'Reply','PUBLISHED')");
        jdbc.update("INSERT INTO conversations(id,type,title,owner_id,peer_user_id,related_type,related_id,replyable) "
                + "VALUES(100,'PRIVATE','Chat',1,2,'POST',10,1)");
        jdbc.update("INSERT INTO messages(conversation_id,sender_id,type,content,created_at,read_at) VALUES "
                + "(100,1,'TEXT','Read message','2026-01-01 10:00:00','2026-01-01 10:01:00'),"
                + "(100,2,'TEXT','Latest message','2026-01-01 10:02:00',NULL)");
        try (SqlSession session = sessions.openSession(true)) {
            ReplyMapper replies = session.getMapper(ReplyMapper.class);
            assertThat(replies.selectByPostId(10L, 0, 20))
                    .extracting(com.campusbridge.dto.view.ReplyView::getAuthorName).containsExactly("Reader");
            assertThat(replies.selectById(20L).getAuthorId()).isEqualTo(2L);
            ConversationMapper conversations = session.getMapper(ConversationMapper.class);
            var view = conversations.selectOwnedViewById(100L, 1L);
            assertThat(view.getUnreadCount()).isEqualTo(1);
            assertThat(view.getLastMessagePreview()).isEqualTo("Latest message");
            assertThat(view.getLastReadAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 1));
            assertThat(conversations.selectOwnedById(100L, 2L)).isNull();
            assertThat(conversations.selectByOwnerId(new Page<>(1, 20), 1L).getRecords()).singleElement()
                    .satisfies(item -> assertThat(item.getUnreadCount()).isEqualTo(1));
            var entity = conversations.selectPrivateByOwnerAndPeer(1L, 2L);
            entity.setTitle("Updated chat");
            assertThat(conversations.updateById(entity)).isEqualTo(1);
            assertThat(conversations.selectOwnedViewById(100L, 1L).getLastMessagePreview())
                    .isEqualTo("Latest message");
        }
    }
    @Test
    void conversationPagesKeepOwnerTotalsOrderAndMessageSummaries() {
        jdbc.update("INSERT INTO conversations(id,type,title,owner_id,peer_user_id,replyable,last_message_at) "
                + "VALUES(100,'PRIVATE','Private chat',1,2,1,'2026-01-01 10:00:00'),"
                + "(101,'SYSTEM','Notice',1,NULL,0,'2026-01-01 11:00:00'),"
                + "(102,'PRIVATE','Peer view',2,1,1,'2026-01-01 12:00:00')");
        jdbc.update("INSERT INTO messages(conversation_id,sender_id,type,content,created_at) "
                + "VALUES(100,2,'TEXT','First message','2026-01-01 09:00:00'),"
                + "(100,2,'TEXT','Latest message','2026-01-01 10:00:00')");
        try (SqlSession session = sessions.openSession(true)) {
            ConversationServiceImpl service = new ConversationServiceImpl(
                    session.getMapper(ConversationMapper.class), session.getMapper(MessageMapper.class),
                    session.getMapper(UserMapper.class), session.getMapper(UserBlockMapper.class),
                    session.getMapper(AttachmentMapper.class));
            var first = service.listConversations(1L, 1, 1);
            assertThat(first.getTotal()).isEqualTo(2);
            assertThat(first.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(101L);
                assertThat(item.getUnreadCount()).isZero();
            });
            var second = service.listConversations(1L, 2, 1);
            assertThat(second.getTotal()).isEqualTo(2);
            assertThat(second.getPage()).isEqualTo(2);
            assertThat(second.getSize()).isEqualTo(1);
            assertThat(second.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(100L);
                assertThat(item.getUnreadCount()).isEqualTo(2);
                assertThat(item.getLastMessagePreview()).isEqualTo("Latest message");
            });
            var beyondLastPage = service.listConversations(1L, 3, 1);
            assertThat(beyondLastPage.getItems()).isEmpty();
            assertThat(beyondLastPage.getTotal()).isEqualTo(2);
            assertThat(beyondLastPage.getPage()).isEqualTo(3);
        }
    }
}
