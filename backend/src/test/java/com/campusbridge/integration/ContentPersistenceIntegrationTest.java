package com.campusbridge.integration;

import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserBlockMapper;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.service.impl.ConversationServiceImpl;
import com.campusbridge.service.impl.ReplyServiceImpl;
import com.campusbridge.service.impl.NotificationServiceImpl;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.NotificationService;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.mapper.ReportMapper;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.Post;
import com.campusbridge.service.impl.InteractionServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
        String schema = Files.readString(Path.of("sql/schema.sql"))
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
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:com/campusbridge/mapper/*.xml"));
        sessions = factory.getObject();
    }

    @BeforeEach
    void seedRows() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : new String[]{"attachments", "post_interactions", "replies", "posts",
                    "reports", "campus_verifications", "messages", "conversations", "user_blocks", "users"}) {
                connection.createStatement().execute("TRUNCATE TABLE " + table);
            }
            connection.createStatement().execute("SET FOREIGN_KEY_CHECKS=1");
        }
        jdbc.update("INSERT INTO users(id,username,password_hash) VALUES(1,'author','unused'),(2,'reader','unused')");
        jdbc.update("INSERT INTO posts(id,author_id,category,title,content,status) "
                + "VALUES(10,1,'INQUIRY_HELP','Original','Body','PUBLISHED')");
    }

    @Test
    void publicQueriesExcludePrivatePostStates() {
        jdbc.update("INSERT INTO posts(id,author_id,category,title,content,status) "
                + "VALUES(11,1,'INQUIRY_HELP','Hidden','Body','HIDDEN'),"
                + "(12,1,'INQUIRY_HELP','Draft','Body','DRAFT')");
        try (SqlSession session = sessions.openSession(true)) {
            PostMapper mapper = session.getMapper(PostMapper.class);
            assertThat(mapper.selectWithFilters(null, null, null, 0, 20)).extracting(Post::getId).containsExactly(10L);
            assertThat(mapper.countWithFilters(null, null, null)).isEqualTo(1);
            assertThat(mapper.selectWithFilters(null, "HIDDEN", null, 0, 20)).isEmpty();
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
            assertThat(mapper.selectById(10L).getLikeCount()).isEqualTo(8);
            assertThat(mapper.selectById(10L).getReplyCount()).isEqualTo(4);
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
                    RequestDtos.CreateReplyRequest request = new RequestDtos.CreateReplyRequest();
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
            assertThat(posts.deleteById(10L)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM replies WHERE post_id=10", Integer.class))
                    .isEqualTo(1);
            assertThat(posts.selectWithFilters(null, null, null, 0, 20)).isEmpty();
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
                RequestDtos.CreateConversationRequest request = new RequestDtos.CreateConversationRequest();
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
        RequestDtos.CreateConversationRequest create = new RequestDtos.CreateConversationRequest();
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
                    RequestDtos.SendMessageRequest request = new RequestDtos.SendMessageRequest();
                    request.setType(ApiEnums.MessageType.TEXT);
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
}
