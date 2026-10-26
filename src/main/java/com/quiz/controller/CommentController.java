package com.quiz.controller;

import com.quiz.common.*;
import com.quiz.entity.Comment;
import com.quiz.mapper.CommentMapper;
import com.quiz.security.SessionService;
import com.quiz.service.QuestionService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentMapper mapper;
    private final QuestionService questions;
    private final SessionService session;
    public CommentController(CommentMapper mapper,QuestionService questions,SessionService session) { this.mapper=mapper;this.questions=questions;this.session=session; }
    public static class Request { @NotBlank @Size(max=300) public String content; }
    @GetMapping("/questions/{id}/comments") public Result<Page<Comment>> list(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        questions.requireVisible(id);PageQuery query=new PageQuery();query.page=page;query.size=size;
        return Result.ok(Page.of(mapper.count(id),mapper.list(id,query.getOffset(),query.getSize())));
    }
    @PostMapping("/questions/{id}/comments") public Result<Comment> create(@PathVariable Long id,@Valid @RequestBody Request request) {
        questions.requireVisible(id); Comment c=new Comment();c.questionId=id;c.userId=session.current().id;c.content=request.content.trim();mapper.insert(c);c.nickname=session.current().nickname;return Result.ok(c);
    }
    @DeleteMapping("/comments/{id}") public Result<Void> delete(@PathVariable Long id) {
        Comment c=mapper.findById(id); if(c==null) throw new BizException(404,"评论不存在");
        questions.requireVisible(c.questionId);
        if(!session.isAdmin() && !Objects.equals(c.userId,session.current().id)) throw new BizException(403,"只能删除自己的评论");
        mapper.softDelete(id);return Result.ok();
    }
}
