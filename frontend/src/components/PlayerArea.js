import './PlayerArea.css'
import Spot from "./Spot";
import {getSpotState, getBoardPositions, getPlayerState} from '../utility/marblePositionHelper';

const PlayerArea = (props) => {
    const boardPositions = getBoardPositions(props.boardState, props.player);
    const playerState = getPlayerState(props.boardState, props.player);
    return (
        <div className={"player-area-" + props.orientation + " player-area"}>
            <div>
                <Spot marble={getSpotState(boardPositions, 5)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(boardPositions, 6)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(boardPositions, 7)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(boardPositions, 8)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(boardPositions, 9)} selectMarble={props.selectMarble}/>
            </div>
            <div>
                <Spot marble={getSpotState(boardPositions, 4)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerState?.safePositions, 5)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(boardPositions, 10)} selectMarble={props.selectMarble}/>
            </div>
            <div>
                <Spot marble={getSpotState(boardPositions, 3)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerState?.safePositions, 4)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(boardPositions, 11)} selectMarble={props.selectMarble}/>
            </div>
            <div>
                <Spot marble={getSpotState(boardPositions, 2)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerState?.safePositions, 3)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(boardPositions, 12)} selectMarble={props.selectMarble}/>
            </div>
            <div>
                <Spot marble={getSpotState(boardPositions, 1)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerState?.safePositions, 2)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(boardPositions, 13)} selectMarble={props.selectMarble}/>
            </div>
        </div>
    );
}

export default PlayerArea;