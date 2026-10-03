package com.mydbd.mdm.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.mdm.dto.VehicleSaveRequest;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.service.VehicleService;
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
 * 车辆档案接口
 */
@RestController
@RequestMapping("/api/mdm/vehicles")
@RequiredArgsConstructor
@RequiresPerm("mdm:vehicle:view")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public Result<PageData<Vehicle>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String plateColor,
            @RequestParam(required = false) Integer operationType) {
        return Result.ok(vehicleService.page(page, size, keyword, deptId, plateColor, operationType));
    }

    /** 车辆下拉供监控/风险等多页面使用，登录即可；数据范围在服务层裁剪 */
    @GetMapping("/options")
    @RequiresPerm(value = {})
    public Result<List<OptionVO>> options() {
        return Result.ok(vehicleService.options());
    }

    @GetMapping("/{id}")
    public Result<Vehicle> detail(@PathVariable Long id) {
        return Result.ok(vehicleService.detail(id));
    }

    @PostMapping
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Long> create(@Valid @RequestBody VehicleSaveRequest request) {
        return Result.ok(vehicleService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VehicleSaveRequest request) {
        vehicleService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return Result.ok();
    }
}
