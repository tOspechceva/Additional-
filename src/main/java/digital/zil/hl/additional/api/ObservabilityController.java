package digital.zil.hl.additional.api;

import digital.zil.hl.additional.service.ObservabilityService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    private final ObservabilityService observabilityService;

    public ObservabilityController(final ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping
    public Object getStats(@RequestParam(required = false) final String window) {
        if (window == null || window.isBlank()) {
            return observabilityService.getAllWindows();
        }
        return observabilityService.getWindow(window.trim().toLowerCase());
    }

    @GetMapping("/windows")
    public Map<String, Map<String, ObservabilityService.OperationStats>> getAllWindows() {
        return observabilityService.getAllWindows();
    }
}
