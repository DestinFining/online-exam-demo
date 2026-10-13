package com.example.exam.note;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;import com.example.exam.common.exception.BusinessException;import com.example.exam.question.QuestionMapper;import com.example.exam.system.user.SysUser;import com.example.exam.system.user.SysUserMapper;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.LocalDateTime;import java.util.List;
@Service public class QuestionNoteService{
 private final QuestionNoteMapper mapper;private final QuestionMapper questionMapper;private final SysUserMapper userMapper;
 public QuestionNoteService(QuestionNoteMapper mapper,QuestionMapper questionMapper,SysUserMapper userMapper){this.mapper=mapper;this.questionMapper=questionMapper;this.userMapper=userMapper;}
 public List<QuestionNote> list(Long questionId,String username){SysUser u=requireUser(username);requireQuestion(questionId);return mapper.selectList(new QueryWrapper<QuestionNote>().eq("user_id",u.getId()).eq("question_id",questionId).orderByDesc("updated_at"));}
 @Transactional public QuestionNote create(Long questionId,NoteRequest request,String username){SysUser u=requireUser(username);requireQuestion(questionId);QuestionNote n=new QuestionNote();n.setUserId(u.getId());n.setQuestionId(questionId);n.setContent(request.content().trim());mapper.insert(n);return n;}
 @Transactional public QuestionNote update(Long id,NoteRequest request,String username){SysUser u=requireUser(username);QuestionNote n=requireOwned(id,u.getId());n.setContent(request.content().trim());n.setUpdatedAt(LocalDateTime.now());mapper.updateById(n);return n;}
 @Transactional public void delete(Long id,String username){SysUser u=requireUser(username);mapper.deleteById(requireOwned(id,u.getId()).getId());}
 private QuestionNote requireOwned(Long id,Long uid){QuestionNote n=mapper.selectById(id);if(n==null||!uid.equals(n.getUserId()))throw new BusinessException(404,"笔记不存在");return n;}
 private void requireQuestion(Long id){if(questionMapper.selectById(id)==null)throw new BusinessException(404,"题目不存在");}
 private SysUser requireUser(String username){SysUser u=userMapper.selectOne(new QueryWrapper<SysUser>().eq("username",username).last("LIMIT 1"));if(u==null)throw new BusinessException(401,"当前用户不存在");return u;}
}
