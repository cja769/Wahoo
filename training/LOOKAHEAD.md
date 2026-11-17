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
  --preset <selected-preset> --seed 20271143 --pairs 8192 \
  --report runs/lookahead/holdout.json
python -m training.lookahead_examples runs/lookahead/holdout.json \
  --value runs/lookahead/live.wav --report runs/lookahead/example-replays.json
```
