package com.quiz;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfilesResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Exercises controllers, auth interceptors, transactions and XML SQL together.
 * Defaults to isolated H2; quiz.test.profile may select prod/dev for a configured MySQL.
 * Existing database content is never edited: cleanup only soft-deletes IDs created here.
 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles(resolver=QuizIntegrationTest.DatabaseProfile.class)
class QuizIntegrationTest {
    public static class DatabaseProfile implements ActiveProfilesResolver {
        @Override public String[] resolve(Class<?> testClass) {
            return System.getProperty("quiz.test.profile","test").split(",");
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String admin,alice,bob;
    long aliceId,categoryId;
    final String runMarker="it_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    final Set<Long> createdUsers=new LinkedHashSet<>();
    final Set<Long> createdQuestions=new LinkedHashSet<>();
    final Set<Long> createdResumes=new LinkedHashSet<>();
    final Set<Long> createdComments=new LinkedHashSet<>();
    @BeforeEach void users() throws Exception {
        admin=login(System.getProperty("quiz.test.admin.username",environment("QUIZ_TEST_ADMIN_USERNAME","admin")),System.getProperty("quiz.test.admin.password",environment("QUIZ_TEST_ADMIN_PASSWORD","admin123")));
        String a="alice_"+runMarker,b="bob_"+runMarker;
        aliceId=createUser(a,"Alice");
        createUser(b,"Bob");
        alice=login(a,"secret123");bob=login(b,"secret123");
        JsonNode categories=call(get("/api/categories"),alice,null,0).path("data");
        Assumptions.assumeTrue(categories.isArray() && categories.size()>0,"集成测试需要至少一个已有可用分类");
        categoryId=categories.get(0).path("id").asLong();
    }
    @AfterEach void cleanupCreatedData() {
        List<String> failures=new ArrayList<>();
        for(Long id:createdComments) cleanup("comment "+id,() -> jdbc.update("UPDATE comment SET status=0 WHERE id=?",id),failures);
        for(Long id:createdQuestions) cleanup("question "+id,() -> jdbc.update("UPDATE question SET status=0 WHERE id=?",id),failures);
        for(Long id:createdResumes) {
            cleanup("resume questions "+id,() -> jdbc.update("UPDATE question SET status=0 WHERE resume_id=?",id),failures);
            cleanup("resume blocks "+id,() -> jdbc.update("UPDATE resume_block SET status=0 WHERE resume_id=?",id),failures);
            cleanup("resume "+id,() -> jdbc.update("UPDATE resume SET status=0 WHERE id=?",id),failures);
        }
        for(Long id:createdUsers) {
            cleanup("disable user "+id,() -> jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",id),failures);
            cleanup("logout user "+id,() -> StpUtil.logout(id),failures);
        }
        // Answer records intentionally remain as audit history; their questions are soft-deleted.
        // Only revoke the admin token issued by this test, preserving other admin sessions.
        if(admin!=null && !admin.isEmpty()) cleanup("test admin token",() -> StpUtil.logoutByTokenValue(admin),failures);
        assertTrue(failures.isEmpty(),"本轮测试数据清理未完成（"+runMarker+"）："+failures);
    }
    private void cleanup(String action,Runnable operation,List<String> failures) {
        try { operation.run(); }
        catch(Exception ex) { failures.add(action+" / "+ex.getClass().getSimpleName()); }
    }
    private long createUser(String username,String nickname) throws Exception {
        long id=call(post("/api/admin/users"),admin,map("username",username,"password","secret123","nickname",runMarker+" "+nickname,"role","USER"),0).path("data").path("id").asLong();
        assertTrue(id>0,"新建测试账号应返回ID");createdUsers.add(id);return id;
    }
    @Test void chainIsolationPublicationRevocationAndCascade() throws Exception {
        long root=createQuestion(alice,"QA",null,null,null,"root");
        long child=createQuestion(alice,"QA",root,null,null,"child");
        long grandchild=createQuestion(alice,"QA",child,null,null,"grandchild");
        long sibling=createQuestion(alice,"QA",root,null,null,"sibling");
        call(get("/api/questions/"+child),bob,null,404);
        call(get("/api/questions/"+root+"/chain"),bob,null,404);
        call(post("/api/questions/"+child+"/comments"),bob,map("content","越权"),404);
        call(get("/api/admin/questions"),alice,null,403);
        JsonNode chain=call(get("/api/questions/"+child+"/chain?brief=1"),alice,null,0).path("data");
        assertEquals(4,chain.size());for(JsonNode q:chain) { assertFalse(q.has("answer"));assertFalse(q.has("analysis")); }
        call(put("/api/admin/questions/"+child+"/public"),admin,map("isPublic",1),0);
        for(long id:new long[]{root,child,grandchild,sibling}) assertEquals(1,call(get("/api/questions/"+id),bob,null,0).path("data").path("isPublic").asInt());
        call(put("/api/questions/"+root),alice,question("QA",null,null,null,"changed"),403);
        call(post("/api/questions"),alice,question("QA",root,null,null,"forbidden"),403);
        call(put("/api/admin/questions/"+root+"/public"),admin,map("isPublic",0),0);
        call(delete("/api/questions/"+child),alice,null,0);
        call(get("/api/questions/"+grandchild),alice,null,404);
        call(get("/api/questions/"+sibling),alice,null,0);
        call(get("/api/questions/"+root),alice,null,0);
    }
    @Test void practiceDoesNotLeakAnswersAndLatestRecordControlsWrongBook() throws Exception {
        long id=createQuestion(alice,"MULTI",null,null,null,"grading");
        JsonNode start=call(post("/api/practice/start"),alice,map("mode","ORDER","count",100),0).path("data");
        assertTrue(start.size()>0);for(JsonNode q:start) { assertFalse(q.has("answer"));assertFalse(q.has("analysis")); }
        JsonNode result=call(post("/api/practice/submit"),alice,map("questionId",id,"userAnswer","A"),0);
        assertFalse(result.path("data").path("correct").asBoolean());
        assertTrue(contains(call(get("/api/practice/wrong?size=100"),alice,null,0).path("data").path("list"),id));
        result=call(post("/api/practice/submit"),alice,map("questionId",id,"userAnswer","CAAC"),0);
        assertTrue(result.path("data").path("correct").asBoolean());
        assertFalse(contains(call(get("/api/practice/wrong?size=100"),alice,null,0).path("data").path("list"),id));
        long qa=createQuestion(alice,"QA",null,null,null,"qa");
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE user_id=?",Long.class,aliceId);
        call(post("/api/practice/reveal"),alice,map("questionId",qa),0);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE user_id=?",Long.class,aliceId));
        call(post("/api/practice/submit"),alice,map("questionId",qa),400);
        call(post("/api/practice/submit"),alice,map("questionId",qa,"selfCorrect",0),0);
        assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE user_id=?",Long.class,aliceId));
        JsonNode stats=call(get("/api/practice/stats"),alice,null,0).path("data");
        assertEquals(3,stats.path("totalAnswered").asInt());assertEquals(1,stats.path("correctCount").asInt());
        call(post("/api/practice/submit"),bob,map("questionId",qa,"selfCorrect",1),404);
    }
    @Test void resumeContentAndQuestionsInheritVisibilityAndCascade() throws Exception {
        JsonNode defs=call(get("/api/block-defs"),alice,null,0).path("data");
        Assumptions.assumeTrue(defs.isArray() && defs.size()>0,"简历集成测试需要至少一个已启用的现有分块定义");
        JsonNode definition=defs.get(0);
        for(JsonNode d:defs) if(d.path("repeatable").asInt()==0) { definition=d;break; }
        long def=definition.path("id").asLong();
        Map<String,Object> content=blockContent(definition);
        long resume=call(post("/api/resumes"),alice,map("title",runMarker+" 简历专项","blocks",Collections.singletonList(map("blockDefId",def,"content",content))),0).path("data").path("id").asLong();
        assertTrue(resume>0,"新建测试简历应返回ID");createdResumes.add(resume);
        long block=call(get("/api/resumes/"+resume+"/blocks"),alice,null,0).path("data").get(0).path("id").asLong();
        if(definition.path("repeatable").asInt()==0)
            call(post("/api/resumes/"+resume+"/blocks"),alice,map("blockDefId",def,"content",content),1008);
        call(get("/api/resumes/"+resume),bob,null,404);
        long q=createQuestion(alice,"QA",null,resume,block,"resume-question");
        long child=createQuestion(admin,"QA",q,resume,null,"admin-followup");
        JsonNode inherited=call(get("/api/questions/"+child),admin,null,0).path("data");
        assertEquals(aliceId,inherited.path("ownerId").asLong());assertEquals(0,inherited.path("isPublic").asInt());
        assertFalse(contains(call(get("/api/questions?size=100"),alice,null,0).path("data").path("list"),q));
        assertFalse(contains(call(post("/api/practice/start"),alice,map("mode","RANDOM","count",100),0).path("data"),q));
        call(put("/api/admin/questions/"+q+"/public"),admin,map("isPublic",1),400);
        call(put("/api/admin/resumes/"+resume+"/public"),admin,map("isPublic",1),0);
        call(get("/api/resumes/"+resume),bob,null,0);
        assertEquals(1,call(get("/api/questions/"+child),bob,null,0).path("data").path("isPublic").asInt());
        call(put("/api/resumes/blocks/"+block),alice,map("content",content),403);
        JsonNode practice=call(post("/api/practice/start"),bob,map("mode","ORDER","resumeId",resume,"type","JUDGE"),0).path("data");
        assertEquals(2,practice.size());
        call(put("/api/admin/resumes/"+resume+"/public"),admin,map("isPublic",0),0);
        call(delete("/api/resumes/"+resume),alice,null,0);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM resume_block WHERE resume_id=? AND status=1",Long.class,resume));
        call(get("/api/questions/"+child),admin,null,404);
    }
    @Test void disabledUsersLoseSessionAndPasswordsNeverSerialize() throws Exception {
        JsonNode me=call(get("/api/auth/me"),alice,null,0).path("data");assertFalse(me.has("password"));
        call(put("/api/admin/users/"+aliceId),admin,map("nickname","Alice","role","USER","status",0),0);
        call(get("/api/auth/me"),alice,null,401);
        call(get("/api/questions"),null,null,401);
        long adminId=call(get("/api/auth/me"),admin,null,0).path("data").path("id").asLong();
        call(put("/api/admin/users/"+adminId),admin,map("nickname","admin","role","USER","status",1),403);
    }
    @Test void validationAndCommentOwnershipAreEnforced() throws Exception {
        long id=createQuestion(alice,"QA",null,null,null,"comments");
        call(post("/api/questions/"+id+"/comments"),alice,map("content","  "),400);
        long comment=call(post("/api/questions/"+id+"/comments"),alice,map("content",runMarker+" 思路补充"),0).path("data").path("id").asLong();
        assertTrue(comment>0,"新建测试评论应返回ID");createdComments.add(comment);
        call(put("/api/admin/questions/"+id+"/public"),admin,map("isPublic",1),0);
        call(delete("/api/comments/"+comment),bob,null,403);
        call(delete("/api/comments/"+comment),admin,null,0);
        Map<String,Object> invalid=question("SINGLE",null,null,null,"invalid");invalid.put("answer","Z");
        call(post("/api/questions"),alice,invalid,400);
        call(post("/api/questions"),alice,map("type","UNKNOWN"),400);
    }
    private long createQuestion(String token,String type,Long parent,Long resume,Long block,String title) throws Exception {
        long id=call(post("/api/questions"),token,question(type,parent,resume,block,title),0).path("data").path("id").asLong();
        assertTrue(id>0,"新建测试题目应返回ID");createdQuestions.add(id);return id;
    }
    private Map<String,Object> question(String type,Long parent,Long resume,Long block,String title) {
        Map<String,Object> q=map("categoryId",categoryId,"type",type,"difficulty",2,"title",runMarker+" "+title,"content","题干描述","answer","参考答案","parentId",parent,"resumeId",resume,"blockId",block,"ownerId",999999,"isPublic",1);
        if("MULTI".equals(type)||"SINGLE".equals(type)) { q.put("options",Arrays.asList(map("key","A","text","A选项"),map("key","B","text","B选项"),map("key","C","text","C选项")));q.put("answer","MULTI".equals(type)?"AC":"A"); }
        return q;
    }
    private Map<String,Object> blockContent(JsonNode definition) throws Exception {
        JsonNode schema=definition.path("fieldSchema");
        if(schema.isTextual()) schema=json.readTree(schema.asText());
        assertTrue(schema.isArray(),"现有分块的fieldSchema应为数组");
        Map<String,Object> content=new LinkedHashMap<>();
        for(JsonNode field:schema) {
            String type=field.path("type").asText();
            Object value="date".equals(type)?"2026-10":"list".equals(type)?Collections.singletonList(runMarker+" 测试条目"):runMarker+" 测试内容";
            content.put(field.path("key").asText(),value);
        }
        return content;
    }
    private String login(String username,String password) throws Exception { return call(post("/api/auth/login"),null,map("username",username,"password",password),0).path("data").path("token").asText(); }
    private JsonNode call(MockHttpServletRequestBuilder request,String token,Object body,int code) throws Exception {
        if(token!=null)request.header("satoken",token);
        if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        MvcResult response=mvc.perform(request).andReturn();
        JsonNode result=json.readTree(response.getResponse().getContentAsString());
        // Register returned creation IDs before asserting the expected code. Even an
        // accidentally accepted unauthorized request is then included in cleanup.
        if(result!=null && result.path("code").asInt(-1)==0 && "POST".equals(response.getRequest().getMethod())) {
            long id=result.path("data").path("id").asLong();
            String uri=response.getRequest().getRequestURI();
            if(id>0) {
                if("/api/questions".equals(uri)) createdQuestions.add(id);
                else if("/api/resumes".equals(uri)) createdResumes.add(id);
                else if("/api/admin/users".equals(uri)) createdUsers.add(id);
                else if(uri.matches("/api/questions/[0-9]+/comments")) createdComments.add(id);
            }
        }
        // Do not put successful login response bodies (including session tokens) in failures.
        assertNotNull(result,"接口应返回JSON响应");assertEquals(code,result.path("code").asInt(),result.path("message").asText());return result;
    }
    private static boolean contains(JsonNode rows,long id) { for(JsonNode q:rows)if(q.path("id").asLong()==id)return true;return false; }
    private static String environment(String key,String fallback) { String value=System.getenv(key);return value==null || value.isEmpty()?fallback:value; }
    private static Map<String,Object> map(Object... pairs) { Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result; }
}
