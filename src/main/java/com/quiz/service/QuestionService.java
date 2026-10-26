package com.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.*;
import com.quiz.entity.Question;
import com.quiz.entity.Resume;
import com.quiz.entity.dto.QuestionQuery;
import com.quiz.entity.dto.QuestionRequest;
import com.quiz.mapper.CategoryMapper;
import com.quiz.mapper.QuestionMapper;
import com.quiz.security.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class QuestionService {
    private final QuestionMapper mapper;
    private final CategoryMapper categories;
    private final ResumeService resumes;
    private final SessionService session;
    private final ObjectMapper json;
    public QuestionService(QuestionMapper mapper, CategoryMapper categories, ResumeService resumes, SessionService session, ObjectMapper json) {
        this.mapper=mapper; this.categories=categories; this.resumes=resumes; this.session=session; this.json=json;
    }
    public Question requireVisible(Long id) {
        Question q=mapper.findById(id);
        if(q==null || !session.visible(q.ownerId,q.isPublic)) throw new BizException(404,"题目不存在");
        if(q.resumeId!=null) resumes.requireVisible(q.resumeId);
        return q;
    }
    private Question editableLocked(Long id) {
        Question initial=requireVisible(id);
        if(initial.resumeId!=null) resumes.requireEditable(initial.resumeId);
        Question root=mapper.lockById(initial.rootId==null?initial.id:initial.rootId);
        if(root==null) throw new BizException(404,"题目不存在");
        Question q=mapper.lockById(id);
        if(q==null || !session.visible(q.ownerId,q.isPublic)) throw new BizException(404,"题目不存在");
        session.requireEdit(q.ownerId,q.isPublic);
        return q;
    }
    public Page<Question> list(QuestionQuery query, boolean all, boolean brief) {
        query.admin=session.isAdmin(); query.userId=session.current().id;
        query.ordinary=!all;
        if(query.resumeId!=null && all) resumes.requireVisible(query.resumeId);
        List<Question> rows=mapper.list(query);
        if(brief) rows.forEach(this::hideAnswer);
        return Page.of(mapper.count(query),rows);
    }
    public Page<Question> resumeQuestions(Long id,QuestionQuery query,boolean brief) {
        resumes.requireVisible(id);
        query.resumeId=id;
        return list(query,true,brief);
    }
    public List<Question> chain(Long id, boolean brief) {
        Question q=requireVisible(id);
        List<Question> rows=mapper.chain(q.rootId==null?q.id:q.rootId);
        // A breadth-first traversal preserves parent/child levels for branched chains.
        Map<Long,List<Question>> children=rows.stream().filter(n->n.parentId!=null).collect(Collectors.groupingBy(n->n.parentId));
        List<Question> ordered=new ArrayList<>();
        Deque<Question> queue=new ArrayDeque<>();
        rows.stream().filter(n->n.parentId==null).forEach(queue::add);
        while(!queue.isEmpty()) { Question n=queue.remove(); ordered.add(n); queue.addAll(children.getOrDefault(n.id,Collections.emptyList())); }
        if(brief) ordered.forEach(this::hideAnswer);
        return ordered;
    }
    public void hideAnswer(Question q) { q.answer=null; q.analysis=null; }
    @Transactional
    public Question create(QuestionRequest request) {
        Question parent=request.parentId==null?null:requireVisible(request.parentId);
        Long resumeId=request.resumeId;
        if(parent!=null) {
            if(resumeId!=null && !Objects.equals(resumeId,parent.resumeId)) throw new BizException(400,"追问必须与父题属于同一简历");
            resumeId=parent.resumeId;
        }
        Resume resume=resumeId==null?null:resumes.requireEditable(resumeId);
        if(parent!=null) parent=editableLocked(parent.id);
        Question q=validated(request);
        q.ownerId=session.current().id; q.isPublic=session.isAdmin()?1:0;
        q.resumeId=resumeId;
        if(parent!=null) { q.parentId=parent.id; q.rootId=parent.rootId==null?parent.id:parent.rootId; q.ownerId=parent.ownerId; q.isPublic=parent.isPublic; }
        if(resume!=null) { q.ownerId=resume.ownerId; q.isPublic=resume.isPublic; }
        q.blockId=request.blockId;
        if(q.blockId==null && parent!=null) q.blockId=parent.blockId;
        if(q.blockId!=null) { Checks.require(resumeId!=null,"普通题不能关联简历条目"); resumes.requireBlock(q.blockId,resumeId); }
        mapper.insert(q);
        return requireVisible(q.id);
    }
    @Transactional
    public Question update(Long id,QuestionRequest request) {
        Question old=editableLocked(id);
        Checks.require(request.parentId==null || Objects.equals(request.parentId,old.parentId),"不能修改题目的父题");
        Checks.require(request.resumeId==null || Objects.equals(request.resumeId,old.resumeId),"不能修改题目所属简历");
        Question q=validated(request); q.id=id; q.blockId=request.blockId;
        if(q.blockId!=null) { Checks.require(old.resumeId!=null,"普通题不能关联简历条目"); resumes.requireBlock(q.blockId,old.resumeId); }
        mapper.update(q);
        return requireVisible(id);
    }
    @Transactional
    public void delete(Long id) {
        Question q=editableLocked(id);
        List<Question> chain=mapper.chain(q.rootId==null?q.id:q.rootId);
        Set<Long> ids=new LinkedHashSet<>(); ids.add(id);
        boolean changed;
        do { changed=false; for(Question child:chain) if(child.parentId!=null && ids.contains(child.parentId)) changed|=ids.add(child.id); } while(changed);
        mapper.softDelete(new ArrayList<>(ids));
    }
    @Transactional
    public void setPublic(Long id,Integer isPublic) {
        Checks.require(isPublic!=null && (isPublic==0 || isPublic==1),"isPublic 必须为 0 或 1");
        Question q=editableLocked(id);
        Checks.require(q.resumeId==null,"简历题目需随简历整体调整公开状态");
        mapper.setChainPublic(q.rootId==null?q.id:q.rootId,isPublic);
    }
    private Question validated(QuestionRequest request) {
        Checks.require(categories.findById(request.categoryId)!=null,"分类不存在");
        Checks.require(request.content.getBytes(StandardCharsets.UTF_8).length<=65535
                && request.answer.getBytes(StandardCharsets.UTF_8).length<=65535
                && (request.analysis==null || request.analysis.getBytes(StandardCharsets.UTF_8).length<=65535),
                "题干、答案或解析超过数据库文本容量，请缩短内容");
        Question q=new Question(); q.categoryId=request.categoryId; q.type=request.type; q.difficulty=request.difficulty;
        q.title=request.title.trim(); q.content=request.content; q.answer=request.answer.trim(); q.analysis=request.analysis;
        if("SINGLE".equals(q.type) || "MULTI".equals(q.type)) {
            JsonNode options=request.options;
            if(options!=null && options.isTextual()) { try { options=json.readTree(options.asText()); } catch(Exception e) { throw new BizException(400,"选项格式错误"); } }
            Checks.require(options!=null && options.isArray() && options.size()>=2 && options.size()<=6,"选项应为 2～6 项");
            Set<String> keys=new HashSet<>();
            for(JsonNode option:options) {
                String key=option.path("key").asText(""); String text=option.path("text").asText("");
                Checks.require(option.isObject() && key.matches("[A-F]") && keys.add(key) && option.path("text").isTextual() && !text.trim().isEmpty() && text.length()<=2000,"选项 key 必须为不重复的 A～F，且内容非空");
            }
            q.answer=q.answer.toUpperCase(Locale.ROOT);
            Checks.require(q.answer.matches("[A-F]+"),"答案须使用选项字母");
            if("SINGLE".equals(q.type)) Checks.require(q.answer.length()==1 && keys.contains(q.answer),"单选答案必须为一个有效选项");
            else {
                q.answer=GradingService.normalizeMulti(q.answer);
                Checks.require(q.answer.length()>=2,"多选答案至少选择两项");
                for(char key:q.answer.toCharArray()) Checks.require(keys.contains(String.valueOf(key)),"答案包含不存在的选项");
            }
            q.options=options.toString();
        } else if("JUDGE".equals(q.type)) {
            q.answer=q.answer.toUpperCase(Locale.ROOT); Checks.require("T".equals(q.answer)||"F".equals(q.answer),"判断题答案必须为 T 或 F");
        } else if("FILL".equals(q.type)) {
            for(String blank:q.answer.split("\\|\\|",-1)) Checks.require(!blank.trim().isEmpty(),"填空题每一空的答案不能为空");
        }
        return q;
    }
}
