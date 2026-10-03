package com.mydbd.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.mdm.dto.BindRequests.BindDriverRequest;
import com.mydbd.mdm.dto.BindRequests.BindTerminalRequest;
import com.mydbd.mdm.dto.BindRequests.UnbindDriverRequest;
import com.mydbd.mdm.dto.BindRequests.UnbindTerminalRequest;
import com.mydbd.mdm.entity.Driver;
import com.mydbd.mdm.entity.Terminal;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.entity.VehicleDriver;
import com.mydbd.mdm.entity.VehicleTerminal;
import com.mydbd.mdm.mapper.DriverMapper;
import com.mydbd.mdm.mapper.TerminalMapper;
import com.mydbd.mdm.mapper.VehicleDriverMapper;
import com.mydbd.mdm.mapper.MdmVehicleMapper;
import com.mydbd.mdm.mapper.VehicleTerminalMapper;
import com.mydbd.mdm.vo.VehicleBindingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆绑定关系服务：
 * 车↔终端一对一（支持同事务换绑）、一车一主班可多副班、一司机同时仅一条有效绑定。
 * 互斥以 Service 校验 + 数据库部分唯一索引双重保障。
 */
@Service
@RequiredArgsConstructor
public class BindingService {

    private final MdmVehicleMapper vehicleMapper;
    private final TerminalMapper terminalMapper;
    private final DriverMapper driverMapper;
    private final VehicleTerminalMapper vehicleTerminalMapper;
    private final VehicleDriverMapper vehicleDriverMapper;

    /** 车辆绑定视图：当前终端、在班司机、两类历史 */
    public VehicleBindingVO getBinding(Long vehicleId) {
        Vehicle vehicle = requireVehicle(vehicleId);
        List<VehicleTerminal> terminalHistory = vehicleTerminalMapper.listHistoryByVehicle(vehicleId);
        List<VehicleDriver> driverHistory = vehicleDriverMapper.listHistoryByVehicle(vehicleId);

        VehicleTerminal currentTerminal = terminalHistory.stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 1)
                .findFirst().orElse(null);
        List<VehicleDriver> activeDrivers = driverHistory.stream()
                .filter(d -> d.getStatus() != null && d.getStatus() == 1)
                .toList();
        return new VehicleBindingVO(vehicle.getId(), vehicle.getVehicleNo(),
                currentTerminal, activeDrivers, terminalHistory, driverHistory);
    }

    /**
     * 绑定终端；车辆已绑定其他终端时自动执行"换绑"（旧关系同事务解绑）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindTerminal(Long vehicleId, BindTerminalRequest req) {
        Vehicle vehicle = requireVehicle(vehicleId);
        Terminal terminal = terminalMapper.selectById(req.terminalId());
        if (terminal == null || terminal.getValidMark() == null || terminal.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "终端不存在");
        }
        if (terminal.getStatus() == null || terminal.getStatus() != 1) {
            throw new BizException(ErrorCode.CONFLICT, "终端当前不是正常状态，不能绑定");
        }

        // 终端是否已绑定到其他车辆
        VehicleTerminal terminalBinding = findActiveTerminalBinding(req.terminalId());
        if (terminalBinding != null && !terminalBinding.getVehicleId().equals(vehicleId)) {
            Vehicle other = vehicleMapper.selectById(terminalBinding.getVehicleId());
            String otherNo = other == null ? String.valueOf(terminalBinding.getVehicleId()) : other.getVehicleNo();
            throw new BizException(ErrorCode.CONFLICT, "该终端已绑定车辆 " + otherNo + "，请先解绑");
        }

        LocalDateTime now = LocalDateTime.now();
        // 本车当前终端：相同则报错，不同则换绑
        VehicleTerminal current = findActiveVehicleTerminal(vehicleId);
        if (current != null) {
            if (current.getTerminalId().equals(req.terminalId())) {
                throw new BizException(ErrorCode.CONFLICT, "该终端已绑定在本车上，无需重复绑定");
            }
            current.setStatus(0);
            current.setUnbindTime(now);
            current.setRemark(appendRemark(current.getRemark(), "换绑：旧终端卸除"));
            vehicleTerminalMapper.updateById(current);
        }

        VehicleTerminal bind = new VehicleTerminal();
        bind.setVehicleId(vehicleId);
        bind.setTerminalId(req.terminalId());
        bind.setBindType(req.bindType() == null ? 1 : req.bindType());
        bind.setBindTime(now);
        bind.setInstallTime(now);
        bind.setInstaller(req.installer());
        bind.setStatus(1);
        bind.setValidMark(1);
        bind.setRemark(req.remark());
        vehicleTerminalMapper.insert(bind);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindTerminal(Long vehicleId, UnbindTerminalRequest req) {
        requireVehicle(vehicleId);
        VehicleTerminal current = findActiveVehicleTerminal(vehicleId);
        if (current == null) {
            throw new BizException(ErrorCode.CONFLICT, "当前车辆没有绑定终端");
        }
        current.setStatus(0);
        current.setUnbindTime(LocalDateTime.now());
        current.setRemark(appendRemark(current.getRemark(), req.remark()));
        vehicleTerminalMapper.updateById(current);
    }

    /**
     * 绑定司机（主班/副班）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindDriver(Long vehicleId, BindDriverRequest req) {
        requireVehicle(vehicleId);
        Driver driver = driverMapper.selectById(req.driverId());
        if (driver == null || driver.getValidMark() == null || driver.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "驾驶员不存在");
        }
        if (driver.getStatus() == null || driver.getStatus() != 1) {
            throw new BizException(ErrorCode.CONFLICT, "驾驶员当前不是在岗状态，不能绑定");
        }

        // 司机是否已绑定其他车辆
        VehicleDriver driverBinding = findActiveDriverBinding(req.driverId());
        if (driverBinding != null && !driverBinding.getVehicleId().equals(vehicleId)) {
            Vehicle other = vehicleMapper.selectById(driverBinding.getVehicleId());
            String otherNo = other == null ? String.valueOf(driverBinding.getVehicleId()) : other.getVehicleNo();
            throw new BizException(ErrorCode.CONFLICT, "该司机已绑定车辆 " + otherNo + "，请先解绑");
        }
        // 已绑定在本车
        if (driverBinding != null) {
            throw new BizException(ErrorCode.CONFLICT, "该司机已绑定在本车上，无需重复绑定");
        }

        int driverType = req.driverType() == null ? 1 : req.driverType();
        if (driverType == 1) {
            long mainCount = vehicleDriverMapper.selectCount(new LambdaQueryWrapper<VehicleDriver>()
                    .eq(VehicleDriver::getVehicleId, vehicleId)
                    .eq(VehicleDriver::getStatus, 1)
                    .eq(VehicleDriver::getValidMark, 1)
                    .eq(VehicleDriver::getDriverType, 1));
            if (mainCount > 0) {
                throw new BizException(ErrorCode.CONFLICT, "本车已有主班司机，一车仅一名主班");
            }
        }

        VehicleDriver bind = new VehicleDriver();
        bind.setVehicleId(vehicleId);
        bind.setDriverId(req.driverId());
        bind.setDriverType(driverType);
        bind.setBindTime(LocalDateTime.now());
        bind.setStatus(1);
        bind.setValidMark(1);
        bind.setRemark(req.remark());
        vehicleDriverMapper.insert(bind);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindDriver(Long vehicleId, UnbindDriverRequest req) {
        requireVehicle(vehicleId);
        VehicleDriver bind = vehicleDriverMapper.selectOne(new LambdaQueryWrapper<VehicleDriver>()
                .eq(VehicleDriver::getVehicleId, vehicleId)
                .eq(VehicleDriver::getDriverId, req.driverId())
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1)
                .last("limit 1"));
        if (bind == null) {
            throw new BizException(ErrorCode.CONFLICT, "该司机当前未绑定本车");
        }
        bind.setStatus(0);
        bind.setUnbindTime(LocalDateTime.now());
        bind.setRemark(appendRemark(bind.getRemark(), req.remark()));
        vehicleDriverMapper.updateById(bind);
    }

    // ===== 内部方法 =====

    private Vehicle requireVehicle(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null || vehicle.getValidMark() == null || vehicle.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    private VehicleTerminal findActiveTerminalBinding(Long terminalId) {
        return vehicleTerminalMapper.selectOne(new LambdaQueryWrapper<VehicleTerminal>()
                .eq(VehicleTerminal::getTerminalId, terminalId)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1)
                .last("limit 1"));
    }

    private VehicleTerminal findActiveVehicleTerminal(Long vehicleId) {
        return vehicleTerminalMapper.selectOne(new LambdaQueryWrapper<VehicleTerminal>()
                .eq(VehicleTerminal::getVehicleId, vehicleId)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1)
                .last("limit 1"));
    }

    private VehicleDriver findActiveDriverBinding(Long driverId) {
        return vehicleDriverMapper.selectOne(new LambdaQueryWrapper<VehicleDriver>()
                .eq(VehicleDriver::getDriverId, driverId)
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1)
                .last("limit 1"));
    }

    private String appendRemark(String oldRemark, String newRemark) {
        if (newRemark == null || newRemark.isBlank()) {
            return oldRemark;
        }
        if (oldRemark == null || oldRemark.isBlank()) {
            return newRemark;
        }
        return oldRemark + "；" + newRemark;
    }
}
