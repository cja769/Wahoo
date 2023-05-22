import Spot from "./Spot";
import './StartArea.css';
import {getPlayerState, getSpotState} from "../utility/marblePositionHelper";

const StartArea = (props) => {
    const current = props.player === props.boardState.currentPlayerId;
    const playerState = getPlayerState(props.boardState, props.player);
    return (
        <div className={'start-area-' + props.horizontal}>
            <div className={'start-area-' + props.vertical + '-' + props.horizontal}>
                <Spot current={current} marble={getSpotState(playerState?.homePositions, 0)} selectMarble={props.selectMarble}/>
                <Spot current={current} marble={getSpotState(playerState?.homePositions, 1)} selectMarble={props.selectMarble}/>
                <Spot current={current} marble={getSpotState(playerState?.homePositions, 2)} selectMarble={props.selectMarble}/>
                <Spot current={current} marble={getSpotState(playerState?.homePositions, 3)} selectMarble={props.selectMarble}/>
            </div>
        </div>);
}

export default StartArea;