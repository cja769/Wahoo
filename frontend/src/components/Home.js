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
    useEffect(() => {
        fetch("/api/games")
            .then(r => r.json())
            .then(j => setGames(j));
        initSocket('/topic/joinable', message => setGames(JSON.parse(message.body)));
    }, []);

    useEffect(() => {
        const found = games.games.find(g => g.gameId === selectedGame?.gameId);
        if (found) {
            setSelectedGame(found);
        }
    }, [games, selectedGame]);

    const createGame = () => {
        setDisableCreate(true);
        fetch("/api/create")
            .then(() => {
                setDisableCreate(false);
            });
    }

    const joinGame = (gameId, playerId, playerName) => {
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
        .then(r => r.text())
        .then(t => props.setPlayerToken(t, gameId, playerId));
    };
    return (
        <div className={'center'}>
            <div>
                <div className={'center'}>
                    <div>
                        <button className={'button'} disabled={disableCreate} onClick={createGame}>Create Game</button>
                    </div>
                </div>
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
                                <span className={'joinable-game'}>{g.gameId}</span>
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