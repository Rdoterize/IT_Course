package cn.edu.achievement;

import org.apache.ibatis.annotations.*;
import java.util.List;
import static cn.edu.achievement.Rows.*;

@Mapper
public interface Database {
    @Select("SELECT * FROM app_user ORDER BY id") List<User> users();
    @Select("SELECT * FROM app_user WHERE id=#{id}") User user(long id);
    @Select("SELECT * FROM app_user WHERE username=#{username}") User userByName(String username);
    @Insert("INSERT INTO app_user(username,password_hash,name,role,number,cohort,department,enabled) VALUES(#{username},#{passwordHash},#{name},#{role},#{number},#{cohort},#{department},#{enabled})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insertUser(User user);
    @Update("UPDATE app_user SET username=#{username},password_hash=#{passwordHash},name=#{name},role=#{role},number=#{number},cohort=#{cohort},department=#{department},enabled=#{enabled} WHERE id=#{id}") void updateUser(User user);
    @Select("SELECT * FROM achievement ORDER BY achieved_on DESC,id DESC") List<Achievement> achievements();
    @Select("SELECT * FROM achievement WHERE id=#{id}") Achievement achievement(long id);
    @Select("SELECT * FROM achievement WHERE id=#{id} FOR UPDATE") Achievement lockAchievement(long id);
    @Insert("INSERT INTO achievement(title,type,level,achieved_on,description,details_json,status,owner_id,created_at,updated_at) VALUES(#{title},#{type},#{level},#{achievedOn},#{description},#{detailsJson},#{status},#{ownerId},#{createdAt},#{updatedAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insertAchievement(Achievement a);
    @Update("UPDATE achievement SET title=#{title},type=#{type},level=#{level},achieved_on=#{achievedOn},description=#{description},details_json=#{detailsJson},status=#{status},updated_at=#{updatedAt} WHERE id=#{id}") void updateAchievement(Achievement a);
    @Delete("DELETE FROM achievement WHERE id=#{id}") void deleteAchievement(long id);
    @Select("SELECT * FROM participant WHERE achievement_id=#{id} ORDER BY sort_order,user_id") List<Participant> participants(long id);
    @Insert("INSERT INTO participant(achievement_id,user_id,role,sort_order) VALUES(#{achievementId},#{userId},#{role},#{sortOrder})") void insertParticipant(Participant p);
    @Delete("DELETE FROM participant WHERE achievement_id=#{id}") void deleteParticipants(long id);
    @Select("SELECT indicator_id FROM achievement_indicator WHERE achievement_id=#{id} ORDER BY indicator_id") List<Long> achievementIndicators(long id);
    @Insert("INSERT INTO achievement_indicator(achievement_id,indicator_id) VALUES(#{achievementId},#{indicatorId})") void linkIndicator(@Param("achievementId") long achievementId,@Param("indicatorId") long indicatorId);
    @Delete("DELETE FROM achievement_indicator WHERE achievement_id=#{id}") void unlinkIndicators(long id);
    @Select("SELECT * FROM indicator ORDER BY code,id") List<Indicator> indicators();
    @Select("SELECT * FROM indicator WHERE id=#{id}") Indicator indicator(long id);
    @Insert("INSERT INTO indicator(code,name,description) VALUES(#{code},#{name},#{description})") @Options(useGeneratedKeys=true,keyProperty="id") void insertIndicator(Indicator i);
    @Update("UPDATE indicator SET code=#{code},name=#{name},description=#{description} WHERE id=#{id}") void updateIndicator(Indicator i);
    @Select("SELECT * FROM review_record WHERE achievement_id=#{id} ORDER BY created_at DESC,id DESC") List<Review> reviews(long id);
    @Insert("INSERT INTO review_record(achievement_id,reviewer_id,approved,comment,created_at) VALUES(#{achievementId},#{reviewerId},#{approved},#{comment},#{createdAt})") @Options(useGeneratedKeys=true,keyProperty="id") void insertReview(Review r);
    @Select("SELECT * FROM attachment WHERE achievement_id=#{id} ORDER BY id") List<Attachment> attachments(long id);
    @Select("SELECT * FROM attachment WHERE id=#{id}") Attachment attachment(long id);
    @Insert("INSERT INTO attachment(achievement_id,original_name,stored_name,content_type,size,created_at) VALUES(#{achievementId},#{originalName},#{storedName},#{contentType},#{size},#{createdAt})") @Options(useGeneratedKeys=true,keyProperty="id") void insertAttachment(Attachment a);
    @Delete("DELETE FROM attachment WHERE id=#{id}") void deleteAttachment(long id);
}
