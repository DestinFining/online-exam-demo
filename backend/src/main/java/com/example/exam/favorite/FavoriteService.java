package com.example.exam.favorite;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {
    private final FavoriteMapper mapper; private final QuestionMapper questionMapper; private final SysUserMapper userMapper;
    public FavoriteService(FavoriteMapper mapper, QuestionMapper questionMapper, SysUserMapper userMapper){this.mapper=mapper;this.questionMapper=questionMapper;this.userMapper=userMapper;}
    public Page<FavoriteView> page(int page,int size,String username){
        SysUser user=requireUser(username);
        Page<Favorite> source=mapper.selectPage(new Page<>(Math.max(1,page),Math.min(Math.max(1,size),100)),new QueryWrapper<Favorite>().eq("user_id",user.getId()).orderByDesc("created_at"));
        Page<FavoriteView> result=new Page<>(source.getCurrent(),source.getSize(),source.getTotal());
        result.setRecords(source.getRecords().stream().map(f->{Question q=questionMapper.selectById(f.getQuestionId());return new FavoriteView(f.getId(),f.getQuestionId(),q==null?null:q.getContent(),q==null?null:q.getType(),q==null?null:q.getDifficulty(),f.getCreatedAt());}).toList());
        return result;
    }
    @Transactional public void add(Long questionId,String username){
        SysUser user=requireUser(username); requireQuestion(questionId);
        long exists=mapper.selectCount(new QueryWrapper<Favorite>().eq("user_id",user.getId()).eq("question_id",questionId));
        if(exists>0)return; Favorite f=new Favorite();f.setUserId(user.getId());f.setQuestionId(questionId);mapper.insert(f);
    }
    @Transactional public void remove(Long questionId,String username){
        SysUser user=requireUser(username); mapper.delete(new QueryWrapper<Favorite>().eq("user_id",user.getId()).eq("question_id",questionId));
    }
    public boolean exists(Long questionId,String username){SysUser u=requireUser(username);return mapper.selectCount(new QueryWrapper<Favorite>().eq("user_id",u.getId()).eq("question_id",questionId))>0;}
    private void requireQuestion(Long id){if(questionMapper.selectById(id)==null)throw new BusinessException(404,"题目不存在");}
    private SysUser requireUser(String username){SysUser u=userMapper.selectOne(new QueryWrapper<SysUser>().eq("username",username).last("LIMIT 1"));if(u==null)throw new BusinessException(401,"当前用户不存在");return u;}
}
