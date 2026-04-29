package digital.zil.hl.additional.api;

import digital.zil.hl.additional.api.dto.UserProgressResponse;
import digital.zil.hl.additional.model.User;
import digital.zil.hl.additional.service.AdditionalProgressService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API Additional service для "Дополнительно" (прогресс в процентах).
 */
@RestController
@RequestMapping("/api/progress")
public class AdditionalProgressController {
    private final AdditionalProgressService additionalProgressService;

    public AdditionalProgressController(final AdditionalProgressService additionalProgressService) {
        this.additionalProgressService = additionalProgressService;
    }

    @GetMapping("/users/{userId}")
    public UserProgressResponse getUserProgress(@PathVariable final long userId) {
        final AdditionalProgressService.UserProgressView userProgressView = additionalProgressService.getUserProgress(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        final User user = userProgressView.user();
        return new UserProgressResponse(user.id(), user.login(), user.email(), userProgressView.progressPercent());
    }

    @GetMapping("/users")
    public List<UserProgressResponse> getAllUsersProgress() {
        final Map<User, Double> allProgress = additionalProgressService.calculateAllUsersProgressPercent();
        return allProgress.entrySet().stream()
                .map(entry -> new UserProgressResponse(
                        entry.getKey().id(),
                        entry.getKey().login(),
                        entry.getKey().email(),
                        entry.getValue()
                ))
                .toList();
    }
}
