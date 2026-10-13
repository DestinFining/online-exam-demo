package com.example.exam.practice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOption;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestion;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;import java.util.*;

@Service public class PracticeService{
 private static final Set<String> TYPES=Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK","SHORT_ANSWER","PROGRAMMING");
 private static final Set<String> AUTO_GRADABLE_TYPES=Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK");
 private static final Set<String> SUBJECTIVE_TYPES=Set.of("SHORT_ANSWER","PROGRAMMING");
 private final QuestionMapper qMapper;private final QuestionOptionMapper optionMapper;private final SysUserMapper userMapper;private final PracticeRecordMapper recordMapper;private final WrongQuestionMapper wrongMapper;private final ObjectMapper objectMapper;
 public PracticeService(QuestionMapper qMapper,QuestionOptionMapper optionMapper,SysUserMapper userMapper,PracticeRecordMapper recordMapper,WrongQuestionMapper wrongMapper,ObjectMapper objectMapper){this.qMapper=qMapper;this.optionMapper=optionMapper;this.userMapper=userMapper;this.recordMapper=recordMapper;this.wrongMapper=wrongMapper;this.objectMapper=objectMapper;}
 public long countAvailable(Long subjectId, Long knowledgePointId, String rawType, String rawDifficulty){
   String type=normalizeFilter(rawType);
   String difficulty=normalizeFilter(rawDifficulty);
   if(type!=null&&!TYPES.contains(type))throw new BusinessException(400,"不支持的题型: "+type);
   if(difficulty!=null&&!Set.of("EASY","MEDIUM","HARD").contains(difficulty))throw new BusinessException(400,"不支持的难度: "+difficulty);
   // Subjective questions are intentionally excluded because this practice flow only supports auto-grading.
   if(type!=null&&SUBJECTIVE_TYPES.contains(type))return 0L;
   QueryWrapper<Question> query=new QueryWrapper<Question>()
       .eq(subjectId!=null,"subject_id",subjectId)
       .eq(knowledgePointId!=null,"knowledge_point_id",knowledgePointId)
       .eq(type!=null,"type",type)
       .notIn(type==null,"type",List.of("SHORT_ANSWER","PROGRAMMING"))
       .eq(difficulty!=null,"difficulty",difficulty)
       .eq("status",1);
   Long count=qMapper.selectCount(query);
   return count==null?0L:count;
 }
 public List<PracticeQuestionView> start(PracticeStartRequest req){
   // Empty strings are sent by clearable Vue selects. Treat them as "no filter".
   String type=normalizeFilter(req.type());
   String difficulty=normalizeFilter(req.difficulty());
   if(type!=null&&!TYPES.contains(type))throw new BusinessException(400,"不支持的题型: "+type);
   if(difficulty!=null&&!Set.of("EASY","MEDIUM","HARD").contains(difficulty))throw new BusinessException(400,"不支持的难度: "+difficulty);
   if("SHORT_ANSWER".equals(type)||"PROGRAMMING".equals(type))
     throw new BusinessException(409,"主观题（简答题、编程题）暂不支持自动判分，请在考试中作答并由教师阅卷");
   List<Question> all=qMapper.selectList(new QueryWrapper<Question>()
       .eq(req.subjectId()!=null,"subject_id",req.subjectId())
       .eq(req.knowledgePointId()!=null,"knowledge_point_id",req.knowledgePointId())
       .eq(type!=null,"type",type)
       .notIn(type==null,"type",List.of("SHORT_ANSWER","PROGRAMMING"))
       .eq(difficulty!=null,"difficulty",difficulty)
       .eq("status",1).last("LIMIT 5000"));
   if(all.isEmpty())throw new BusinessException(404,"没有符合条件的练习题，请清除筛选条件或先添加已启用的题目");
   Collections.shuffle(all);
   int n=Math.min(req.safeCount(),all.size());
   return all.subList(0,n).stream().map(this::toView).toList();
 }
 @Transactional public PracticeResult answer(PracticeAnswerRequest req,String username){
   SysUser user=requireUser(username);
   Question q=requireQuestion(req.questionId());
   // Free-text/programming answers cannot be objectively evaluated by this practice endpoint.
   // Do not silently mark them wrong or pollute the wrong-question book; route them to exam grading.
   if ("SHORT_ANSWER".equals(q.getType()) || "PROGRAMMING".equals(q.getType())) {
     throw new BusinessException(409,"主观题（简答题、编程题）暂不支持自动判分，请在考试中提交并由教师阅卷");
   }
   boolean correct=evaluate(q,req.answerContent());
   PracticeRecord r=new PracticeRecord();r.setUserId(user.getId());r.setQuestionId(q.getId());r.setSessionType(up(req.mode())==null?"RANDOM":up(req.mode()));r.setAnswerContent(toJson(req.answerContent()));r.setIsCorrect(correct?1:0);r.setCreatedAt(LocalDateTime.now());recordMapper.insert(r);updateWrong(user.getId(),q.getId(),correct);
   return new PracticeResult(q.getId(),correct,correct?"回答正确":"回答错误，可查看解析并进入错题本");
 }
 private PracticeQuestionView toView(Question q){List<PracticeQuestionView.Option> opts=optionMapper.selectList(new QueryWrapper<QuestionOption>().eq("question_id",q.getId()).orderByAsc("sort").orderByAsc("id")).stream().map(o->new PracticeQuestionView.Option(o.getId(),o.getOptionKey(),o.getOptionContent(),o.getSort())).toList();return new PracticeQuestionView(q.getId(),q.getType(),q.getContent(),q.getDifficulty(),q.getDefaultScore(),opts);}
 private boolean evaluate(Question q,Object actual){
   String type=q.getType();
   if(type!=null&&SUBJECTIVE_TYPES.contains(type))throw new BusinessException(409,"主观题（简答题、编程题）暂不支持自动判分，请在考试中提交并由教师阅卷");
   if(type==null||!AUTO_GRADABLE_TYPES.contains(type))throw new BusinessException(409,"当前题型暂不支持自动判分，未记录答题结果或错题记录");
   if(actual==null)return false;
   return switch(type){case "SINGLE_CHOICE","TRUE_FALSE","MULTIPLE_CHOICE"->choice(q,actual);case "FILL_BLANK"->fill(q,actual);default->throw new BusinessException(409,"当前题型暂不支持自动判分，未记录答题结果或错题记录");};
 }
 private boolean choice(Question q,Object actual){Set<String> expected=new TreeSet<>();optionMapper.selectList(new QueryWrapper<QuestionOption>().eq("question_id",q.getId()).eq("is_correct",1)).forEach(o->expected.add(o.getOptionKey().toUpperCase(Locale.ROOT)));Set<String> real=toSet(actual);return expected.equals(real);}
 private boolean fill(Question q,Object actual){try{JsonNode node=objectMapper.readTree(q.getAnswerConfig());JsonNode arr=node.get("answers");if(arr==null||!arr.isArray())return false;String val=String.valueOf(actual).trim();for(JsonNode a:arr)if(val.equalsIgnoreCase(a.asText().trim()))return true;return false;}catch(Exception e){return false;}}
 private Set<String> toSet(Object value){Set<String> s=new TreeSet<>();if(value instanceof Collection<?> c)c.forEach(v->{if(v!=null)s.add(String.valueOf(v).trim().toUpperCase(Locale.ROOT));});else{String x=String.valueOf(value).trim();if(x.startsWith("[")&&x.endsWith("]")){try{JsonNode n=objectMapper.readTree(x);if(n.isArray())n.forEach(v->s.add(v.asText().trim().toUpperCase(Locale.ROOT)));}catch(Exception ignored){}}else{for(String p:x.split(","))if(!p.isBlank())s.add(p.trim().toUpperCase(Locale.ROOT));}}return s;}
 private void updateWrong(Long uid,Long qid,boolean correct){WrongQuestion w=wrongMapper.selectOne(new QueryWrapper<WrongQuestion>().eq("user_id",uid).eq("question_id",qid).last("LIMIT 1"));LocalDateTime now=LocalDateTime.now();if(!correct){if(w==null){w=new WrongQuestion();w.setUserId(uid);w.setQuestionId(qid);w.setWrongCount(1);w.setRedoCount(0);w.setMasteryLevel(0);w.setLastWrongAt(now);wrongMapper.insert(w);}else{w.setWrongCount((w.getWrongCount()==null?0:w.getWrongCount())+1);w.setMasteryLevel(0);w.setLastWrongAt(now);wrongMapper.updateById(w);}}else if(w!=null){int redo=(w.getRedoCount()==null?0:w.getRedoCount())+1;w.setRedoCount(redo);w.setLastCorrectAt(now);w.setMasteryLevel(redo>=2?3:2);wrongMapper.updateById(w);}}
 private String toJson(Object v){try{return objectMapper.writeValueAsString(v);}catch(Exception e){return "null";}}
 private String up(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);} private String normalizeFilter(String s){String value=up(s);return value==null||value.isBlank()?null:value;} private Question requireQuestion(Long id){Question q=qMapper.selectById(id);if(q==null||!Integer.valueOf(1).equals(q.getStatus()))throw new BusinessException(404,"题目不存在");return q;} private SysUser requireUser(String u){SysUser x=userMapper.selectOne(new QueryWrapper<SysUser>().eq("username",u).last("LIMIT 1"));if(x==null)throw new BusinessException(401,"当前用户不存在");return x;}
}
