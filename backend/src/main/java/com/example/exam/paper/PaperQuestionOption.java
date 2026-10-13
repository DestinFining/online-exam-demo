package com.example.exam.paper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("paper_question_option")
public class PaperQuestionOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperQuestionId;
    private String optionKey;
    private String optionContent;
    private Integer sort;
    private Integer isCorrect;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaperQuestionId() { return paperQuestionId; }
    public void setPaperQuestionId(Long paperQuestionId) { this.paperQuestionId = paperQuestionId; }
    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public String getOptionContent() { return optionContent; }
    public void setOptionContent(String optionContent) { this.optionContent = optionContent; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
}
