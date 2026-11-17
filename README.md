# Wahoo

The Spring Boot backend runs the four-seat, two-team game. The React frontend
uses the same HTTP and WebSocket gameplay contracts. Java manages turns,
dice, legal moves, captures, and victory in both multiplayer games and
headless simulations.

## Build and play

Use JDK 17 and Node.js. From the repository root:

```sh
(cd frontend && npm ci && npm test -- --watchAll=false && npm run build)
(cd backend && bash gradlew test bootJar -PfrontendBuildDir="$PWD/../frontend/build")
java -jar backend/build/libs/wahoo-0.0.1-SNAPSHOT.jar
```

The backend listens on port 8081 by default. The old NEAT-only network and
training endpoints return HTTP 410. Training runs offline using the commands
below. A Java heuristic chooses legal moves when no model is configured.

## Deploy to Render

The root Dockerfile builds React and packages its static assets into the Java
jar. The UI, API, and SockJS endpoint share one HTTPS origin. For local frontend
development, the React dev server proxies API and WebSocket traffic to port
8081.

Create a Render Web Service from this repository, choose **Docker** and the
**Free** instance, and use `/actuator/health` for the health check. The
`render.yaml` blueprint contains the same defaults. Java reads Render's `PORT`
and the image caps Java heap at 256 MiB. The free service may need to wake up
before loading the page. Games and seats are in memory and reset on a restart.

In the lobby, choose an AI model and select **Create Game**. Join that game
and claim an available seat. Opposite seats are teammates, and bots fill any
unclaimed seats when the game starts. The Docker image loads the approved
five-space PPO actor with bounded lookahead on close moves. Select **Earlier
PPO checkpoint** to compare the previous direct-scoring policy. For the
benchmarks and reproduction steps, see [the lookahead experiment](training/LOOKAHEAD.md).
When running the jar outside Docker, set `WAHOO_POLICY_PATH` to load an
approved model, `WAHOO_POLICY_VALUE_PATH` for its matching lookahead value head,
and `WAHOO_PREVIEW_POLICY_PATH` to expose a comparison policy. The Java
heuristic is the default if no approved model is configured. Training runs
offline.

## Game rules

- Each color's safe lane has five positions. Slot 0 is the entrance and
  slots 1–4 are the four finishing positions. Moves must land exactly on
  the lane without passing another marble of the same color.
- A one or a six can bring a marble out of its starting area. A move on the
  main track can jump other colors and sends a different-color marble home
  if it lands on that marble.
- If there is a legal move, the player must make one. A non-six roll ends
  the turn after that move or immediately if there is no legal move.
- Rolling a six grants another roll. The player must first make a legal move
  when one is available. Sixes count toward the streak even when no move is
  available.
- Each third consecutive six sends the furthest marble of the active color
  on the main track back to its starting area. Safe and home marbles are
  protected. The streak resets and the same player rolls again, even when
  there was no eligible marble.
- After filling all four finishing positions, a player controls their
  teammate's marbles. The same movement and six-penalty rules apply to that
  color. A team wins after both colors finish all four marbles.

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
python -m training.train --run runs/five-space-v3 --epochs 4 --seed 104729 \
  --steps 20971520 --checkpoint-every 1048576
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
the actor. Rules schema 3 encodes the shorter safe lane and its new
normalization. Java inference and training resume reject older schemas. Keep
the older checkpoints for reference and start a fresh run for these rules.

The learner controls both teammates, alternates teams by environment, and
samples starting seats uniformly from each environment's seeded stream.
Opponents include seeded random legal moves, a capture and safety heuristic,
recent and older frozen PPO snapshots, and the incumbent when one has passed
the benchmark. A team win pays +1, a loss pays -1, and an unfinished game
at the configured turn limit pays up to ±0.15 based on board progress
differential. All intermediate rewards are zero.

### Train the shared move-scoring policy

Pass `--shared-moves` to use one scorer for all four actions. It sees the
candidate marble, the legal move, and pooled information about each seat.
Renumbering marbles within a color only renumbers the scores. The learner
uses the same Java rules, binary observations, and legal-action mask.

To initialize from a trusted schema 3 dense checkpoint and train against
the incumbent, historical snapshots, and recent policies:

```sh
run=runs/shared-moves-104729
mkdir -p "$run/frozen"
cp models/best.wah "$run/best.wah"
cp models/preview.wah "$run/frozen/previous.wah"
python -m training.train --run "$run" --shared-moves --seed 104729 \
  --warm-start /path/to/teacher-run/dense-checkpoint.zip --epochs 4 --learning-rate 0.0001 \
  --steps 6291456 --checkpoint-every 1048576
```

The teacher checkpoint needs the original run's schema 3 `config.json`.
Use the matching teacher actor as `$run/best.wah` to reproduce the
experiment. Arbitrary compatible actor files can go in
`$run/frozen/`. The warm start learns from 524,288 Java positions and saves
a step-zero checkpoint before PPO starts. Resume with the original
`--shared-moves` configuration and omit `--warm-start`.

## Stop and resume

Press Ctrl+C or send SIGTERM. The trainer finishes the current PPO update and
writes a checkpoint. Checkpoints are saved periodically as well. A hard kill
can discard work since the last completed checkpoint. Only one writer can
train or promote in a run directory at a time.

```sh
python -m training.train --run runs/five-space-v3 --epochs 4 --seed 104729 \
  --resume --steps 500000 --checkpoint-every 1048576
```

`--steps` adds that many decisions, rounded up to a complete PPO rollout.
Resume with the original PPO, seed, environment-count, and turn-limit
arguments from `config.json`. It restores model and optimizer state from
`latest.json` and starts fresh matches with a new deterministic seed stream.
In-flight episodes and partial rollout buffers are discarded. Keep
`step-*.wah` files to preserve the historical opponents. Each `.zip` is a
trusted Python training checkpoint and its matching `.wah` is an inference
actor. `metrics.jsonl` records decisions and rolls per second, episode
statistics, mean reward, and PPO diagnostics.

## Benchmark and deploy

For example, evaluate a candidate against random, heuristic, an older
checkpoint, and the incumbent when present:

```sh
python -m training.benchmark runs/five-space-v3/step-000020971520.zip \
  --opponent runs/five-space-v3/step-000004194304.wah \
  --run runs/five-space-v3 --pairs 1024 --seed 20270110 \
  --report runs/five-space-v3/evaluation.json --promote
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
WAHOO_POLICY_PATH="$(pwd)/runs/five-space-v3/best.wah" \
  java -jar backend/build/libs/wahoo-0.0.1-SNAPSHOT.jar
```

The `.wah` binary stores the actor's weights. Java accepts the dense
`WAH1` actor and the shared move-scoring `WAH2` actor under rules schema 3.
Java loads the weights once, uses the same observation encoding as training,
and picks the highest-scoring legal action. A configured
file that fails format or schema validation fails startup. Gameplay needs
only Java and the built frontend assets. Check `/actuator/health` after a
deploy and compare the actor hashes in `/api/bots` with the benchmark reports.

See the [training results](training/EXPERIMENT.md) for measurements and reproduction commands.
