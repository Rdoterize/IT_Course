package cn.edu.achievement;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.storage-dir=./target/test-materials","app.seed-demo=true"})
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired Database db; @Autowired ObjectMapper json; @Autowired FileService files;
    Map<String,Object> body(String title){return AppService.map("title",title,"type","COMPETITION","level","NATIONAL","achievedOn","2026-05-10","description","测试成果","details",Map.of("awardName","一等奖"),"participants",List.of(AppService.map("userId",db.userByName("teacher").id,"role","ADVISOR","sortOrder",2)),"indicatorIds",List.of());}
    long create(String owner,String title)throws Exception {
        String content=mvc.perform(post("/api/achievements").with(user(owner)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body(title)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number)json.readValue(content,Map.class).get("id")).longValue();
    }
    @Test void realSessionLoginNeedsCsrfAndRotatesSession()throws Exception{
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"teacher\",\"password\":\"Teacher@12345\"}")).andExpect(status().isForbidden());
        MvcResult csrfResult=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Map<?,?> token=json.readValue(csrfResult.getResponse().getContentAsString(),Map.class);MockHttpSession session=(MockHttpSession)csrfResult.getRequest().getSession();String oldId=session.getId();
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").toString(),token.get("token").toString()).contentType("application/json").content("{\"username\":\"teacher\",\"password\":\"Teacher@12345\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertNotEquals(oldId,session.getId());mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("teacher"));
    }
    @Test void eachAchievementAndAttachmentEnforcesOwnership()throws Exception{
        long id=create("student","访问控制测试");
        mvc.perform(get("/api/achievements/"+id).with(user("student2"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/achievements/"+id).with(user("teacher"))).andExpect(status().isOk());
        mvc.perform(put("/api/achievements/"+id).with(user("teacher")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body("越权编辑")))).andExpect(status().isForbidden());
        MockMultipartFile pdf=new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4\nproof\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        var response=mvc.perform(multipart("/api/achievements/"+id+"/attachments").file(pdf).with(user("student")).with(csrf())).andExpect(status().isOk()).andReturn();
        long attachment=((Number)json.readValue(response.getResponse().getContentAsString(),Map.class).get("id")).longValue();
        mvc.perform(get("/api/attachments/"+attachment+"/download").with(user("student2"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/attachments/"+attachment+"/download").with(user("teacher"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(delete("/api/attachments/"+attachment).with(user("teacher")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/achievements/"+id+"/submit").with(user("student")).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/achievements/"+id+"/attachments").file(pdf).with(user("student")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/attachments/"+attachment).with(user("admin")).with(csrf())).andExpect(status().isConflict());
    }
    @Test void reviewCannotSelfApproveAndLocksApprovedContent()throws Exception{
        long own=create("admin","管理员自审测试");
        mvc.perform(post("/api/achievements/"+own+"/submit").with(user("admin")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/achievements/"+own+"/review").with(user("admin")).with(csrf()).contentType("application/json").content("{\"approved\":true,\"comment\":\"同意\"}")).andExpect(status().isBadRequest());
        long id=create("student","正常审核测试");
        mvc.perform(post("/api/achievements/"+id+"/submit").with(user("student")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/achievements/"+id+"/review").with(user("teacher")).with(csrf()).contentType("application/json").content("{\"approved\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/achievements/"+id+"/review").with(user("admin")).with(csrf()).contentType("application/json").content("{\"approved\":false,\"comment\":\"请补充信息\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/achievements/"+id+"/submit").with(user("student")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/achievements/"+id+"/review").with(user("admin")).with(csrf()).contentType("application/json").content("{\"approved\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(put("/api/achievements/"+id).with(user("admin")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body("不能直接改")))).andExpect(status().isConflict());
        mvc.perform(get("/api/achievements/"+id+"/reviews").with(user("student"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void statisticsCountOneSharedAchievementAndOnlyApproved()throws Exception{
        long approved=create("student","独立成果统计"),draft=create("student","草稿不纳入");
        var a=db.achievement(approved);a.achievedOn=java.time.LocalDate.of(2035,4,1);a.status="APPROVED";db.updateAchievement(a);
        var d=db.achievement(draft);d.achievedOn=java.time.LocalDate.of(2035,4,1);db.updateAchievement(d);
        Rows.Participant extra=new Rows.Participant();extra.achievementId=approved;extra.userId=db.userByName("teacher").id;extra.role="MEMBER";extra.sortOrder=3;db.insertParticipant(extra);
        mvc.perform(get("/api/stats?year=2035").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.participants").value(2)).andExpect(jsonPath("$.teachers").value(1)).andExpect(jsonPath("$.students").value(1));
        mvc.perform(get("/api/stats?year=2035").with(user("student2"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void fileNamesMagicBytesAndStoredPathsAreValidated()throws Exception{
        long id=create("student","附件校验测试");
        for(MockMultipartFile bad:List.of(new MockMultipartFile("file","../../escape.pdf","application/pdf","%PDF-x".getBytes()),new MockMultipartFile("file","fake.png","image/png","<script>alert(1)</script>".getBytes()),new MockMultipartFile("file","wrong.jpg","image/jpeg","%PDF-1.7".getBytes()))){
            mvc.perform(multipart("/api/achievements/"+id+"/attachments").file(bad).with(user("student")).with(csrf())).andExpect(status().isBadRequest());
        }
        assertThrows(ApiException.class,()->files.safePath("../secret.pdf"));assertThrows(ApiException.class,()->files.safePath("C:\\secret.pdf"));
        Path safe=files.safePath("12345678-1234-1234-1234-123456789abc.pdf");assertEquals(Path.of("./target/test-materials").toRealPath(),safe.getParent());
        mvc.perform(multipart("/api/achievements/"+id+"/attachments").file(new MockMultipartFile("file","large.pdf","application/pdf",new byte[20*1024*1024+1])).with(user("student")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void exportsAreRealXlsxAndZipAndRespectVisibility()throws Exception{
        long id=create("student","仅本人导出测试");var a=db.achievement(id);a.achievedOn=java.time.LocalDate.of(2036,1,1);a.status="APPROVED";db.updateAchievement(a);
        byte[] excel=mvc.perform(get("/api/exports/achievements.xlsx?year=2036").with(user("student"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(excel))){assertEquals(1,book.getSheetAt(0).getLastRowNum());assertEquals("仅本人导出测试",book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());}
        byte[] hidden=mvc.perform(get("/api/exports/achievements.xlsx?year=2036").with(user("student2"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(hidden))){assertEquals(0,book.getSheetAt(0).getLastRowNum());}
        byte[] zip=mvc.perform(get("/api/exports/materials.zip?year=2036").with(user("student"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Set<String> entries=new HashSet<>();try(ZipInputStream stream=new ZipInputStream(new ByteArrayInputStream(zip),StandardCharsets.UTF_8)){ZipEntry e;while((e=stream.getNextEntry())!=null)entries.add(e.getName());}assertTrue(entries.contains("成果清单.xlsx"));assertTrue(entries.contains("材料索引.csv"));
    }
    @Test void userAdministrationAndPasswordProtection()throws Exception{
        mvc.perform(get("/api/users").with(user("student"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").with(user("admin")).with(csrf()).contentType("application/json").content("{\"username\":\"newstudent\",\"password\":\"Student@12345\",\"name\":\"新同学\",\"role\":\"STUDENT\",\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(put("/api/auth/password").with(user("student")).with(csrf()).contentType("application/json").content("{\"oldPassword\":\"incorrect\",\"newPassword\":\"Changed@12345\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users?role=TEACHER").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
    }
}
