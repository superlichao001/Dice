package com.bihell.dice.system.service.impl;

import com.bihell.dice.framework.common.exception.TipException;
import com.bihell.dice.system.entity.AuthContent;
import com.bihell.dice.system.mapper.AuthContentMapper;
import com.bihell.dice.system.service.AuthContentService;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * @author haseochen
 */
@Slf4j
@Service
@Transactional(rollbackFor = Throwable.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Deprecated
public class AuthContentServiceImpl implements AuthContentService {

    private final AuthContentMapper authContentMapper;

    @Override
    public AuthContent save(AuthContent authContent) {

        if (authContent != null && authContent.getContentType() != null && authContent.getContentName() != null && authContent.getContentValue() != null) {
            Map<String, Object> param = ImmutableMap.of("project_type", authContent.getProjectType(), "content_type", authContent.getContentType(), "content_name", authContent.getContentName());
            if (authContentMapper.selectByMap(param).size() > 0) {
                throw new TipException("内容重复！");
            }
            authContent.insert();

        } else {
            throw new TipException("参数缺失！");
        }
        return authContent;
    }

    @Override
    public AuthContent update(AuthContent authContent) {
        if (authContent != null && authContent.getContentType() != null && authContent.getContentName() != null && authContent.getContentValue() != null) {
            Map<String, Object> param = ImmutableMap.of("project_type", authContent.getProjectType(), "content_type", authContent.getContentType(), "content_name", authContent.getContentName());
            if (authContentMapper.selectByMap(param).size() > 0) {
                throw new TipException("内容重复！");
            }
            authContent.updateById();

        } else {
            throw new TipException("参数缺失！");
        }
        return authContent;
    }
}
