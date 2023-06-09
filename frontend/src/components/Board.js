import PlayerArea from "./PlayerArea";
import "./Board.css"
import {useEffect, useState} from "react";
import MiddleConnector from "./MiddleConnector";
import StartArea from "./StartArea";
import {getPlayerState} from "../utility/marblePositionHelper";
import {initSocket} from "../utility/webSocket";
import getBackendUrl from "../utility/constants";

const Board = (props) => {
    const originalPlayerOrder = ['p0', 'p1', 'p2', 'p3']
    const index = originalPlayerOrder.indexOf(props.playerId);
    const playerOrder = originalPlayerOrder.slice(index);
    playerOrder.push(...originalPlayerOrder.slice(0, index));
    const [disableStartButton, setDisableStartButton] = useState(false);
    const [errorMessage, setErrorMessage] = useState();
    const defaultBoardState = {
        currentRoll: 0,
        states: [],
        gameComplete: false,
        currentPlayerId: "",
        currentPlayerName: "",
        awaitingHumanMove: false,
        gameId: "",
        hasStarted : false,
        rolledThreeSixes : false,
        diceRollUpdated : false
    };
    const [boardState, setBoardStateInternal] = useState(defaultBoardState)
    const [diceRoll, setDiceRoll] = useState(0);
    const [socketClient, setSocketClient] = useState();

    const setBoardState = val => {
        const updateDiceRoll = count => {
            if (count === 0) {
                setDiceRoll(val.currentRoll);
                if (val.rolledThreeSixes === true) {
                    setErrorMessage("Player " + val.currentPlayerName + " rolled three sixes!");
                    setTimeout(() => {
                        setErrorMessage(null);
                    }, 3000);
                    setTimeout(() => {
                        setBoardStateInternal(val);
                    }, 300);
                }
                return;
            }
            setTimeout(() => {
                let randomRoll = val.currentRoll;
                while (randomRoll === val.currentRoll) {
                    randomRoll = Math.floor(Math.random() * 6) + 1;
                }
                setDiceRoll(randomRoll);
                updateDiceRoll(count - 1);
            }, 75)
        }
        setDiceRoll(val.currentRoll);
        if (val.diceRollUpdated === true) {
            updateDiceRoll(5);
        }
        if (val.rolledThreeSixes === false) {
            setBoardStateInternal(val);
        }
    }

    useEffect(() => {
        if (props.gameId) {
            fetch(getBackendUrl() + "/api/game/" + props.gameId)
                .then(r => r.json())
                .then(body => {
                    setBoardState(body);
                });
            if (!socketClient) {
                setSocketClient(initSocket('/topic/game/' + props.gameId, message => {
                    setBoardState(JSON.parse(message.body));
                }));
            }
        }
    }, [props.gameId, socketClient]);

    const start = () => {
        setDisableStartButton(true);
        fetch(getBackendUrl() + "/api/start/" + boardState.gameId)
            .then(r => {
                if (r.status !== 200) {
                    throw new Error("Error getting next move");
                }
            })
            .catch(e => {
                setErrorMessage(e.message);
                setTimeout(() => {
                    setErrorMessage(null);
                }, 2000)
                setDisableStartButton(false)
            });
    }

    const forfeitGame = () => {
        fetch(getBackendUrl() + "/api/forfeit", {
            method: "POST",
            headers: {
                'Accept' : 'application/json',
                'Content-Type' : 'application/json'
            },
            body: JSON.stringify({
                playerId : props.playerId,
                gameId : boardState.gameId,
                playerToken : props.playerToken
            })
        }).then(() => props.clearPlayerState());
    }

    const leaveGame = () => {
        props.clearPlayerState();
    }

    const leaveUnstartedGame = () => {
        fetch(getBackendUrl() + "/api/leave", {
            method: "POST",
            headers: {
                'Accept' : 'application/json',
                'Content-Type' : 'application/json'
            },
            body: JSON.stringify({
                playerId : props.playerId,
                gameId : boardState.gameId,
                playerToken : props.playerToken
            })
        }).then(() => props.clearPlayerState());
    }

    const selectMarble = (playerId, marbleId) => {
        if (boardState.awaitingHumanMove === true) {
            fetch(getBackendUrl() + "/api/play", {
                method: "POST",
                headers: {
                    'Accept' : 'application/json',
                    'Content-Type' : 'application/json'
                },
                body: JSON.stringify({
                    playerId,
                    marbleId,
                    gameId : boardState.gameId,
                    playerToken : props.playerToken
                })
            })
            .then(r => {
                if (r.status !== 200) {
                    throw new Error("Error getting next move");
                }
            })
            .catch(e => {
                setErrorMessage(e.message);
                setTimeout(() => {
                    setErrorMessage(null);
                }, 2000)
            });
        }
    }

    const playerStates = [];
    for (const playerId of playerOrder) {
        playerStates.push(getPlayerState(boardState, playerId));
    }
    return (
        <>
            <div className={'center'}>
                <div className={boardState.endReason ? 'hidden' : ''}>
                    {boardState.hasStarted &&
                        <>
                            <button className={'button'} onClick={forfeitGame}>Forfeit</button>
                        </>
                    }
                    {!boardState.hasStarted &&
                        <>
                            <button className={'button'} disabled={disableStartButton} onClick={start}>Start</button>
                            <button className={'button'} onClick={leaveUnstartedGame}>Leave</button>
                        </>
                    }
                </div>
            </div>
            <div className={'center'}>
                <h3 className={"error-text" + (!errorMessage ? ' hidden' : '')}>{errorMessage || "This is a spacing placeholder"}</h3>
            </div>
            <div className={'center'}>
                <div className={'center player-name'}>
                    <span>{playerStates[2]?.playerName}</span>
                </div>
                <div className={'player-name-spacer'}></div>
                <div className={'center player-name'}>
                    <span>{playerStates[3]?.playerName}</span>
                </div>
            </div>
            <div className={"board-top"}>
                <StartArea vertical={'top'} horizontal={'left'} selectMarble={selectMarble} player={playerOrder[2]} boardState={boardState}/>
                <PlayerArea orientation={'top'} boardState={boardState} player={playerOrder[2]} selectMarble={selectMarble}/>
                <StartArea vertical={'top'} horizontal={'right'} selectMarble={selectMarble} player={playerOrder[3]} boardState={boardState}/>
            </div>
            <div className={'board-middle'}>
                <PlayerArea orientation={'left'} boardState={boardState} player={playerOrder[1]} selectMarble={selectMarble}/>
                <MiddleConnector selectMarble={selectMarble} boardState={boardState} diceRoll={diceRoll} playerOrder={playerOrder}/>
                <PlayerArea orientation={'right'} boardState={boardState} player={playerOrder[3]}  selectMarble={selectMarble}/>
                {boardState.endReason &&
                    <div className={'leave-area'}>
                        <button className={'button'} onClick={leaveGame}>Leave Game</button>
                    </div>
                }
            </div>
            <div className={'board-bottom'}>
                <StartArea vertical={'bottom'} horizontal={'left'} selectMarble={selectMarble} player={playerOrder[1]} boardState={boardState}/>
                <PlayerArea orientation={'bottom'} boardState={boardState} player={playerOrder[0]} selectMarble={selectMarble}/>
                <StartArea vertical={'bottom'} horizontal={'right'} selectMarble={selectMarble} player={playerOrder[0]} boardState={boardState}/>
            </div>
            <div className={'center'}>
                <div className={'center player-name'}>
                    <span>{playerStates[1]?.playerName}</span>
                </div>
                <div className={'player-name-spacer'}></div>
                <div className={'center player-name'}>
                    <span>{playerStates[0]?.playerName}</span>
                </div>
            </div>
        </>
    );
}

export default Board;