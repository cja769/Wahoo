import {useEffect, useState} from "react";
import './Home.css';
import JoinGame from "./JoinGame";
import {initSocket} from "../utility/webSocket";

const Home = (props) => {
    const [games, setGames] = useState({
        games : []
    })
    const [selectedGame, setSelectedGame] = useState();
    const [disableCreate, setDisableCreate] = useState(false);
    const [bots, setBots] = useState({});
    const [error, setError] = useState(null);
    const [socketClient, setSocketClient] = useState();
    useEffect(() => {
        fetch("/api/games")
            .then(r => r.json())
            .then(j => setGames(j));
        if (!socketClient) {
            setSocketClient(initSocket('/topic/joinable', message => setGames(JSON.parse(message.body))));
        }
    }, [socketClient, setSocketClient]);

    useEffect(() => {
        fetch("/api/bots")
            .then(response => response.ok ? response.json() : {})
            .then(setBots)
            .catch(() => setBots({}));
    }, []);

    useEffect(() => {
        const found = games?.games?.find(g => g.gameId === selectedGame?.gameId);
        if (found) {
            setSelectedGame(found);
        }
    }, [games, selectedGame]);

    const createGame = async (preview = false) => {
        setError(null);
        setDisableCreate(true);
        try {
            const response = await fetch("/api/create" + (preview ? "?ppoPreview=true" : ""));
            if (!response.ok) {
                throw new Error("Could not create the game. Please try again.");
            }
            setGames(await response.json());
        } catch (error) {
            setError(error.message);
        } finally {
            setDisableCreate(false);
        }
    };

    const joinGame = (gameId, playerId, playerName) => {
        setError(null);
        fetch("/api/join", {
            method: "POST",
            headers: {
                'Accept' : 'application/json',
                'Content-Type' : 'application/json'
            },
            body: JSON.stringify({
                gameId,
                playerId,
                playerName
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error("Could not join the game. The seat may already be taken.");
            }
            return response.text();
        })
        .then(token => props.setPlayerToken(token, gameId, playerId))
        .catch(error => {
            setSelectedGame(null);
            setError(error.message);
        });
    };
    return (
        <div className={'center'}>
            <div>
                <div className={'center'}>
                    <div>
                        <button className={'button'} disabled={disableCreate} onClick={() => createGame()}>Create Game</button>
                        {bots.ppoPreview && (
                            <button className={'button'} disabled={disableCreate}
                                    onClick={() => createGame(true)}>
                                {bots.ppoDefault ? "Create Game with Earlier PPO Bots" : "Create Game with PPO Bots"}
                            </button>
                        )}
                    </div>
                </div>
                {error && <p role="alert">{error}</p>}
                {games.games && games.games?.length !== 0 && (<div className={'center'}>
                    <h2>Join a Game</h2>
                </div>)}
                {
                    games.games?.map(g => {
                        const onClick = () => {
                            setSelectedGame(g);
                        }
                        return (
                            <div className={'center'} key={g.gameId} onClick={onClick}>
                                <span className={'joinable-game'}>{g.ppoPreview ? (bots.ppoDefault ? "Earlier PPO bots · " : "PPO bots · ") : ""}{g.gameId}</span>
                            </div>
                        )
                    })
                }
            </div>
            {selectedGame && <div className={'backdrop'}></div> }
            {selectedGame && <JoinGame selectedGame={selectedGame} setSelectedGame={setSelectedGame} joinGame={joinGame}/>}
        </div>
    )
}

export default Home;