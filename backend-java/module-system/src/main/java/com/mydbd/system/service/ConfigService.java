package com.mydbd.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydbd.common.api.PageData;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.system.entity.SysConfig;
import com.mydbd.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数服务：key-value 配置，带内存缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    private final Map<String, String> valueCache = new ConcurrentHashMap<>();

    public PageData<SysConfig> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(keyword), SysConfig::getConfigName, keyword)
                .or()
                .like(StringUtils.hasText(keyword), SysConfig::getConfigKey, keyword)
                .orderByDesc(SysConfig::getCreateDate);
        Page<SysConfig> result = configMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Transactional
    public void add(SysConfig config) {
        Long exists = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, config.getConfigKey()));
        if (exists > 0) throw new BizException(ErrorCode.BAD_REQUEST, "参数键已存在");
        validate(config);
        if (config.getIsSystem() == null) config.setIsSystem(0);
        configMapper.insert(config);
        valueCache.put(config.getConfigKey(), config.getConfigValue());
    }

    @Transactional
    public void update(SysConfig config) {
        SysConfig db = configMapper.selectById(config.getId());
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        validate(config);
        configMapper.updateById(config);
        valueCache.put(db.getConfigKey(), config.getConfigValue());
    }

    @Transactional
    public void delete(Long id) {
        SysConfig db = configMapper.selectById(id);
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (db.getIsSystem() != null && db.getIsSystem() == 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内置参数不可删除");
        }
        configMapper.deleteById(id);
        valueCache.remove(db.getConfigKey());
    }

    public String getValue(String key, String defaultValue) {
        return valueCache.computeIfAbsent(key, k -> {
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, k));
            return c != null ? c.getConfigValue() : null;
        }) != null ? valueCache.get(key) : defaultValue;
    }

    public Integer getInt(String key, Integer defaultValue) {
        String v = getValue(key, null);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return defaultValue; }
    }

    public Boolean getBool(String key, Boolean defaultValue) {
        String v = getValue(key, null);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }

    public <T> T getJson(String key, Class<T> clazz, T defaultValue) {
        String v = getValue(key, null);
        if (v == null) return defaultValue;
        try { return objectMapper.readValue(v, clazz); } catch (Exception e) {
            log.warn("config json parse failed key={}", key, e);
            return defaultValue;
        }
    }

    private void validate(SysConfig config) {
        String type = config.getValueType();
        String val = config.getConfigValue();
        if (!StringUtils.hasText(val)) return;
        switch (type) {
            case "INT":
                try { Integer.parseInt(val.trim()); }
                catch (Exception e) { throw new BizException(ErrorCode.BAD_REQUEST, "参数值必须为整数"); }
                break;
            case "BOOL":
                if (!"true".equalsIgnoreCase(val.trim()) && !"false".equalsIgnoreCase(val.trim())) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "参数值必须为 true 或 false");
                }
                break;
            case "JSON":
                try { objectMapper.readTree(val); }
                catch (Exception e) { throw new BizException(ErrorCode.BAD_REQUEST, "参数值不是合法 JSON"); }
                break;
            default: break;
        }
    }
}
