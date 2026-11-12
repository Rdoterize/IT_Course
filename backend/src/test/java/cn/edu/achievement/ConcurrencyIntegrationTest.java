package cn.edu.achievement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.storage-dir=./target/test-materials","app.seed-demo=true"})
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {
    @Autowired AppService app; @Autowired Database db; @Autowired PlatformTransactionManager transactions;
    <T> T as(String username,Supplier<T> action){var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username,"",List.of()));SecurityContextHolder.setContext(context);try{return action.get();}finally{SecurityContextHolder.clearContext();}}
    @Test void competingReviewsProduceOnlyOneDecision()throws Exception{
        long id=((Number)as("student",()->app.saveAchievement(null,AppService.map("title","并发审核测试","type","PAPER","level","OTHER","achievedOn","2026-04-01","description","","details",Map.of(),"participants",List.of(),"indicatorIds",List.of()))).get("id")).longValue();
        as("student",()->app.submit(id));
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2);List<Future<Integer>> calls=new ArrayList<>();
        try{
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                db.lockAchievement(id);
                for(boolean approved:List.of(true,false))calls.add(executor.submit(()->{ready.countDown();return as("admin",()->{try{app.review(id,Map.of("approved",approved,"comment","并发决定"));return 200;}catch(ApiException e){return e.status;}});}));
                try{assertTrue(ready.await(5,TimeUnit.SECONDS));Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            });
            List<Integer> outcomes=new ArrayList<>();for(Future<Integer> call:calls)outcomes.add(call.get(10,TimeUnit.SECONDS));Collections.sort(outcomes);assertEquals(List.of(200,409),outcomes);assertEquals(1,db.reviews(id).size());
        }finally{executor.shutdownNow();new TransactionTemplate(transactions).executeWithoutResult(tx->db.deleteAchievement(id));}
    }
    @Test void submissionAndAttachmentMutationShareTheSameLock()throws Exception{
        long id=((Number)as("student",()->app.saveAchievement(null,AppService.map("title","提交锁测试","type","PAPER","level","OTHER","achievedOn","2026-04-02","description","","details",Map.of(),"participants",List.of(),"indicatorIds",List.of()))).get("id")).longValue();
        ExecutorService executor=Executors.newSingleThreadExecutor();List<Future<String>> calls=new ArrayList<>();CountDownLatch ready=new CountDownLatch(1);
        try{
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                Rows.Achievement row=db.lockAchievement(id);
                calls.add(executor.submit(()->{ready.countDown();return as("student",()->{try{app.submit(id);return "submitted";}catch(ApiException e){return "blocked:"+e.status;}});}));
                try{assertTrue(ready.await(5,TimeUnit.SECONDS));Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                assertFalse(calls.getFirst().isDone(),"Submission must wait while another mutation owns the achievement row lock");
                row.status="PENDING";db.updateAchievement(row);
            });
            assertEquals("blocked:409",calls.getFirst().get(10,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();new TransactionTemplate(transactions).executeWithoutResult(tx->db.deleteAchievement(id));}
    }
}
