package com.jay.wahoo.config;

import com.jay.wahoo.ai.DensePolicy;
import com.jay.wahoo.ai.Policy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class PolicyConfig {
    @Bean
    public Policy computerPolicy(@Value("${wahoo.policy.path:}") String path) throws IOException {
        return path.isBlank() ? Policy.HEURISTIC : DensePolicy.load(Path.of(path));
    }
}
