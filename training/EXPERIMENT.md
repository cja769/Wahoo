# Bounded PPO experiment

The run used 16 environments, four Java workers, one PyTorch CPU thread,
256 decisions per environment per rollout, batch size 256, four epochs,
64-unit hidden layers, and training seed 104729. The runtime was JDK 17,
Python 3.12, torch 2.7.1+cpu, and SB3 Contrib 2.7.1. There were four phases
with a total of 6,291,456 learner decisions and approximately 11 minutes
of training. Final-phase throughput was approximately 10,000 learner
decisions and 31,500 Java die rolls per second.

## Held-out matches

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

## Reproduce

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


## Continued training and promotion

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
promotion. The approved actor is `models/best.wah` and the earlier actor
remains available as `models/preview.wah`.
