package com.campusbridge.unit;

import com.campusbridge.mapper.UserMapper;
import com.campusbridge.pojo.User;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperAccountUpdateTest {
    @Test
    void accountUpdatesPreserveConcurrentDisableAndRoleChange() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver", "jdbc:h2:mem:user_account_updates;MODE=MySQL", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("""
                    CREATE TABLE users (
                      id BIGINT PRIMARY KEY, username VARCHAR(50), password_hash VARCHAR(100),
                      phone_cipher VARCHAR(100), phone_hash VARCHAR(64), email VARCHAR(100),
                      role VARCHAR(20), enabled INT, created_at TIMESTAMP, updated_at TIMESTAMP)
                    """);
            sql.execute("INSERT INTO users (id, username, role, enabled) VALUES (1, 'admin', 'ADMIN', 1)");
            MybatisConfiguration configuration = new MybatisConfiguration(
                    new Environment("test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(UserMapper.class);
            SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
            try (SqlSession session = factory.openSession(true)) {
                UserMapper users = session.getMapper(UserMapper.class);
                assertThat(users.selectById(1L).getEnabled()).isEqualTo(1);
                sql.execute("UPDATE users SET role = 'USER', enabled = 0 WHERE id = 1");
                LocalDateTime now = LocalDateTime.now();
                assertThat(users.updatePassword(1L, "new-password-hash", now)).isEqualTo(1);
                assertThat(users.updateEmail(1L, "new@example.test", now)).isEqualTo(1);
                assertThat(users.updatePhone(1L, "13800000001", "new-phone-hash", now)).isEqualTo(1);
                User current = users.selectById(1L);
                assertThat(current.getEnabled()).isZero();
                assertThat(current.getRole()).isEqualTo("USER");
                assertThat(current.getPasswordHash()).isEqualTo("new-password-hash");
                assertThat(current.getEmail()).isEqualTo("new@example.test");
                assertThat(current.getPhoneHash()).isEqualTo("new-phone-hash");
            }
        }
    }
}
