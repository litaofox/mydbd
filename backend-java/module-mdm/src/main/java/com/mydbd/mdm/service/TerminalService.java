package com.mydbd.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.mdm.dto.TerminalSaveRequest;
import com.mydbd.mdm.entity.Terminal;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.entity.VehicleTerminal;
import com.mydbd.mdm.mapper.TerminalMapper;
import com.mydbd.mdm.mapper.MdmVehicleMapper;
import com.mydbd.mdm.mapper.VehicleTerminalMapper;
import com.mydbd.mdm.vo.OptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端档案服务
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalMapper terminalMapper;
    private final MdmVehicleMapper vehicleMapper;
    private final VehicleTerminalMapper vehicleTerminalMapper;

    /** 终端分页（关键字：终端编号/SIM 卡号） */
    public PageData<Terminal> page(long page, long size, String keyword, Integer status,
                                   String protocolType, String equipmentType) {
        LambdaQueryWrapper<Terminal> wrapper = new LambdaQueryWrapper<Terminal>()
                .eq(Terminal::getValidMark, 1)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Terminal::getIdentityCode, keyword)
                        .or().like(Terminal::getSimAccount, keyword))
                .eq(status != null, Terminal::getStatus, status)
                .eq(StringUtils.hasText(protocolType), Terminal::getProtocolType, protocolType)
                .eq(StringUtils.hasText(equipmentType), Terminal::getEquipmentType, equipmentType)
                .orderByDesc(Terminal::getId);
        Page<Terminal> result = terminalMapper.selectPage(
                new Page<>(page, size <= 0 ? 10 : Math.min(size, 100)), wrapper);
        enrich(result.getRecords());
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public List<OptionVO> options() {
        return terminalMapper.selectList(new LambdaQueryWrapper<Terminal>()
                        .eq(Terminal::getValidMark, 1)
                        .eq(Terminal::getStatus, 1)
                        .orderByAsc(Terminal::getId))
                .stream()
                .map(t -> new OptionVO(t.getId(), t.getIdentityCode()))
                .toList();
    }

    public Terminal detail(Long id) {
        Terminal terminal = requireTerminal(id);
        enrich(List.of(terminal));
        return terminal;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(TerminalSaveRequest req) {
        checkIdentityConflict(req.identityCode(), null);
        Terminal terminal = new Terminal();
        applyFields(terminal, req);
        terminal.setValidMark(1);
        terminalMapper.insert(terminal);
        return terminal.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TerminalSaveRequest req) {
        Terminal terminal = requireTerminal(id);
        checkIdentityConflict(req.identityCode(), id);
        applyFields(terminal, req);
        terminalMapper.updateById(terminal);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Terminal terminal = requireTerminal(id);
        long bound = vehicleTerminalMapper.selectCount(new LambdaQueryWrapper<VehicleTerminal>()
                .eq(VehicleTerminal::getTerminalId, id)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1));
        if (bound > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该终端已绑定车辆，请先解绑");
        }
        terminal.setValidMark(0);
        terminalMapper.updateById(terminal);
    }

    // ===== 内部方法 =====

    private void enrich(List<Terminal> terminals) {
        if (terminals.isEmpty()) {
            return;
        }
        List<Long> ids = terminals.stream().map(Terminal::getId).toList();
        List<VehicleTerminal> binds = vehicleTerminalMapper.selectList(new LambdaQueryWrapper<VehicleTerminal>()
                .in(VehicleTerminal::getTerminalId, ids)
                .eq(VehicleTerminal::getStatus, 1)
                .eq(VehicleTerminal::getValidMark, 1));
        if (binds.isEmpty()) {
            return;
        }
        Map<Long, String> vehicleNos = new HashMap<>();
        vehicleMapper.selectBatchIds(binds.stream().map(VehicleTerminal::getVehicleId).distinct().toList())
                .forEach(v -> vehicleNos.put(v.getId(), v.getVehicleNo()));
        Map<Long, String> boundMap = new HashMap<>();
        binds.forEach(b -> boundMap.put(b.getTerminalId(), vehicleNos.get(b.getVehicleId())));
        terminals.forEach(t -> t.setBoundVehicleNo(boundMap.get(t.getId())));
    }

    private Terminal requireTerminal(Long id) {
        Terminal terminal = terminalMapper.selectById(id);
        if (terminal == null || terminal.getValidMark() == null || terminal.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "终端不存在");
        }
        return terminal;
    }

    private void checkIdentityConflict(String identityCode, Long excludeId) {
        Long count = terminalMapper.selectCount(new LambdaQueryWrapper<Terminal>()
                .eq(Terminal::getIdentityCode, identityCode)
                .eq(Terminal::getValidMark, 1)
                .ne(excludeId != null, Terminal::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "终端编号已存在：" + identityCode);
        }
    }

    private void applyFields(Terminal t, TerminalSaveRequest req) {
        t.setIdentityCode(req.identityCode());
        t.setTlMac(req.tlMac());
        t.setOemCode(req.oemCode());
        t.setTlModel(req.tlModel());
        t.setSimAccount(req.simAccount());
        t.setProtocolType(req.protocolType());
        t.setEquipmentType(req.equipmentType());
        t.setVideoChannel(req.videoChannel());
        t.setStatus(req.status());
        t.setRemark(req.remark());
    }
}
