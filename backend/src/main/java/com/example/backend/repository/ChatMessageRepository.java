package com.example.backend.repository;

import com.example.backend.Entity.ChatMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 对应 {@code chat_message} 表的数据访问层。
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /** 按对话 ID 查询消息，按创建时间升序排列（保持对话顺序） */
    List<ChatMessageEntity> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

    /** 删除指定会话下的所有消息（级联删除） */
    void deleteByConversationId(Long conversationId);
}
