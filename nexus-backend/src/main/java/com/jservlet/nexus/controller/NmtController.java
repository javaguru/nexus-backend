package com.jservlet.nexus.controller;

import com.jservlet.nexus.shared.service.monitor.NmtMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nmt/admin/monitor")
public class NmtController {

    private final NmtMonitorService nmtService;

    public NmtController(NmtMonitorService nmtService) {
        this.nmtService = nmtService;
    }

    @GetMapping(value = "/nmt", produces = "text/plain")
    public String getNativeMemory() {
        return nmtService.getNativeMemoryReport();
    }
}
