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
    const [model, setModel] = useState("default");
    useEffect(() => {
        let active = true;
        let updates = 0;
        const loadGames = () => {
            const revision = updates;
            fetch("/api/games")
                .then(response => response.ok ? response.json() : null)
                .then(lobbies => {
                    if (active && lobbies && updates === revision) setGames(lobbies);
                })
                .catch(() => {});
        };
        loadGames();
        const disconnect = initSocket("/topic/joinable", message => {
            updates++;
            setGames(JSON.parse(message.body));
        }, loadGames);
        return () => {
            active = false;
            disconnect();
        };
    }, []);

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

    const createGame = async (event) => {
        event.preventDefault();
        setError(null);
        setDisableCreate(true);
        try {
            const response = await fetch("/api/create" + (model === "earlier" ? "?ppoPreview=true" : ""));
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
                <form className="create-game" onSubmit={createGame}>
                    <label htmlFor="ai-model">AI model</label>
                    <select id="ai-model" value={model} onChange={event => setModel(event.target.value)}>
                        <option value="default">{bots.ppoDefaultLookahead ? "Strongest approved PPO + lookahead"
                            : bots.ppoDefault ? "Strongest approved PPO" : "Heuristic"}</option>
                        {bots.ppoPreview && <option value="earlier">Earlier PPO checkpoint</option>}
                    </select>
                    <button className="button" disabled={disableCreate}>Create Game</button>
                </form>
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
                            <div className="center" key={g.gameId}>
                                <button className="joinable-game" onClick={onClick}>
                                    {g.ppoPreview ? "Earlier PPO" : bots.ppoDefault ? "Approved PPO" : "Heuristic"} · {g.gameId.slice(0, 8)}
                                </button>
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