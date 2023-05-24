package com.jay.wahoo.service;

import com.jay.wahoo.WahooEnvironment;
import com.jay.wahoo.neat.Pool;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;

@Service
@Profile("production")
@RequiredArgsConstructor
public class TrainingService {

    private final PoolService poolService;

    @PostConstruct
    public void init() {
        Mono.defer(() -> {
            while (true) {
                try {
                    WahooEnvironment environment = new WahooEnvironment();
                    Pool pool = poolService.getPool();
                    pool.breedNewGeneration();
                    pool.evaluateFitness(environment);
                    poolService.savePool(pool);
                } catch (Exception e) {}
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }
}
