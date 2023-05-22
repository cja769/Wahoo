import Spot from "./Spot";
import './MiddleConnector.css';
import {getBoardPositions, getPlayerState, getSpotState} from "../utility/marblePositionHelper";
import Die1 from "./svg/Die1";
import Die2 from "./svg/Die2";
import Die3 from "./svg/Die3";
import Die4 from "./svg/Die4";
import Die5 from "./svg/Die5";
import Die6 from "./svg/Die6";

const MiddleConnector = (props) => {
    const playerOne = getBoardPositions(props.boardState, props.playerOrder[0]);
    const playerTwo = getBoardPositions(props.boardState, props.playerOrder[1]);
    const playerThree = getBoardPositions(props.boardState, props.playerOrder[2]);
    const playerFour = getBoardPositions(props.boardState, props.playerOrder[3]);
    const playerOneState = getPlayerState(props.boardState, props.playerOrder[0]);
    const playerTwoState = getPlayerState(props.boardState, props.playerOrder[1]);
    const playerThreeState = getPlayerState(props.boardState, props.playerOrder[2]);
    const playerFourState = getPlayerState(props.boardState, props.playerOrder[3]);
    const diceRoll = props.diceRoll;
    return (
        <div className={'middle-area'}>
            <div className={'die'}>
                {diceRoll === 1 && <Die1/>}
                {diceRoll === 2 && <Die2/>}
                {diceRoll === 3 && <Die3/>}
                {diceRoll === 4 && <Die4/>}
                {diceRoll === 5 && <Die5/>}
                {diceRoll === 6 && <Die6/>}
            </div>
            <div className={'middle-area-row'}>
                <Spot marble={getSpotState(playerFour, 0)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(playerFourState?.safePositions, 0)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(playerFourState?.safePositions, 1)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerOne, 0)} selectMarble={props.selectMarble}/>
            </div>
            <div className={'middle-area-row'}>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerOneState?.safePositions, 0)} selectMarble={props.selectMarble}/>
            </div>
            <div className={'middle-area-row'}>
                <Spot marble={getSpotState(playerThreeState?.safePositions, 1)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerOneState?.safePositions, 1)} selectMarble={props.selectMarble}/>
            </div>
            <div className={'middle-area-row'}>
                <Spot marble={getSpotState(playerThreeState?.safePositions, 0)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
                <Spot empty={true}/>
            </div>
            <div className={'middle-area-row'}>
                <Spot marble={getSpotState(playerThree, 0)} selectMarble={props.selectMarble}/>
                <Spot empty={true}/>
                <Spot marble={getSpotState(playerTwoState?.safePositions, 1)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(playerTwoState?.safePositions, 0)} selectMarble={props.selectMarble}/>
                <Spot marble={getSpotState(playerTwo, 0)} selectMarble={props.selectMarble}/>
            </div>
        </div>
    )
}

export default MiddleConnector;