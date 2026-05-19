package com.arknow;

import com.arknow.auth.audit.LoginLogMapper;
import com.arknow.comment.mapper.CommentMapper;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.notification.mapper.NotificationMapper;
import com.arknow.relation.mapper.RelationMapper;
import com.arknow.relation.outbox.OutboxMapper;
import com.arknow.user.mapper.UserMapper;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MapperMockConfiguration {
    @Bean @Primary public UserMapper userMapper() { return Mockito.mock(UserMapper.class); }
    @Bean @Primary public LoginLogMapper loginLogMapper() { return Mockito.mock(LoginLogMapper.class); }
    @Bean @Primary public CommentMapper commentMapper() { return Mockito.mock(CommentMapper.class); }
    @Bean @Primary public KnowPostMapper knowPostMapper() { return Mockito.mock(KnowPostMapper.class); }
    @Bean @Primary public NotificationMapper notificationMapper() { return Mockito.mock(NotificationMapper.class); }
    @Bean @Primary public RelationMapper relationMapper() { return Mockito.mock(RelationMapper.class); }
    @Bean @Primary public OutboxMapper outboxMapper() { return Mockito.mock(OutboxMapper.class); }
}
