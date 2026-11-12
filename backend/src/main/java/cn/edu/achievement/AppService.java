package cn.edu.achievement;

import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static cn.edu.achievement.Rows.*;

@Service
public class AppService {
    public static final List<String> TYPES=List.of("TEACHING","RESEARCH","COMPETITION","SCHOLARSHIP","PATENT","PAPER","SOFTWARE","CERTIFICATION");
    public static final List<String> LEVELS=List.of("INTERNATIONAL","NATIONAL","PROVINCIAL","UNIVERSITY","OTHER");
    public static final List<String> STATUSES=List.of("DRAFT","PENDING","APPROVED","REJECTED");
    final Database db; final PasswordEncoder encoder; final ObjectMapper json;
    public AppService(Database db,PasswordEncoder encoder,ObjectMapper json) { this.db=db; this.encoder=encoder; this.json=json; }
    public User actor() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        User u=auth == null ? null : db.userByName(auth.getName());
        if(u==null||!u.enabled) throw new ApiException(401,"请重新登录"); return u;
    }
    public User admin() { User u=actor(); if(!u.role.equals("ADMIN")) throw ApiException.forbidden(); return u; }
    public Map<String,Object> userView(User u) {
        return map("id",u.id,"username",u.username,"name",u.name,"role",u.role,"number",u.number,"cohort",u.cohort,"department",u.department,"enabled",u.enabled);
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> m=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]); return m;
    }
    static String text(Map<String,Object> input,String key,int max,boolean required) {
        Object raw=input.get(key); if(raw!=null&&!(raw instanceof String)) throw ApiException.bad(key+"必须是文本");
        String s=raw==null?"":((String)raw).trim();
        if((required&&s.isEmpty())||s.length()>max)throw ApiException.bad(key+"不能为空且长度不能超过"+max); return s;
    }
    static long id(Object raw) { if(!(raw instanceof Number n)||n.longValue()<=0||n.doubleValue()!=n.longValue())throw ApiException.bad("无效编号");return n.longValue(); }
    static String choice(Map<String,Object> input,String key,List<String> allowed) {String s=text(input,key,30,true);if(!allowed.contains(s))throw ApiException.bad("无效的"+key);return s;}
    static String password(Map<String,Object> input,String key) {
        String p=text(input,key,72,true); if(p.length()<8||!p.matches("(?s).*[A-Za-z].*")||!p.matches("(?s).*\\d.*"))throw ApiException.bad("密码需为8至72位，包含字母和数字");return p;
    }
    public List<Map<String,Object>> options() {actor();return db.users().stream().filter(u->u.enabled).map(this::userView).toList();}
    public Map<String,Object> users(Map<String,String> query) {
        admin(); String q=query.getOrDefault("q","").toLowerCase(Locale.ROOT);
        String role=query.getOrDefault("role","");
        return page(db.users().stream().filter(u->role.isBlank()||u.role.equals(role)).filter(u->(u.name+u.username+u.number).toLowerCase(Locale.ROOT).contains(q)).map(this::userView).toList(),query);
    }
    @Transactional public Map<String,Object> saveUser(Long userId,Map<String,Object> input) {
        User admin=admin(), u=userId==null?new User():db.user(userId);if(u==null)throw ApiException.missing();
        String oldUsername=u.username;
        u.username=text(input,"username",80,true);if(!u.username.matches("[A-Za-z0-9_.-]{3,80}"))throw ApiException.bad("账号只能包含字母、数字、下划线、点和连字符，至少3位");
        u.name=text(input,"name",100,true);u.role=choice(input,"role",List.of("ADMIN","TEACHER","STUDENT"));
        u.number=text(input,"number",80,false);u.cohort=text(input,"cohort",40,false);u.department=text(input,"department",150,false);
        Object enabled=input.getOrDefault("enabled",true);if(!(enabled instanceof Boolean))throw ApiException.bad("enabled必须为布尔值");u.enabled=(boolean)enabled;
        if(userId!=null&&userId.equals(admin.id)&&(!u.enabled||!u.role.equals("ADMIN")||!u.username.equals(oldUsername)))throw ApiException.bad("不能停用、降权或修改当前管理员的登录账号");
        if(userId==null||input.get("password") instanceof String p&&!p.isBlank())u.passwordHash=encoder.encode(password(input,"password"));
        if(userId==null)db.insertUser(u);else db.updateUser(u);return userView(u);
    }
    @Transactional public void changePassword(Map<String,Object> input) {
        User u=actor();String old=text(input,"oldPassword",72,true);if(!encoder.matches(old,u.passwordHash))throw ApiException.bad("原密码错误");
        String p=password(input,"newPassword");if(encoder.matches(p,u.passwordHash))throw ApiException.bad("新密码不能与原密码相同");u.passwordHash=encoder.encode(p);db.updateUser(u);
    }
    boolean visible(Achievement a,User u) {return u.role.equals("ADMIN")||a.ownerId.equals(u.id)||db.participants(a.id).stream().anyMatch(p->p.userId.equals(u.id));}
    Achievement require(long id,User u) { Achievement a=db.achievement(id);if(a==null)throw ApiException.missing();if(!visible(a,u))throw ApiException.forbidden();return a; }
    Achievement editable(long id,User u) {
        // Every mutation locks this row first, including attachment mutations, so submission
        // and review see an immutable set of fields, participants and private materials.
        Achievement a=db.lockAchievement(id);if(a==null)throw ApiException.missing();
        if(!u.role.equals("ADMIN")&&!a.ownerId.equals(u.id))throw ApiException.forbidden();
        if(!List.of("DRAFT","REJECTED").contains(a.status))throw new ApiException(409,"只有草稿或退回成果可以修改");return a;
    }
    public Map<String,Object> view(Achievement a) {
        Object details=a.detailsJson==null?Map.of():json.readValue(a.detailsJson,Map.class);
        return map("id",a.id,"title",a.title,"type",a.type,"level",a.level,"achievedOn",a.achievedOn,"description",a.description,"details",details,"status",a.status,"ownerId",a.ownerId,"ownerName",db.user(a.ownerId).name,"createdAt",a.createdAt,"updatedAt",a.updatedAt,
            "participants",db.participants(a.id).stream().map(p->map("userId",p.userId,"name",db.user(p.userId).name,"role",p.role,"sortOrder",p.sortOrder)).toList(),"indicatorIds",db.achievementIndicators(a.id));
    }
    public Map<String,Object> achievement(long id) {return view(require(id,actor()));}
    public List<Achievement> filtered(Map<String,String> q,boolean exportDefault) {
        User u=actor();String scope=q.getOrDefault("scope","all"),status=q.getOrDefault("status",exportDefault?"APPROVED":"");
        if(!List.of("all","mine","guided").contains(scope))throw ApiException.bad("无效查询范围");
        String search=q.getOrDefault("q","").toLowerCase(Locale.ROOT),type=q.getOrDefault("type",""),level=q.getOrDefault("level","");
        String year=q.getOrDefault("year",""),indicator=q.getOrDefault("indicatorId","");
        if(!year.isEmpty()&&!year.matches("\\d{4}"))throw ApiException.bad("年份格式错误");
        Long indicatorId=indicator.isBlank()?null:Long.valueOf(indicator);
        return db.achievements().stream().filter(a->visible(a,u))
            .filter(a->scope.equals("all")||scope.equals("mine")&&(a.ownerId.equals(u.id)||db.participants(a.id).stream().anyMatch(p->p.userId.equals(u.id)&&!p.role.equals("ADVISOR")))||scope.equals("guided")&&db.participants(a.id).stream().anyMatch(p->p.userId.equals(u.id)&&p.role.equals("ADVISOR")))
            .filter(a->search.isEmpty()||(a.title+" "+Objects.toString(a.description,"")).toLowerCase(Locale.ROOT).contains(search))
            .filter(a->type.isEmpty()||a.type.equals(type)).filter(a->level.isEmpty()||a.level.equals(level)).filter(a->status.isEmpty()||a.status.equals(status))
            .filter(a->year.isEmpty()||a.achievedOn.getYear()==Integer.parseInt(year)).filter(a->indicatorId==null||db.achievementIndicators(a.id).contains(indicatorId)).toList();
    }
    public Map<String,Object> achievements(Map<String,String> query) {return page(filtered(query,false).stream().map(this::view).toList(),query);}
    public static Map<String,Object> page(List<?> items,Map<String,String> query) {
        int page=Integer.parseInt(query.getOrDefault("page","1")),size=Integer.parseInt(query.getOrDefault("size","12"));
        if(page<1||size<1||size>200)throw ApiException.bad("页码需大于0，每页1至200条");
        int from=(int)Math.min((long)(page-1)*size,items.size());return map("items",items.subList(from,Math.min(from+size,items.size())),"total",items.size(),"page",page,"size",size);
    }
    @Transactional public Map<String,Object> saveAchievement(Long achievementId,Map<String,Object> input) {
        User u=actor();Achievement a=achievementId==null?new Achievement():editable(achievementId,u);
        a.title=text(input,"title",250,true);a.type=choice(input,"type",TYPES);a.level=choice(input,"level",LEVELS);
        a.achievedOn=LocalDate.parse(text(input,"achievedOn",10,true));if(a.achievedOn.getYear()<1900||a.achievedOn.getYear()>2100)throw ApiException.bad("成果年份需在1900至2100之间");
        a.description=text(input,"description",10000,false);Object details=input.getOrDefault("details",Map.of());if(!(details instanceof Map))throw ApiException.bad("details需为对象");a.detailsJson=json.writeValueAsString(details);if(a.detailsJson.length()>20000)throw ApiException.bad("分类详情过长");
        List<Participant> participants=new ArrayList<>();Set<String> seen=new HashSet<>();
        Object raw=input.getOrDefault("participants",List.of());if(!(raw instanceof List<?> list)||list.size()>100)throw ApiException.bad("参与人格式错误或超过100人");
        for(Object p: (List<?>)raw) {
            if(!(p instanceof Map<?,?> pm))throw ApiException.bad("参与人格式错误");
            Participant row=new Participant();row.userId=id(pm.get("userId"));User member=db.user(row.userId);if(member==null||!member.enabled)throw ApiException.bad("参与人不存在或已停用");
            row.role=Objects.toString(pm.get("role"),"");if(!List.of("AUTHOR","MEMBER","ADVISOR").contains(row.role))throw ApiException.bad("无效参与角色");
            if(row.role.equals("ADVISOR")&&!member.role.equals("TEACHER"))throw ApiException.bad("指导教师必须为教师账号");
            Object order=pm.get("sortOrder");row.sortOrder=order instanceof Number n?n.intValue():participants.size()+1;if(row.sortOrder<0||row.sortOrder>1000)throw ApiException.bad("成员排序需在0至1000之间");
            if(!seen.add(row.userId+":"+row.role))throw ApiException.bad("参与人角色重复");participants.add(row);
        }
        Long owner=achievementId==null?u.id:a.ownerId;
        if(participants.stream().noneMatch(p->p.userId.equals(owner))) {Participant p=new Participant();p.userId=owner;p.role="AUTHOR";p.sortOrder=0;participants.add(p);}
        Object rawIndicators=input.getOrDefault("indicatorIds",List.of());if(!(rawIndicators instanceof List<?>))throw ApiException.bad("指标列表格式错误");
        Set<Long> indicators=new LinkedHashSet<>();for(Object value:(List<?>)rawIndicators){long i=id(value);if(db.indicator(i)==null)throw ApiException.bad("支撑指标不存在");indicators.add(i);}
        a.updatedAt=LocalDateTime.now();if(achievementId==null){a.status="DRAFT";a.ownerId=u.id;a.createdAt=a.updatedAt;db.insertAchievement(a);}else db.updateAchievement(a);
        db.deleteParticipants(a.id);for(Participant p:participants){p.achievementId=a.id;db.insertParticipant(p);}db.unlinkIndicators(a.id);for(long i:indicators)db.linkIndicator(a.id,i);
        return view(a);
    }
    @Transactional public void deleteAchievement(long id) {editable(id,actor());db.deleteAchievement(id);}
    @Transactional public Map<String,Object> submit(long id) {Achievement a=editable(id,actor());a.status="PENDING";a.updatedAt=LocalDateTime.now();db.updateAchievement(a);return view(a);}
    @Transactional public Map<String,Object> review(long id,Map<String,Object> input) {
        User u=admin();Achievement a=db.lockAchievement(id);if(a==null)throw ApiException.missing();if(!a.status.equals("PENDING"))throw new ApiException(409,"只能审核待审成果");
        if(a.ownerId.equals(u.id)||db.participants(id).stream().anyMatch(p->p.userId.equals(u.id)))throw ApiException.bad("不能审核自己拥有或参与的成果");
        if(!(input.get("approved") instanceof Boolean))throw ApiException.bad("审核结果必填");
        Review r=new Review();r.achievementId=id;r.reviewerId=u.id;r.approved=(boolean)input.get("approved");r.comment=text(input,"comment",2000,!r.approved);r.createdAt=LocalDateTime.now();db.insertReview(r);
        a.status=r.approved?"APPROVED":"REJECTED";a.updatedAt=r.createdAt;db.updateAchievement(a);return view(a);
    }
    public List<Map<String,Object>> reviews(long id) {require(id,actor());return db.reviews(id).stream().map(r->map("id",r.id,"reviewerName",db.user(r.reviewerId).name,"approved",r.approved,"comment",r.comment,"createdAt",r.createdAt)).toList();}
    public List<Indicator> indicators(){actor();return db.indicators();}
    @Transactional public Indicator saveIndicator(Long id,Map<String,Object> input) {
        admin();Indicator i=id==null?new Indicator():db.indicator(id);if(i==null)throw ApiException.missing();i.code=text(input,"code",60,true);i.name=text(input,"name",150,true);i.description=text(input,"description",3000,false);if(id==null)db.insertIndicator(i);else db.updateIndicator(i);return i;
    }
    public Map<String,Object> stats(Map<String,String> query) {
        Map<String,String> q=new HashMap<>();if(query.containsKey("year"))q.put("year",query.get("year"));List<Achievement> all=filtered(q,false);
        List<Achievement> approved=all.stream().filter(a->a.status.equals("APPROVED")).toList();Set<Long> people=new HashSet<>();Map<Long,Long> counts=new HashMap<>();long instances=0;
        for(Achievement a:approved){Set<Long> unique=db.participants(a.id).stream().map(p->p.userId).collect(Collectors.toSet());instances+=unique.size();people.addAll(unique);unique.forEach(p->counts.merge(p,1L,Long::sum));}
        List<Map<String,Object>> top=counts.entrySet().stream().sorted(Map.Entry.<Long,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(8).map(e->map("name",db.user(e.getKey()).name,"value",e.getValue())).toList();
        Map<String,Object> result=map("total",approved.size(),"participants",instances,"teachers",people.stream().filter(i->db.user(i).role.equals("TEACHER")).count(),"students",people.stream().filter(i->db.user(i).role.equals("STUDENT")).count(),"pending",all.stream().filter(a->a.status.equals("PENDING")).count(),"byType",group(approved,a->a.type),"byLevel",group(approved,a->a.level),"byYear",group(approved,a->String.valueOf(a.achievedOn.getYear())),"byMonth",group(approved,a->a.achievedOn.toString().substring(0,7)),"topContributors",top);return result;
    }
    private List<Map<String,Object>> group(List<Achievement> values,java.util.function.Function<Achievement,String> key) {return values.stream().collect(Collectors.groupingBy(key,TreeMap::new,Collectors.counting())).entrySet().stream().map(e->map("name",e.getKey(),"value",e.getValue())).toList();}
}
