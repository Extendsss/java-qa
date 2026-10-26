package com.quiz.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @Value("${quiz.initial-admin.username:admin}") private String adminUsername;
    @Value("${quiz.initial-admin.password:admin123}") private String adminPassword;
    public DataInitializer(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE role='ADMIN'",Long.class)==0) {
            if (adminPassword.length()<6 || adminPassword.length()>72) throw new IllegalStateException("ADMIN_PASSWORD 应为6～72字符");
            jdbc.update("INSERT INTO sys_user(username,password,nickname,role,status) VALUES(?,?,?,'ADMIN',1)",adminUsername,new BCryptPasswordEncoder().encode(adminPassword),"超级管理员");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM question_category",Long.class)==0) {
            String[] categories={"计算机基础","数据结构","Java","MySQL","计算机网络"};
            for(int i=0;i<categories.length;i++)jdbc.update("INSERT INTO question_category(name,sort) VALUES(?,?)",categories[i],i+1);
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM block_def",Long.class)==0) seedBlocks();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM question",Long.class)==0) seedQuestions();
    }
    private void seedBlocks() {
        block("basic","基本信息",0,1,field("姓名","text",true),field("电话","text",false),field("邮箱","text",false),field("求职意向","text",false));
        block("edu","教育经历",1,2,field("学校","text",true),field("专业","text",false),field("学历","text",false),field("开始时间","date",false),field("结束时间","date",false));
        block("work","工作经历",1,3,field("公司","text",true),field("工作岗位","text",false),field("开始时间","date",false),field("结束时间","date",false),field("工作内容","list",false));
        block("project","项目经历",1,4,field("项目名","text",true),field("开始时间","date",false),field("结束时间","date",false),field("工作岗位","text",false),field("简介","textarea",false),field("详情","list",false));
        block("skill","技能特长",1,5,field("技能清单","list",true));
        block("about","自我评价",0,6,field("评价","textarea",true));
    }
    private Map<String,Object> field(String name,String type,boolean required) {
        Map<String,Object> f=new LinkedHashMap<String,Object>();f.put("key",name);f.put("label",name);f.put("type",type);f.put("required",required);return f;
    }
    @SafeVarargs private final void block(String code,String name,int repeatable,int sort,Map<String,Object>... fields) {
        jdbc.update("INSERT INTO block_def(code,name,field_schema,repeatable,enabled,sort,built_in) VALUES(?,?,?,?,1,?,1)",code,name,encode(Arrays.asList(fields)),repeatable,sort);
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException ex) { throw new IllegalStateException(ex); } }
    private void seedQuestions() {
        Long owner=jdbc.queryForObject("SELECT id FROM sys_user WHERE role='ADMIN' ORDER BY id LIMIT 1",Long.class);
        List<Long> javaIds=jdbc.queryForList("SELECT id FROM question_category WHERE name='Java' AND status=1",Long.class);
        if(javaIds.isEmpty()) return;
        Long java=javaIds.get(0);
        Long root=question(java,owner,"QA",2,"Eureka 和 Nacos 的区别","Eureka 和 Nacos 有哪些区别？",null,"Eureka 主要提供服务注册与发现；Nacos 同时提供注册中心与配置中心，并支持不同的一致性与健康检查机制。具体实现取决于版本与实例类型。","从服务发现、配置管理、一致性、健康检查四个方面展开。",null,null);
        Long child=question(java,owner,"QA",2,"Nacos 的数据存在哪里","Nacos 的注册数据和配置数据分别如何存储？",null,"临时服务实例通常维护于内存并进行集群同步；配置数据可持久化于外部 MySQL。不同版本对持久实例与嵌入式数据库的支持不同。","先区分注册信息、配置数据与本地缓存。",root,root);
        question(java,owner,"QA",3,"Nacos 如何维持心跳","Nacos 如何判断服务实例是否健康？",null,"Nacos 1.x 临时实例通常依赖客户端心跳，超时后标记不健康或剔除；2.x 客户端主要使用长连接感知连接状态。持久实例可通过服务端主动探测检查健康。","回答时说明版本与实例类型。",child,root);
        List<Long> mysql=jdbc.queryForList("SELECT id FROM question_category WHERE name='MySQL' AND status=1",Long.class);
        if(!mysql.isEmpty()) question(mysql.get(0),owner,"SINGLE",1,"MySQL 默认端口","MySQL 服务默认使用的 TCP 端口是？","[{\"key\":\"A\",\"text\":\"3306\"},{\"key\":\"B\",\"text\":\"5432\"},{\"key\":\"C\",\"text\":\"1433\"},{\"key\":\"D\",\"text\":\"1521\"}]","A","MySQL 默认端口为 3306。",null,null);
        question(java,owner,"MULTI",2,"Java 基本数据类型","下列哪些是 Java 基本数据类型？","[{\"key\":\"A\",\"text\":\"int\"},{\"key\":\"B\",\"text\":\"String\"},{\"key\":\"C\",\"text\":\"boolean\"},{\"key\":\"D\",\"text\":\"double\"}]","ACD","String 是引用类型。",null,null);
        question(java,owner,"JUDGE",1,"Java 字符串不可变","Java 中 String 对象是不可变的。",null,"T","String 的值创建后不能改变，拼接操作会产生新字符串。",null,null);
        question(java,owner,"FILL",1,"线程与进程","资源分配的基本单位是____，CPU 调度的基本单位是____。",null,"进程||线程","多空答案以 || 分隔。",null,null);
    }
    private Long question(Long category,Long owner,String type,int difficulty,String title,String content,String options,String answer,String analysis,Long parent,Long root) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement=connection.prepareStatement("INSERT INTO question(category_id,owner_id,type,difficulty,title,content,options,answer,analysis,parent_id,root_id,is_public) VALUES(?,?,?,?,?,?,?,?,?,?,?,1)",new String[]{"id"});
            Object[] values={category,owner,type,difficulty,title,content,options,answer,analysis,parent,root};
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
