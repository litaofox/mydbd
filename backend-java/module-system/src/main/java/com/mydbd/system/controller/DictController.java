package com.mydbd.system.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.system.entity.DictItem;
import com.mydbd.system.entity.DictType;
import com.mydbd.system.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据字典接口
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
@RequiresPerm("system:dict:view")
public class DictController {

    private final DictService dictService;

    // ---------- 字典类型 ----------
    @GetMapping("/types")
    public Result<PageData<DictType>> pageTypes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.pageTypes(page, size, keyword));
    }

    @PostMapping("/types")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "CREATE", objectType = "DICT_TYPE")
    public Result<Void> addType(@RequestBody DictType type) {
        dictService.addType(type);
        return Result.ok();
    }

    @PutMapping("/types/{id}")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "UPDATE", objectType = "DICT_TYPE", objectId = "#id")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody DictType type) {
        type.setId(id);
        dictService.updateType(type);
        return Result.ok();
    }

    @DeleteMapping("/types/{id}")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "DELETE", objectType = "DICT_TYPE", objectId = "#id")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    // ---------- 字典项 ----------
    @GetMapping("/types/{typeId}/items")
    public Result<List<DictItem>> listItems(@PathVariable Long typeId) {
        return Result.ok(dictService.listItems(typeId));
    }

    @PostMapping("/items")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "CREATE", objectType = "DICT_ITEM")
    public Result<Void> addItem(@RequestBody DictItem item) {
        dictService.addItem(item);
        return Result.ok();
    }

    @PutMapping("/items/{id}")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "UPDATE", objectType = "DICT_ITEM", objectId = "#id")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody DictItem item) {
        item.setId(id);
        dictService.updateItem(item);
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    @RequiresPerm("system:dict:edit")
    @AuditLog(module = "SYSTEM", action = "DELETE", objectType = "DICT_ITEM", objectId = "#id")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.ok();
    }

    // ---------- 对外查询 ----------
    @GetMapping("/items/{dictCode}")
    public Result<List<DictItem>> getItemsByCode(@PathVariable String dictCode) {
        return Result.ok(dictService.getItemsByCode(dictCode));
    }

    @GetMapping("/all")
    public Result<Map<String, List<DictItem>>> getAll() {
        return Result.ok(dictService.getAllDictMap());
    }
}
