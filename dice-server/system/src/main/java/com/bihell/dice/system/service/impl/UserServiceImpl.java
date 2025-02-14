package com.bihell.dice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bihell.dice.config.properties.DiceProperties;
import com.bihell.dice.framework.common.exception.BusinessException;
import com.bihell.dice.framework.common.exception.TipException;
import com.bihell.dice.framework.core.pagination.PageInfo;
import com.bihell.dice.framework.core.pagination.Paging;
import com.bihell.dice.framework.shiro.util.JwtTokenUtil;
import com.bihell.dice.framework.shiro.util.SaltUtil;
import com.bihell.dice.framework.shiro.vo.LoginSysUserVo;
import com.bihell.dice.framework.shiro.vo.RoleInfoVO;
import com.bihell.dice.framework.util.LoginUtil;
import com.bihell.dice.framework.util.PasswordUtil;
import com.bihell.dice.framework.util.PhoneUtil;
import com.bihell.dice.system.entity.*;
import com.bihell.dice.system.enums.FrameEnum;
import com.bihell.dice.system.enums.KeepaliveEnum;
import com.bihell.dice.system.enums.LinkExternalEnum;
import com.bihell.dice.system.enums.MenuLevelEnum;
import com.bihell.dice.system.mapper.SysRolePermissionMapper;
import com.bihell.dice.system.mapper.UserMapper;
import com.bihell.dice.system.param.UserPageParam;
import com.bihell.dice.system.service.*;
import com.bihell.dice.system.vo.*;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import com.bihell.dice.framework.common.service.impl.BaseServiceImpl;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service 层实现类
 *
 * @author bihell
 * @since 2017/7/12 21:24
 */
@Service("usersService")
@Slf4j
@Transactional(rollbackFor = Throwable.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserServiceImpl extends BaseServiceImpl<UserMapper, SysUser> implements UserService {

    private final UserMapper userMapper;
    private final AuthRelRoleUserService authRelRoleUserService;
    private final DiceProperties diceProperties;
    private final SysUserRoleService sysUserRoleService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    private SysDepartmentService sysDepartmentService;

    @Lazy
    @Autowired
    private SysRoleService sysRoleService;

    @Lazy
    @Autowired
    private SysRolePermissionService sysRolePermissionService;

    @Lazy
    @Autowired
    private SysPermissionService sysPermissionService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteSysUser(Long id) throws Exception {
        return super.removeById(id);
    }

    @Override
    public Paging<SysUserQueryVo> getUserPageList(UserPageParam userPageParam) {
        Page<SysUserQueryVo> page = new PageInfo<>(userPageParam, OrderItem.desc("create_time"));
        IPage<SysUserQueryVo> iPage = userMapper.getSysUserPageList(page, userPageParam);

        if (iPage != null && org.apache.commons.collections4.CollectionUtils.isNotEmpty(iPage.getRecords())) {
            // 手机号码脱敏处理
            iPage.getRecords().forEach(vo -> {
                vo.setPhone(PhoneUtil.desensitize(vo.getPhone()));
            });

            // 设置角色id
            iPage.getRecords().forEach(item -> {
                List<SysUserRole> sysUserRoleList = sysUserRoleService.list(Wrappers.lambdaQuery(SysUserRole.class).eq(SysUserRole::getUserId, item.getId()));
                item.setRoleIds(sysUserRoleList.stream().map(SysUserRole::getRoleId).collect(Collectors.toList()));
            });
        }
        return new Paging(iPage);
    }

    @Override
    public void assignRole(SysUser sysUser) {
        sysUser.deleteById();
        if (!CollectionUtils.isEmpty(sysUser.getRoles())) {
            List<AuthRelRoleUser> authRelRoleUserList = sysUser.getRoles().stream()
                    .filter(Objects::nonNull)
                    .map(i -> new AuthRelRoleUser().setUserId(sysUser.getId()).setRoleId(i))
                    .collect(Collectors.toList());
            authRelRoleUserService.saveBatch(authRelRoleUserList);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addUser(SysUserVo sysUserVo) throws Exception {
        SysUser sysUser = BeanUtil.copyProperties(sysUserVo, SysUser.class);

        // 校验用户名是否存在
        boolean isExists = sysUser.selectCount(new QueryWrapper<SysUser>().lambda().eq(SysUser::getUsername, sysUser.getUsername()).or().eq(SysUser::getEmail, sysUser.getEmail())) > 0;
        if (isExists) {
            throw new BusinessException("用户名或邮箱已存在");
        }

        sysUser.setId(null);

        // 生成盐值
        String salt = null;
        String password = sysUser.getPwd();
        // 如果密码为空，则设置默认密码
        if (StringUtils.isBlank(password)) {
            salt = diceProperties.getLoginInitSalt();
            password = diceProperties.getLoginInitPassword();
        } else {
            salt = SaltUtil.generateSalt();
        }
        // 密码加密
        sysUser.setSalt(salt);
        sysUser.setPwd(PasswordUtil.encrypt(password, salt));

        // 如果头像为空，则设置默认头像
        if (StringUtils.isBlank(sysUser.getAvatar())) {
            sysUser.setAvatar(diceProperties.getLoginInitHead());
        }

        sysUser.insert();

        // 删除用户与角色关联
        sysUserRoleService.remove(Wrappers.lambdaQuery(SysUserRole.class).eq(SysUserRole::getUserId, sysUser.getId()));

        // 新增用户与角色关联
        if (Objects.nonNull(sysUserVo.getRoleIds())) {
            List<SysUserRole> sysUserRoleList = sysUserVo.getRoleIds().stream().map(item -> {
                // 校验部门和角色 todo
                try {
                    checkDepartmentAndRole(sysUser.getDeptId(), item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                SysUserRole sysUserRole = new SysUserRole();
                sysUserRole.setUserId(sysUser.getId());
                sysUserRole.setRoleId(item);
                return sysUserRole;
            }).collect(Collectors.toList());
            sysUserRoleService.saveBatch(sysUserRoleList);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUser(SysUserVo sysUserVo) throws Exception {

        // 获取系统用户
        SysUser updateSysUser = getById(sysUserVo.getId());
        if (updateSysUser == null) {
            throw new BusinessException("修改的用户不存在");
        }

        // 删除用户与角色关联
        sysUserRoleService.remove(Wrappers.lambdaQuery(SysUserRole.class).eq(SysUserRole::getUserId, updateSysUser.getId()));

        // 新增用户与角色关联
        if (Objects.nonNull(sysUserVo.getRoleIds())) {
            List<SysUserRole> sysUserRoleList = sysUserVo.getRoleIds().stream().map(item -> {
                // 校验部门和角色 todo
                try {
                    checkDepartmentAndRole(sysUserVo.getDeptId(), item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                SysUserRole sysUserRole = new SysUserRole();
                sysUserRole.setUserId(sysUserVo.getId());
                sysUserRole.setRoleId(item);
                return sysUserRole;
            }).collect(Collectors.toList());
            sysUserRoleService.saveBatch(sysUserRoleList);
        }

        updateSysUser = BeanUtil.copyProperties(sysUserVo, SysUser.class);
        updateSysUser.setUpdateTime(new Date());

        return super.updateById(updateSysUser);
    }

    @Override
    public SysUser getUserSingle(Integer id) {
        LambdaQueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>().lambda()
                .select(SysUser.class, info -> !"password".equals(info.getProperty()))
                .eq(SysUser::getId, id);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 修改用户信息
     *
     * @param oldUsername 原用户名
     * @param newUsername 新用户名
     * @param email       邮箱
     * @return boolean
     */
    @Override
    public boolean resetUser(String oldUsername, String newUsername, String email) {
        SysUser sysUser = new SysUser().selectOne(new QueryWrapper<SysUser>().lambda().eq(SysUser::getUsername, oldUsername));
        if (null == sysUser) {
            throw new TipException("该用户名不存在");
        }

        sysUser.setUsername(newUsername);
        sysUser.setEmail(email);

        return sysUser.updateById();
    }

    @Override
    public void checkDepartmentAndRole(Long departmentId, Long roleId) throws Exception {
        // 校验部门是否存在并且可用
        boolean isEnableDepartment = sysDepartmentService.isEnableSysDepartment(departmentId);
        if (!isEnableDepartment) {
            throw new BusinessException("该部门不存在或已禁用");
        }
        // 校验角色是否存在并且可用
        boolean isEnableRole = sysRoleService.isEnableSysRole(roleId);
        if (!isEnableRole) {
            throw new BusinessException("该角色不存在或已禁用");
        }
    }

    @Override
    public List<RouteItemVO> getMenuList() throws Exception {
        // 重复代码待抽象 todo
        String token =  JwtTokenUtil.getToken();
        String tokenSha256 = DigestUtils.sha256Hex(token);
        LoginSysUserVo loginSysUserVo = (LoginSysUserVo) redisTemplate.opsForValue().get(tokenSha256);
        List<SysPermission> sysPermissions;

        if (loginSysUserVo.getRoles().stream().anyMatch(item -> item.getValue().equals("admin"))) {
            sysPermissions = sysPermissionService.list(Wrappers.lambdaQuery(SysPermission.class)
                    .in(SysPermission::getLevel, MenuLevelEnum.ONE.getCode(), MenuLevelEnum.TWO.getCode())
                    .orderByAsc(SysPermission::getSort)
            );
        } else {
            // 查询菜单
            List<Long> roleIdList = loginSysUserVo.getRoles().stream().map(RoleInfoVO::getId).collect(Collectors.toList());
            List<SysRolePermission> sysRoleMenuList = new SysRolePermission().selectList(
                    new QueryWrapper<SysRolePermission>().lambda().in(SysRolePermission::getRoleId,roleIdList));
            if (sysRoleMenuList.isEmpty()) {
                sysPermissions = Lists.newArrayList();
            } else {
                Set<Long> menuIds = sysRoleMenuList.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toSet());
                sysPermissions = sysPermissionService.list(Wrappers.lambdaQuery(SysPermission.class)
                        .in(SysPermission::getLevel, MenuLevelEnum.ONE.getCode(), MenuLevelEnum.TWO.getCode())
                        .in(SysPermission::getId,menuIds)
                        .orderByAsc(SysPermission::getSort)
                );
            }
        }

        List<RouteItemVO> routeItemVOList = sysPermissions.stream().filter(item -> item.getParentId() == null).map(item -> {
            RouteItemVO node = new RouteItemVO();
            node.setPath(item.getLevel().equals(MenuLevelEnum.ONE.getCode()) ? "/" + item.getRoutePath() : item.getRoutePath());

            node.setComponent(item.getLevel().equals(MenuLevelEnum.ONE.getCode()) && item.getParentId() == null ? "LAYOUT" : item.getComponent());

            node.setName(StrUtil.upperFirst(item.getRoutePath()));
            node.setMeta(new RouteMetoVO());

            RouteMetoVO routeMetoVO = new RouteMetoVO();
            routeMetoVO.setTitle(item.getName());
            routeMetoVO.setIcon(item.getIcon());
            if (item.getLevel().equals(MenuLevelEnum.TWO.getCode())) {
                routeMetoVO.setIgnoreKeepAlive(item.getKeepAlive().equals(KeepaliveEnum.YES.getCode()));
                if (item.getIsExt().equals(LinkExternalEnum.YES.getCode())) {
                    if (item.getFrame().equals(FrameEnum.YES.getCode())) {
                        routeMetoVO.setFrameSrc(item.getComponent());
                    }
                    if (item.getFrame().equals(FrameEnum.NO.getCode())) {
                        node.setPath(item.getComponent());
                    }
                }
            }
            node.setMeta(routeMetoVO);
            node.setChildren(getChildrenList(item, sysPermissions));
            return node;
        }).collect(Collectors.toList());
        return routeItemVOList;
    }

    @Override
    public List<String> getPermCode() throws Exception {
        return sysPermissionService.getPermissionCodesByUserId(LoginUtil.getUserId());
    }

    private List<RouteItemVO> getChildrenList(SysPermission root, List<SysPermission> list) {
        List<RouteItemVO> childrenList = list.stream().filter(item -> item.getParentId() != null && item.getParentId().equals(root.getId())).map(item -> {
            RouteItemVO node = new RouteItemVO();
            node.setPath(item.getLevel().equals(MenuLevelEnum.ONE.getCode()) ? "/" + item.getRoutePath() : item.getRoutePath());
            node.setComponent(item.getLevel().equals(MenuLevelEnum.ONE.getCode()) && item.getParentId() == null ? "LAYOUT" : item.getComponent());
            node.setName(StrUtil.upperFirst(item.getRoutePath()));
            node.setMeta(new RouteMetoVO());

            RouteMetoVO routeMetoVO = new RouteMetoVO();
            routeMetoVO.setTitle(item.getName());
            routeMetoVO.setIcon(item.getIcon());
            routeMetoVO.setHideMenu(item.getIsShow()==0);
            if (item.getLevel().equals(MenuLevelEnum.TWO.getCode())) {
                routeMetoVO.setIgnoreKeepAlive(!item.getKeepAlive().equals(KeepaliveEnum.YES.getCode()));
                if (item.getIsExt().equals(LinkExternalEnum.YES.getCode())) {
                    if (item.getFrame().equals(FrameEnum.YES.getCode())) {
                        routeMetoVO.setFrameSrc(item.getComponent());
                    }
                    if (item.getFrame().equals(FrameEnum.NO.getCode())) {
                        node.setPath(item.getComponent());
                    }
                }
            }
            node.setMeta(routeMetoVO);
            node.setChildren(getChildrenList(item, list));
            return node;
        }).collect(Collectors.toList());
        return childrenList;
    }
}
