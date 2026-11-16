package com.campusbridge.integration;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.view.ContentItemView;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.ContentSort;
import com.campusbridge.enums.RewardType;
import java.util.Set;
import com.campusbridge.mapper.ContentMapper;
import com.campusbridge.service.impl.ContentServiceImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real UNION queries using session-local tables.
 * Uses H2 by default or an explicitly supplied MySQL database ending in _test.
 */
class ContentQueryIntegrationTest {
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    private Connection connection;
    private SqlSession session;
    private JdbcTemplate jdbc;
    private ContentServiceImpl content;

    @BeforeEach
    void initializeSessionTables() throws Exception {
        String mysqlUrl = System.getenv("CAMPUSBRIDGE_TEST_MYSQL_URL");
        if (mysqlUrl == null || mysqlUrl.isBlank()) {
            connection = DriverManager.getConnection(
                    "jdbc:h2:mem:content_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        } else {
            connection = DriverManager.getConnection(mysqlUrl,
                    System.getenv().getOrDefault("CAMPUSBRIDGE_TEST_MYSQL_USER", "root"),
                    System.getenv().getOrDefault("CAMPUSBRIDGE_TEST_MYSQL_PASSWORD", ""));
            assertThat(connection.getCatalog()).endsWith("_test");
        }
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TEMPORARY TABLE posts (
                    id BIGINT PRIMARY KEY, author_id BIGINT, category VARCHAR(30),
                    title VARCHAR(100), content TEXT, status VARCHAR(30),
                    created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TEMPORARY TABLE tasks (
                    id BIGINT PRIMARY KEY, requester_id BIGINT, category VARCHAR(30),
                    title VARCHAR(100), description TEXT, status VARCHAR(30),
                    campus VARCHAR(50), location VARCHAR(120), delivery_location VARCHAR(120),
                    reward_type VARCHAR(30), reward_description VARCHAR(200), reward_amount INT,
                    deadline TIMESTAMP NULL, created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ContentMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        content = new ContentServiceImpl(session.getMapper(ContentMapper.class));
    }

    @AfterEach
    void closeSessionTables() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void totalAndDeepPagesIncludeMoreThanFiveHundredItemsPerType() throws Exception {
        try (PreparedStatement posts = connection.prepareStatement(
                "INSERT INTO posts VALUES (?,1,'INQUIRY_HELP','Post','Body','PUBLISHED',?,?)");
             PreparedStatement tasks = connection.prepareStatement(
                     "INSERT INTO tasks VALUES (?,1,'ERRAND','Task','Body','OPEN',"
                             + "'North','Gate',NULL,'FREE',NULL,NULL,?,?,?)")) {
            for (int i = 1; i <= 620; i++) {
                posts.setLong(1, i);
                posts.setObject(2, BASE_TIME.plusSeconds(i));
                posts.setObject(3, BASE_TIME.plusSeconds(i));
                posts.addBatch();
            }
            for (int i = 1; i <= 630; i++) {
                tasks.setLong(1, i);
                tasks.setObject(2, BASE_TIME.plusDays(1));
                tasks.setObject(3, BASE_TIME.plusSeconds(i));
                tasks.setObject(4, BASE_TIME.plusSeconds(i));
                tasks.addBatch();
            }
            posts.executeBatch();
            tasks.executeBatch();
        }
        PagedResult<ContentItemView> page = query(null, ContentSort.CREATED_AT, 13, 100);
        assertThat(page.getTotal()).isEqualTo(1250);
        assertThat(page.getItems()).hasSize(50);
        assertThat(query(Set.of(ContentItemType.POST), ContentSort.CREATED_AT, 7, 100).getItems()).hasSize(20);
        assertThat(query(Set.of(ContentItemType.POST), ContentSort.CREATED_AT, 7, 100).getTotal()).isEqualTo(620);
        PagedResult<ContentItemView> hugePage =
                query(null, ContentSort.CREATED_AT, Integer.MAX_VALUE, 100);
        assertThat(hugePage.getTotal()).isEqualTo(1250);
        assertThat(hugePage.getItems()).isEmpty();
    }

    @Test
    void contentReturnsCategoryStatusAndRewardDataWithoutDisplayFormatting() {
        post(1, "PUBLISHED", BASE_TIME);
        task(1, "OPEN", BASE_TIME.plusSeconds(1), BASE_TIME.plusDays(1));
        jdbc.update("UPDATE tasks SET reward_type='POINTS', reward_amount=5, reward_description='Thank you' WHERE id=1");
        var task = query(Set.of(ContentItemType.TASK), null, 1, 20).getItems().get(0);
        assertThat(task.getCategory()).isEqualTo("ERRAND");
        assertThat(task.getStatus()).isEqualTo("OPEN");
        assertThat(task.getRewardType()).isEqualTo(com.campusbridge.enums.RewardType.POINTS);
        assertThat(task.getRewardAmount()).isEqualTo(5);
        assertThat(task.getRewardDescription()).isEqualTo("Thank you");
        var post = query(Set.of(ContentItemType.POST), null, 1, 20).getItems().get(0);
        assertThat(post.getCategory()).isEqualTo("INQUIRY_HELP");
        assertThat(post.getRewardType()).isNull();
        assertThat(post.getRewardAmount()).isNull();
        assertThat(post.getRewardDescription()).isNull();
    }

    @Test
    void privateStatusesStayExcludedEvenWhenExplicitlyRequested() {
        post(1, "PUBLISHED", BASE_TIME);
        post(2, "CLOSED", BASE_TIME);
        post(3, "DRAFT", BASE_TIME);
        post(4, "HIDDEN", BASE_TIME);
        post(5, "DELETED", BASE_TIME);
        post(6, "PENDING_REVIEW", BASE_TIME);
        post(7, "REJECTED", BASE_TIME);
        task(1, "OPEN", BASE_TIME, BASE_TIME.plusDays(1));
        task(2, "DRAFT", BASE_TIME, null);
        task(3, "PENDING_REVIEW", BASE_TIME, null);
        task(4, "REJECTED", BASE_TIME, null);
        task(5, "CANCELLED", BASE_TIME, BASE_TIME.plusDays(1));
        task(6, "DISPUTED", BASE_TIME, BASE_TIME.plusDays(1));
        assertThat(query(null, ContentSort.CREATED_AT, 1, 20).getTotal()).isEqualTo(2);
        assertThat(content.listContent(null, Set.of(ContentItemType.TASK), null, null, null, "CANCELLED",
                null, null, 1, 20).getItems()).extracting(ContentItemView::getSourceId)
                .containsExactly(5L);
        for (String status : new String[]{"DRAFT", "PENDING_REVIEW", "REJECTED", "HIDDEN", "DELETED"}) {
            assertThat(content.listContent(null, null, null, null, null, status,
                    null, null, 1, 20).getItems()).isEmpty();
        }
        assertThat(content.listContent(null, Set.of(ContentItemType.POST), null, null, null, "CLOSED",
                null, null, 1, 20).getItems()).extracting(ContentItemView::getSourceId)
                .containsExactly(2L);
    }

    @Test
    void taskSpecificFiltersMatchLocationOrDeliveryLocationAndExcludePosts() {
        post(1, "PUBLISHED", BASE_TIME);
        task(1, "OPEN", BASE_TIME, BASE_TIME.plusDays(1));
        task(2, "OPEN", BASE_TIME, BASE_TIME.plusDays(1));
        task(3, "OPEN", BASE_TIME, BASE_TIME.plusDays(1));
        jdbc.update("UPDATE tasks SET location='West Gate',delivery_location='East Dorm',"
                + "reward_type='POINTS' WHERE id=1");
        jdbc.update("UPDATE tasks SET location='East Gate',reward_type='POINTS',campus='South' WHERE id=2");
        jdbc.update("UPDATE tasks SET location='East Gate',reward_type='FREE' WHERE id=3");
        PagedResult<ContentItemView> page = content.listContent(
                "Task", null, "ERRAND", "North", "East", "OPEN", RewardType.POINTS, null, 1, 20);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).extracting(ContentItemView::getSourceId).containsExactly(1L);
        assertThat(page.getItems().get(0).getItemType()).isEqualTo(ContentItemType.TASK);
        assertThat(content.listContent(null, null, null, null, "West Gate", null,
                null, null, 1, 20).getItems()).extracting(ContentItemView::getSourceId)
                .containsExactly(1L);
    }

    @Test
    void createdSortIgnoresLastEditAndDeadlineSortUsesEarliestDeadlineBeforePosts() {
        post(1, "PUBLISHED", BASE_TIME.plusDays(3));
        task(1, "OPEN", BASE_TIME.plusDays(2), BASE_TIME.plusDays(5));
        task(2, "OPEN", BASE_TIME.plusDays(1), BASE_TIME.plusDays(4));
        jdbc.update("UPDATE tasks SET updated_at=? WHERE id=2", BASE_TIME.plusDays(20));
        assertThat(query(null, ContentSort.CREATED_AT, 1, 20).getItems())
                .extracting(ContentItemView::getItemType)
                .containsExactly(ContentItemType.POST,
                        ContentItemType.TASK, ContentItemType.TASK);
        PagedResult<ContentItemView> deadlines = query(null, ContentSort.DEADLINE, 1, 20);
        assertThat(deadlines.getItems()).extracting(ContentItemView::getSourceId)
                .containsExactly(2L, 1L, 1L);
        assertThat(deadlines.getItems().get(0).getSortTime()).isEqualTo(BASE_TIME.plusDays(4));
        assertThat(deadlines.getItems().get(2).getItemType()).isEqualTo(ContentItemType.POST);
        assertThat(deadlines.getItems().get(2).getSortTime()).isNull();
        assertThat(query(null, null, 1, 20).getItems())
                .extracting(ContentItemView::getCreatedAt)
                .containsExactly(BASE_TIME.plusDays(3), BASE_TIME.plusDays(2), BASE_TIME.plusDays(1));
    }

    @Test
    void keywordIsBoundAsDataAndEqualDatesHaveStableOrdering() {
        post(1, "PUBLISHED", BASE_TIME);
        post(2, "PUBLISHED", BASE_TIME);
        task(1, "OPEN", BASE_TIME, BASE_TIME.plusDays(1));
        assertThat(content.listContent("' OR 1=1 --", null, null, null, null, null,
                null, null, 1, 20).getTotal()).isZero();
        assertThat(query(Set.of(ContentItemType.POST), ContentSort.CREATED_AT, 1, 20).getItems())
                .extracting(ContentItemView::getSourceId).containsExactly(2L, 1L);
    }

    private PagedResult<ContentItemView> query(
            Set<ContentItemType> types, ContentSort sort, Integer page, Integer size) {
        return content.listContent(null, types, null, null, null, null, null, sort, page, size);
    }

    private void post(long id, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO posts VALUES (?,1,'INQUIRY_HELP','Post','Body',?,?,?)",
                id, status, createdAt, createdAt);
    }

    private void task(long id, String status, LocalDateTime createdAt, LocalDateTime deadline) {
        jdbc.update("INSERT INTO tasks VALUES (?,1,'ERRAND','Task','Body',?,'North',"
                        + "'Gate',NULL,'FREE',NULL,NULL,?,?,?)",
                id, status, deadline, createdAt, createdAt);
    }
}
