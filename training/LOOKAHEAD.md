# Bounded lookahead experiment

The candidate uses the approved shared move-scoring actor for the initial scores.
It compares close legal moves by playing a limited number of rolls on a scratch
Java game. Every candidate starts from the same board and receives the same
hypothetical dice stream for each seat within a sample. The scratch game applies
the ordinary rules, including captures, six streaks, and moving a teammate's
marble. The real game's dice stream is independent.

As the rollout ends, the value head from the same PPO checkpoint estimates
the position for the team whose roll it is. Completed games use the actual
winner. Search overrides the actor only when the estimated improvement exceeds
the preset threshold and a penalty proportional to the actor's score gap.
Equivalent starting-area marbles are evaluated together.

## Reproduce

Use the JDK and Python dependencies from the repository README. The archived
shared-policy training run contains the trusted
`runs/shared-moves-130363/step-000004194304.zip` checkpoint. The experiment uses
the matching (40a5dcd5…) actor from the previous release.

```sh
(cd backend && ./gradlew classes)
classes="$PWD/backend/build/classes/java/main"
javac --release 17 -cp "$classes" -d "$classes" training/java/*.java
export WAHOO_JAVA_CLASSPATH="$classes"
checkpoint=runs/shared-moves-130363/step-000004194304.zip
mkdir -p runs/lookahead
python -m training.export_value "$checkpoint" runs/lookahead/live.wav
python -m training.verify_value "$checkpoint" --value runs/lookahead/live.wav

for preset in short medium more-samples wide deep sensitive ppo wide-ppo; do
  python -m training.lookahead_benchmark --value runs/lookahead/live.wav \
    --preset "$preset" --seed 20271142 --pairs 1024 \
    --report "runs/lookahead/dev-$preset.json"
done
```

The initial screen also ran a sixteen-roll PPO continuation. Its cost was
considerably higher than the heuristic continuation at the same depth, so it
was excluded from the final selection. Apply the highest development win rate
among the eight completed settings to a fresh set of 8,192 mirrored pairs on
seed 20271143. Each pair swaps the candidate and
opposing teams on the same per-seat dice streams. The confidence interval
resamples dice pairs. Latency reports upper-bound percentiles using power-of-two
nanosecond buckets and includes Java observation encoding.

When the target is a single decision, `training.lookahead_examples` replays
the board from the benchmark fixture. It compares the two legal moves using
new matched dice and the frozen actor on both sides through the end of each
game. These are estimates for specific moves under that continuation policy.

```sh
python -m training.lookahead_benchmark --value runs/lookahead/live.wav \
  --preset deep --seed 20271143 --pairs 8192 \
  --report runs/lookahead/holdout.json
python -m training.lookahead_examples runs/lookahead/holdout.json \
  --value runs/lookahead/live.wav --report runs/lookahead/example-replays.json
```

## Local app configuration

Set `WAHOO_POLICY_PATH` to the shared PPO actor and `WAHOO_POLICY_VALUE_PATH`
to its matching value head. The server verifies the actor digest encoded in the
value file before accepting the pair. The approved setting uses 16 rolls and
12 samples with the heuristic for continuation moves. A blank value path uses
direct PPO scoring. Set `WAHOO_PREVIEW_POLICY_PATH` to a direct-scoring actor for
comparison in the lobby. The health endpoint exposes startup status and
`/api/bots` shows whether the default uses lookahead.

The initial holdout used seed 20271143. The independent confirmation uses
seed 20271160 with the same preset and 8,192 mirrored pairs. Use
`--opponent lookahead --opponent-value PATH` to compare a direct actor with
the approved lookahead policy. Use `--opponent-actor PATH` to select the
reference actor explicitly after changing the bundled default.

## Promotion results

The incumbent checkpoint received another 4,194,304 PPO decisions in an
independent continuation. The strongest development checkpoint received
its own holdout against both direct PPO and lookahead.

| Matchup | Wins / games | Win rate | Paired 95% CI |
| --- | ---: | ---: | ---: |
| Lookahead vs live PPO (first holdout) | 8,436/16,384 | 51.49% | 50.82%–52.17% |
| Lookahead vs live PPO (confirmation) | 8,448/16,384 | 51.56% | 50.90%–52.22% |
| Continued PPO vs live PPO | 8,113/16,384 | 49.52% | 48.82%–50.21% |
| Continued PPO vs lookahead | 7,721/16,384 | 47.13% | 46.42%–47.83% |
| Live PPO vs heuristic | 5,476/8,192 | 66.85% | 65.91%–67.80% |
| Lookahead vs heuristic | 5,574/8,192 | 68.04% | 67.11%–68.97% |
| Continued PPO vs heuristic | 5,375/8,192 | 65.61% | 64.66%–66.56% |

Lookahead passed two independent head-to-head holdouts. The confirmation
heuristic gain was +1.20 percentage points with a paired 95% confidence
interval of +0.09 to +2.27 points. See the [promotion results]
(benchmarks/lookahead/promotion.json) for exact wins, seeds, and model digests.
