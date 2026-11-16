# Wahoo

The Spring Boot backend runs the four-seat, two-team game. The React frontend
uses the same HTTP and WebSocket gameplay contracts. Java manages turns,
dice, legal moves, captures, and victory in both multiplayer games and
headless simulations.

## Build and play

Use JDK 17 and Node.js. From the repository root:

```sh
(cd backend && bash gradlew test bootJar)
(cd frontend && npm ci && npm start)
java -jar backend/build/libs/wahoo-0.0.1-SNAPSHOT.jar
```

The backend listens on port 8081 by default. The old NEAT-only network and
training endpoints return HTTP 410. Training runs offline using the commands
below. A Java heuristic chooses legal moves when no model is configured.

## Train on CPU or GPU

Use Python 3.12 on Linux or macOS. Set `JAVA_HOME` to a JDK 17 installation if
`java` on your path points elsewhere. Run from the repository root:

```sh
(cd backend && bash gradlew classes)
python -m venv .venv
source .venv/bin/activate
# Linux x86-64 CPU installation. Use the matching PyTorch CUDA build for a GPU.
python -m pip install 'torch==2.7.1+cpu' --index-url https://download.pytorch.org/whl/cpu
python -m pip install -r training/requirements.txt
python -m pytest -q training
python -m training.train --run runs/wahoo --steps 500000
```

`--device cuda` enables PyTorch CUDA when a matching build is installed.
`--envs` sets the number of concurrent boards, `--workers` sets the Java worker
threads, and `--torch-threads` sets the CPU threads used by PyTorch. The
small actor is usually fast on CPU. Use `--help` for PPO options and defaults.
For repeatability use a fixed training seed and the same software versions and
PPO configuration.

Each batch makes one round trip to a persistent headless JVM. Forced moves and
turns with no legal move are processed in Java. At a decision, the client
receives one float32 observation and one four-bit mask per environment. The
same Java code encodes observations for deployed bots. Each marble index
belongs to the acting color or to the teammate's color during assisted play.

The policy observes the roll, six streak, assisted play, completed marbles, and
each marble's home, track and safe positions, progress, and exposure to the
opponents' possible next rolls. Legal action features describe the destination,
progress, captures, and destination exposure if the opposing colors moved
from the resulting board. The seat order and track positions are relative to
the actor. Java forbids passing one's own color, uses exact safe-lane moves,
and sends the furthest marble of the color in play home on each third six,
including marbles already in the safe lane. A third-six penalty is followed
by another roll. A normal roll with zero legal moves ends the turn.

The learner controls both teammates and alternates teams and starting seats.
Opponents include seeded random legal moves, a capture and safety heuristic,
recent and older frozen PPO snapshots, and the incumbent when one has passed
the benchmark. A team win pays +1, a loss pays -1, and an unfinished game
at the configured turn limit pays up to ±0.15 based on board progress
differential. All intermediate rewards are zero.

## Stop and resume

Press Ctrl+C or send SIGTERM. The trainer finishes the current PPO update and
writes a checkpoint. Checkpoints are saved periodically as well. A hard kill
can discard work since the last completed checkpoint. Only one writer can
train or promote in a run directory at a time.

```sh
python -m training.train --run runs/wahoo --resume --steps 500000
```

`--steps` adds that many decisions, rounded up to a complete PPO rollout.
Resume with the original PPO, seed, environment-count, and turn-limit
arguments from `config.json`. It restores model and optimizer state from
`latest.json` and starts fresh matches with a new deterministic seed stream.
In-flight episodes and partial rollout buffers are discarded. Keep
`step-*.wah` files to preserve the historical opponents. Each `.zip` is a
trusted Python training checkpoint and its matching `.wah` is an inference
actor. `metrics.jsonl` records decisions and rolls per second, episode
statistics, and PPO diagnostics.

## Benchmark and deploy

For example, evaluate a candidate against random, heuristic, an older
checkpoint, and the incumbent when present:

```sh
python -m training.benchmark runs/wahoo/step-000000503808.zip \
  --opponent runs/wahoo/step-000000032768.wah \
  --run runs/wahoo --pairs 512 --seed 20270101 \
  --report runs/wahoo/evaluation.json --promote
```

The benchmark defaults to 512 mirrored pairs per opponent. Every pair uses
the same per-seat dice streams with candidate and opponent teams swapped.
Starting seats rotate, and evaluation seeds use a separate namespace from
training. Reports give real wins as a fraction of all games, including
unfinished games in the denominator, game length, completed marbles,
throughput, and a 95% bootstrap interval over the paired results. Use a fresh
held-out seed for a promotion decision after any tuning or checkpoint
selection.

Promotion requires Python and Java to agree on masked decisions and logits,
at least 256 pairs per opponent at the standard 800-turn horizon, a 95%
interval with a lower win-rate bound above 50% against random, heuristic, and
any incumbent, and at most 10% unfinished games in those matchups. A passing
`--promote` atomically publishes `best.wah` and records its benchmark.
A failed promotion exits with status 2 and leaves the current deployable
actor in place.

Deploy the approved actor by passing its path at Java startup:

```sh
WAHOO_POLICY_PATH="$(pwd)/runs/wahoo/best.wah" \
  java -jar backend/build/libs/wahoo-0.0.1-SNAPSHOT.jar
```

The `.wah` binary stores a versioned copy of the PPO actor's two Linear/Tanh
layers and output layer. Java loads the weights once, applies the shared
observation schema, and picks the highest-scoring legal action. A configured
file that fails format or schema validation fails startup. Gameplay needs
only Java and the built frontend assets.
