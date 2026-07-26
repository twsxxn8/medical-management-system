package com.example.backend.repository;

import com.example.backend.Entity.ChatConversationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 对应 {@code chat_conversation} 表的数据访问层。
 */
@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, Long> {

    /** 查询用户的所有对话，按更新时间倒序排列 */
    List<ChatConversationEntity> findByUserIdOrderByUpdateTimeDesc(Long userId);

    /** 删除指定用户拥有的指定对话（数据隔离） */
    void deleteByIdAndUserId(Long id, Long userId);
}
