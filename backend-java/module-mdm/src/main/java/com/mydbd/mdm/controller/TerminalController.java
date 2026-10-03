package com.mydbd.mdm.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.mdm.dto.TerminalSaveRequest;
import com.mydbd.mdm.entity.Terminal;
import com.mydbd.mdm.service.TerminalService;
import com.mydbd.mdm.vo.OptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 终端档案接口
 */
@RestController
@RequestMapping("/api/mdm/terminals")
@RequiredArgsConstructor
@RequiresPerm("mdm:terminal:view")
public class TerminalController {

    private final TerminalService terminalService;

    @GetMapping
    public Result<PageData<Terminal>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String protocolType,
            @RequestParam(required = false) String equipmentType) {
        return Result.ok(terminalService.page(page, size, keyword, status, protocolType, equipmentType));
    }

    @GetMapping("/options")
    public Result<List<OptionVO>> options() {
        return Result.ok(terminalService.options());
    }

    @GetMapping("/{id}")
    public Result<Terminal> detail(@PathVariable Long id) {
        return Result.ok(terminalService.detail(id));
    }

    @PostMapping
    @RequiresPerm("mdm:terminal:edit")
    public Result<Long> create(@Valid @RequestBody TerminalSaveRequest request) {
        return Result.ok(terminalService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPerm("mdm:terminal:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TerminalSaveRequest request) {
        terminalService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("mdm:terminal:edit")
    public Result<Void> delete(@PathVariable Long id) {
        terminalService.delete(id);
        return Result.ok();
    }
}
