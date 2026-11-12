package cn.edu.achievement;

import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    final AppService app;final FileService files;final ExportService exports;final AuthenticationManager authentication;final SecurityContextRepository contextRepository;final HttpSessionCsrfTokenRepository csrfRepository;
    ApiController(AppService app,FileService files,ExportService exports,AuthenticationManager authentication,SecurityContextRepository contextRepository,HttpSessionCsrfTokenRepository csrfRepository){this.app=app;this.files=files;this.exports=exports;this.authentication=authentication;this.contextRepository=contextRepository;this.csrfRepository=csrfRepository;}
    @GetMapping("/auth/csrf") Object csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/auth/me") Object me(){return app.userView(app.actor());}
    @PostMapping("/auth/login") Object login(@RequestBody Map<String,Object> input,HttpServletRequest req,HttpServletResponse res){
        String username=AppService.text(input,"username",80,true),password=AppService.text(input,"password",72,true);
        var auth=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username,password));
        req.getSession(true);req.changeSessionId();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contextRepository.saveContext(context,req,res);csrfRepository.saveToken(null,req,res);return app.userView(app.actor());
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest req,HttpServletResponse res){var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();csrfRepository.saveToken(null,req,res);}
    @PutMapping("/auth/password") @ResponseStatus(HttpStatus.NO_CONTENT) void password(@RequestBody Map<String,Object> input,HttpServletRequest req,HttpServletResponse res){app.changePassword(input);logout(req,res);}
    @GetMapping("/users/options") Object options(){return app.options();}
    @GetMapping("/users") Object users(@RequestParam Map<String,String> query){return app.users(query);}
    @PostMapping("/users") Object addUser(@RequestBody Map<String,Object> input){return app.saveUser(null,input);}
    @PutMapping("/users/{id}") Object editUser(@PathVariable long id,@RequestBody Map<String,Object> input){return app.saveUser(id,input);}
    @GetMapping("/achievements") Object achievements(@RequestParam Map<String,String> q){return app.achievements(q);}
    @GetMapping("/achievements/{id}") Object achievement(@PathVariable long id){return app.achievement(id);}
    @PostMapping("/achievements") Object create(@RequestBody Map<String,Object> input){return app.saveAchievement(null,input);}
    @PutMapping("/achievements/{id}") Object update(@PathVariable long id,@RequestBody Map<String,Object> input){return app.saveAchievement(id,input);}
    @DeleteMapping("/achievements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable long id){files.deleteAchievement(id);}
    @PostMapping("/achievements/{id}/submit") Object submit(@PathVariable long id){return app.submit(id);}
    @PostMapping("/achievements/{id}/review") Object review(@PathVariable long id,@RequestBody Map<String,Object> input){return app.review(id,input);}
    @GetMapping("/achievements/{id}/reviews") Object reviews(@PathVariable long id){return app.reviews(id);}
    @GetMapping("/achievements/{id}/attachments") Object attachments(@PathVariable long id){return files.list(id);}
    @PostMapping("/achievements/{id}/attachments") Object upload(@PathVariable long id,@RequestParam("file") MultipartFile file)throws IOException{return files.upload(id,file);}
    @GetMapping("/attachments/{id}/download") ResponseEntity<byte[]> download(@PathVariable long id)throws IOException{var a=files.readable(id);return binary(files.bytes(a),a.contentType,a.originalName);}
    @DeleteMapping("/attachments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteAttachment(@PathVariable long id){files.delete(id);}
    @GetMapping("/indicators") Object indicators(){return app.indicators();}
    @PostMapping("/indicators") Object addIndicator(@RequestBody Map<String,Object> input){return app.saveIndicator(null,input);}
    @PutMapping("/indicators/{id}") Object editIndicator(@PathVariable long id,@RequestBody Map<String,Object> input){return app.saveIndicator(id,input);}
    @GetMapping("/stats") Object stats(@RequestParam Map<String,String> query){return app.stats(query);}
    @GetMapping("/exports/achievements.xlsx") ResponseEntity<byte[]> xlsx(@RequestParam Map<String,String> query)throws IOException{return binary(exports.xlsx(query),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","成果清单.xlsx");}
    @GetMapping("/exports/materials.zip") ResponseEntity<byte[]> zip(@RequestParam Map<String,String> query)throws IOException{return binary(exports.zip(query),"application/zip","支撑材料.zip");}
    private ResponseEntity<byte[]> binary(byte[] data,String mime,String name){return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"no-store").body(data);}
}
