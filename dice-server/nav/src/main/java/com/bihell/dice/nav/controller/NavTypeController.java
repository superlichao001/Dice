package com.bihell.dice.nav.controller;

import com.bihell.dice.framework.util.LoginUtil;
import com.bihell.dice.nav.entity.NavType;
import com.bihell.dice.nav.param.NavDetailPageParam;
import com.bihell.dice.nav.param.NavTypePageParam;
import com.bihell.dice.nav.service.NavTypeService;
import com.bihell.dice.nav.vo.NavTypeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bihell.dice.framework.common.controller.BaseController;
import com.bihell.dice.framework.common.api.ApiResult;
import com.bihell.dice.framework.core.pagination.Paging;
import com.bihell.dice.framework.log.annotation.Module;
import com.bihell.dice.framework.log.annotation.OperationLog;
import com.bihell.dice.framework.log.enums.OperationLogType;
import com.bihell.dice.framework.core.validator.groups.Add;
import com.bihell.dice.framework.core.validator.groups.Update;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *  控制器
 *
 * @author tpxcer
 * @since 2021-06-04
 */
@Slf4j
@RestController
@RequestMapping("/v1/api/admin/nav/type")
public class NavTypeController extends BaseController {

    @Autowired
    private NavTypeService navDetailService;

    /**
     * 添加
     */
    @RequiresPermissions("nav:type:add")
    @PostMapping("/add")
    @OperationLog(name = "添加", type = OperationLogType.ADD)
    @ApiOperation(value = "添加", response = ApiResult.class)
    public ApiResult<Boolean> addNavType(@Validated(Add.class) @RequestBody NavType navType) throws Exception {
        navType.setCreator(LoginUtil.getUserId());
        boolean flag = navDetailService.saveNavType(navType);
        return ApiResult.result(flag);
    }

    /**
     * 修改
     */
    @RequiresPermissions("nav:type:update")
    @PostMapping("/update")
    @OperationLog(name = "修改", type = OperationLogType.UPDATE)
    @ApiOperation(value = "修改", response = ApiResult.class)
    public ApiResult<Boolean> updateNavType(@Validated(Update.class) @RequestBody NavType navType) throws Exception {
        navType.setModifier(LoginUtil.getUserId());
        boolean flag = navDetailService.updateNavType(navType);
        return ApiResult.result(flag);
    }

    /**
     * 删除
     */
    @RequiresPermissions("nav:type:delete")
    @PostMapping("/delete/{id}")
    @OperationLog(name = "删除", type = OperationLogType.DELETE)
    @ApiOperation(value = "删除", response = ApiResult.class)
    public ApiResult<Boolean> deleteNavType(@PathVariable("id") Long id) throws Exception {
        boolean flag = navDetailService.deleteNavType(id);
        return ApiResult.result(flag);
    }

    /**
     * 获取详情
     */
    @GetMapping("/info/{id}")
    @OperationLog(name = "详情", type = OperationLogType.INFO)
    @ApiOperation(value = "详情", response = NavType.class)
    public ApiResult<NavType> getNavType(@PathVariable("id") Long id) throws Exception {
        NavType navDetail = navDetailService.getById(id);
        return ApiResult.ok(navDetail);
    }

    /**
     * 分页列表
     */
    @PostMapping("/getPageList")
    @OperationLog(name = "分页列表", type = OperationLogType.PAGE)
    @ApiOperation(value = "分页列表", response = NavType.class)
    public ApiResult<Paging<NavType>> getNavTypePageList(@Validated @RequestBody NavTypePageParam navDetailPageParam) throws Exception {
        Paging<NavType> paging = navDetailService.getNavTypePageList(navDetailPageParam);
        return ApiResult.ok(paging);
    }

    /**
     * 获取导航类型树形列表
     */
    @GetMapping("/getNavTypeTree")
    @OperationLog(name = "获取导航类型树形列表", type = OperationLogType.OTHER_QUERY)
    @ApiOperation(value = "获取导航类型树形列表", response = NavTypeVo.class)
    public ApiResult<List<NavTypeVo>> getDepartmentTree(@Validated NavTypePageParam navTypePageParam) throws Exception {
        List<NavTypeVo> treeVos = navDetailService.getNavTypeTree(navTypePageParam);
        return ApiResult.ok(treeVos);
    }
}

