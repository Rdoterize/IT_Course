package cn.edu.achievement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.edu.achievement.Rows.*;

@Service
public class FileService {
    static final long MAX_SIZE=20L*1024*1024;
    final Path root; final Database db; final AppService app;
    public FileService(@Value("${app.storage-dir}") String directory,Database db,AppService app) throws IOException {
        Path directoryPath=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(directoryPath);
        this.root=directoryPath.toRealPath();this.db=db;this.app=app;
    }
    Path safePath(String storedName) {
        if(storedName==null||!storedName.matches("[a-f0-9-]{36}\\.(pdf|png|jpg)"))throw ApiException.bad("非法文件存储路径");
        Path path=root.resolve(storedName).normalize();
        if(!path.startsWith(root)||!path.getParent().equals(root)||Files.isSymbolicLink(path))throw ApiException.bad("非法文件存储路径");return path;
    }
    static String originalName(String name) {
        if(name==null||name.isBlank()||name.length()>240||name.contains("/")||name.contains("\\")||name.contains("..")||name.chars().anyMatch(c->c<32||c==127)||name.contains(":"))throw ApiException.bad("文件名无效");return name;
    }
    public Map<String,Object> view(Attachment a) {return AppService.map("id",a.id,"originalName",a.originalName,"contentType",a.contentType,"size",a.size,"createdAt",a.createdAt);}
    public List<Map<String,Object>> list(long id) {app.require(id,app.actor());return db.attachments(id).stream().map(this::view).toList();}
    @Transactional public Map<String,Object> upload(long achievementId,MultipartFile file) throws IOException {
        app.editable(achievementId,app.actor());if(file.isEmpty()||file.getSize()>MAX_SIZE)throw ApiException.bad("文件不能为空且不能超过20MB");
        String name=originalName(file.getOriginalFilename());byte[] content=file.getBytes();String ext,mime;
        if(content.length>=5&&new String(content,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")){ext="pdf";mime="application/pdf";}
        else if(content.length>=8&&Arrays.equals(Arrays.copyOf(content,8),new byte[]{(byte)137,80,78,71,13,10,26,10})){ext="png";mime="image/png";}
        else if(content.length>=3&&(content[0]&255)==255&&(content[1]&255)==216&&(content[2]&255)==255){ext="jpg";mime="image/jpeg";}
        else throw ApiException.bad("只允许内容有效的PDF、PNG、JPEG文件");
        String suffix=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(!(suffix.equals(ext)||ext.equals("jpg")&&suffix.equals("jpeg")))throw ApiException.bad("文件扩展名与实际格式不一致");
        if(db.attachments(achievementId).size()>=50)throw ApiException.bad("每项成果最多50个附件");
        Attachment a=new Attachment();a.achievementId=achievementId;a.originalName=name;a.contentType=mime;a.size=(long)content.length;a.createdAt=LocalDateTime.now();a.storedName=UUID.randomUUID()+"."+ext;
        Path path=safePath(a.storedName);Files.write(path,content,StandardOpenOption.CREATE_NEW);
        try {db.insertAttachment(a);if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)removeQuietly(path);}});}
        catch(RuntimeException e){Files.deleteIfExists(path);throw e;}return view(a);
    }
    Attachment readable(long id) {Attachment a=db.attachment(id);if(a==null)throw ApiException.missing();app.require(a.achievementId,app.actor());return a;}
    byte[] bytes(Attachment a) throws IOException {Path p=safePath(a.storedName);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new ApiException(404,"材料文件不存在，请联系管理员");return Files.readAllBytes(p);}
    @Transactional public void delete(long id) {Attachment a=readable(id);app.editable(a.achievementId,app.actor());Path p=safePath(a.storedName);db.deleteAttachment(id);cleanupAfterCommit(p);}
    @Transactional public void deleteAchievement(long id) {app.editable(id,app.actor());List<Path> paths=db.attachments(id).stream().map(a->safePath(a.storedName)).toList();app.deleteAchievement(id);paths.forEach(this::cleanupAfterCommit);}
    void cleanupAfterCommit(Path p) {if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){removeQuietly(p);}});else removeQuietly(p);}
    private static void removeQuietly(Path p) {try{Files.deleteIfExists(p);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(FileService.class).warn("Unable to clean private file {}",p.getFileName());}}
}
