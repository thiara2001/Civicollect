package com.civicollect.repository;

import com.civicollect.domain.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    List<Comment> findByNeedIdOrderByCreatedAtAsc(Long needId);

    void deleteByNeedId(Long needId);
}
