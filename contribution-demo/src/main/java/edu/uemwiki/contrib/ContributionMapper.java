package edu.uemwiki.contrib;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContributionMapper extends BaseMapper<Contribution> {
    @Select("SELECT * FROM contribution WHERE id=#{id}") Contribution find(long id);
}
