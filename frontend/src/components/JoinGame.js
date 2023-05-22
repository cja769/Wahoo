import './JoinGame.css'
import {useState} from "react";
const JoinGame = (props) => {
    const [selectedPlayer, setSelectedPlayer] = useState();
    const [inputName, setInputName] = useState();
    const [valid, setIsValid] = useState();
    const validate = (selectedPlayer, inputName) => {
        setIsValid(selectedPlayer && inputName);
    }
    const nameOnChange = event => {
        setInputName(event.target.value);
        validate(selectedPlayer, event.target.value);
    };
    const nameOnFocus = () => {
        validate(selectedPlayer, inputName);
    }
    const playerSelect = selectedPlayer => {
        setSelectedPlayer(selectedPlayer);
        validate(selectedPlayer, inputName);
    }
    const teams = props.selectedGame.players.map(p => {
        const selectOne = () => playerSelect(p.memberOne.playerId);
        const selectTwo = () => playerSelect(p.memberTwo.playerId);
        return (
                <div key={p.memberOne.playerId} className={'center'}>
                    <h4 key={p.memberOne.playerId}
                        className={
                            'player' +
                            (selectedPlayer === p.memberOne.playerId ? ' selected' : '') +
                            (!p.memberOne.isHuman ? ' selectable' : '')}
                        onClick={!p.memberOne.isHuman ? selectOne : null}>{p.memberOne.playerName}</h4>
                    <h4 key={p.memberTwo.playerId}
                        className={
                            'player' +
                            (selectedPlayer === p.memberTwo.playerId ? ' selected' : '') +
                            (!p.memberTwo.isHuman ? ' selectable' : '')}
                        onClick={!p.memberTwo.isHuman ? selectTwo : null}>{p.memberTwo.playerName}</h4>
                </div>
        );
    });
    const vs = key => (
        <div key={key} className={'vs center'}>
            <h5 key={key}>vs</h5>
        </div>);
    const teamVs = [];
    for (const i in teams) {
        teamVs.push(teams[i]);
        if (i < teams.length - 1) {
            teamVs.push(vs('vs' + i));
        }
    }

    const cancel = () => props.setSelectedGame(null);
    const joinGame = () => props.joinGame(props.selectedGame.gameId, selectedPlayer, inputName);
    return (
        <div className={'join-game-modal'}>
            <div className={'center'}>
                <h3 className={'text'}>Join Game</h3>
            </div>
            {teamVs}
            <div className={'center'}>
                <input onChange={nameOnChange} onFocus={nameOnFocus} className={'name-input'} type={'text'} placeholder={'Enter Name'}/>
            </div>
            <div className={'center'}>
                <button onClick={joinGame} disabled={!valid} className={'button'}>Join</button>
                <button onClick={cancel} className={'button'}>Cancel</button>
            </div>
        </div>
    );
}

export default JoinGame;