package com.bihell.dice.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bihell.dice.system.entity.SysDepartment;
import com.bihell.dice.system.param.SysDepartmentPageParam;
import com.bihell.dice.system.vo.SysDepartmentQueryVo;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

/**
 * <pre>
 * 部门 Mapper 接口
 * </pre>
 *
 * @author geekidea
 * @since 2019-10-24
 */
@Repository
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    /**
     * 根据ID获取查询对象
     *
     * @param id
     * @return
     */
    SysDepartmentQueryVo getSysDepartmentById(Serializable id);

    /**
     * 获取分页对象
     *
     * @param page
     * @param sysDepartmentPageParam
     * @return
     */
    IPage<SysDepartmentQueryVo> getSysDepartmentPageList(@Param("page") Page page, @Param("param") SysDepartmentPageParam sysDepartmentPageParam);

}
