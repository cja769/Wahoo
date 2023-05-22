import './App.css';
import Board from "./components/Board";
import Home from "./components/Home";
import {useState} from "react";

function App() {
    const [playerToken, setPlayerToken] = useState(localStorage.getItem("playerToken"));
    const [gameId, setGameId] = useState(localStorage.getItem("gameId"));
    const [playerId, setPlayerId] = useState(localStorage.getItem("playerId"))
    const setToken = (token, gameId, playerId) => {
        localStorage.setItem("playerToken", token);
        localStorage.setItem("gameId", gameId);
        localStorage.setItem("playerId", playerId);
        setPlayerToken(token);
        setGameId(gameId);
        setPlayerId(playerId);
    };
    const clearPlayerState = () => {
        localStorage.removeItem("playerToken");
        localStorage.removeItem("gameId");
        localStorage.removeItem("playerId");
        setPlayerToken(null);
        setGameId(null);
        setPlayerId(null);
    }

    return (
      <div className={'app'}>
          {!playerToken && <Home setPlayerToken={setToken}/>}
          {playerToken && <Board playerToken={playerToken} clearPlayerState={clearPlayerState} gameId={gameId} playerId={playerId}/>}
      </div>
    );
}

export default App;
