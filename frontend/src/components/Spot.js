import './Spot.css'
import {playerColorMap} from "../utility/constants";

const Spot = (props) => {
    const color = playerColorMap[props.marble?.playerId];
    const selectMarble = () => {
        props.selectMarble(props.marble?.playerId, props.marble?.marbleIdentifier);
    }
    return (
        <span
            data-safe-slot={props.safeSlot}
            data-safe-player={props.safePlayer}
            onClick={props.marble?.isMovable === true ? selectMarble : null}
            className={"spot" +
                (props.empty ? " blank" : "") +
                (color ? " " + color : " empty") +
                (props.marble?.isMovable === true ? " movable" : "") +
                (props.current === true ? " current" : "")}></span>
    );
}

export default Spot;