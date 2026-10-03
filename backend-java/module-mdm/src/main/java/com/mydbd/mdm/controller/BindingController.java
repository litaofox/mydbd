package com.mydbd.mdm.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.mdm.dto.BindRequests.BindDriverRequest;
import com.mydbd.mdm.dto.BindRequests.BindTerminalRequest;
import com.mydbd.mdm.dto.BindRequests.UnbindDriverRequest;
import com.mydbd.mdm.dto.BindRequests.UnbindTerminalRequest;
import com.mydbd.mdm.service.BindingService;
import com.mydbd.mdm.vo.VehicleBindingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 车辆绑定关系接口（车-终端、车-司机）
 */
@RestController
@RequestMapping("/api/mdm/vehicles/{vehicleId}")
@RequiredArgsConstructor
@RequiresPerm("mdm:vehicle:view")
public class BindingController {

    private final BindingService bindingService;

    @GetMapping("/bindings")
    public Result<VehicleBindingVO> bindings(@PathVariable Long vehicleId) {
        return Result.ok(bindingService.getBinding(vehicleId));
    }

    @PostMapping("/bind-terminal")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> bindTerminal(@PathVariable Long vehicleId,
                                     @Valid @RequestBody BindTerminalRequest request) {
        bindingService.bindTerminal(vehicleId, request);
        return Result.ok();
    }

    @PostMapping("/unbind-terminal")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> unbindTerminal(@PathVariable Long vehicleId,
                                       @RequestBody(required = false) UnbindTerminalRequest request) {
        bindingService.unbindTerminal(vehicleId,
                request == null ? new UnbindTerminalRequest(null) : request);
        return Result.ok();
    }

    @PostMapping("/bind-driver")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> bindDriver(@PathVariable Long vehicleId,
                                   @Valid @RequestBody BindDriverRequest request) {
        bindingService.bindDriver(vehicleId, request);
        return Result.ok();
    }

    @PostMapping("/unbind-driver")
    @RequiresPerm("mdm:vehicle:edit")
    public Result<Void> unbindDriver(@PathVariable Long vehicleId,
                                     @Valid @RequestBody UnbindDriverRequest request) {
        bindingService.unbindDriver(vehicleId, request);
        return Result.ok();
    }
}
