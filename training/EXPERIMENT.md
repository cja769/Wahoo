# Wahoo PPO training experiments


## League continuation and current actor

Two runs resumed the 23,068,672-decision incumbent and added a combined
12,582,912 decisions. The league sampled 35% incumbent, 23% fixed PPO
opponents, 20% recent snapshots, 20% heuristic, and 2% random legal play.
One run used the original learning rate of 0.0003 and the other used 0.0001.

The 27,262,976-decision actor from the lower-rate
run qualified on 8,192 fresh mirrored pairs per
matchup. It won 8,583 of 16,384 games against the incumbent
(52.39%, paired 95% CI 51.67%–53.10%).
Against the heuristic, the candidate won 58.61% and the
incumbent won 53.26% on identical dice and seats.
That was a paired gain of 5.35 percentage points
(95% CI 4.33–6.35 points).

The candidate is the default actor in `models/best.wah`. Python and Java
agreed on all 384 masked decisions checked. See the
[held-out report](benchmarks/five-space-league/held-out-promotion.json).


## Competitive continuation

The original 20,971,520-decision checkpoint was continued for 10,485,760
decisions with the standard opponent pool. The strongest candidate from that
run won 49.91% of 16,384 held-out games against the incumbent.

A second run started from the same checkpoint and spent 75% of its games
against PPO opponents, 20% against the heuristic, and 5% against random
play. The 23,068,672-decision checkpoint led the development matchups. The
run completed 25,165,824 decisions in total. Its selected checkpoint won
54.31% against the incumbent on 8,192 unseen mirrored pairs using seed
20270131. The paired 95% confidence interval was 53.61–55.00%.

| Opponent | Wins/games | Win rate | Paired 95% CI |
| --- | ---: | ---: | ---: |
| 20.97M PPO | 8,898/16,384 | 54.31% | 53.61–55.00% |
| Corrected heuristic | 8,966/16,384 | 54.72% | 54.02–55.43% |
| Random legal play | 16,242/16,384 | 99.13% | 98.99–99.27% |

This experiment originally promoted the 23,068,672-decision actor. Python and
Java agreed on all 384 parity decisions. See the
[held-out report](benchmarks/five-space-competitive/held-out-incumbent.json).

## Five-space rules, schema 3

A fresh Maskable PPO run completed **20,971,520 learner decisions**
in approximately 35.2 minutes on CPU. It averaged
9,916 learner decisions and
31,969 simulation rolls per second across
136,094 completed training episodes.
The run used seed 104729, 16 environments, four Java workers, one PyTorch
thread, 256 decisions per environment per rollout, a batch size of 256,
four PPO epochs, and two 64-unit hidden layers.

Development tests used 512 mirrored seed pairs per opponent on seed 20270108.
The interim candidate was selected on the lower of its win rates against the
heuristic and the 4,194,304-decision checkpoint. The two held-out evaluations
each used 1,024 new mirrored seed pairs per opponent, with an 800-turn limit.
The final checkpoint cleared both baseline gates. Its head-to-head interval
included 50%, so it received one independent confirmation on 4,096 new
mirrored seed pairs per opponent under the same gate.
The 95% confidence intervals resample mirrored pairs. Wins are divided by all
games, including those that did not finish.

At 16,777,216 decisions the heuristic development result dipped to 50.7%.
The [head-to-head diagnostic](benchmarks/five-space-v3/diagnostics-000016777216.json)
against the 12,582,912 checkpoint was 51.8% (95% CI 48.8–54.7%).
Against the heuristic, the aggregate team rates were 49.6% and 51.8% on
balanced seats. Median PPO approximate KL was 0.0032 and the median clip
fraction was 2.7% between those checkpoints. Training continued with the
same configuration through the scheduled cutoff.

This experiment originally promoted the **20,971,520-decision** actor. Python and Java chose the same legal action in all 384 parity checks. `models/preview.wah` uses the 4,194,304-decision actor.

### Interim checkpoint at 12,582,912 decisions

Passed promotion on seed 20270109. [Benchmark data](benchmarks/five-space-v3/held-out-interim.json).

| Opponent | Wins / games | Win rate | 95% CI | Mean turns | Mean completed marbles | Unfinished |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Random legal play | 2,024 / 2,048 | 98.8% | 98.3%–99.3% | 373.3 | 7.98 | 0 |
| Capture and safety heuristic | 1,069 / 2,048 | 52.2% | 50.2%–54.2% | 404.9 | 6.74 | 0 |
| Earlier PPO at 4,194,304 decisions | 1,139 / 2,048 | 55.6% | 53.6%–57.6% | 420.4 | 6.84 | 0 |

### Final checkpoint at 20,971,520 decisions

Failed promotion on seed 20270110. [Benchmark data](benchmarks/five-space-v3/held-out-final.json).

| Opponent | Wins / games | Win rate | 95% CI | Mean turns | Mean completed marbles | Unfinished |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Random legal play | 2,028 / 2,048 | 99.0% | 98.6%–99.4% | 375.8 | 7.98 | 0 |
| Capture and safety heuristic | 1,138 / 2,048 | 55.6% | 53.6%–57.6% | 404.7 | 6.83 | 0 |
| Earlier PPO at 4,194,304 decisions | 1,214 / 2,048 | 59.3% | 57.2%–61.3% | 426.5 | 6.93 | 0 |
| Validated PPO at 12,582,912 decisions | 1,054 / 2,048 | 51.5% | 49.3%–53.6% | 435.5 | 6.70 | 0 |

### Confirmation checkpoint at 20,971,520 decisions

Passed promotion on seed 20270111. [Benchmark data](benchmarks/five-space-v3/held-out-confirmation.json).

| Opponent | Wins / games | Win rate | 95% CI | Mean turns | Mean completed marbles | Unfinished |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Random legal play | 8,095 / 8,192 | 98.8% | 98.6%–99.0% | 373.5 | 7.98 | 0 |
| Capture and safety heuristic | 4,516 / 8,192 | 55.1% | 54.1%–56.1% | 408.1 | 6.80 | 0 |
| Earlier PPO at 4,194,304 decisions | 4,872 / 8,192 | 59.5% | 58.5%–60.5% | 428.9 | 6.93 | 0 |
| Validated PPO at 12,582,912 decisions | 4,191 / 8,192 | 51.2% | 50.1%–52.2% | 435.6 | 6.67 | 0 |

### Reproduce

Build the Java classes and install the Python dependencies as described in the
[README](../README.md). Use a fresh benchmark directory to repeat promotion.

```sh
python -m training.train --run runs/five-space-v3 --epochs 4 --seed 104729 \
  --steps 20971520 --checkpoint-every 1048576
python -m training.benchmark runs/five-space-v3/step-000012582912.zip \
  --opponent runs/five-space-v3/step-000004194304.wah \
  --run runs/five-space-v3-replay --pairs 1024 --seed 20270109 --promote
python -m training.benchmark runs/five-space-v3/step-000020971520.zip \
  --opponent runs/five-space-v3/step-000004194304.wah \
  --run runs/five-space-v3-replay --pairs 1024 --seed 20270110 --promote
python -m training.benchmark runs/five-space-v3/step-000020971520.zip \
  --opponent runs/five-space-v3/step-000004194304.wah \
  --run runs/five-space-v3-replay --pairs 4096 --seed 20270111 --promote
```

A rejected promotion returns exit code 2. Run all three benchmarks to reproduce the
selection. Each JSON report records the candidate and opponent actor hashes.

## Retired six-space rules

The historical runs below used the six-space safe lane and the previous
turn behavior. Their actors use a retired observation schema.

The run used 16 environments, four Java workers, one PyTorch CPU thread,
256 decisions per environment per rollout, batch size 256, four epochs,
64-unit hidden layers, and training seed 104729. The runtime was JDK 17,
Python 3.12, torch 2.7.1+cpu, and SB3 Contrib 2.7.1. There were four phases
with a total of 6,291,456 learner decisions and approximately 11 minutes
of training. Final-phase throughput was approximately 10,000 learner
decisions and 31,500 Java die rolls per second.

### Held-out matches

At step 6,291,456, evaluation used seed 20270104 and 512 mirrored dice pairs
per opponent. The win rate uses all 1,024 games in each matchup. The bounds
are paired bootstrap 95% confidence intervals.

| Opponent | Wins / games | Win rate | 95% CI | Mean turns | Mean candidate marbles completed | Unfinished |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Random legal moves | 1,005 / 1,024 | 98.1% | 97.3–98.9% | 390.3 | 7.96 | 0 |
| Capture and safety heuristic | 505 / 1,024 | 49.3% | 46.5–52.2% | 428.5 | 6.49 | 0 |
| PPO step 196,608 | 855 / 1,024 | 83.5% | 81.3–85.6% | 375.0 | 7.61 | 0 |
| PPO step 4,194,304 | 557 / 1,024 | 54.4% | 51.6–57.2% | 446.2 | 6.69 | 1 |

The earlier benchmark against the heuristic was 18.2% at 196,608 decisions,
43.1% at 2,097,152, and 48.6% at 4,194,304. The latest policy won direct
head-to-head matches against earlier snapshots. It did not pass the
heuristic promotion gate, so the Java app defaults to the heuristic. Python
and Java agreed on all 384 masked decisions checked for the final checkpoint.
The largest absolute logit difference was 0.00000191.

### Reproduce the historical run

From the repository root after building the Java classes and installing
the Python dependencies:

```sh
python -m training.train --run runs/experiment --epochs 4 \
  --steps 196608 --checkpoint-every 16384
python -m training.train --run runs/experiment --epochs 4 --resume \
  --steps 1900544 --checkpoint-every 65536
python -m training.train --run runs/experiment --epochs 4 --resume \
  --steps 2097152 --checkpoint-every 131072
python -m training.train --run runs/experiment --epochs 4 --resume \
  --steps 2097152 --checkpoint-every 131072

python -m training.benchmark runs/experiment/step-000006291456.zip \
  --opponent runs/experiment/step-000000196608.wah \
  --opponent runs/experiment/step-000004194304.wah \
  --run runs/experiment --pairs 512 --seed 20270104 \
  --report runs/experiment/evaluation.json --promote
```


### Continued training and promotion

Training resumed at 6,291,456 decisions and added 10,485,760, using the same
configuration and saving every 1,048,576 decisions. The final checkpoint at
16,777,216 passed the promotion gate on an independent dice seed (20270106).
Each matchup used 1,024 mirrored seed pairs.

| Opponent | Wins / games | Win rate | Paired 95% CI |
| --- | ---: | ---: | ---: |
| Random legal moves | 2,016 / 2,048 | 98.4% | 97.9–98.9% |
| Capture and safety heuristic | 1,149 / 2,048 | 56.1% | 54.1–58.1% |
| PPO step 6,291,456 | 1,101 / 2,048 | 53.8% | 51.9–55.7% |

Java and Python agreed on all 384 masked decisions checked during
promotion. Those actors remain in repository history with the six-space implementation.
