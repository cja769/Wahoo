package com.jay.wahoo.service;

import com.jay.wahoo.WahooEnvironment;
import com.jay.wahoo.neat.Pool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingService {

    private final PoolService poolService;
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    public void init() {
//        if (activeProfile.equals("production")) {
            Mono.defer(() -> {
                while (true) {
                    try {
                        train(1);
                    } catch (Exception e) {
                        log.error("An error occurred training", e);
                    }
                }
            }).subscribeOn(Schedulers.boundedElastic()).subscribe();
//        }
    }

    public void train(int numPasses) throws IOException {
        for (int i = 0; i < numPasses; i++) {
            WahooEnvironment environment = new WahooEnvironment();
            Pool pool = poolService.getPool();
            if (!pool.isFreshPool()) {
                pool.breedNewGeneration();
            }
            pool.setFreshPool(false);
            pool.evaluateFitness(environment);
            poolService.savePool(pool);
            log.info("Generation " + pool.getGenerations() + " has been trained");
        }
    }
}
