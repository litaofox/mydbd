package com.mydbd.mdm.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.mdm.dto.DriverSaveRequest;
import com.mydbd.mdm.entity.Driver;
import com.mydbd.mdm.service.DriverService;
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
 * 驾驶员档案接口
 */
@RestController
@RequestMapping("/api/mdm/drivers")
@RequiredArgsConstructor
@RequiresPerm("mdm:driver:view")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    public Result<PageData<Driver>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(driverService.page(page, size, keyword, status));
    }

    @GetMapping("/options")
    public Result<List<OptionVO>> options() {
        return Result.ok(driverService.options());
    }

    @GetMapping("/{id}")
    public Result<Driver> detail(@PathVariable Long id) {
        return Result.ok(driverService.detail(id));
    }

    @PostMapping
    @RequiresPerm("mdm:driver:edit")
    public Result<Long> create(@Valid @RequestBody DriverSaveRequest request) {
        return Result.ok(driverService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPerm("mdm:driver:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DriverSaveRequest request) {
        driverService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("mdm:driver:edit")
    public Result<Void> delete(@PathVariable Long id) {
        driverService.delete(id);
        return Result.ok();
    }
}
