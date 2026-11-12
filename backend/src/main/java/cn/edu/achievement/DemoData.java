package cn.edu.achievement;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static cn.edu.achievement.Rows.*;

@Component
public class DemoData implements ApplicationRunner {
    final Database db; final PasswordEncoder encoder; final boolean demo; final String initialUsername,initialPassword;
    DemoData(Database db,PasswordEncoder encoder,@Value("${app.seed-demo:true}") boolean demo,
             @Value("${INITIAL_ADMIN_USERNAME:admin}") String initialUsername,@Value("${INITIAL_ADMIN_PASSWORD:}") String initialPassword){
        this.db=db;this.encoder=encoder;this.demo=demo;this.initialUsername=initialUsername;this.initialPassword=initialPassword;
    }
    @Override @Transactional public void run(ApplicationArguments args){
        if(!db.users().isEmpty())return;
        if(!demo){
            if(!initialUsername.matches("[A-Za-z0-9_.-]{3,80}"))throw new IllegalStateException("INITIAL_ADMIN_USERNAME must be 3-80 letters, digits, dots, underscores or hyphens");
            AppService.password(Map.of("password",initialPassword),"password");
            user(initialUsername,initialPassword,"系统管理员","ADMIN","ADMIN"," ");return;
        }
        User admin=user("admin","Admin@12345","陈老师 · 管理员","ADMIN","A001","");
        User teacher=user("teacher","Teacher@12345","张明","TEACHER","T202001","");
        User student=user("student","Student@12345","李思远","STUDENT","2023001001","2023");
        User teacher2=user("teacher2","Teacher@12345","王静","TEACHER","T202103","");
        User student2=user("student2","Student@12345","陈雨桐","STUDENT","2023001002","2023");
        User student3=user("student3","Student@12345","赵子涵","STUDENT","2024001003","2024");
        Indicator engineering=indicator("BY-03","设计与开发解决方案","用于归档支撑工程设计能力的成果与材料；支撑项示例，需由专业确认。");
        Indicator research=indicator("BY-04","研究与创新实践","归集科研训练、论文、专利与竞赛成果，不自动计算达成度。");
        Indicator quality=indicator("JX-01","教学建设与持续改进","课程建设、教学改革与教师教学成果支撑材料。");
        Indicator growth=indicator("XS-01","学生发展与职业能力","学生奖励、奖学金、行业认证及职业能力材料。");
        String[] titles={"数据结构课程获批省级一流本科课程","智能感知与边缘计算科研项目立项","全国大学生计算机设计大赛二等奖","2025—2026学年国家奖学金","一种面向校园场景的智能巡检方法","面向复杂环境的轻量级目标检测研究","智慧校园业绩管理平台 V1.0","华为认证网络工程师 HCIA","软件工程课程教学改革与实践","产学协同育人项目：可信软件开发","中国国际大学生创新大赛省级金奖","2024—2025学年校级一等奖学金","一种低功耗环境数据采集装置","基于知识图谱的课程资源推荐方法","实践教学过程评价系统 V2.0","计算机技术与软件专业技术资格","程序设计课程案例库建设","面向教学场景的大模型辅助评价研究","蓝桥杯全国软件和信息技术专业人才大赛","优秀学生综合奖学金","一种便携式实验数据记录装置","多模态课堂学习行为识别方法","实验室设备预约管理系统","云计算平台运维职业认证"};
        for(int index=0;index<titles.length;index++){
            String type=AppService.TYPES.get(index%8);boolean faculty=List.of("TEACHING","RESEARCH").contains(type);
            User owner=faculty?(index%3==0?teacher2:teacher):(index%3==0?student2:student);
            Achievement a=new Achievement();a.title=titles[index];a.type=type;a.level=AppService.LEVELS.get((index%4)+1);
            a.achievedOn=LocalDate.of(2024+index/8,1+index%9,5+index%20);
            a.description="演示数据："+titles[index]+"。用于展示成果填报、审核、多人协作、分类统计与认证材料归档。请在实际使用前替换为真实记录。";
            a.detailsJson=details(type,index);a.ownerId=owner.id;a.status=index<16?"APPROVED":index<20?"PENDING":index<22?"DRAFT":"REJECTED";
            a.createdAt=a.achievedOn.atTime(9,0);a.updatedAt=a.createdAt.plusDays(1);db.insertAchievement(a);
            participant(a,owner,"AUTHOR",1);
            if(!faculty&&List.of("COMPETITION","PATENT","PAPER","SOFTWARE").contains(type)){
                participant(a,teacher,"ADVISOR",9);participant(a,student3,"MEMBER",2);
            }else if(faculty&&type.equals("RESEARCH")){participant(a,teacher2.id.equals(owner.id)?teacher:teacher2,"MEMBER",2);}
            db.linkIndicator(a.id,(type.equals("TEACHING")?quality:List.of("SCHOLARSHIP","CERTIFICATION").contains(type)?growth:type.equals("COMPETITION")?engineering:research).id);
            if(a.status.equals("APPROVED")||a.status.equals("REJECTED")){
                Review r=new Review();r.achievementId=a.id;r.reviewerId=admin.id;r.approved=a.status.equals("APPROVED");r.comment=r.approved?"演示审核：信息与支撑分类已核对。":"请补充完整成果信息与证明材料后重新提交。";r.createdAt=a.updatedAt;db.insertReview(r);
            }
        }
    }
    User user(String username,String password,String name,String role,String number,String cohort){User u=new User();u.username=username;u.passwordHash=encoder.encode(password);u.name=name;u.role=role;u.number=number;u.cohort=cohort.trim();u.department="计算机科学与技术";u.enabled=true;db.insertUser(u);return u;}
    Indicator indicator(String code,String name,String description){Indicator i=new Indicator();i.code=code;i.name=name;i.description=description;db.insertIndicator(i);return i;}
    void participant(Achievement a,User u,String role,int order){Participant p=new Participant();p.achievementId=a.id;p.userId=u.id;p.role=role;p.sortOrder=order;db.insertParticipant(p);}
    String details(String type,int index){return switch(type){
        case "TEACHING"->"{\"projectNo\":\"JG-2026-"+index+"\",\"organization\":\"省教育厅\",\"awardName\":\"一流本科课程\"}";
        case "RESEARCH"->"{\"projectNo\":\"KY-"+(202400+index)+"\",\"funding\":\"200000\",\"organization\":\"省自然科学基金\"}";
        case "COMPETITION"->"{\"competitionName\":\"大学生创新实践竞赛\",\"awardName\":\"二等奖\",\"organizer\":\"竞赛组织委员会\"}";
        case "SCHOLARSHIP"->"{\"scholarshipName\":\"优秀学生奖学金\",\"amount\":\"8000\",\"academicYear\":\"2025-2026\"}";
        case "PATENT"->"{\"patentNo\":\"DEMO-ZL2026"+index+"\",\"patentType\":\"发明专利\",\"patentStatus\":\"已授权\"}";
        case "PAPER"->"{\"journalName\":\"计算机教育与实践（演示）\",\"doi\":\"\",\"paperStatus\":\"已发表\"}";
        case "SOFTWARE"->"{\"registrationNo\":\"DEMO-2026SR"+index+"\",\"version\":\"V1.0\"}";
        default->"{\"certificateNo\":\"DEMO-CERT-"+index+"\",\"issuer\":\"行业认证机构（演示）\",\"validUntil\":\"2028-12-31\"}";
    };}
}
