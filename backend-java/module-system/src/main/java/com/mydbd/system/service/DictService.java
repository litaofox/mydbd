package com.mydbd.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.system.entity.DictItem;
import com.mydbd.system.entity.DictType;
import com.mydbd.system.mapper.DictItemMapper;
import com.mydbd.system.mapper.DictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据字典服务：类型 + 项，带按 code 的内存缓存。
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    // key=dictCode, value=启用项列表
    private final Map<String, List<DictItem>> itemCache = new ConcurrentHashMap<>();

    public PageData<DictType> pageTypes(long page, long size, String keyword) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<DictType>()
                .like(StringUtils.hasText(keyword), DictType::getDictName, keyword)
                .or()
                .like(StringUtils.hasText(keyword), DictType::getDictCode, keyword)
                .orderByDesc(DictType::getCreateDate);
        Page<DictType> result = dictTypeMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Transactional
    public void addType(DictType type) {
        Long exists = dictTypeMapper.selectCount(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getDictCode, type.getDictCode()));
        if (exists > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字典编码已存在");
        }
        if (type.getStatus() == null) type.setStatus(1);
        dictTypeMapper.insert(type);
    }

    @Transactional
    public void updateType(DictType type) {
        DictType db = dictTypeMapper.selectById(type.getId());
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        dictTypeMapper.updateById(type);
        // 编码变更则清缓存
        if (!db.getDictCode().equals(type.getDictCode())) {
            itemCache.remove(db.getDictCode());
        }
        itemCache.remove(type.getDictCode());
    }

    @Transactional
    public void deleteType(Long id) {
        DictType db = dictTypeMapper.selectById(id);
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        db.setStatus(0);
        dictTypeMapper.updateById(db);
        itemCache.remove(db.getDictCode());
    }

    public List<DictItem> listItems(Long typeId) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, typeId)
                .orderByAsc(DictItem::getSort));
    }

    @Transactional
    public void addItem(DictItem item) {
        if (item.getStatus() == null) item.setStatus(1);
        if (item.getSort() == null) item.setSort(0);
        dictItemMapper.insert(item);
        evictByType(item.getDictTypeId());
    }

    @Transactional
    public void updateItem(DictItem item) {
        DictItem db = dictItemMapper.selectById(item.getId());
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        dictItemMapper.updateById(item);
        evictByType(item.getDictTypeId() != null ? item.getDictTypeId() : db.getDictTypeId());
    }

    @Transactional
    public void deleteItem(Long id) {
        DictItem db = dictItemMapper.selectById(id);
        if (db == null) throw new BizException(ErrorCode.NOT_FOUND);
        db.setStatus(0);
        dictItemMapper.updateById(db);
        evictByType(db.getDictTypeId());
    }

    /** 按字典编码查启用项（带缓存） */
    public List<DictItem> getItemsByCode(String dictCode) {
        return itemCache.computeIfAbsent(dictCode, code -> {
            DictType type = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictType>()
                    .eq(DictType::getDictCode, code).eq(DictType::getStatus, 1));
            if (type == null) return List.of();
            return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                    .eq(DictItem::getDictTypeId, type.getId())
                    .eq(DictItem::getStatus, 1)
                    .orderByAsc(DictItem::getSort));
        });
    }

    /** 全量字典 map（code -> 启用项），供前端一次性拉取 */
    public Map<String, List<DictItem>> getAllDictMap() {
        List<DictType> types = dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getStatus, 1));
        return types.stream().collect(Collectors.toMap(
                DictType::getDictCode,
                t -> getItemsByCode(t.getDictCode())));
    }

    private void evictByType(Long typeId) {
        if (typeId == null) return;
        DictType type = dictTypeMapper.selectById(typeId);
        if (type != null) {
            itemCache.remove(type.getDictCode());
        }
    }
}
