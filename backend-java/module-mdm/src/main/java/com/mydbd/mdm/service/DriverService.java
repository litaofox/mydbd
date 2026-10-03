package com.mydbd.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.mdm.dto.DriverSaveRequest;
import com.mydbd.mdm.entity.Driver;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.entity.VehicleDriver;
import com.mydbd.mdm.mapper.DriverMapper;
import com.mydbd.mdm.mapper.VehicleDriverMapper;
import com.mydbd.mdm.mapper.MdmVehicleMapper;
import com.mydbd.mdm.vo.OptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 驾驶员档案服务
 */
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverMapper driverMapper;
    private final MdmVehicleMapper vehicleMapper;
    private final VehicleDriverMapper vehicleDriverMapper;

    /** 司机分页（关键字：姓名/手机号/驾驶证号） */
    public PageData<Driver> page(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<Driver> wrapper = new LambdaQueryWrapper<Driver>()
                .eq(Driver::getValidMark, 1)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Driver::getDriverName, keyword)
                        .or().like(Driver::getContactPhone, keyword)
                        .or().like(Driver::getLicenseCode, keyword))
                .eq(status != null, Driver::getStatus, status)
                .orderByDesc(Driver::getId);
        Page<Driver> result = driverMapper.selectPage(
                new Page<>(page, size <= 0 ? 10 : Math.min(size, 100)), wrapper);
        enrich(result.getRecords());
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public List<OptionVO> options() {
        return driverMapper.selectList(new LambdaQueryWrapper<Driver>()
                        .eq(Driver::getValidMark, 1)
                        .eq(Driver::getStatus, 1)
                        .orderByAsc(Driver::getId))
                .stream()
                .map(d -> new OptionVO(d.getId(), d.getDriverName()))
                .toList();
    }

    public Driver detail(Long id) {
        Driver driver = requireDriver(id);
        enrich(List.of(driver));
        return driver;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(DriverSaveRequest req) {
        checkLicenseConflict(req.licenseCode(), null);
        Driver driver = new Driver();
        applyFields(driver, req);
        driver.setValidMark(1);
        driverMapper.insert(driver);
        return driver.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DriverSaveRequest req) {
        Driver driver = requireDriver(id);
        checkLicenseConflict(req.licenseCode(), id);
        applyFields(driver, req);
        driverMapper.updateById(driver);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Driver driver = requireDriver(id);
        long bound = vehicleDriverMapper.selectCount(new LambdaQueryWrapper<VehicleDriver>()
                .eq(VehicleDriver::getDriverId, id)
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1));
        if (bound > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该司机存在有效车辆绑定，请先解绑");
        }
        driver.setValidMark(0);
        driverMapper.updateById(driver);
    }

    // ===== 内部方法 =====

    private void enrich(List<Driver> drivers) {
        if (drivers.isEmpty()) {
            return;
        }
        List<Long> ids = drivers.stream().map(Driver::getId).toList();
        List<VehicleDriver> binds = vehicleDriverMapper.selectList(new LambdaQueryWrapper<VehicleDriver>()
                .in(VehicleDriver::getDriverId, ids)
                .eq(VehicleDriver::getStatus, 1)
                .eq(VehicleDriver::getValidMark, 1));
        if (binds.isEmpty()) {
            return;
        }
        Map<Long, String> vehicleNos = new HashMap<>();
        List<Vehicle> vehicles = vehicleMapper.selectBatchIds(
                binds.stream().map(VehicleDriver::getVehicleId).distinct().toList());
        vehicles.forEach(v -> vehicleNos.put(v.getId(), v.getVehicleNo()));
        Map<Long, String> boundMap = new HashMap<>();
        binds.forEach(b -> boundMap.put(b.getDriverId(), vehicleNos.get(b.getVehicleId())));
        drivers.forEach(d -> d.setBoundVehicleNo(boundMap.get(d.getId())));
    }

    private Driver requireDriver(Long id) {
        Driver driver = driverMapper.selectById(id);
        if (driver == null || driver.getValidMark() == null || driver.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "驾驶员不存在");
        }
        return driver;
    }

    private void checkLicenseConflict(String licenseCode, Long excludeId) {
        Long count = driverMapper.selectCount(new LambdaQueryWrapper<Driver>()
                .eq(Driver::getLicenseCode, licenseCode)
                .eq(Driver::getValidMark, 1)
                .ne(excludeId != null, Driver::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "驾驶证号已存在：" + licenseCode);
        }
    }

    private void applyFields(Driver d, DriverSaveRequest req) {
        d.setDriverName(req.driverName());
        d.setSex(req.sex());
        d.setIdcard(emptyToNull(req.idcard()));
        d.setContactPhone(emptyToNull(req.contactPhone()));
        d.setLicenseCode(req.licenseCode());
        d.setLicenceCategory(req.licenceCategory());
        d.setDriverImg(emptyToNull(req.driverImg()));
        d.setStatus(req.status());
        d.setRemark(req.remark());
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
