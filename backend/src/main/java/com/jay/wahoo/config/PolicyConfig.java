package com.jay.wahoo.config;

import com.jay.wahoo.ai.DensePolicy;
import com.jay.wahoo.ai.Policy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Configuration
public class PolicyConfig {
    @Bean
    public BotPolicies computerPolicies(@Value("${wahoo.policy.path:}") String path,
                                       @Value("${wahoo.preview-policy.path:}") String previewPath) throws IOException {
        Policy defaultPolicy = path.isBlank() ? Policy.HEURISTIC : DensePolicy.load(Path.of(path));
        Optional<Policy> preview = previewPath.isBlank() ? Optional.empty()
            : Optional.of(DensePolicy.load(Path.of(previewPath)));
        return new BotPolicies(defaultPolicy, preview);
    }

    public record BotPolicies(Policy defaultPolicy, Optional<Policy> preview) {}
}
