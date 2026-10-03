package com.mydbd.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.mdm.dto.VehicleSaveRequest;
import com.mydbd.mdm.entity.Dept;
import com.mydbd.mdm.entity.Terminal;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.entity.VehicleDriver;
import com.mydbd.mdm.entity.VehicleTerminal;
import com.mydbd.mdm.mapper.DeptMapper;
import com.mydbd.mdm.mapper.DriverMapper;
import com.mydbd.mdm.mapper.TerminalMapper;
import com.mydbd.mdm.mapper.VehicleDriverMapper;
import com.mydbd.mdm.mapper.MdmVehicleMapper;
import com.mydbd.mdm.mapper.VehicleTerminalMapper;
import com.mydbd.mdm.vo.OptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 车辆档案服务：分页筛选、详情聚合、CRUD 与占用校验
 */
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final MdmVehicleMapper vehicleMapper;
    private final DeptMapper deptMapper;
    private final TerminalMapper terminalMapper;
    private final DriverMapper driverMapper;
    private final VehicleTerminalMapper vehicleTerminalMapper;
    private final VehicleDriverMapper vehicleDriverMapper;

    /** 车辆分页（关键字：车牌/VIN；deptId 含其全部下级组织；受 IAM 数据范围约束） */
    public PageData<Vehicle> page(long page, long size, String keyword, Long deptId,
                                  String plateColor, Integer operationType) {
        long clampedSize = clampSize(size);
        Set<Long> scope = currentScope();
        if (scope != null && scope.isEmpty()) {
            return new PageData<>(0L, page, clampedSize, List.of());
        }
        Collection<Long> allowed = null;
        if (scope != null) {
            allowed = scope;
            if (deptId != null) {
                Set<Long> intersected = new HashSet<>(expandDeptTree(deptId));
                intersected.retainAll(scope);
                if (intersected.isEmpty()) {
                    return new PageData<>(0L, page, clampedSize, List.of());
                }
                allowed = intersected;
            }
        } else if (deptId != null) {
            allowed = expandDeptTree(deptId);
        }
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getValidMark, 1)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Vehicle::getVehicleNo, keyword)
                        .or().like(Vehicle::getVin, keyword))
                .eq(StringUtils.hasText(plateColor), Vehicle::getVehiclePlateColor, plateColor)
                .eq(operationType != null, Vehicle::getOperationType, operationType)
                .in(allowed != null, Vehicle::getDeptId, allowed == null ? List.of() : allowed)
                .orderByDesc(Vehicle::getId);
        Page<Vehicle> result = vehicleMapper.selectPage(new Page<>(page, clampedSize), wrapper);
        enrich(result.getRecords());
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 车辆下拉（受 IAM 数据范围约束） */
    public List<OptionVO> options() {
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getValidMark, 1);
        Set<Long> scope = currentScope();
        if (scope != null) {
            if (scope.isEmpty()) {
                return List.of();
            }
            wrapper.in(Vehicle::getDeptId, scope);
        }
        wrapper.orderByAsc(Vehicle::getId);
        return vehicleMapper.selectList(wrapper).stream()
                .map(v -> new OptionVO(v.getId(), v.getVehicleNo()))
                .toList();
    }

    /** 车辆详情（含组织名、当前终端、主班司机；受 IAM 数据范围约束） */
    public Vehicle detail(Long id) {
        Vehicle vehicle = requireVehicle(id);
        Set<Long> scope = currentScope();
        if (scope != null && (vehicle.getDeptId() == null || !scope.contains(vehicle.getDeptId()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在或无权查看");
        }
        enrich(List.of(vehicle));
        return vehicle;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(VehicleSaveRequest req) {
        checkPlateConflict(req.vehicleNo(), req.vehiclePlateColor(), null);
        if (req.deptId() != null && deptMapper.selectById(req.deptId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属组织不存在");
        }
        Vehicle vehicle = new Vehicle();
        applyFields(vehicle, req);
        vehicle.setValidMark(1);
        vehicleMapper.insert(vehicle);
        return vehicle.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VehicleSaveRequest req) {
        Vehicle vehicle = requireVehicle(id);
        checkPlateConflict(req.vehicleNo(), req.vehiclePlateColor(), id);
        if (req.deptId() != null && deptMapper.selectById(req.deptId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属组织不存在");
        }
        applyFields(vehicle, req);
        vehicleMapper.updateById(vehicle);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Vehicle vehicle = requireVehicle(id);
        long boundTerminals = vehicleTerminalMapper.selectCount(new LambdaQueryWrapper<VehicleTerminal>()
                .eq(VehicleTerminal::getVehicleId, id)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1));
        long boundDrivers = vehicleDriverMapper.selectCount(new LambdaQueryWrapper<VehicleDriver>()
                .eq(VehicleDriver::getVehicleId, id)
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1));
        if (boundTerminals > 0 || boundDrivers > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该车辆存在有效终端或司机绑定，请先解绑");
        }
        vehicle.setValidMark(0);
        vehicleMapper.updateById(vehicle);
    }

    // ===== 内部方法 =====

    /** 批量填充组织名、当前终端编号、主班司机姓名 */
    void enrich(List<Vehicle> vehicles) {
        if (vehicles.isEmpty()) {
            return;
        }
        List<Long> deptIds = vehicles.stream().map(Vehicle::getDeptId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectBatchIds(deptIds).forEach(d -> deptNames.put(d.getId(), d.getDeptName()));
        }

        List<Long> vehicleIds = vehicles.stream().map(Vehicle::getId).toList();
        Map<Long, String> terminalMap = loadActiveTerminalMap(vehicleIds);
        Map<Long, String> mainDriverMap = loadActiveMainDriverMap(vehicleIds);

        vehicles.forEach(v -> {
            if (v.getDeptId() != null) {
                v.setDeptName(deptNames.get(v.getDeptId()));
            }
            v.setTerminalIdentity(terminalMap.get(v.getId()));
            v.setMainDriverName(mainDriverMap.get(v.getId()));
        });
    }

    /** vehicleId -> 当前有效终端编号 */
    private Map<Long, String> loadActiveTerminalMap(List<Long> vehicleIds) {
        List<VehicleTerminal> binds = vehicleTerminalMapper.selectList(new LambdaQueryWrapper<VehicleTerminal>()
                .in(VehicleTerminal::getVehicleId, vehicleIds)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1));
        if (binds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> terminalIds = new HashMap<>();
        List<Terminal> terminals = terminalMapper.selectBatchIds(
                binds.stream().map(VehicleTerminal::getTerminalId).distinct().toList());
        terminals.forEach(t -> terminalIds.put(t.getId(), t.getIdentityCode()));
        Map<Long, String> result = new HashMap<>();
        binds.forEach(b -> result.put(b.getVehicleId(), terminalIds.get(b.getTerminalId())));
        return result;
    }

    /** vehicleId -> 当前主班司机姓名 */
    private Map<Long, String> loadActiveMainDriverMap(List<Long> vehicleIds) {
        List<VehicleDriver> binds = vehicleDriverMapper.selectList(new LambdaQueryWrapper<VehicleDriver>()
                .in(VehicleDriver::getVehicleId, vehicleIds)
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1)
                .eq(VehicleDriver::getDriverType, 1));
        if (binds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> driverNames = new HashMap<>();
        driverMapper.selectBatchIds(binds.stream().map(VehicleDriver::getDriverId).distinct().toList())
                .forEach(d -> driverNames.put(d.getId(), d.getDriverName()));
        Map<Long, String> result = new HashMap<>();
        binds.forEach(b -> result.put(b.getVehicleId(), driverNames.get(b.getDriverId())));
        return result;
    }

    private Vehicle requireVehicle(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null || vehicle.getValidMark() == null || vehicle.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    /** 当前用户数据范围：null=全部，空集合=不可见任何部门数据 */
    private Set<Long> currentScope() {
        UserInfo current = UserContext.get();
        return current == null ? null : current.deptScope();
    }

    private void checkPlateConflict(String vehicleNo, String plateColor, Long excludeId) {
        Long count = vehicleMapper.selectCount(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getVehicleNo, vehicleNo)
                .eq(Vehicle::getVehiclePlateColor, plateColor)
                .eq(Vehicle::getValidMark, 1)
                .ne(excludeId != null, Vehicle::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "车牌号+车牌颜色已存在：" + vehicleNo);
        }
    }

    /** 组织 ID 展开为自身及全部下级（用于按组织筛选车辆） */
    private List<Long> expandDeptTree(Long rootId) {
        List<Long> result = new ArrayList<>(List.of(rootId));
        List<Long> frontier = List.of(rootId);
        while (!frontier.isEmpty()) {
            List<Dept> children = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                    .in(Dept::getParentId, frontier)
                    .eq(Dept::getValidMark, 1));
            if (children.isEmpty()) {
                break;
            }
            frontier = children.stream().map(Dept::getId).toList();
            result.addAll(frontier);
        }
        return result;
    }

    private long clampSize(long size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }

    private void applyFields(Vehicle v, VehicleSaveRequest req) {
        v.setDeptId(req.deptId());
        v.setVehicleNo(req.vehicleNo());
        v.setVehiclePlateColor(req.vehiclePlateColor());
        v.setVin(emptyToNull(req.vin()));
        v.setVehicleType(req.vehicleType());
        v.setOperationType(req.operationType());
        v.setVehicleIndustry(req.vehicleIndustry());
        v.setRoadLicenseNo(req.roadLicenseNo());
        v.setProvinceCode(req.provinceCode());
        v.setCityCode(req.cityCode());
        v.setCountyCode(req.countyCode());
        v.setVehicleColor(req.vehicleColor());
        v.setVehicleBrand(req.vehicleBrand());
        v.setOwnerName(req.ownerName());
        v.setOwnerPhone(emptyToNull(req.ownerPhone()));
        v.setRemark(req.remark());
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
