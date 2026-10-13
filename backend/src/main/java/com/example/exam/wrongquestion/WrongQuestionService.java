package com.example.exam.wrongquestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WrongQuestionService {
    private final WrongQuestionMapper mapper;
    private final SysUserMapper userMapper;

    public WrongQuestionService(WrongQuestionMapper mapper, SysUserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    public List<WrongQuestion> myList(String username) {
        SysUser user = requireUser(username);
        return mapper.selectList(new QueryWrapper<WrongQuestion>()
                .eq("user_id", user.getId())
                .orderByDesc("last_wrong_at"));
    }

    @Transactional
    public WrongQuestion markWrong(Long questionId, String username) {
        SysUser user = requireUser(username);
        WrongQuestion existing = mapper.selectOne(new QueryWrapper<WrongQuestion>()
                .eq("user_id", user.getId()).eq("question_id", questionId).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new WrongQuestion();
            existing.setUserId(user.getId());
            existing.setQuestionId(questionId);
            existing.setWrongCount(1);
            existing.setRedoCount(0);
            existing.setMasteryLevel(0);
            existing.setLastWrongAt(now);
            mapper.insert(existing);
        } else {
            existing.setWrongCount((existing.getWrongCount() == null ? 0 : existing.getWrongCount()) + 1);
            existing.setLastWrongAt(now);
            existing.setMasteryLevel(0);
            mapper.updateById(existing);
        }
        return existing;
    }

    @Transactional
    public WrongQuestion markCorrect(Long questionId, String username) {
        SysUser user = requireUser(username);
        WrongQuestion existing = mapper.selectOne(new QueryWrapper<WrongQuestion>()
                .eq("user_id", user.getId()).eq("question_id", questionId).last("LIMIT 1"));
        if (existing == null) return null;
        int redo = (existing.getRedoCount() == null ? 0 : existing.getRedoCount()) + 1;
        existing.setRedoCount(redo);
        existing.setLastCorrectAt(LocalDateTime.now());
        existing.setMasteryLevel(redo >= 2 ? 3 : 2);
        mapper.updateById(existing);
        return existing;
    }

    private SysUser requireUser(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) throw new BusinessException(401, "当前用户不存在");
        return user;
    }
}
